package com.webizon.notifications.service;

import com.webizon.notifications.config.NotificationProperties;
import com.webizon.notifications.model.NotificationChannel;
import com.webizon.notifications.model.NotificationOutboxEntry;
import com.webizon.notifications.model.NotificationStatus;
import com.webizon.notifications.repo.NotificationOutboxRepository;
import com.webizon.tenancy.AllowCrossTenant;
import com.webizon.tenancy.TenantContext;
import com.webizon.tenancy.model.Tenant;
import com.webizon.tenancy.repo.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Background sweeper that drains the notification outbox.
 *
 * <p>Runs on a configurable cadence (default 20 seconds) and for
 * every tenant:
 * <ol>
 *   <li>Reclaims stale {@code SENDING} rows whose last update is
 *       older than {@code staleSendingTimeout} — these are rows a
 *       previous tick claimed but failed to transition (process
 *       death mid-send). Reset to PENDING with attempt count
 *       incremented so they retry but never stall.</li>
 *   <li>Loads dispatchable rows (PENDING / FAILED with
 *       {@code nextAttemptAt <= now}) ordered by due-time ascending,
 *       bounded by {@code batchSize}.</li>
 *   <li>For each row, flips to SENDING inside a dedicated tx, calls
 *       the channel-specific sender, and then flips to SENT or
 *       FAILED in a second tx. Using two transactions (not one
 *       around the send) keeps the SMTP I/O out of any DB locks —
 *       a slow mail server can NOT cascade into row-lock starvation
 *       on the outbox table.</li>
 * </ol>
 *
 * <h2>Why a sweeper and not a Kafka topic?</h2>
 * Notifications are durable <em>already</em> — the outbox row is
 * the durable log. Layering Kafka on top would require a second
 * consumer position, a second at-least-once guarantee, and an extra
 * serialisation format. The sweeper gives us exactly the same
 * at-least-once delivery semantics with one moving part.
 *
 * <h2>Failure isolation</h2>
 * Each row runs inside its own programmatic transaction. A send
 * failure only rolls back that row's status flip, and the loop
 * catches every exception so one broken recipient cannot stall the
 * batch.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@AllowCrossTenant(reason = "Background job: enumerates all tenants and drains each workspace's notification outbox.")
public class NotificationDispatcher {

    private final TenantRepository tenantRepository;
    private final NotificationOutboxRepository outboxRepository;
    private final NotificationProperties properties;
    private final EmailSender emailSender;
    private final TransactionTemplate transactionTemplate;

    // ------------------------------------------------------------------
    // Scheduled entry point
    // ------------------------------------------------------------------

    @Scheduled(
            fixedDelayString   = "${webizon.notifications.dispatch.tick-interval:20s}",
            initialDelayString = "${webizon.notifications.dispatch.initial-delay:15s}")
    public void tick() {
        List<UUID> tenantIds = loadTenantIds();
        if (tenantIds.isEmpty()) {
            return;
        }

        for (UUID tenantId : tenantIds) {
            try {
                TenantContext.runWith(tenantId, () -> runForTenant(tenantId));
            } catch (Exception ex) {
                // A failure in one tenant's batch must not stop the
                // loop — log and move on to the next tenant.
                log.error("Notification dispatch failed for tenant {}: {}",
                        tenantId, ex.getMessage(), ex);
            }
        }
    }

    // ------------------------------------------------------------------
    // Per-tenant drain
    // ------------------------------------------------------------------

