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
 * A single behavioural event in the analytics log.
 *
 * <p>Rows are append-only — never updated, never deleted. The entity
 * therefore extends {@link BaseEntity} directly (not
 * {@link com.webizon.tenancy.TenantAwareEntity}) to skip the
 * {@code @Version} column that only matters for mutable rows. Tenant
 * isolation is still wired manually through the {@code @TenantId}
 * annotation and {@link TenantEntityListener} listener so cross-tenant
 * writes fail just as loudly as in every other module.
 *
 * <p>{@code eventId} and {@code sessionId} are both nullable: a
 * landing-page view may not yet have a session, and some cross-session
 * signals (future) may not have an event either. {@code profileId} is
 * also nullable to allow pre-auth tracking — paired with
 * {@code clientKey} so the recorder can stitch pre-auth and post-auth
 * views together later.
 */
@Entity
@Table(name = "analytics_events")
@EntityListeners(TenantEntityListener.class)
@Getter
@Setter
public class AnalyticsEvent extends BaseEntity implements TenantAware {

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false, columnDefinition = "UUID")
    private UUID tenantId;

    @Column(name = "event_id", columnDefinition = "UUID")
    private UUID eventId;

    @Column(name = "session_id", columnDefinition = "UUID")
    private UUID sessionId;

    @Column(name = "profile_id", columnDefinition = "UUID")
    private UUID profileId;

    /** Best-effort pre-auth id (cookie / device). Nullable once profile_id is known. */
    @Column(name = "client_key", length = 64)
    private String clientKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 48)
    private AnalyticsEventType eventType;

    @Column(name = "offset_seconds")
    private Integer offsetSeconds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();
}
