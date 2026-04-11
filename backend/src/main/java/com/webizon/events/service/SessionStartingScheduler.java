package com.webizon.events.service;

import com.webizon.events.model.Event;
import com.webizon.events.model.Session;
import com.webizon.events.model.SessionRegistration;
import com.webizon.events.repo.EventRepository;
import com.webizon.events.repo.SessionRegistrationRepository;
import com.webizon.events.repo.SessionRepository;
import com.webizon.notifications.model.NotificationKind;
import com.webizon.notifications.service.NotificationRequest;
import com.webizon.notifications.service.NotificationService;
import com.webizon.tenancy.AllowCrossTenant;
import com.webizon.tenancy.TenantContext;
import com.webizon.tenancy.model.Tenant;
import com.webizon.tenancy.repo.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Background sweeper that fans out {@code SESSION_STARTING}
 * notifications the moment a session transitions to LIVE (or
 * AUTO_LIVE).
 *
 * <h2>Why a sweeper instead of an AFTER_COMMIT listener?</h2>
 * An {@code @TransactionalEventListener(AFTER_COMMIT)} fires
 * exactly once on the thread that committed the status flip. If
 * that thread crashes mid-fan-out, every registration that had
 * not yet been enqueued is lost forever — the event is never
 * re-delivered because Spring's event bus is in-memory only.
 *
 * <p>A sweeper is crash-safe by construction. The status flip to
 * LIVE happens inside {@code SessionService.startLive} and commits
 * regardless of notification state; this scheduler then picks up
 * the row on its next tick, reads every active registration, and
 * fans out. A crash halfway through the fan-out leaves
 * {@code session_start_notified_at} still null so the NEXT tick
 * replays the same session from scratch. The outbox's
 * {@code (tenant_id, idempotency_key)} unique index dedupes the
 * rows that already made it through, so the retry only adds the
 * missing ones.
 *
 * <h2>Tick cadence</h2>
 * 30 seconds is the default: slow enough that admins rarely
 * observe a "click Start Live → wait for mail" lag that matters
 * in practice, fast enough that a 60k-viewer session empties its
 * fan-out in under a minute.
 *
 * <h2>Batch shape</h2>
 * Fan-out for a single session can easily reach tens of thousands
 * of notifications. The scheduler reads the registration list in
 * a paginated loop — 500 rows per page — so a huge session does
 * not materialize a pathologically large result set into one JVM
 * heap block.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@AllowCrossTenant(reason = "Background job: enumerates all tenants and drains each workspace's pending SESSION_STARTING notifications.")
public class SessionStartingScheduler {

    /** Page size for the registration read loop within a single session fan-out. */
    private static final int REGISTRATION_PAGE_SIZE = 500;

    private final TenantRepository tenantRepository;
    private final SessionRepository sessionRepository;
    private final EventRepository eventRepository;
    private final SessionRegistrationRepository registrationRepository;
    private final NotificationService notificationService;
    private final TransactionTemplate transactionTemplate;

    // ------------------------------------------------------------------
    // Scheduled entry point
    // ------------------------------------------------------------------

    @Scheduled(
            fixedDelayString   = "${webizon.notifications.session-starting.tick-interval:30s}",
            initialDelayString = "${webizon.notifications.session-starting.initial-delay:20s}")
    public void tick() {
        List<UUID> tenantIds = loadTenantIds();
        if (tenantIds.isEmpty()) {
            return;
        }

        for (UUID tenantId : tenantIds) {
            try {
                TenantContext.runWith(tenantId, () -> runForTenant(tenantId));
            } catch (Exception ex) {
                log.error("Session starting notification tick failed for tenant {}: {}",
                        tenantId, ex.getMessage(), ex);
            }
        }
    }

    // ------------------------------------------------------------------
    // Per-tenant drain
    // ------------------------------------------------------------------

    private void runForTenant(UUID tenantId) {
        List<Session> pending = transactionTemplate.execute(status ->
                sessionRepository.findPendingStartNotification());
        if (pending == null || pending.isEmpty()) {
            return;
        }

        for (Session session : pending) {
            try {
                fanOutForSession(tenantId, session.getId());
            } catch (Exception ex) {
                // One poisoned session must not stop the tick —
                // log and move on. The next tick retries the same
                // row because we never stamped it.
                log.error("SESSION_STARTING fan-out failed for session {} (tenant {}): {}",
                        session.getId(), tenantId, ex.getMessage(), ex);
            }
        }
    }

