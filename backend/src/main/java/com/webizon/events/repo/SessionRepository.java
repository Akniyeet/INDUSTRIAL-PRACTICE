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
}
