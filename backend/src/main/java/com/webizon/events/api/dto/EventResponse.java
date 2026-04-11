package com.webizon.events.api.dto;

import com.webizon.events.model.Event;
import com.webizon.events.model.EventStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Full admin-facing view of an {@link Event}.
 */
public record EventResponse(
        UUID id,
        String slug,
        String title,
        String description,
        String speakerName,
        String speakerBio,
        String coverImageUrl,
        String timezone,
        String language,
        EventStatus status,
        UUID createdByUserId,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public static EventResponse from(Event e) {
        return new EventResponse(
                e.getId(),
                e.getSlug(),
                e.getTitle(),
                e.getDescription(),
                e.getSpeakerName(),
                e.getSpeakerBio(),
                e.getCoverImageUrl(),
                e.getTimezone(),
                e.getLanguage(),
                e.getStatus(),
                e.getCreatedByUserId(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getVersion()
        );
    }
}
