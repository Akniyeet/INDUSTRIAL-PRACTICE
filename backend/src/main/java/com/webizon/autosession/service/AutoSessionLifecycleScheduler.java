package com.webizon.autosession.service;

import com.webizon.events.model.Session;
import com.webizon.events.model.SessionStatus;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Background tick loop that drives AUTO session state transitions.
 *
 * <p>Two things happen every tick:
 * <ol>
 *   <li>{@code AUTO_SCHEDULED} rows whose {@code startTime} is at or
 *       before {@code now} are promoted to {@code AUTO_LIVE}. This is
 *       what makes a scheduled 18:00 slot actually start at 18:00
 *       without an admin pressing a button.</li>
 *   <li>{@code AUTO_LIVE} rows whose wall-clock runtime has passed
 *       {@code plannedDurationSeconds} are finalized as
 *       {@code AUTO_ENDED}. Replay keeps running in the engine until
 *       this happens, so small clock skews do not cut an event
 *       short.</li>
 * </ol>
 *
 * <h2>Tenant iteration</h2>
 * The scheduler runs outside any HTTP request and therefore has no
 * ambient tenant. It reads the global {@code tenants} table (which
 * has no RLS) to enumerate workspaces, then pivots into each one via
 * {@link TenantContext#runWith(UUID, Runnable)} so Hibernate's
 * multi-tenant connection provider sets {@code app.current_tenant}
 * on the pooled connection. Per-tenant work runs in its own
 * transaction so one tenant's failure can never roll back another's.
 *
 * <h2>Tick cadence</h2>
 * A 2-second fixed delay is deliberately coarser than the replay
 * engine's cadence: lifecycle transitions are cheap-but-rare; replay
 * dispatching is expensive-but-frequent. Separating them keeps the
 * replay loop from wasting work on state checks on every tick.
 *
 * <h2>Virtual threads</h2>
 * Spring Boot's {@code spring.threads.virtual.enabled=true} runs
 * scheduled tasks on a virtual thread, so blocking JDBC calls inside
 * the loop do not tie up a platform thread.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@AllowCrossTenant(reason = "Background job: enumerates all tenants and pivots into each workspace's scope.")
public class AutoSessionLifecycleScheduler {

    /** Seconds of runtime grace above planned duration before auto-end fires. */
    private static final long END_GRACE_SECONDS = 5;

    private final TenantRepository tenantRepository;
    private final SessionRepository sessionRepository;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(fixedDelayString = "${webizon.auto-session.lifecycle-tick-ms:2000}",
               initialDelayString = "${webizon.auto-session.lifecycle-initial-delay-ms:5000}")
    public void tick() {
        List<UUID> tenantIds = loadTenantIds();
        if (tenantIds.isEmpty()) return;

        for (UUID tenantId : tenantIds) {
            try {
                TenantContext.runWith(tenantId,
                        () -> transactionTemplate.executeWithoutResult(
                                status -> runForTenant(tenantId)));
            } catch (Exception ex) {
                // Isolated failure — log and keep going. The next
                // tick retries the same rows, so a transient DB
                // error self-heals without a supervisor restart.
                log.error("AUTO session lifecycle tick failed for tenant {}: {}",
                        tenantId, ex.getMessage(), ex);
            }
        }
    }

    /**
     * Loads the full set of tenant IDs. Runs outside any tenant
     * context; the {@code tenants} table has no RLS so this is
     * safe. Bounded in size by product shape, not traffic — we
     * expect a few thousand tenants maximum at MVP scale.
     */
    private List<UUID> loadTenantIds() {
        try {
            return transactionTemplate.execute(status ->
                    tenantRepository.findAll().stream().map(Tenant::getId).toList());
        } catch (Exception ex) {
            log.error("Failed to enumerate tenants for auto-session lifecycle tick: {}",
                    ex.getMessage(), ex);
            return List.of();
        }
    }

    /**
     * Per-tenant work. Runs inside a tenant-scoped transaction so
     * both queries respect RLS and the Hibernate tenant filter.
     */
    private void runForTenant(UUID tenantId) {
        Instant now = Instant.now();
        promoteScheduledToLive(tenantId, now);
        endExpiredLiveSessions(tenantId, now);
    }

    private void promoteScheduledToLive(UUID tenantId, Instant now) {
        List<Session> ready = sessionRepository.findAutoSessionsReadyToStart(now);
        for (Session session : ready) {
            session.setStatus(SessionStatus.AUTO_LIVE);
            session.setActualStartedAt(now);
            session.setLastReplayOffsetSeconds(0);
            sessionRepository.save(session);
            log.info("Tenant {}: AUTO session {} transitioned to AUTO_LIVE (start={})",
                    tenantId, session.getId(), session.getStartTime());
        }
    }

    private void endExpiredLiveSessions(UUID tenantId, Instant now) {
        List<Session> airing = sessionRepository.findAllByStatus(SessionStatus.AUTO_LIVE);
        for (Session session : airing) {
            Instant started = session.getActualStartedAt();
            if (started == null) {
                // Defensive: should never happen after a promotion,
                // but a corrupt row here must not crash the tick.
                continue;
            }
            long planned = session.getPlannedDurationSeconds();
            long elapsed = Duration.between(started, now).getSeconds();
            if (elapsed >= planned + END_GRACE_SECONDS) {
                session.setStatus(SessionStatus.AUTO_ENDED);
                session.setActualEndedAt(now);
                session.setFinalizedAt(now);
                sessionRepository.save(session);
                log.info("Tenant {}: AUTO session {} reached planned duration ({}s) and finalized",
                        tenantId, session.getId(), planned);
            }
        }
    }
}
