package com.webizon.billing.api;

import com.webizon.billing.api.dto.BillingInvoiceResponse;
import com.webizon.billing.api.dto.ClosePeriodRequest;
import com.webizon.billing.api.dto.CurrentUsageResponse;
import com.webizon.billing.config.BillingProperties;
import com.webizon.billing.model.BillingInvoice;
import com.webizon.billing.model.BillingInvoiceLine;
import com.webizon.billing.model.BillingUsageRecord;
import com.webizon.billing.model.UsageKind;
import com.webizon.billing.repo.BillingInvoiceLineRepository;
import com.webizon.billing.repo.BillingInvoiceRepository;
import com.webizon.billing.repo.BillingUsageRecordRepository;
import com.webizon.billing.service.InvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tenant-facing billing HTTP surface.
 *
 * <h2>What lives here</h2>
 * <ul>
 *   <li><b>Current-period estimate</b> — the "how much do I owe this
 *       month?" dashboard card. Aggregates open
 *       {@link BillingUsageRecord}s for the natural calendar month
 *       containing {@code now} and applies VAT via the exact same
 *       {@link BillingProperties#computeVatMillis(long)} path that
 *       the invoice close uses, so the estimate can never drift from
 *       the eventual invoice.</li>
 *   <li><b>Invoice history</b> — paginated list and per-invoice
 *       detail with lines, all RLS-scoped to the caller's tenant.</li>
 *   <li><b>Admin state transitions</b> — manual close / issue / pay
 *       / void. The scheduled job will eventually drive the common
 *       case but the manual path stays for ad-hoc closes, replay of
 *       voided periods, and integration tests.</li>
 * </ul>
 *
 * <h2>Authorisation</h2>
 * <ul>
 *   <li>Reads: any tenant role that also sees events — owner, admin,
 *       analyst, moderator, presenter. The billing section is mostly
 *       read-only for everyone except the owner.</li>
 *   <li>State transitions: TENANT_OWNER and TENANT_ADMIN only. A
 *       moderator should never mark an invoice paid, even on
 *       purpose — that is a contractual action.</li>
 * </ul>
 *
 * <p>This controller intentionally does not expose PDF rendering
 * itself; the invoice carries a {@code pdfAssetId} and the frontend
 * presigns a download through {@code /api/v1/storage} so the file
 * access path is uniform with every other asset in the system.
 */
@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
public class BillingController {

    private final InvoiceService invoiceService;
    private final BillingInvoiceRepository invoiceRepository;
    private final BillingInvoiceLineRepository invoiceLineRepository;
    private final BillingUsageRecordRepository usageRepository;
    private final BillingProperties billingProperties;

    // ------------------------------------------------------------------
    // Current period estimate
    // ------------------------------------------------------------------

    /**
     * Snapshot of open usage for the calendar month containing the
     * current wall-clock instant.
     *
     * <p>"Open" means usage that has not yet been claimed by an
     * invoice. After a period is closed, its rows flip to
     * {@code invoice_id NOT NULL} and disappear from this query
     * until the next month's close runs.
     */
    @GetMapping("/usage/current")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_ANALYST','TENANT_MODERATOR','TENANT_PRESENTER')")
    public CurrentUsageResponse currentUsage() {
        ZonedDateTime nowUtc = ZonedDateTime.now(ZoneOffset.UTC);
        Instant periodStart = nowUtc
                .withDayOfMonth(1)
                .truncatedTo(ChronoUnit.DAYS)
                .toInstant();
        Instant periodEnd = nowUtc
                .withDayOfMonth(1)
                .truncatedTo(ChronoUnit.DAYS)
                .plusMonths(1)
                .toInstant();

        List<BillingUsageRecord> openRows =
                usageRepository.findOpenInPeriod(periodStart, periodEnd);

        Map<UsageKind, long[]> folded = CurrentUsageResponse.foldQuantity(openRows);

        List<CurrentUsageResponse.KindAggregate> breakdown = new ArrayList<>(folded.size());
        long subtotalMillis = 0L;
        for (UsageKind kind : UsageKind.values()) {
            long[] slot = folded.get(kind);
            if (slot == null) continue;
            long quantity     = slot[0];
            long amountMillis = slot[1];
            long rate         = slot[2];
            breakdown.add(new CurrentUsageResponse.KindAggregate(
                    kind,
                    quantity,
                    displayQuantityFor(kind, quantity),
                    unitLabelFor(kind),
                    rate,
                    amountMillis));
            subtotalMillis = Math.addExact(subtotalMillis, amountMillis);
        }

        long vatMillis   = billingProperties.computeVatMillis(subtotalMillis);
        long totalMillis = Math.addExact(subtotalMillis, vatMillis);

        return new CurrentUsageResponse(
                periodStart,
                periodEnd,
                breakdown,
                subtotalMillis,
                vatMillis,
                totalMillis);
    }

    // ------------------------------------------------------------------
    // Invoice history
    // ------------------------------------------------------------------

    @GetMapping("/invoices")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_ANALYST','TENANT_MODERATOR','TENANT_PRESENTER')")
    public Page<BillingInvoiceResponse> listInvoices(Pageable pageable) {
        return invoiceRepository
                .findAllByOrderByPeriodStartDesc(pageable)
                .map(BillingInvoiceResponse::header);
    }

    @GetMapping("/invoices/{invoiceId}")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_ANALYST','TENANT_MODERATOR','TENANT_PRESENTER')")
    public BillingInvoiceResponse getInvoice(@PathVariable UUID invoiceId) {
        BillingInvoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Invoice not found: " + invoiceId));
        List<BillingInvoiceLine> lines =
                invoiceLineRepository.findAllByInvoiceIdOrderByLineNumberAsc(invoiceId);
        return BillingInvoiceResponse.withLines(invoice, lines);
    }

    // ------------------------------------------------------------------
    // Admin state transitions
    // ------------------------------------------------------------------

    @PostMapping("/invoices/close")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN')")
    public BillingInvoiceResponse closePeriod(@Valid @RequestBody ClosePeriodRequest req) {
        BillingInvoice draft = invoiceService.closePeriod(req.periodStart(), req.periodEnd());
        List<BillingInvoiceLine> lines =
                invoiceLineRepository.findAllByInvoiceIdOrderByLineNumberAsc(draft.getId());
        return BillingInvoiceResponse.withLines(draft, lines);
    }

    @PostMapping("/invoices/{invoiceId}/issue")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN')")
    public BillingInvoiceResponse issue(@PathVariable UUID invoiceId) {
        BillingInvoice issued = invoiceService.issue(invoiceId);
        List<BillingInvoiceLine> lines =
                invoiceLineRepository.findAllByInvoiceIdOrderByLineNumberAsc(issued.getId());
        return BillingInvoiceResponse.withLines(issued, lines);
    }

    @PostMapping("/invoices/{invoiceId}/pay")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN')")
    public BillingInvoiceResponse markPaid(@PathVariable UUID invoiceId) {
        BillingInvoice paid = invoiceService.markPaid(invoiceId);
        List<BillingInvoiceLine> lines =
                invoiceLineRepository.findAllByInvoiceIdOrderByLineNumberAsc(paid.getId());
        return BillingInvoiceResponse.withLines(paid, lines);
    }

    @PostMapping("/invoices/{invoiceId}/void")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN')")
    public BillingInvoiceResponse voidInvoice(@PathVariable UUID invoiceId) {
        BillingInvoice voided = invoiceService.voidInvoice(invoiceId);
        List<BillingInvoiceLine> lines =
                invoiceLineRepository.findAllByInvoiceIdOrderByLineNumberAsc(voided.getId());
        return BillingInvoiceResponse.withLines(voided, lines);
    }

    // ------------------------------------------------------------------
    // Helpers — kept private so the wire shape stays a single source
    // of truth. A future refactor may push these onto UsageKind itself
    // once the display rules settle.
    // ------------------------------------------------------------------

    private static long displayQuantityFor(UsageKind kind, long naturalQuantity) {
        return switch (kind) {
            case SEAT_LIVE, SEAT_AUTO -> Math.floorDiv(naturalQuantity, 60L);
            // 30.44 days * 86400 seconds ≈ 2_629_746 seconds per month.
            case STORAGE_GB_MONTH     -> Math.floorDiv(naturalQuantity, 2_629_746L);
        };
    }

    private static String unitLabelFor(UsageKind kind) {
        return switch (kind) {
            case SEAT_LIVE, SEAT_AUTO -> "seat-minutes";
            case STORAGE_GB_MONTH     -> "GB-months";
        };
    }
}
