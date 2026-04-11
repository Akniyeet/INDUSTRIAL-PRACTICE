package com.webizon.chat.repo;

import com.webizon.chat.model.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * JPA repository for {@link ChatMessage}. Tenant-scoped via Hibernate's
 * {@code @TenantId} filter and PostgreSQL RLS.
 *
 * <p>Queries are read-path-first and intentionally parsimonious with
 * projections: the live feed under load reads thousands of rows per
 * second, and wide selects would multiply network and heap cost.
 */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    /**
     * Most recent non-deleted messages for a session, newest first.
     *
     * <p>Used to bootstrap the chat pane when a viewer joins. Matches
     * the partial index {@code chat_messages_session_live_idx} so the
     * executor does an index-only range scan regardless of how many
     * soft-deleted rows the session accumulates.
     */
    @Query("""
           select m from ChatMessage m
           where m.sessionId = :sessionId
             and m.deleted = false
             and m.hidden = false
           order by m.createdAt desc
           """)
    List<ChatMessage> findLiveFeed(@Param("sessionId") UUID sessionId, Pageable pageable);

    /**
     * All replay-eligible messages for a source LIVE session ordered
     * by their captured {@code offset_seconds}. Used by the admin
     * curation view to show the full transcript that would replay in
     * future AUTO sessions — deleted and hidden rows are excluded,
     * but messages marked {@code excludedFromReplay} are still
     * returned so the admin can toggle them back on.
     */
    @Query("""
           select m from ChatMessage m
           where m.sessionId = :sourceSessionId
             and m.deleted = false
             and m.hidden = false
             and m.offsetSeconds is not null
           order by m.offsetSeconds asc, m.createdAt asc
           """)
    List<ChatMessage> findHistoricalTranscript(@Param("sourceSessionId") UUID sourceSessionId);

    /**
     * Replay-window query: every message for the source session
     * whose offset falls inside {@code (fromOffsetExclusive,
     * toOffsetInclusive]}, filtered to the replay pipeline's rules
     * — not deleted, not hidden, not admin-excluded. The tick loop
     * calls this with its advancing cursor on every pass.
     *
     * <p>The interval is half-open on the left so the same row can
     * never be dispatched twice by two consecutive ticks.
     */
    @Query("""
           select m from ChatMessage m
           where m.sessionId = :sourceSessionId
             and m.deleted = false
             and m.hidden = false
             and m.excludedFromReplay = false
             and m.offsetSeconds is not null
             and m.offsetSeconds > :fromOffsetExclusive
             and m.offsetSeconds <= :toOffsetInclusive
           order by m.offsetSeconds asc, m.createdAt asc
           """)
    List<ChatMessage> findReplayWindow(
            @Param("sourceSessionId") UUID sourceSessionId,
            @Param("fromOffsetExclusive") int fromOffsetExclusive,
            @Param("toOffsetInclusive") int toOffsetInclusive);

    /**
     * Count non-deleted messages a user has sent in a session. Used by
     * anti-flood policies and engagement analytics.
     */
    long countBySessionIdAndUserIdAndDeletedFalse(UUID sessionId, UUID userId);
}
