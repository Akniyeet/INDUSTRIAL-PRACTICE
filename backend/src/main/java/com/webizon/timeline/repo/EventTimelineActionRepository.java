package com.webizon.timeline.repo;

import com.webizon.timeline.model.EventTimelineAction;
import com.webizon.timeline.model.TimelineActionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * JPA repository for {@link EventTimelineAction}.
 *
 * <p>The hot read path is {@link #findReplayQueueForSourceSession}: it
 * returns the ordered, deduplicated list of active actions an AUTO
 * session must replay, and it hits the partial index
 * {@code event_timeline_actions_replay_idx}.
 */
public interface EventTimelineActionRepository extends JpaRepository<EventTimelineAction, UUID> {

    /**
     * Every timeline row (active or not) for a source session, in
     * ascending offset order. Used by the admin view so moderators can
     * see exactly what was captured — including disabled rows.
     */
    List<EventTimelineAction> findAllBySourceSessionIdOrderByOffsetSecondsAsc(UUID sourceSessionId);

    /**
     * The ordered replay queue for an AUTO session. Only active rows
     * are returned so a deactivated CTA or edited-out admin message
     * never appears in playback.
     */
    @Query("""
           select a from EventTimelineAction a
           where a.sourceSessionId = :sourceSessionId
             and a.active = true
           order by a.offsetSeconds asc, a.createdAt asc
           """)
    List<EventTimelineAction> findReplayQueueForSourceSession(
            @Param("sourceSessionId") UUID sourceSessionId);

    /**
     * Every row for a specific source session whose offset is >=
     * {@code fromOffset}. Supports "fast-forward" reconnection in AUTO
     * sessions: if a viewer rejoins at offset 180s, we only need to
     * emit actions from 180s onward.
     */
    @Query("""
           select a from EventTimelineAction a
           where a.sourceSessionId = :sourceSessionId
             and a.active = true
             and a.offsetSeconds >= :fromOffset
           order by a.offsetSeconds asc, a.createdAt asc
           """)
    List<EventTimelineAction> findReplayQueueFromOffset(
            @Param("sourceSessionId") UUID sourceSessionId,
            @Param("fromOffset") int fromOffset);

    /**
     * Timeline rows inside a half-open window
     * {@code (fromOffsetExclusive, toOffsetInclusive]}. The replay
     * engine's tick loop calls this once per AUTO session per tick with
     * the advancing cursor so the same row can never be dispatched by
     * two consecutive ticks.
     *
     * <p>The left edge is exclusive on purpose: after a tick dispatches
     * a row whose offset equals the current cursor, it advances the
     * cursor to that offset; the next tick must not re-emit the row.
     */
    @Query("""
           select a from EventTimelineAction a
           where a.sourceSessionId = :sourceSessionId
             and a.active = true
             and a.offsetSeconds > :fromOffsetExclusive
             and a.offsetSeconds <= :toOffsetInclusive
           order by a.offsetSeconds asc, a.createdAt asc
           """)
    List<EventTimelineAction> findReplayWindow(
            @Param("sourceSessionId") UUID sourceSessionId,
            @Param("fromOffsetExclusive") int fromOffsetExclusive,
            @Param("toOffsetInclusive") int toOffsetInclusive);

    List<EventTimelineAction> findAllByEventIdAndActionType(UUID eventId, TimelineActionType type);
}
