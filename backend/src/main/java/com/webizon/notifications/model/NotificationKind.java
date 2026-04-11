package com.webizon.notifications.model;

/**
 * Business reasons a notification row can exist. Kept deliberately
 * small — adding a new kind is a schema change
 * ({@code V009__notification_outbox.sql} carries the CHECK
 * constraint) so the set grows with intent rather than drift.
 *
 * <p>Each kind implicitly picks a <em>content style</em> and a
 * <em>target audience</em>. The dispatcher itself is kind-agnostic —
 * subject and body are frozen at enqueue time — but the
 * {@link #defaultMaxAttempts()} hook lets us tune persistence per
 * kind (session reminders are not worth retrying for two hours, an
 * invoice absolutely is).
 */
public enum NotificationKind {

    /** A DRAFT invoice was promoted to ISSUED. High-value, retry hard. */
    INVOICE_ISSUED(8),

    /** An ISSUED invoice was marked PAID. Informational receipt. */
    INVOICE_PAID(5),

    /** Scheduled session starts in N minutes. Reserved for Phase 13+. */
    EVENT_REMINDER(3),

    /** Session just transitioned to LIVE. Reserved for Phase 13+. */
    SESSION_STARTING(2),

    /** A new user accepted a tenant invite. */
    WELCOME(5);

    private final int defaultMaxAttempts;

    NotificationKind(int defaultMaxAttempts) {
        this.defaultMaxAttempts = defaultMaxAttempts;
    }

    /**
     * How many times the dispatcher should retry before marking the
     * row DEAD. Per-kind tuning matters because reminder notifications
     * lose value after the event starts, while billing notifications
     * must eventually land.
     */
    public int defaultMaxAttempts() {
        return defaultMaxAttempts;
    }
}
