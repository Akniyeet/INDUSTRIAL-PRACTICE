package com.webizon.analytics.api.dto;

import com.webizon.analytics.model.AnalyticsEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Minimal acknowledgement for a recorded analytics event. The client
 * only needs an id for correlation; the full row is never returned
 * to keep the response small on the hot path.
 */
public record TrackEventResponse(UUID id, Instant createdAt) {

    public static TrackEventResponse from(AnalyticsEvent e) {
        return new TrackEventResponse(e.getId(), e.getCreatedAt());
    }
}
