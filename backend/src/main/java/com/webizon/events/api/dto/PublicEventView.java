package com.webizon.events.api.dto;

import com.webizon.events.model.Event;

import java.time.Instant;

/**
 * Subset of {@link Event} fields that is safe to expose on the public
 * landing page. Internal fields like {@code createdByUserId} and
 * {@code version} are intentionally omitted.
 */
public record PublicEventView(
        String slug,
        String title,
        String description,
        String speakerName,
        String speakerBio,
        String coverImageUrl,
        String timezone,
        String language,
        Instant updatedAt
) {
    public static PublicEventView from(Event e) {
        return new PublicEventView(
                e.getSlug(),
                e.getTitle(),
                e.getDescription(),
                e.getSpeakerName(),
                e.getSpeakerBio(),
                e.getCoverImageUrl(),
                e.getTimezone(),
                e.getLanguage(),
                e.getUpdatedAt()
        );
    }
}
