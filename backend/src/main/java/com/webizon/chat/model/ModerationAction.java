package com.webizon.chat.model;

import com.webizon.common.BaseEntity;
import com.webizon.tenancy.TenantAware;
import com.webizon.tenancy.TenantEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

import java.util.UUID;

/**
 * Immutable audit entry for a moderator act.
 *
 * <p>This table is append-only: every row represents something a
 * moderator did, timestamped and attributed. Updates are never allowed
 * — if a moderator's decision needs to be undone (e.g. unmute), a new
 * row is inserted rather than editing the old one. This is why the
 * entity omits {@code updated_at} / optimistic locking and why the
 * underlying table has no updated_at trigger.
 *
 * <p>We extend {@link BaseEntity} directly (not {@link com.webizon.tenancy.TenantAwareEntity})
 * because we don't need the {@code @Version} column for immutable rows,
 * but we still need multi-tenant isolation — so we manually wire the
 * {@code @TenantId} field and the tenant listener.
 */
@Entity
@Table(name = "moderation_actions")
@EntityListeners(TenantEntityListener.class)
@Getter
@Setter
public class ModerationAction extends BaseEntity implements TenantAware {

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false, columnDefinition = "UUID")
    private UUID tenantId;

    @Column(name = "event_id", nullable = false, columnDefinition = "UUID")
    private UUID eventId;

    @Column(name = "session_id", nullable = false, columnDefinition = "UUID")
    private UUID sessionId;

    /** Null for {@link ModerationActionType#MESSAGE_DELETE} / HIDE / SLOW_MODE_CHANGE. */
    @Column(name = "target_user_id", columnDefinition = "UUID")
    private UUID targetUserId;

    @Column(name = "moderator_user_id", nullable = false, columnDefinition = "UUID")
    private UUID moderatorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 20)
    private ModerationActionType actionType;

    /** Required for MESSAGE_DELETE and MESSAGE_HIDE; null for user-targeted actions. */
    @Column(name = "target_message_id", columnDefinition = "UUID")
    private UUID targetMessageId;

    @Column(name = "reason", length = 500)
    private String reason;

    /** Required for time-bounded actions (MUTE). Null otherwise. */
    @Column(name = "duration_seconds")
    private Integer durationSeconds;
}
