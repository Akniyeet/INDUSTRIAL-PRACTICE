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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Background sweeper that drains pending session reminders into the
 * notification outbox.
 *
 * <p>Every tick and for every tenant:
 * <ol>
 *   <li>Finds sessions whose {@code startTime} has just entered the
 *       reminder lead window — the half-open interval
 *       {@code (now, now + leadTime]}.</li>
 *   <li>For each such session, pulls the pending-reminder set from
 *       {@link SessionRegistrationRepository#findPendingReminders(UUID)}
 *       — active registrations that opted in to email reminders and
 *       have not yet had a reminder enqueued.</li>
 *   <li>For each registration, calls
 *       {@link NotificationService#enqueue(NotificationRequest)} with
 *       a stable idempotency key
 *       ({@code "event-reminder:<sessionId>:<profileId>"}) and then
 *       stamps {@code reminder_sent_at} on the registration row.</li>
 * </ol>
 *
 * <h2>Why a separate column instead of only the outbox unique key</h2>
 * The outbox's partial unique index on {@code (tenant_id, idempotency_key)}
 * is the authoritative no-duplicate guard, but it only kicks in at
 * INSERT time. Without {@code reminder_sent_at} the scheduler would
 * still call {@code findPendingReminders} → {@code enqueue} for every
 * already-notified row on every tick, burning CPU and producing a
 * hot chain of idempotency short-circuits. Stamping the local row
 * means chronically-scheduled sessions drop out of the pending index
 * entirely.
 *
 * <h2>Transaction shape</h2>
 * Each registration's enqueue + mark happens inside its own
 * {@link TransactionTemplate#executeWithoutResult} block. The two
 * writes (outbox insert, registration update) must be atomic so a
 * crash between them cannot leave an outbox row without a matching
 * {@code reminder_sent_at} marker (which would cause a second
 * enqueue on the next tick, which would be swallowed by the
 * outbox's unique index, which would be wasteful but not
 * incorrect). The per-row tx also isolates one poisoned registration
 * from the rest of the batch.
 *
 * <h2>Tenant iteration</h2>
 * Same pattern as every other background sweeper: read the global
 * tenants table outside any tenant context, then pivot into each
 * workspace via {@link TenantContext#runWith(UUID, Runnable)} so
 * Hibernate + RLS apply the correct filter.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@AllowCrossTenant(reason = "Background job: enumerates all tenants and drains each workspace's pending session reminders.")
public class SessionReminderScheduler {

    private final TenantRepository tenantRepository;
    private final SessionRepository sessionRepository;
    private final EventRepository eventRepository;
    private final SessionRegistrationRepository registrationRepository;
    private final NotificationService notificationService;
    private final TransactionTemplate transactionTemplate;

    /**
     * How far ahead of a session's {@code startTime} a reminder
     * should fire. 30 minutes by default — long enough for a viewer
     * to read the mail, open the landing page, and queue up before
     * the broadcast, short enough that the reminder still feels
     * relevant.
     */
    @Value("${webizon.notifications.session-reminder.lead-time:30m}")
    private Duration leadTime;

    // ------------------------------------------------------------------
    // Scheduled entry point
    // ------------------------------------------------------------------

    @Scheduled(
            fixedDelayString   = "${webizon.notifications.session-reminder.tick-interval:60s}",
            initialDelayString = "${webizon.notifications.session-reminder.initial-delay:30s}")
    public void tick() {
        List<UUID> tenantIds = loadTenantIds();
        if (tenantIds.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        Instant windowEnd = now.plus(leadTime);

        for (UUID tenantId : tenantIds) {
            try {
                TenantContext.runWith(tenantId, () -> runForTenant(tenantId, now, windowEnd));
            } catch (Exception ex) {
                // One tenant's failure must not stop the loop — the
                // next tick retries, so a transient DB blip heals
                // without an operator touching anything.
                log.error("Session reminder tick failed for tenant {}: {}",
                        tenantId, ex.getMessage(), ex);
            }
        }
    }

    // ------------------------------------------------------------------
    // Per-tenant drain
    // ------------------------------------------------------------------

    private void runForTenant(UUID tenantId, Instant now, Instant windowEnd) {
        List<Session> dueSoon = transactionTemplate.execute(status ->
                sessionRepository.findUpcomingWithinWindow(now, windowEnd));
        if (dueSoon == null || dueSoon.isEmpty()) {
            return;
        }

        int fannedOut = 0;
        int skipped = 0;

        for (Session session : dueSoon) {
            // A per-session read of its Event — cached inside the tx
            // below so the body renderer can reuse it without a
            // second round trip.
            Event event = transactionTemplate.execute(s ->
                    eventRepository.findById(session.getEventId()).orElse(null));
            if (event == null) {
                // The event was deleted between the schedule write
                // and this tick. The registrations will cascade
                // away too, but we skip to be safe.
                log.warn("Tenant {}: session {} has no parent event; skipping reminder fan-out",
                        tenantId, session.getId());
                continue;
            }

            List<SessionRegistration> pending = transactionTemplate.execute(s ->
                    registrationRepository.findPendingReminders(session.getId()));
            if (pending == null || pending.isEmpty()) {
                continue;
            }

            for (SessionRegistration registration : pending) {
                try {
                    boolean fired = transactionTemplate.execute(s ->
                            enqueueReminder(tenantId, event, session, registration));
                    if (Boolean.TRUE.equals(fired)) {
                        fannedOut++;
                    } else {
                        skipped++;
                    }
                } catch (Exception ex) {
                    // Poisoned row — log and keep going so one
                    // broken registration cannot stall the rest.
                    log.error("Tenant {}: reminder enqueue failed for registration {} (session {}): {}",
                            tenantId, registration.getId(), session.getId(), ex.getMessage(), ex);
                }
            }
        }

        if (fannedOut > 0 || skipped > 0) {
            log.debug("Session reminder tick tenant={} fannedOut={} skipped={} sessions={}",
                    tenantId, fannedOut, skipped, dueSoon.size());
        }
    }

    /**
     * Enqueue a single reminder and stamp the local row, both
     * inside the same tx. Returns {@code true} if a reminder was
     * newly-enqueued, {@code false} if the row was filtered out
     * between the tick read and this write (e.g. the viewer opted
     * out while the batch was in flight).
     */
    private boolean enqueueReminder(UUID tenantId,
                                    Event event,
                                    Session session,
                                    SessionRegistration registrationSnapshot) {
        // Re-read inside the tx so a concurrent opt-out or a
        // previous tick's enqueue is observed atomically.
        SessionRegistration row = registrationRepository.findById(registrationSnapshot.getId())
                .orElse(null);
        if (row == null || !row.isActive() || !row.isEmailRemindersEnabled() || row.getReminderSentAt() != null) {
            return false;
        }

        String subject = renderSubject(event, session);
        String body    = renderBody(event, session, row);
        String idempotencyKey = "event-reminder:" + session.getId() + ":" + row.getProfileId();

        notificationService.enqueue(NotificationRequest.email(
                row.getProfileId(),
                NotificationKind.EVENT_REMINDER,
                row.getNotifyEmail(),
                subject,
                body,
                idempotencyKey));

        row.markReminderSent(Instant.now());
        registrationRepository.save(row);
        return true;
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    private String renderSubject(Event event, Session session) {
        long minutes = Math.max(1, Duration.between(Instant.now(), session.getStartTime()).toMinutes());
        return "Напоминание: «" + event.getTitle() + "» начнётся через " + minutes + " мин.";
    }

    private String renderBody(Event event, Session session, SessionRegistration registration) {
        ZoneId zone = resolveZone(event.getTimezone());
        String startFmt = DateTimeFormatter
                .ofPattern("d MMMM yyyy, HH:mm")
                .withZone(zone)
                .format(session.getStartTime());

        StringBuilder body = new StringBuilder(512);
        body.append("Здравствуйте!\n\n")
            .append("Скоро начнётся вебинар, на который вы записались:\n\n")
            .append("  ").append(event.getTitle()).append('\n')
            .append("  Начало: ").append(startFmt).append(" (").append(zone.getId()).append(")\n");
        if (event.getSpeakerName() != null && !event.getSpeakerName().isBlank()) {
            body.append("  Ведущий: ").append(event.getSpeakerName()).append('\n');
        }
        body.append('\n')
            .append("Зайдите в комнату заранее, чтобы проверить звук и подготовить вопросы.\n")
            .append("Ссылка на трансляцию откроется на странице события за несколько минут до старта.\n\n")
            .append("Если вы больше не хотите получать напоминания, откройте страницу события и нажмите «Отменить регистрацию».\n\n")
            .append("— Webizon");
        return body.toString();
    }

    private static ZoneId resolveZone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneId.of("Asia/Almaty");
        }
        try {
            return ZoneId.of(timezone);
        } catch (Exception ex) {
            return ZoneId.of("Asia/Almaty");
        }
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
            log.error("Failed to enumerate tenants for session reminder tick: {}", ex.getMessage(), ex);
            return List.of();
        }
    }
}
