package com.webizon.events.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Viewer-facing request body for {@code POST /api/v1/sessions/{id}/registration}.
 *
 * <p>All fields are optional. When {@link #notifyEmail()} is blank
 * the service falls back to the email claim on the caller's JWT —
 * the common "remind me at my login email" path. A supplied value
 * is validated here before the service ever runs so malformed
 * addresses bounce with a 400 instead of getting written to the
 * DB and later failing at SMTP time.
 */
public record SessionRegistrationRequest(
        @Email @Size(max = 320) String notifyEmail,
        Boolean emailRemindersEnabled
) {
}
