package com.webizon.notifications.model;

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

import java.time.Instant;
import java.util.UUID;

/**
 * One row on the notification outbox. See
 * {@code V009__notification_outbox.sql} for the full rationale
 * behind the outbox pattern and each column.
 *
 * <p>This entity is write-mostly on the hot path — the only read
 * patterns are: (a) the dispatcher's "give me dispatchable rows"
 * query, and (b) the admin outbox UI. Neither of those hydrates a
 * large graph, so the entity stays flat and carries no JPA
 * associations. {@code profileId} is a bare UUID even though it
 * references {@code users(id)}; the user record is looked up
 * explicitly (and only when needed) by
 * {@code NotificationService}.
 *
 * <p>The {@code version} column inherited via {@code BaseEntity}
 * provides optimistic locking, which is important because the
 * dispatcher's SENDING → SENT / FAILED transitions race with the
 * healer's SENDING → PENDING reclaim. Hibernate will refuse the
 * stale update and the healer will simply re-enqueue the row on the
 * next tick.
 */
@Entity
@Table(name = "notification_outbox")
@Getter
@Setter
public class NotificationOutboxEntry extends TenantAwareEntity {

    @Column(name = "profile_id", columnDefinition = "UUID")
    private UUID profileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 32)
    private NotificationKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 16)
    private NotificationChannel channel = NotificationChannel.EMAIL;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(name = "to_address", nullable = false, length = 320)
    private String toAddress;

    @Column(name = "subject", nullable = false, length = 255)
    private String subject;

    @Column(name = "body_text", nullable = false, columnDefinition = "TEXT")
    private String bodyText;

    @Column(name = "body_html", columnDefinition = "TEXT")
    private String bodyHtml;

    /**
     * Free-form per-kind metadata. Stored as JSONB at the DB layer
     * and represented as a raw String in Java so the entity stays
     * decoupled from Jackson; callers serialise their own payload.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "JSONB")
    private String payloadJson = "{}";

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 5;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt = Instant.now();

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "sent_at")
    private Instant sentAt;

    // ------------------------------------------------------------------
    // Domain helpers — kept on the entity because they're trivial and
    // the status transitions are small enough that a dedicated state
    // machine class would be ceremony without payoff.
    // ------------------------------------------------------------------

    /** Flip to SENDING; the dispatcher calls this just before invoking the sender. */
    public void markSending() {
        this.status = NotificationStatus.SENDING;
    }

    /** Flip to SENT on successful delivery. */
    public void markSent(Instant at) {
        this.status = NotificationStatus.SENT;
        this.sentAt = at;
        this.lastError = null;
    }

    /**
     * Record a failed delivery attempt. Schedules the next attempt
     * according to {@code nextDelayMillis} or tips the row into
     * {@link NotificationStatus#DEAD} if retries are exhausted.
     */
    public void markFailed(String error, Instant nextAttempt) {
        this.attemptCount += 1;
        this.lastError = truncate(error, 4000);
        if (this.attemptCount >= this.maxAttempts) {
            this.status = NotificationStatus.DEAD;
        } else {
            this.status = NotificationStatus.FAILED;
            this.nextAttemptAt = nextAttempt;
        }
    }

    /** Admin replay path: reset a DEAD row back to PENDING. */
    public void resetForReplay(Instant nextAttempt) {
        this.status = NotificationStatus.PENDING;
        this.attemptCount = 0;
        this.lastError = null;
        this.nextAttemptAt = nextAttempt;
        this.sentAt = null;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
