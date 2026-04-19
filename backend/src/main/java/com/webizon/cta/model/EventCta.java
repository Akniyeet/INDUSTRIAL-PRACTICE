package com.webizon.cta.model;

import com.webizon.tenancy.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * A reusable CTA definition attached to an {@link com.webizon.events.model.Event}.
 *
 * <p>CTAs are NOT per-session: a single definition belongs to the event
 * and is shown in every LIVE and AUTO run of that event via timeline
 * actions. Deactivating a CTA hides it across every future session
 * without needing a new row.
 *
 * <p>Type-specific payload fields ({@code actionUrl}, {@code fileUrl})
 * are validated by the database's {@code event_ctas_payload_chk}
 * CHECK constraint — {@code FILE} must carry a {@code fileUrl}, and
 * {@code LINK} / {@code COURSE} / {@code FORM} must carry an
 * {@code actionUrl}. The application layer also validates this before
 * calling {@code save}, so a rejection never reaches the DB under
 * normal flow.
 */
@Entity
@Table(name = "event_ctas")
@Getter
@Setter
public class EventCta extends TenantAwareEntity {

    @Column(name = "event_id", nullable = false, columnDefinition = "UUID")
    private UUID eventId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private CtaType type;

    @Column(name = "button_text", nullable = false, length = 80)
    private String buttonText;

    /** Required when {@link #type} is LINK / COURSE / FORM. */
    @Column(name = "action_url", length = 500)
    private String actionUrl;

    /** Required when {@link #type} is FILE. */
    @Column(name = "file_url", length = 500)
    private String fileUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "placement", nullable = false, length = 16)
    private CtaPlacement placement;

    /** Higher value wins when two CTAs compete for the same placement. */
    @Column(name = "priority", nullable = false)
    private int priority = 100;

    /** When true, this CTA can coexist with other CTAs at the same placement. */
    @Column(name = "allow_stack", nullable = false)
    private boolean allowStack;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_by_user_id", nullable = false, updatable = false, columnDefinition = "UUID")
    private UUID createdByUserId;
}
