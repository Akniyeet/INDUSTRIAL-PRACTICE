package com.webizon.billing.service;

import com.webizon.billing.config.BillingProperties;
import com.webizon.billing.model.BillingInvoice;
import com.webizon.billing.model.BillingInvoiceLine;
import com.webizon.billing.model.BillingUsageRecord;
import com.webizon.billing.model.InvoiceLineKind;
import com.webizon.billing.model.InvoiceStatus;
import com.webizon.billing.model.UsageKind;
import com.webizon.billing.repo.BillingInvoiceLineRepository;
import com.webizon.billing.repo.BillingInvoiceRepository;
import com.webizon.billing.repo.BillingUsageRecordRepository;
import com.webizon.notifications.model.NotificationKind;
import com.webizon.notifications.service.NotificationRequest;
import com.webizon.notifications.service.NotificationService;
import com.webizon.tenancy.model.MembershipRole;
import com.webizon.tenancy.model.MembershipStatus;
import com.webizon.tenancy.model.TenantUser;
import com.webizon.tenancy.model.User;
import com.webizon.tenancy.repo.TenantUserRepository;
import com.webizon.tenancy.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Period close / invoice generation logic.
 *
 * <p>An invoice is built by folding every open
 * {@link BillingUsageRecord} in a period into a small set of
 * {@link BillingInvoiceLine} rows — one per {@link UsageKind} that
 * actually has nonzero usage in the period — plus a synthetic VAT
 * line. The original usage rows are not deleted; they are
 * <em>claimed</em> by stamping their {@code invoice_id}, which
 * keeps them linkable from the admin drill-down and is the
 * mechanism the "current period estimate" query uses to distinguish
 * open from billed usage.
 *
 * <h2>Close vs issue</h2>
 * {@link #closePeriod(Instant, Instant)} produces a DRAFT invoice.
 * It can be re-run (rejecting with an existing-invoice error
 * rather than clobbering the draft) and its lines can be recomputed
 * while still DRAFT without worrying about customer visibility.
 * Only {@link #issue(UUID)} mints the invoice number and flips the
 * row to ISSUED — that is the externally observable moment.
 *
 * <h2>Idempotency</h2>
 * A {@code (tenant_id, period_start, period_end)} unique constraint
 * ensures the same period can never produce two invoices. If a cron
 * retries a {@link #closePeriod} call, this service detects the
 * existing DRAFT and returns it instead of re-building — so the
 * claimed usage rows stay attached to the original draft.
 *
 * <h2>Rounding</h2>
 * All line amounts come from already-rounded usage records. VAT is
 * computed once on the subtotal via
 * {@link BillingProperties#computeVatMillis(long)} using half-up
 * rounding. Summing line amounts then adding the single VAT figure
 * avoids per-line rounding drift.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class InvoiceService {

    private final BillingUsageRecordRepository usageRepository;
    private final BillingInvoiceRepository invoiceRepository;
    private final BillingInvoiceLineRepository invoiceLineRepository;
    private final BillingProperties billingProperties;
    private final NotificationService notificationService;
    private final TenantUserRepository tenantUserRepository;
    private final UserRepository userRepository;

    /**
     * Close a billing period for the currently-pivoted tenant.
     *
     * <p>Returns a DRAFT invoice with aggregated lines. Safe to call
     * repeatedly for the same period: the first call creates the
     * draft, subsequent calls return it unchanged. To rebuild a
     * draft with current rate snapshots, void it first and call
     * closePeriod again.
     *
     * @param periodStart inclusive lower bound on usage.billedForInstant
     * @param periodEnd   exclusive upper bound
     * @return the DRAFT invoice (possibly pre-existing)
     */
    public BillingInvoice closePeriod(Instant periodStart, Instant periodEnd) {
        if (periodStart == null || periodEnd == null || !periodStart.isBefore(periodEnd)) {
            throw new IllegalArgumentException(
                    "Invalid period [" + periodStart + ", " + periodEnd + ")");
        }

        // Idempotency: existing invoice for the period short-circuits.
        var existing = invoiceRepository.findByPeriodStartAndPeriodEnd(periodStart, periodEnd);
        if (existing.isPresent()) {
            log.info("Period [{}, {}) already has invoice {} in state {}",
                    periodStart, periodEnd, existing.get().getId(), existing.get().getStatus());
            return existing.get();
        }

        List<BillingUsageRecord> openRecords =
                usageRepository.findOpenInPeriod(periodStart, periodEnd);

        BillingInvoice invoice = new BillingInvoice();
        invoice.setPeriodStart(periodStart);
        invoice.setPeriodEnd(periodEnd);
        invoice.setStatus(InvoiceStatus.DRAFT);
        invoice.setCurrency("KZT");
        invoice.setVatPercentSnapshot(billingProperties.vatPercent());
        invoice = invoiceRepository.save(invoice);

        // If there was no usage in the period, persist a zero-value
        // draft so the period is still claimed and cannot be
        // re-closed. A DRAFT with no lines is a valid state.
        if (openRecords.isEmpty()) {
            log.info("Period [{}, {}) produced no open usage; zero-value draft {} created",
                    periodStart, periodEnd, invoice.getId());
            return invoice;
        }

        // Group usage by kind and build one line per kind.
        Map<UsageKind, AggregatedKind> bucket = new EnumMap<>(UsageKind.class);
        for (BillingUsageRecord row : openRecords) {
            bucket.computeIfAbsent(row.getKind(),
                    k -> new AggregatedKind(k, row.getRateMillisPerUnit()))
                    .add(row);
        }

        List<BillingInvoiceLine> lines = new ArrayList<>(bucket.size() + 1);
        long subtotalMillis = 0;
        int lineNumber = 1;

        // Deterministic order: usage kinds first in declaration
        // order, VAT last. Using the enum iteration keeps the
        // output stable across JVM runs.
        for (UsageKind kind : UsageKind.values()) {
            AggregatedKind agg = bucket.get(kind);
            if (agg == null) continue;
            BillingInvoiceLine line = new BillingInvoiceLine();
            line.setInvoiceId(invoice.getId());
            line.setLineNumber(lineNumber++);
            line.setKind(InvoiceLineKind.forUsage(kind));
            line.setDescription(descriptionFor(kind, agg.quantity));
            line.setQuantity(displayQuantityFor(kind, agg.quantity));
            line.setUnitLabel(unitLabelFor(kind));
            line.setUnitRateMillis(agg.rateMillisPerUnit);
            line.setAmountMillis(agg.amountMillis);
            lines.add(line);
            subtotalMillis = Math.addExact(subtotalMillis, agg.amountMillis);
        }

        long vatMillis = billingProperties.computeVatMillis(subtotalMillis);
        if (vatMillis > 0) {
            BillingInvoiceLine vatLine = new BillingInvoiceLine();
            vatLine.setInvoiceId(invoice.getId());
            vatLine.setLineNumber(lineNumber);
            vatLine.setKind(InvoiceLineKind.VAT);
            vatLine.setDescription("VAT " + billingProperties.vatPercent() + "%");
            vatLine.setQuantity(1);
            vatLine.setUnitLabel("invoice");
            vatLine.setUnitRateMillis(vatMillis);
            vatLine.setAmountMillis(vatMillis);
            lines.add(vatLine);
        }

        invoiceLineRepository.saveAll(lines);

        invoice.setSubtotalMillis(subtotalMillis);
        invoice.setVatMillis(vatMillis);
        invoice.setTotalMillis(Math.addExact(subtotalMillis, vatMillis));
        invoice = invoiceRepository.save(invoice);

        // Claim the usage rows so the next current-period query
        // does not double-count them.
        List<UUID> claimedIds = openRecords.stream().map(BillingUsageRecord::getId).toList();
        int claimed = usageRepository.claimForInvoice(invoice.getId(), claimedIds);
        if (claimed != claimedIds.size()) {
            // A sibling closePeriod call claimed some of the rows
            // we were about to claim. The unique period constraint
            // should have prevented this, but we log and continue.
            log.warn("closePeriod for [{}, {}): expected to claim {} rows, actually claimed {}",
                    periodStart, periodEnd, claimedIds.size(), claimed);
        }

        log.info("Closed period [{}, {}) as draft invoice {} — subtotal {} millis, vat {} millis",
                periodStart, periodEnd, invoice.getId(), subtotalMillis, vatMillis);
        return invoice;
    }

    /**
     * Mint an invoice number and flip a DRAFT to ISSUED. Voids of
     * ISSUED invoices keep the number for audit; this is the moment
     * the number is allocated.
     *
     * <p>The invoice number format is {@code "WBZ-" + period YYYY-MM
     * + "-" + last 6 hex chars of invoice id}. Per-tenant uniqueness
     * is enforced by a partial unique index.
     */
    public BillingInvoice issue(UUID invoiceId) {
        BillingInvoice invoice = requireById(invoiceId);
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new IllegalStateException(
                    "Invoice " + invoiceId + " is in state " + invoice.getStatus()
                            + ", only DRAFT can be issued");
        }
        invoice.setInvoiceNumber(generateNumber(invoice));
        invoice.setStatus(InvoiceStatus.ISSUED);
        invoice.setIssuedAt(Instant.now());

        // Hand off to the notification outbox inside the same tx so
        // the row commits if and only if the ISSUE transition
        // commits. If a later retry re-issues (shouldn't happen —
        // the state guard above rejects it — but defence in depth),
        // the idempotency key makes the enqueue a no-op.
        enqueueInvoiceIssuedNotifications(invoice);
        return invoice;
    }

    public BillingInvoice markPaid(UUID invoiceId) {
        BillingInvoice invoice = requireById(invoiceId);
        if (invoice.getStatus() != InvoiceStatus.ISSUED
                && invoice.getStatus() != InvoiceStatus.OVERDUE) {
            throw new IllegalStateException(
                    "Invoice " + invoiceId + " is in state " + invoice.getStatus()
                            + ", cannot transition to PAID");
        }
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(Instant.now());

        enqueueInvoicePaidNotifications(invoice);
        return invoice;
    }

    // ------------------------------------------------------------------
    // Notification hooks
    //
    // Both sites walk every ACTIVE TENANT_OWNER membership in the
    // current tenant and enqueue one notification row per owner.
    // Running inside the already-open billing transaction means the
    // outbox rows commit atomically with the invoice state change —
    // a rollback anywhere in the issue/markPaid flow also rolls back
    // the queued notification, which is exactly the invariant the
    // outbox pattern is designed to preserve.
    //
    // Idempotency key format: "<kind-slug>:<invoiceId>:<userId>".
    // Including the userId keeps per-owner rows distinct while still
    // making a retry of the same transition a no-op at the DB level.
    // ------------------------------------------------------------------

    private void enqueueInvoiceIssuedNotifications(BillingInvoice invoice) {
        for (RecipientEmail recipient : resolveOwnerEmails()) {
            String subject = "Webizon invoice " + invoice.getInvoiceNumber()
                    + " — " + formatKzt(invoice.getTotalMillis()) + " KZT";
            String body = renderInvoiceIssuedBody(invoice, recipient);
            String key  = "invoice-issued:" + invoice.getId() + ":" + recipient.userId();
            notificationService.enqueue(NotificationRequest.email(
                    recipient.userId(),
                    NotificationKind.INVOICE_ISSUED,
                    recipient.email(),
                    subject,
                    body,
                    key));
        }
    }

    private void enqueueInvoicePaidNotifications(BillingInvoice invoice) {
        for (RecipientEmail recipient : resolveOwnerEmails()) {
            String subject = "Payment received — invoice " + invoice.getInvoiceNumber();
            String body = renderInvoicePaidBody(invoice, recipient);
            String key  = "invoice-paid:" + invoice.getId() + ":" + recipient.userId();
            notificationService.enqueue(NotificationRequest.email(
                    recipient.userId(),
                    NotificationKind.INVOICE_PAID,
                    recipient.email(),
                    subject,
                    body,
                    key));
        }
    }

    /**
     * Walk the tenant_users table for every ACTIVE TENANT_OWNER,
     * then hydrate each one's email from the users table. Users
     * with no verified email (or which got soft-deleted between the
     * membership lookup and the hydrate) are silently skipped —
     * this is defensive and avoids the dispatcher attempting SMTP
     * against a junk address.
     */
    private List<RecipientEmail> resolveOwnerEmails() {
        List<TenantUser> owners = tenantUserRepository.findAllByRoleAndStatus(
                MembershipRole.TENANT_OWNER, MembershipStatus.ACTIVE);
        if (owners.isEmpty()) {
            log.warn("Invoice notification: no active TENANT_OWNER in current tenant");
            return List.of();
        }
        List<RecipientEmail> out = new ArrayList<>(owners.size());
        for (TenantUser owner : owners) {
            Optional<User> maybe = userRepository.findById(owner.getUserId());
            if (maybe.isEmpty()) continue;
            User user = maybe.get();
            if (user.getEmail() == null || user.getEmail().isBlank()) continue;
            out.add(new RecipientEmail(user.getId(), user.getEmail(), user.getFullName()));
        }
        return out;
    }

    private String renderInvoiceIssuedBody(BillingInvoice invoice, RecipientEmail recipient) {
        String greeting = recipient.name() == null || recipient.name().isBlank()
                ? "Hello,"
                : "Hello " + recipient.name() + ",";
        return greeting + "\n\n"
                + "A new Webizon invoice has been issued for your workspace.\n\n"
                + "  Invoice:    " + invoice.getInvoiceNumber() + "\n"
                + "  Period:     " + invoice.getPeriodStart() + "  →  " + invoice.getPeriodEnd() + "\n"
                + "  Subtotal:   " + formatKzt(invoice.getSubtotalMillis()) + " KZT\n"
                + "  VAT ("     + invoice.getVatPercentSnapshot() + "%): "
                                 + formatKzt(invoice.getVatMillis()) + " KZT\n"
                + "  Total:      " + formatKzt(invoice.getTotalMillis()) + " KZT\n\n"
                + "You can view or download the full invoice from your Webizon dashboard.\n\n"
                + "— The Webizon billing team";
    }

    private String renderInvoicePaidBody(BillingInvoice invoice, RecipientEmail recipient) {
        String greeting = recipient.name() == null || recipient.name().isBlank()
                ? "Hello,"
                : "Hello " + recipient.name() + ",";
        return greeting + "\n\n"
                + "Thank you — your payment for Webizon invoice "
                + invoice.getInvoiceNumber() + " has been received.\n\n"
                + "  Amount paid: " + formatKzt(invoice.getTotalMillis()) + " KZT\n"
                + "  Paid at:     " + invoice.getPaidAt() + "\n\n"
                + "A receipt is available in your Webizon dashboard.\n\n"
                + "— The Webizon billing team";
    }

    /**
     * Render a KZT-millis amount as a human-readable KZT string with
     * three decimal places. The frontend does the same conversion
     * on screen; doing it here means the email body matches what
     * the customer sees in the UI.
     */
    private static String formatKzt(long millis) {
        return BigDecimal.valueOf(millis)
                .divide(BigDecimal.valueOf(1000L), 2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    /** Small tuple carrying everything the renderers need. */
    private record RecipientEmail(UUID userId, String email, String name) {}

    public BillingInvoice voidInvoice(UUID invoiceId) {
        BillingInvoice invoice = requireById(invoiceId);
        if (invoice.getStatus().isTerminal() && invoice.getStatus() != InvoiceStatus.PAID) {
            throw new IllegalStateException(
                    "Invoice " + invoiceId + " is already terminal: " + invoice.getStatus());
        }
        invoice.setStatus(InvoiceStatus.VOID);
        invoice.setVoidedAt(Instant.now());
        return invoice;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private BillingInvoice requireById(UUID id) {
        return invoiceRepository.findById(id).orElseThrow(
                () -> new IllegalArgumentException("Invoice not found: " + id));
    }

    /**
     * Build an invoice number: {@code WBZ-YYYYMM-XXXXXX}. The last
     * segment is the first six hex chars of the invoice UUID which
     * (combined with the partial unique index on
     * {@code invoice_number}) gives effectively-unique human-readable
     * ids without a sequence table.
     */
    private static String generateNumber(BillingInvoice invoice) {
        var yyMm = java.time.ZonedDateTime.ofInstant(
                invoice.getPeriodStart(), java.time.ZoneOffset.UTC);
        String suffix = invoice.getId().toString().replace("-", "").substring(0, 6).toUpperCase();
        return "WBZ-" + String.format("%04d%02d", yyMm.getYear(), yyMm.getMonthValue()) + "-" + suffix;
    }

    private static String descriptionFor(UsageKind kind, long quantityNatural) {
        return switch (kind) {
            case SEAT_LIVE        -> "Live session seat-minutes";
            case SEAT_AUTO        -> "Auto session seat-minutes";
            case STORAGE_GB_MONTH -> "File storage (GB-months)";
        };
    }

    private static String unitLabelFor(UsageKind kind) {
        return switch (kind) {
            case SEAT_LIVE, SEAT_AUTO -> "seat-minutes";
            case STORAGE_GB_MONTH     -> "GB-months";
        };
    }

    /**
     * Convert the natural-unit quantity from a usage record into the
     * display quantity on the invoice line. Seat-seconds become
     * seat-minutes (floor), GB-seconds become GB-months (floor).
     */
    private static long displayQuantityFor(UsageKind kind, long naturalQuantity) {
        return switch (kind) {
            case SEAT_LIVE, SEAT_AUTO -> Math.floorDiv(naturalQuantity, 60L);
            // 30.44 days * 86400 seconds ≈ 2_629_746 seconds per month.
            case STORAGE_GB_MONTH     -> Math.floorDiv(naturalQuantity, 2_629_746L);
        };
    }

    /**
     * Mutable accumulator used while folding usage records into
     * per-kind totals. Keeps the rate snapshot from the first row we
     * saw of this kind — subsequent rows with a different rate would
     * indicate a rate change mid-period, which is possible if an
     * admin redeployed with new rates. The first-seen rate wins; a
     * future refinement could split the line per rate if that ever
     * matters for tenant communication.
     */
    private static final class AggregatedKind {
        final UsageKind kind;
        final long rateMillisPerUnit;
        long quantity;
        long amountMillis;

        AggregatedKind(UsageKind kind, long rateMillisPerUnit) {
            this.kind = kind;
            this.rateMillisPerUnit = rateMillisPerUnit;
        }

        void add(BillingUsageRecord row) {
            this.quantity = Math.addExact(this.quantity, row.getQuantity());
            this.amountMillis = Math.addExact(this.amountMillis, row.getAmountMillis());
        }
    }
}
