package com.webizon.notifications.api.dto;

import com.webizon.notifications.model.NotificationChannel;
import com.webizon.notifications.model.NotificationKind;
import com.webizon.notifications.model.NotificationOutboxEntry;
import com.webizon.notifications.model.NotificationStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Wire shape for a notification outbox row on the admin UI.
 *
 * <p>The HTML body is intentionally omitted from the list view —
 * it is only hydrated by the {@code GET /{id}} detail endpoint. A
 * typical workspace may carry tens of thousands of outbox rows, and
 * dumping every HTML body into a list response would explode the
 * payload for no admin value.
 */
public record NotificationOutboxResponse(
        UUID id,
        UUID profileId,
        NotificationKind kind,
        NotificationChannel channel,
        NotificationStatus status,
        String toAddress,
        String subject,
        String bodyText,
        String bodyHtml,
        String idempotencyKey,
        int attemptCount,
        int maxAttempts,
        Instant nextAttemptAt,
        String lastError,
        Instant sentAt,
        Instant createdAt,
        Instant updatedAt
) {

    /** Compact projection for list endpoints — omits the bodies. */
    public static NotificationOutboxResponse summary(NotificationOutboxEntry e) {
        return new NotificationOutboxResponse(
                e.getId(),
                e.getProfileId(),
                e.getKind(),
                e.getChannel(),
                e.getStatus(),
                e.getToAddress(),
                e.getSubject(),
                null, // bodies omitted
                null,
                e.getIdempotencyKey(),
                e.getAttemptCount(),
                e.getMaxAttempts(),
                e.getNextAttemptAt(),
                e.getLastError(),
                e.getSentAt(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }

    /** Full projection for the detail endpoint. */
    public static NotificationOutboxResponse detail(NotificationOutboxEntry e) {
        return new NotificationOutboxResponse(
                e.getId(),
                e.getProfileId(),
                e.getKind(),
                e.getChannel(),
                e.getStatus(),
                e.getToAddress(),
                e.getSubject(),
                e.getBodyText(),
                e.getBodyHtml(),
                e.getIdempotencyKey(),
                e.getAttemptCount(),
                e.getMaxAttempts(),
                e.getNextAttemptAt(),
                e.getLastError(),
                e.getSentAt(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }
}
