package com.webizon.events.api.dto;

import com.webizon.events.model.Event;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Subset of {@link Event} fields that is safe to expose on the public
 * landing page. Internal fields like {@code createdByUserId} and
 * {@code version} are intentionally omitted.
 *
 * <p>{@code landingConfig} forwards the "Лендинг Builder" payload so
 * the public page can render the admin-authored benefits and timeline
 * sections without a second round-trip.
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
        Instant updatedAt,
        Map<String, Object> landingConfig
) {
    public static PublicEventView from(Event e) {
        return new PublicEventView(
                e.getSlug(),
                e.getTitle(),
                e.getDescription(),
                e.getSpeakerName(),
                e.getSpeakerBio(),
                stripQuery(e.getCoverImageUrl()),
                e.getTimezone(),
                e.getLanguage(),
                e.getUpdatedAt(),
                e.getLandingConfig() != null ? e.getLandingConfig() : new HashMap<>()
        );
    }

    /**
     * Drop the {@code ?X-Amz-…} query-string from a MinIO URL.
     *
     * <p>The upload flow historically persisted the full presigned PUT
     * URL returned by the slot-reservation step, which includes a
     * signature that expires 15 minutes after upload. That made every
     * landing page view after the TTL 403 out. Since the covers bucket
     * is served with an anonymous read policy (see
     * {@code MinioClientConfig.makePublicReadOnly}), we can safely trim
     * everything after the {@code ?} at read time and hand the browser
     * a stable, unsigned URL.
     */
    private static String stripQuery(String url) {
        if (url == null) return null;
        int q = url.indexOf('?');
        return q < 0 ? url : url.substring(0, q);
    }
}