    /**
     * Fan-out a single session. Structured so the final status
     * stamp commits in its own tx after all registration rows
     * have been enqueued — a crash before the stamp leaves the
     * session eligible for re-processing next tick (safe, idempotent)
     * and a crash after the stamp leaves the fan-out complete.
     */
    private void fanOutForSession(UUID tenantId, UUID sessionId) {
        // 1. Re-read the session inside a short tx to confirm it is
        //    still airing and still pending. Another scheduler
        //    instance could have beaten us to it.
        Session session = transactionTemplate.execute(status ->
                sessionRepository.findById(sessionId).orElse(null));
        if (session == null) {
            return;
        }
        if (session.getSessionStartNotifiedAt() != null) {
            return;
        }
        if (!session.getStatus().isAiring()) {
            // Session already ended between the read and now —
            // no point sending "join now" emails for a dead show.
            // Stamp it so it drops out of the pending index.
            stampNotified(sessionId);
            return;
        }

        // 2. Load the parent event once for body rendering.
        Event event = transactionTemplate.execute(status ->
                eventRepository.findById(session.getEventId()).orElse(null));
        if (event == null) {
            log.warn("Tenant {}: session {} has no parent event; stamping to drop from pending",
                    tenantId, sessionId);
            stampNotified(sessionId);
            return;
        }

        // 3. Paginated fan-out so a 60k-viewer session does not
        //    materialize one enormous list in the heap. Each row
        //    goes through its own tx so one poisoned recipient
        //    can never stall the rest of the page.
        int page = 0;
        int enqueued = 0;
        int skipped  = 0;
        while (true) {
            var pageable = org.springframework.data.domain.PageRequest.of(page, REGISTRATION_PAGE_SIZE);
            var registrations = transactionTemplate.execute(status ->
                    registrationRepository.findAllBySessionIdOrderByRegisteredAtDesc(sessionId, pageable));
            if (registrations == null || registrations.isEmpty()) {
                break;
            }

            for (SessionRegistration row : registrations.getContent()) {
                if (!row.isActive() || !row.isEmailRemindersEnabled()) {
                    skipped++;
                    continue;
                }
                try {
                    transactionTemplate.executeWithoutResult(status ->
                            enqueueSessionStarting(event, session, row));
                    enqueued++;
                } catch (Exception ex) {
                    log.error("SESSION_STARTING enqueue failed for registration {} of session {}: {}",
                            row.getId(), sessionId, ex.getMessage(), ex);
                }
            }

            if (registrations.isLast()) {
                break;
            }
            page++;
        }

        // 4. Stamp the session so the next tick does not re-fan-out.
        stampNotified(sessionId);
        log.info("Tenant {}: SESSION_STARTING fan-out complete for session {} enqueued={} skipped={}",
                tenantId, sessionId, enqueued, skipped);
    }

    private void enqueueSessionStarting(Event event, Session session, SessionRegistration row) {
        String subject = "Эфир «" + event.getTitle() + "» уже начался";
        String body    = renderBody(event, session);
        String idempotencyKey = "session-starting:" + session.getId() + ":" + row.getProfileId();

        notificationService.enqueue(NotificationRequest.email(
                row.getProfileId(),
                NotificationKind.SESSION_STARTING,
                row.getNotifyEmail(),
                subject,
                body,
                idempotencyKey));
    }

    private void stampNotified(UUID sessionId) {
        transactionTemplate.executeWithoutResult(status -> {
            Session fresh = sessionRepository.findById(sessionId).orElse(null);
            if (fresh == null || fresh.getSessionStartNotifiedAt() != null) {
                return;
            }
            fresh.setSessionStartNotifiedAt(Instant.now());
            sessionRepository.save(fresh);
        });
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    private String renderBody(Event event, Session session) {
        StringBuilder body = new StringBuilder(512);
        body.append("Здравствуйте!\n\n")
            .append("Эфир, на который вы записались, только что начался:\n\n")
            .append("  ").append(event.getTitle()).append('\n');
        if (event.getSpeakerName() != null && !event.getSpeakerName().isBlank()) {
            body.append("  Ведущий: ").append(event.getSpeakerName()).append('\n');
        }
        body.append('\n')
            .append("Откройте страницу события и подключайтесь — лучшие места занимают первые ").append('\n')
            .append("зрители, и начало обычно самое насыщенное.\n\n")
            .append("Если вы не можете присоединиться сейчас, следите за расписанием — мы объявим ").append('\n')
            .append("следующие показы этого же события.\n\n")
            .append("— Webizon");
        return body.toString();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    @AllowCrossTenant(reason = "Enumerates the tenants table directly; must not be RLS-filtered.")
    private List<UUID> loadTenantIds() {
        try {
            return transactionTemplate.execute(status ->
                    tenantRepository.findAll().stream()
                            .map(Tenant::getId)
                            .filter(Objects::nonNull)
                            .toList());
        } catch (Exception ex) {
            log.error("Failed to enumerate tenants for SESSION_STARTING tick: {}", ex.getMessage(), ex);
            return List.of();
        }
    }
}
