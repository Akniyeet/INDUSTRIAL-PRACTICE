package com.webizon.chat.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/sessions/{id}/chat/messages}.
 *
 * <p>The policy chain also checks message length but we enforce a
 * bean-validation bound here so the REST layer rejects obviously
 * oversized payloads before touching the database.
 */
public record SendMessageRequest(
        @NotBlank
        @Size(max = 500)
        String text,
        UUID replyToMessageId
) {}
