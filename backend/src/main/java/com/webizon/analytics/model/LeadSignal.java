package com.webizon.analytics.model;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A computed interest signal about a user — derived from the raw
 * analytics event log but stored separately so CRM consumers can
 * subscribe to them directly without re-running the rules engine.
 *
 * <p>Append-only, like {@link AnalyticsEvent}: every run of the
 * evaluator produces zero or more new rows; nothing is ever updated.
 * Score is signed so negative signals (BAD_BEHAVIOR) can offset
 * positive ones at read time.
 */
@Entity
@Table(name = "lead_signals")
@EntityListeners(TenantEntityListener.class)
@Getter
@Setter
public class LeadSignal extends BaseEntity implements TenantAware {

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false, columnDefinition = "UUID")
    private UUID tenantId;

    @Column(name = "profile_id", nullable = false, columnDefinition = "UUID")
    private UUID profileId;

    @Column(name = "event_id", columnDefinition = "UUID")
    private UUID eventId;

    @Column(name = "session_id", columnDefinition = "UUID")
    private UUID sessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal_type", nullable = false, length = 48)
    private LeadSignalType signalType;

    @Column(name = "score", nullable = false)
    private int score;

    /** The analytics event row that triggered this signal, if any. */
    @Column(name = "source_event_id", columnDefinition = "UUID")
    private UUID sourceEventId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();
}
