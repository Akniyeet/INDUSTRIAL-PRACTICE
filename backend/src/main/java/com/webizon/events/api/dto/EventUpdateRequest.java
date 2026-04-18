package com.webizon.events.api.dto;

import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Admin payload for {@code PATCH /api/v1/events/{id}}. Every field is
 * optional; nulls are treated as "no change".
 *
 * <p>{@code landingConfig} carries the freeform "Лендинг Builder" blob.
 * Passing {@code null} leaves the stored value untouched; passing an
 * empty map explicitly clears the landing customisation back to default.
 */
public record EventUpdateRequest(

        @Size(max = 200)
        String title,

        @Size(max = 10_000)
        String description,

        @Size(max = 120)
        String speakerName,

        @Size(max = 5_000)
        String speakerBio,

        @Size(max = 500)
        String coverImageUrl,

        @Size(max = 64)
        String timezone,

        @Size(max = 16)
        String language,

        Map<String, Object> landingConfig
) {}
