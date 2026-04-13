package com.webizon.events.model;

import com.webizon.tenancy.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Reusable content unit (topic, landing page, speaker).
 *
 * <p>A single {@code Event} can be broadcast many times as separate
 * {@link Session} rows — never by flipping a finished session's status back
 * to "upcoming". This separation is enforced at the database level by the
 * schema in {@code V002__events_and_sessions.sql}.
 *
 * <p>Slugs are per-tenant unique: two different schools can both expose
 * {@code /event/free-lesson} without conflict.
 */
@Entity
@Table(name = "events", uniqueConstraints = @UniqueConstraint(
        name = "events_slug_uniq",
        columnNames = {"tenant_id", "slug"}
))
@Getter
@Setter
public class Event extends TenantAwareEntity {

    @Column(name = "slug", nullable = false, length = 64)
    private String slug;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "speaker_name", length = 120)
    private String speakerName;

    @Column(name = "speaker_bio", columnDefinition = "text")
    private String speakerBio;

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone = "Asia/Almaty";

    @Column(name = "language", nullable = false, length = 16)
    private String language = "ru";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private EventStatus status = EventStatus.DRAFT;

    @Column(name = "created_by_user_id", nullable = false, updatable = false, columnDefinition = "UUID")
    private UUID createdByUserId;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "landing_config", nullable = false, columnDefinition = "jsonb")
    private java.util.Map<String, Object> landingConfig = new java.util.HashMap<>();
}
