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
     * All non-deleted messages for a source LIVE session ordered by
     * their captured {@code offset_seconds}. Used by the historical
     * replay engine to schedule message insertion during AUTO sessions.
     */
    @Query("""
           select m from ChatMessage m
           where m.sessionId = :sourceSessionId
             and m.deleted = false
             and m.offsetSeconds is not null
           order by m.offsetSeconds asc, m.createdAt asc
           """)
    List<ChatMessage> findHistoricalTranscript(@Param("sourceSessionId") UUID sourceSessionId);

    /**
     * Count non-deleted messages a user has sent in a session. Used by
     * anti-flood policies and engagement analytics.
     */
    long countBySessionIdAndUserIdAndDeletedFalse(UUID sessionId, UUID userId);
}
