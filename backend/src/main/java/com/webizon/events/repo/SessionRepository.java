package com.webizon.events.repo;

import com.webizon.events.model.Session;
import com.webizon.events.model.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA repository for {@link Session}. Tenant-scoped via Hibernate's
 * {@code @TenantId} filter and PostgreSQL RLS.
 */
public interface SessionRepository extends JpaRepository<Session, UUID> {

    List<Session> findAllByEventIdOrderByStartTimeAsc(UUID eventId);

    /**
     * The single currently-airing session for an event, if any. Matches the
     * partial unique index {@code sessions_one_airing_per_event} — at most
     * one row can satisfy this predicate.
     */
    @Query("""
           select s from Session s
           where s.eventId = :eventId
             and s.status in (com.webizon.events.model.SessionStatus.LIVE,
                              com.webizon.events.model.SessionStatus.AUTO_LIVE)
           """)
    Optional<Session> findAiringByEventId(@Param("eventId") UUID eventId);

    /**
     * The next upcoming session for an event, ordered by start time ascending.
     * Used by the landing-page resolver to decide whether to show the
     * waiting room or the slot selector.
     */
    @Query("""
           select s from Session s
           where s.eventId = :eventId
             and s.status in (com.webizon.events.model.SessionStatus.SCHEDULED,
                              com.webizon.events.model.SessionStatus.AUTO_SCHEDULED)
             and s.startTime >= :now
           order by s.startTime asc
           """)
    List<Session> findUpcomingByEventId(@Param("eventId") UUID eventId,
                                        @Param("now") Instant now);

    /**
     * All upcoming AUTO sessions for an event — the slot selector after a
     * LIVE has finished.
     */
    @Query("""
           select s from Session s
           where s.eventId = :eventId
             and s.type = com.webizon.events.model.SessionType.AUTO
             and s.status = com.webizon.events.model.SessionStatus.AUTO_SCHEDULED
             and s.startTime >= :now
           order by s.startTime asc
           """)
    List<Session> findUpcomingAutoSlotsByEventId(@Param("eventId") UUID eventId,
                                                 @Param("now") Instant now);

    List<Session> findAllByEventIdAndStatusInOrderByStartTimeAsc(UUID eventId,
                                                                 List<SessionStatus> statuses);

    // ------------------------------------------------------------------
    // Scheduler read paths (Phase 8 — Auto Sessions)
    // ------------------------------------------------------------------

    /**
     * Every session in the given status. The auto-session lifecycle
     * scheduler uses this twice per tick — once for
     * {@code AUTO_SCHEDULED} rows whose {@code startTime} has arrived,
     * once for {@code AUTO_LIVE} rows the replay engine needs to
     * advance. The query is cross-tenant by design: the scheduler
     * runs outside of any HTTP request so a tenant filter would be
     * meaningless; it uses {@code @AllowCrossTenant} at the call site.
     */
    @Query("""
           select s from Session s
           where s.status = :status
           """)
    List<Session> findAllByStatus(@Param("status") SessionStatus status);

    /**
     * AUTO sessions whose {@code startTime} has passed and are still
     * in {@code AUTO_SCHEDULED}. These are the rows the lifecycle
     * scheduler promotes to {@code AUTO_LIVE} on each tick.
     */
    @Query("""
           select s from Session s
           where s.status = com.webizon.events.model.SessionStatus.AUTO_SCHEDULED
             and s.startTime <= :cutoff
           order by s.startTime asc
           """)
    List<Session> findAutoSessionsReadyToStart(@Param("cutoff") Instant cutoff);

    /**
     * Finalized sessions the billing usage sweeper has not yet
     * metered. Ordered by {@code finalized_at} ascending so older
     * sessions are billed first — a backlog never skips anything.
     * Matches the partial index {@code sessions_billing_unmetered_idx}.
     */
    @Query("""
           select s from Session s
           where s.finalizedAt is not null
             and s.billingMeteredAt is null
           order by s.finalizedAt asc
           """)
    List<Session> findUnmeteredFinalizedSessions();

    /**
     * Upcoming sessions (LIVE {@code SCHEDULED} or AUTO
     * {@code AUTO_SCHEDULED}) whose {@code startTime} falls inside
     * the half-open window {@code (now, windowEnd]}. Powers the
     * session reminder scheduler's per-tick fan-out: every tick
     * picks up sessions that have just entered the reminder lead
     * time but are not yet airing, and enqueues EVENT_REMINDER
     * notifications for every active registration.
     *
     * <p>Ordered by {@code startTime} ascending so the sooner-to-
     * start session's reminders fire first — under a big backlog
     * the scheduler drains in start-time order which is the
     * user-visible correct behaviour.
     */
    @Query("""
           select s from Session s
           where s.status in (com.webizon.events.model.SessionStatus.SCHEDULED,
                              com.webizon.events.model.SessionStatus.AUTO_SCHEDULED)
             and s.startTime > :now
             and s.startTime <= :windowEnd
           order by s.startTime asc
           """)
    List<Session> findUpcomingWithinWindow(@Param("now") Instant now,
                                           @Param("windowEnd") Instant windowEnd);

    /**
     * Currently-airing sessions whose {@code SESSION_STARTING}
     * fan-out has not yet been completed. Powers the
     * {@code SessionStartingScheduler} — it picks up a session
     * that just transitioned to LIVE / AUTO_LIVE, fans out
     * notifications to every active registration, then stamps
     * {@code sessionStartNotifiedAt} so the row drops out of this
     * query on subsequent ticks.
     *
     * <p>Matches the partial index
     * {@code sessions_pending_start_notification_idx} so the scan
     * stays O(pending) even as tens of thousands of finalized
     * sessions accumulate in the table.
     */
    @Query("""
           select s from Session s
           where s.status in (com.webizon.events.model.SessionStatus.LIVE,
                              com.webizon.events.model.SessionStatus.AUTO_LIVE)
             and s.sessionStartNotifiedAt is null
           order by s.actualStartedAt asc
           """)
    List<Session> findPendingStartNotification();
}
