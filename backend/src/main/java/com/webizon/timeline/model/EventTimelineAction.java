package com.webizon.timeline.model;

import com.webizon.tenancy.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A single timeline entry captured in a LIVE session or authored
 * manually by an admin.
 *
 * <p>Timeline actions are the mechanism that makes AUTO sessions feel
 * alive: during a LIVE broadcast every admin action (CTA shown, system
 * message posted, state change) is written into this table with the
 * video-relative {@code offsetSeconds}. When an AUTO session plays
 * back, the {@code TimelineService} loads every active row whose
 * {@code sourceSessionId} equals the AUTO session's
 * {@code sourceLiveSessionId} and emits them at the correct moment.
 *
 * <p>The payload column is typed as JSONB in PostgreSQL and is mapped
 * here through Hibernate 6's native JSON support
 * ({@link SqlTypes#JSON}). Jackson handles the conversion
 * {@code Map<String,Object> ↔ JSONB} automatically; no custom
 * converter is needed.
 *
 * <p>Rows are mutable — an admin can edit the offset, tweak the
 * payload, or flip {@code isActive = false} to drop an action from
 * future replays without losing the audit trail of when it was
 * originally captured.
 */
@Entity
@Table(name = "event_timeline_actions")
@Getter
@Setter
public class EventTimelineAction extends TenantAwareEntity {

    @Column(name = "event_id", nullable = false, columnDefinition = "UUID")
    private UUID eventId;

    /**
     * The LIVE session this action was captured in (or manually
     * attached to). AUTO sessions replay actions whose
     * {@code sourceSessionId} matches their
     * {@link com.webizon.events.model.Session#getSourceLiveSessionId()}.
     */
    @Column(name = "source_session_id", nullable = false, columnDefinition = "UUID")
    private UUID sourceSessionId;

    @Column(name = "offset_seconds", nullable = false)
    private int offsetSeconds;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 32)
    private TimelineActionType actionType;

    /**
     * Action-specific payload. See {@link TimelineActionType} for the
     * documented schema per action kind.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload = new HashMap<>();

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_by_user_id", nullable = false, updatable = false, columnDefinition = "UUID")
    private UUID createdByUserId;
}
