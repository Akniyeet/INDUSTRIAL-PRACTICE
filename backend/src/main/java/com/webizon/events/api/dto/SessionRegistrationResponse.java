package com.webizon.events.api.dto;

import com.webizon.events.model.SessionRegistration;

import java.time.Instant;
import java.util.UUID;

/**
 * Wire shape returned by every registration endpoint. Intentionally
 * minimal — the viewer dashboard only needs the lifecycle state,
 * the reminder target, and the ids needed to re-fetch the parent
 * session if the row is clicked.
 */
public record SessionRegistrationResponse(
        UUID id,
        UUID sessionId,
        UUID eventId,
        UUID profileId,
        String notifyEmail,
        boolean emailRemindersEnabled,
        Instant registeredAt,
        Instant unregisteredAt,
        Instant reminderSentAt
) {

    public static SessionRegistrationResponse from(SessionRegistration r) {
        return new SessionRegistrationResponse(
                r.getId(),
                r.getSessionId(),
                r.getEventId(),
                r.getProfileId(),
                r.getNotifyEmail(),
                r.isEmailRemindersEnabled(),
                r.getRegisteredAt(),
                r.getUnregisteredAt(),
                r.getReminderSentAt());
    }
}
