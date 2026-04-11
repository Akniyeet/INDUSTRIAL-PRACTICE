package com.webizon.billing.service;

import com.webizon.events.model.Session;
import com.webizon.events.repo.SessionRepository;
import com.webizon.tenancy.AllowCrossTenant;
import com.webizon.tenancy.TenantContext;
import com.webizon.tenancy.model.Tenant;
import com.webizon.tenancy.repo.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Background tick loop that drives the billing usage sweep.
 *
 * <p>Every tick the scheduler:
 * <ol>
 *   <li>Enumerates every tenant (the {@code tenants} table has no
 *       RLS so a plain findAll() is legal here).</li>
 *   <li>For each tenant, pivots into that tenant's scope via
 *       {@link TenantContext#runWith(UUID, Runnable)} so Hibernate
 *       multi-tenancy and PostgreSQL RLS both start filtering to
 *       the current workspace.</li>
 *   <li>Loads every finalized session that has not yet been
 *       metered (the partial index {@code sessions_billing_unmetered_idx}
 *       keeps this O(backlog)).</li>
 *   <li>Hands each session to {@link UsageMeteringService#meterSession(Session)},
 *       which aggregates the attendance rows and writes a single
 *       {@code billing_usage_records} row plus stamps the session.</li>
 * </ol>
 *
 * <h2>Why a sweeper and not an event listener?</h2>
 * The earlier design considered an {@code @TransactionalEventListener}
 * on a {@code SessionFinalizedEvent} published by
 * {@code SessionService.endLive/endAuto/cancel} and by
 * {@code AutoSessionLifecycleScheduler}. Rejected because:
 * <ul>
 *   <li>Billing would silently stop if the event publisher was
 *       missed in a future code path (e.g. a new admin CLI tool
 *       that sets {@code finalizedAt} directly). The sweeper
 *       recovers such sessions automatically on the next tick.</li>
 *   <li>Event listeners run on the publisher's thread and
 *       tenant-scoped billing writes would need to traverse the
 *       same transactional boundary as the session state transition
 *       — tightly coupling billing availability to session state
 *       changes.</li>
 *   <li>An event listener cannot retry on failure without
 *       per-event persistence; the sweeper's idempotent query is
 *       the retry mechanism.</li>
 * </ul>
 *
 * <h2>Tick cadence</h2>
 * 30 seconds by default. Metering latency does not need to be
 * sub-second — invoices are monthly — but keeping the cadence under
 * a minute means the tenant dashboard's current-period estimate is
 * never more than ~30 seconds stale after a session ends.
 *
 * <h2>Failure isolation</h2>
 * Each tenant runs inside its own programmatic transaction so a
 * thrown exception only rolls back that tenant's batch. The
 * enclosing try/catch logs and continues to the next tenant.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@AllowCrossTenant(reason = "Background job: enumerates all tenants and bills each workspace's finalized sessions.")
public class UsageMeteringScheduler {

    /**
     * Per-tenant batch cap. A huge backlog (e.g. after a multi-day
     * outage) is still processed, one batch per tick, so no single
     * transaction holds thousands of rows in the persistence
     * context.
     */
    private static final int MAX_SESSIONS_PER_TENANT_PER_TICK = 200;

    private final TenantRepository tenantRepository;
    private final SessionRepository sessionRepository;
    private final UsageMeteringService usageMeteringService;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(
            fixedDelayString   = "${webizon.billing.usage-sweep-tick-ms:30000}",
            initialDelayString = "${webizon.billing.usage-sweep-initial-delay-ms:10000}")
    public void tick() {
        List<UUID> tenantIds = loadTenantIds();
        if (tenantIds.isEmpty()) {
            return;
        }

        for (UUID tenantId : tenantIds) {
            try {
                TenantContext.runWith(tenantId,
                        () -> transactionTemplate.executeWithoutResult(
                                status -> runForTenant(tenantId)));
            } catch (Exception ex) {
                // A failure in one tenant's batch must not stop the
                // loop — log and move on. The next tick will pick
                // up any rows the failing batch left behind.
                log.error("Usage sweep failed for tenant {}: {}",
                        tenantId, ex.getMessage(), ex);
            }
        }
    }

    /**
     * Process up to {@link #MAX_SESSIONS_PER_TENANT_PER_TICK}
     * un-metered finalized sessions for the currently-pivoted
     * tenant. Runs inside the caller's transaction so all session
     * stamps and usage rows commit together.
     */
    private void runForTenant(UUID tenantId) {
        List<Session> backlog = sessionRepository.findUnmeteredFinalizedSessions();
        if (backlog.isEmpty()) {
            return;
        }

        int limit = Math.min(MAX_SESSIONS_PER_TENANT_PER_TICK, backlog.size());
        int billed = 0;
        for (int i = 0; i < limit; i++) {
            Session session = backlog.get(i);
            usageMeteringService.meterSession(session);
            billed++;
        }

        if (log.isDebugEnabled()) {
            log.debug("Metered {} session(s) for tenant {} ({} still queued)",
                    billed, tenantId, backlog.size() - billed);
        }
    }

    @AllowCrossTenant(reason = "Enumerates the tenants table directly; must not be RLS-filtered.")
    private List<UUID> loadTenantIds() {
        return tenantRepository.findAll().stream()
                .map(Tenant::getId)
                .toList();
    }
}
