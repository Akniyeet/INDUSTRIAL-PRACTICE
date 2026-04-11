package com.webizon.chat.model;

import com.webizon.tenancy.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Effective chat state of one user inside one session.
 *
 * <p>Upserted on every moderation action and on every successful
 * message send (to update {@code last_message_at} for slow-mode
 * enforcement). The unique constraint on {@code (session_id, user_id)}
 * guarantees that the policy chain sees a single authoritative row
 * per participant.
 *
 * <p><b>Mute resolution rule</b> — a user is considered muted if either:
 * <ul>
 *   <li>{@code isMuted} is {@code true} and {@code muteUntil} is null
 *       (indefinite mute), or</li>
 *   <li>{@code muteUntil} is non-null and in the future</li>
 * </ul>
 * Expired mutes are cleared lazily — the policy chain treats an expired
 * {@code muteUntil} as "not muted" without waiting for a background job.
 */
@Entity
@Table(
        name = "chat_user_statuses",
        uniqueConstraints = @UniqueConstraint(
                name = "chat_user_statuses_session_user_uniq",
                columnNames = {"session_id", "user_id"}
        )
)
@Getter
@Setter
public class ChatUserStatus extends TenantAwareEntity {

    @Column(name = "event_id", nullable = false, columnDefinition = "UUID")
    private UUID eventId;

    @Column(name = "session_id", nullable = false, columnDefinition = "UUID")
    private UUID sessionId;

    @Column(name = "user_id", nullable = false, columnDefinition = "UUID")
    private UUID userId;

    @Column(name = "can_send_messages", nullable = false)
    private boolean canSendMessages = true;

    @Column(name = "is_muted", nullable = false)
    private boolean muted;

    @Column(name = "mute_until")
    private Instant muteUntil;

    @Column(name = "is_chat_banned", nullable = false)
    private boolean chatBanned;

    @Column(name = "is_room_banned", nullable = false)
    private boolean roomBanned;

    @Column(name = "warning_count", nullable = false)
    private int warningCount;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    /**
     * True iff this user is effectively muted right now. Collapses the
     * two mute encodings (indefinite flag and timed {@code muteUntil})
     * into a single predicate the policy chain can call.
     */
    public boolean isEffectivelyMuted(Instant now) {
        if (muted && muteUntil == null) {
            return true;
        }
        return muteUntil != null && muteUntil.isAfter(now);
    }
}
