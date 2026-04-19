package com.webizon.analytics.repo;

import com.webizon.analytics.model.AnalyticsEvent;
import com.webizon.analytics.model.AnalyticsEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * JPA repository for {@link AnalyticsEvent}.
 *
 * <p>Writes go through {@link JpaRepository#save}; reads are a mix of
 * derived queries and a few explicit JPQL aggregates that power the
 * admin report views.
 */
public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, UUID> {

    /** Total number of events of a given type inside a session — the CTR denominator. */
    long countBySessionIdAndEventType(UUID sessionId, AnalyticsEventType eventType);

    /** Distinct users who generated a given event type inside a session. */
    @Query("""
           select count(distinct a.profileId) from AnalyticsEvent a
           where a.sessionId = :sessionId
             and a.eventType = :eventType
             and a.profileId is not null
           """)
    long countDistinctProfilesBySessionIdAndEventType(@Param("sessionId") UUID sessionId,
                                                        @Param("eventType") AnalyticsEventType eventType);

    /**
     * Events for a session ordered by time — used by the admin "event
     * stream" view. Bounded by a limit in the caller (the service
     * wraps this with Pageable when needed) so we don't accidentally
     * page through millions of rows.
     */
    List<AnalyticsEvent> findTop500BySessionIdOrderByCreatedAtDesc(UUID sessionId);

    /**
     * Retention sampling: count distinct profiles whose watch
     * milestone reached at least {@code offsetSeconds}. Pairs with
     * the {@code WATCH_MILESTONE} analytics event's {@code
     * offset_seconds} column so a single pass can build the full
     * retention curve.
     */
    @Query("""
           select count(distinct a.profileId) from AnalyticsEvent a
           where a.sessionId = :sessionId
             and a.eventType = com.webizon.analytics.model.AnalyticsEventType.WATCH_MILESTONE
             and a.offsetSeconds >= :offsetSeconds
             and a.profileId is not null
           """)
    long countViewersReachingOffset(@Param("sessionId") UUID sessionId,
                                      @Param("offsetSeconds") int offsetSeconds);

    /**
     * Events of a specific type on a specific CTA (matched via the
     * metadata {@code ctaId} field). Uses JSONB arrow-text equality
     * so it can ride the GIN index on metadata if we add one later.
     */
    @Query(value = """
           SELECT COUNT(*) FROM analytics_events
            WHERE session_id  = :sessionId
              AND event_type  = :eventType
              AND metadata ->> 'ctaId' = :ctaId
           """, nativeQuery = true)
    long countByCtaAndEventType(@Param("sessionId") UUID sessionId,
                                 @Param("eventType") String eventType,
                                 @Param("ctaId") String ctaId);

    /** Count events of given types for a specific user in a session — used by AI lead scoring. */
    @Query("""
           select count(a) from AnalyticsEvent a
           where a.sessionId = :sessionId
             and a.profileId = :profileId
             and a.eventType in :eventTypes
           """)
    long countBySessionIdAndProfileIdAndEventTypeIn(
            @Param("sessionId") UUID sessionId,
            @Param("profileId") UUID profileId,
            @Param("eventTypes") List<AnalyticsEventType> eventTypes);

    /** Fetch events of given types for a specific user in a session — used by AI lead scoring. */
    @Query("""
           select a from AnalyticsEvent a
           where a.sessionId = :sessionId
             and a.profileId = :profileId
             and a.eventType in :eventTypes
           order by a.createdAt asc
           """)
    List<AnalyticsEvent> findAllBySessionIdAndProfileIdAndEventTypeIn(
            @Param("sessionId") UUID sessionId,
            @Param("profileId") UUID profileId,
            @Param("eventTypes") List<AnalyticsEventType> eventTypes);

    /** Profiles that triggered a specific event type in a session — used by session export. */
    @Query("""
           select distinct a.profileId from AnalyticsEvent a
           where a.sessionId = :sessionId
             and a.eventType = :eventType
             and a.profileId is not null
           """)
    List<UUID> findDistinctProfileIdsBySessionIdAndEventType(
            @Param("sessionId") UUID sessionId,
            @Param("eventType") AnalyticsEventType eventType);
}
