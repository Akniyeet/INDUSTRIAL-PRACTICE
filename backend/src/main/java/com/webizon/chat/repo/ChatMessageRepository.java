package com.webizon.chat.repo;

import com.webizon.chat.model.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JPA repository for {@link ChatMessage}. Tenant-scoped via Hibernate's
 * {@code @TenantId} filter and PostgreSQL RLS.
 *
 * <p>Live feed uses <strong>keyset (cursor-based) pagination</strong> instead
 * of offset-based to avoid the O(offset) scan cost at high message counts.
 * The cursor is {@code (createdAt, id)} — both columns are covered by the
 * partial index {@code chat_messages_session_live_idx}.
 */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    /**
     * <strong>Initial load</strong> — most recent messages, newest first.
     * Called when a viewer first opens the chat pane (no cursor yet).
     */
    @Query("""
           select m from ChatMessage m
           where m.sessionId = :sessionId
             and m.deleted = false
             and m.hidden = false
           order by m.createdAt desc, m.id desc
           """)
    List<ChatMessage> findLiveFeed(@Param("sessionId") UUID sessionId, Pageable pageable);

    /**
     * <strong>Cursor-based load</strong> — messages older than the cursor,
     * newest first. Uses keyset pagination: WHERE (createdAt, id) < (cursor).
     *
     * <p>This avoids the PostgreSQL "skip N rows" cost that offset-based
     * pagination suffers at high offsets. At 60K messages, offset=50000
     * scans 50K index entries; keyset seeks directly to the cursor row.
     *
     * @param sessionId  session to load
     * @param cursorTime createdAt of the last loaded message
     * @param cursorId   id of the last loaded message (tie-breaker)
     * @param pageable   limit only (page number ignored)
     */
    @Query("""
           select m from ChatMessage m
           where m.sessionId = :sessionId
             and m.deleted = false
             and m.hidden = false
             and (m.createdAt < :cursorTime
                  or (m.createdAt = :cursorTime and m.id < :cursorId))
           order by m.createdAt desc, m.id desc
           """)
    List<ChatMessage> findLiveFeedBefore(
            @Param("sessionId") UUID sessionId,
            @Param("cursorTime") Instant cursorTime,
            @Param("cursorId") UUID cursorId,
            Pageable pageable);

    /**
     * <strong>New messages since cursor</strong> — messages newer than the
     * cursor, oldest first. Used for "load new" polling or after reconnect.
     */
    @Query("""
           select m from ChatMessage m
           where m.sessionId = :sessionId
             and m.deleted = false
             and m.hidden = false
             and (m.createdAt > :cursorTime
                  or (m.createdAt = :cursorTime and m.id > :cursorId))
           order by m.createdAt asc, m.id asc
           """)
    List<ChatMessage> findLiveFeedAfter(
            @Param("sessionId") UUID sessionId,
            @Param("cursorTime") Instant cursorTime,
            @Param("cursorId") UUID cursorId,
            Pageable pageable);

    /**
     * All replay-eligible messages for a source LIVE session ordered
     * by offset. Used by the admin curation view.
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
     * Replay-window query for the timeline tick loop.
     * Half-open interval: (fromExclusive, toInclusive].
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

    long countBySessionIdAndUserIdAndDeletedFalse(UUID sessionId, UUID userId);

    @Query("""
           select m from ChatMessage m
           where m.sessionId = :sessionId
             and m.deleted = false
           order by m.createdAt asc
           """)
    List<ChatMessage> findExportTranscript(@Param("sessionId") UUID sessionId);
}
