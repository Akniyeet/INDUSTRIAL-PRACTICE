package com.webizon.events.api.dto;

import com.webizon.events.model.Event;
import com.webizon.events.model.EventStatus;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Full admin-facing view of an {@link Event}.
 *
 * <p>{@code landingConfig} mirrors the "Лендинг Builder" JSONB blob
 * stored on the event row so the admin edit form can rehydrate the
 * benefits/timeline steps a previous author composed.
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
        long version,
        Map<String, Object> landingConfig
) {
    public static EventResponse from(Event e) {
        return new EventResponse(
                e.getId(),
                e.getSlug(),
                e.getTitle(),
                e.getDescription(),
                e.getSpeakerName(),
                e.getSpeakerBio(),
                stripQuery(e.getCoverImageUrl()),
                e.getTimezone(),
                e.getLanguage(),
                e.getStatus(),
                e.getCreatedByUserId(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getVersion(),
                e.getLandingConfig() != null ? e.getLandingConfig() : new HashMap<>()
        );
    }

    /**
     * Trim {@code ?X-Amz-…} from a presigned MinIO URL so the admin UI
     * can render the cover via the bucket's anonymous-read policy
     * instead of a signature that expires 15 minutes after upload.
     * Mirrors {@link PublicEventView#from} — kept in sync so both
     * facets of the event (admin and public) present the same stable
     * cover URL to the browser.
     */
    private static String stripQuery(String url) {
        if (url == null) return null;
        int q = url.indexOf('?');
        return q < 0 ? url : url.substring(0, q);
    }
}