    private void runForTenant(UUID tenantId) {
        // 1. Heal stuck SENDING rows.
        reclaimStaleSending();

        // 2. Load dispatchable batch. A fresh list per tenant keeps
        //    the persistence context small — the dispatcher never
        //    holds more than batchSize rows attached at a time.
        List<NotificationOutboxEntry> batch =
                transactionTemplate.execute(status ->
                        outboxRepository.findDispatchable(
                                Instant.now(),
                                PageRequest.of(0, properties.dispatch().batchSize())));
        if (batch == null || batch.isEmpty()) {
            return;
        }

        int sent   = 0;
        int failed = 0;
        for (NotificationOutboxEntry entry : batch) {
            // Each row gets its own short tx for claim, then a send
            // outside the tx, then another tx for the outcome. This
            // structure is the reason SMTP latency cannot block
            // other rows — no DB row is held locked during the send.
            UUID entryId = entry.getId();
            try {
                boolean claimed = transactionTemplate.execute(s -> claim(entryId));
                if (!Boolean.TRUE.equals(claimed)) {
                    continue;
                }

                SendOutcome outcome = dispatch(entry);

                transactionTemplate.executeWithoutResult(s -> record(entryId, outcome));

                if (outcome.success) sent++;
                else                 failed++;
            } catch (Exception ex) {
                // Something unexpected — most commonly a DB error on
                // the claim step. Log and move on.
                log.error("Unhandled notification dispatch error for entry {}: {}",
                        entryId, ex.getMessage(), ex);
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("Notification dispatch tenant={} sent={} failed={} batch={}",
                    tenantId, sent, failed, batch.size());
        }
    }

    // ------------------------------------------------------------------
    // Step 1: reclaim stale SENDING rows to PENDING
    // ------------------------------------------------------------------

    private void reclaimStaleSending() {
        Duration timeout = properties.dispatch().staleSendingTimeout();
        Instant threshold = Instant.now().minus(timeout);
        List<NotificationOutboxEntry> stale =
                transactionTemplate.execute(s -> outboxRepository.findStaleSending(threshold));
        if (stale == null || stale.isEmpty()) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            for (NotificationOutboxEntry entry : stale) {
                log.warn("Reclaiming stale SENDING notification id={} (last update {}, timeout {})",
                        entry.getId(), entry.getUpdatedAt(), timeout);
                // Treat reclaim as a failed attempt so repeated
                // stalls eventually trip the dead-letter threshold.
                entry.markFailed("Reclaimed from stale SENDING state", Instant.now());
                outboxRepository.save(entry);
            }
        });
    }

    // ------------------------------------------------------------------
    // Step 2: claim a row (PENDING/FAILED → SENDING)
    // ------------------------------------------------------------------

    /**
     * @return true if the row was successfully claimed; false if
     *         another dispatcher instance beat us to it or the row
     *         is no longer dispatchable.
     */
    private boolean claim(UUID entryId) {
        NotificationOutboxEntry entry = outboxRepository.findById(entryId).orElse(null);
        if (entry == null) return false;
        if (!NotificationStatus.DISPATCHABLE.contains(entry.getStatus())) {
            return false;
        }
        entry.markSending();
        outboxRepository.save(entry);
        return true;
    }

    // ------------------------------------------------------------------
    // Step 3: dispatch (SMTP I/O, runs OUTSIDE any DB transaction)
    // ------------------------------------------------------------------

    private SendOutcome dispatch(NotificationOutboxEntry entry) {
        if (entry.getChannel() != NotificationChannel.EMAIL) {
            return SendOutcome.fail(
                    "Unsupported channel: " + entry.getChannel() + " (Phase 12 ships EMAIL only)");
        }
        try {
            emailSender.send(entry);
            return SendOutcome.ok();
        } catch (EmailSender.EmailDeliveryException ex) {
            return SendOutcome.fail(ex.getMessage());
        } catch (Exception unexpected) {
            // Defence in depth: catch everything so one buggy
            // recipient cannot crash the dispatcher thread.
            return SendOutcome.fail("Unexpected sender error: " + unexpected.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Step 4: record the outcome (SENDING → SENT | FAILED | DEAD)
    // ------------------------------------------------------------------

    private void record(UUID entryId, SendOutcome outcome) {
        NotificationOutboxEntry entry = outboxRepository.findById(entryId).orElse(null);
        if (entry == null) {
            // Row vanished (admin delete?). Nothing to do.
            return;
        }
        if (outcome.success) {
            entry.markSent(Instant.now());
        } else {
            // Use the post-this-attempt count to pick the next
            // delay. entry.attemptCount is still the pre-attempt
            // value at this point; the mark-failed method increments
            // it before deciding DEAD vs FAILED.
            Duration delay = properties.backoffFor(entry.getAttemptCount());
            Instant nextAttempt = Instant.now().plus(delay);
            entry.markFailed(outcome.error, nextAttempt);
        }
        outboxRepository.save(entry);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    @AllowCrossTenant(reason = "Enumerates the tenants table directly; must not be RLS-filtered.")
    private List<UUID> loadTenantIds() {
        return tenantRepository.findAll().stream()
                .map(Tenant::getId)
                .toList();
    }

    /** Tiny immutable carrier for the send step's result. */
    private record SendOutcome(boolean success, String error) {
        static SendOutcome ok()                  { return new SendOutcome(true,  null);  }
        static SendOutcome fail(String error)    { return new SendOutcome(false, error); }
    }
}
