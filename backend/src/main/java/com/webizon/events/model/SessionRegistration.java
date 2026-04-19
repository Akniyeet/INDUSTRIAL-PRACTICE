package com.webizon.events.model;

import com.webizon.tenancy.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A viewer's opt-in to receive a reminder for a specific
 * {@link Session}. Produced when an authenticated visitor clicks
 * "Remind me" on the public event landing page; consumed by the
 * session reminder scheduler to fan out {@code EVENT_REMINDER}
 * notifications N minutes before the session starts.
 *
 * <h2>Why separate from attendance</h2>
 * Attendance tracks what happened — a registration tracks what a
 * viewer intends to happen. A single user may register for a
 * session and then miss the airing; the two signals have entirely
 * different downstream consumers (reminders vs. billing meters /
 * retention analytics), so conflating them would break both
 * pipelines. See the javadoc on the V010 migration for the full
 * rationale.
 *
 * <h2>Lifecycle</h2>
 * <ul>
 *   <li>{@code registeredAt} — set on first insert; never updated.</li>
 *   <li>{@code unregisteredAt} — set on opt-out; {@code null} while
 *       active. The partial unique index
 *       {@code session_registrations_active_uniq} enforces that at
 *       most one active row exists per (session, profile) pair, so
 *       an opt-out + opt-in cycle produces a fresh row cleanly.</li>
 *   <li>{@code reminderSentAt} — set by the reminder scheduler when
 *       the {@code EVENT_REMINDER} row has been written to the
 *       notification outbox. Acts as an idempotency cursor so a
 *       scheduler restart does not re-fan-out already-notified
 *       viewers.</li>
 * </ul>
 *
 * <h2>Email freezing</h2>
 * {@link #notifyEmail} is captured at registration time and never
 * updated on profile email changes. This mirrors the notification
 * outbox convention and prevents a later profile change from
 * silently misrouting a pre-scheduled reminder.
 */
@Entity
@Table(name = "session_registrations")
@Getter
@Setter
public class SessionRegistration extends TenantAwareEntity {

    @Column(name = "session_id", nullable = false, updatable = false, columnDefinition = "UUID")
    private UUID sessionId;

    @Column(name = "event_id", nullable = false, updatable = false, columnDefinition = "UUID")
    private UUID eventId;

    /** Canonical Webizon profile id the registration belongs to. */
    @Column(name = "profile_id", nullable = false, updatable = false, columnDefinition = "UUID")
    private UUID profileId;

    /**
     * Delivery target frozen at registration time. Immune to later
     * profile email changes — a reminder always goes to the address
     * the user was reachable at when they clicked "Remind me".
     */
    @Column(name = "notify_email", nullable = false, length = 320)
    private String notifyEmail;

    /**
     * Explicit email-reminder opt-in. Defaults true because the
     * registration action IS the consent, but a user who later opts
     * out of reminders flips this to false without removing the
     * whole registration (we keep the row for analytics).
     */
    @Column(name = "email_reminders_enabled", nullable = false)
    private boolean emailRemindersEnabled = true;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private Instant registeredAt = Instant.now();

    /**
     * Non-null after opt-out. The partial unique index over active
     * rows treats {@code null} as "currently registered", so an
     * opt-out cycle cleanly permits re-registration with a new row.
     */
    @Column(name = "unregistered_at")
    private Instant unregisteredAt;

    /**
     * Set by {@code SessionReminderScheduler} once the
     * {@code EVENT_REMINDER} notification has been enqueued. Kept
     * separate from the outbox's own status so the scheduler can
     * filter pending rows with an index scan instead of joining
     * back to the outbox table every tick.
     */
    @Column(name = "reminder_sent_at")
    private Instant reminderSentAt;

    // ------------------------------------------------------------------
    // Lifecycle helpers
    // ------------------------------------------------------------------

    /**
     * @return {@code true} if this registration is still active —
     *         i.e. the viewer has not opted out. The scheduler and
     *         reminder API both filter on this condition before
     *         acting.
     */
    public boolean isActive() {
        return unregisteredAt == null;
    }

    /**
     * Mark this registration as opted-out. Idempotent — calling it
     * twice is safe and leaves the original timestamp alone.
     */
    public void markUnregistered(Instant at) {
        if (unregisteredAt == null) {
            this.unregisteredAt = at;
        }
    }

    /** Mark the reminder as enqueued on the outbox. */
    public void markReminderSent(Instant at) {
        this.reminderSentAt = at;
    }
}
