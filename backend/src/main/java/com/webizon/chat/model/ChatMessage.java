package com.webizon.chat.model;

import com.webizon.tenancy.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A single line in a session's chat.
 *
 * <p>Tables-not-JSON is deliberate: chat is the most read-heavy artifact
 * in the whole system, and per-row storage lets us use partial indexes
 * (e.g. {@code chat_messages_session_live_idx WHERE is_deleted = FALSE})
 * to keep the hot live-feed lookup small even after heavy moderation.
 *
 * <p>All relationships are held as raw UUIDs rather than {@code @ManyToOne}
 * references because:
 * <ul>
 *   <li>chat traffic under load makes lazy-proxy overhead dominate wall time</li>
 *   <li>the chat layer never needs to navigate the object graph —
 *       everything is joined at the query level when needed</li>
 *   <li>it prevents accidental N+1 fetches in controllers</li>
 * </ul>
 *
 * <p>Soft deletion only: rows stay forever for audit, with {@code is_deleted}
 * flipped to {@code true}. Moderators can still see deleted rows through
 * the admin chat view.
 */
@Entity
@Table(name = "chat_messages")
@Getter
@Setter
public class ChatMessage extends TenantAwareEntity {

    @Column(name = "event_id", nullable = false, columnDefinition = "UUID")
    private UUID eventId;

    @Column(name = "session_id", nullable = false, columnDefinition = "UUID")
    private UUID sessionId;

    /**
     * Author. Null for {@link MessageType#SYSTEM} rows; enforced by the
     * {@code chat_messages_author_chk} DB constraint.
     */
    @Column(name = "user_id", columnDefinition = "UUID")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 16)
    private MessageType messageType;

    /**
     * One-level reply only. The application layer rejects any attempt
     * to reply to a message that already has a non-null
     * {@code reply_to_message_id}.
     */
    @Column(name = "reply_to_message_id", columnDefinition = "UUID")
    private UUID replyToMessageId;

    @Column(name = "text", nullable = false, length = 2000)
    private String text;

    /**
     * Video-relative offset in seconds at which this message was posted.
     * Required for messages that should replay in AUTO sessions; nullable
     * for pure current-session chat (e.g. chat during a live broadcast
     * before the recording timeline exists).
     */
    @Column(name = "offset_seconds")
    private Integer offsetSeconds;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Column(name = "is_hidden", nullable = false)
    private boolean hidden;

    /**
     * Admin curation flag: when {@code true}, the historical chat
     * replay engine skips this message in every future AUTO session.
     * Orthogonal to {@link #deleted} and {@link #hidden} — the message
     * remains fully visible in the live audit trail; only the replay
     * pipeline ignores it. Used for off-topic banter, links that
     * expired, and answered questions that would be confusing out of
     * context.
     */
    @Column(name = "excluded_from_replay", nullable = false)
    private boolean excludedFromReplay;

    @Column(name = "deleted_by_user_id", columnDefinition = "UUID")
    private UUID deletedByUserId;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
