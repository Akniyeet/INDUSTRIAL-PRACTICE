package com.webizon.events.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Admin payload for {@code POST /api/v1/events}.
 *
 * <p>The slug pattern matches the database CHECK constraint in
 * {@code V002__events_and_sessions.sql}. Slugs are per-tenant unique, so
 * keeping them short and URL-safe is the only requirement.
 *
 * <p>{@code landingConfig} carries the freeform "Лендинг Builder" blob:
 * {@code benefitsTitle}, {@code benefits[]} ({@code {title, desc, icon}}),
 * {@code timelineTitle}, {@code timelineSubtitle},
 * {@code timeline[]} ({@code {title, desc}}). Stored as JSONB so the
 * landing UI stays flexible without schema churn.
 */
public record EventCreateRequest(

        @NotBlank
        @Size(min = 3, max = 64)
        @Pattern(
                regexp = "^[a-z0-9][a-z0-9-]{1,62}[a-z0-9]$",
                message = "slug must be 3-64 chars of lowercase letters, digits or hyphens"
        )
        String slug,

        @NotBlank
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
