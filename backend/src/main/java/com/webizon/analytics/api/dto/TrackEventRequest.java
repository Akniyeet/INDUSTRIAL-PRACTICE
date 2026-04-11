package com.webizon.analytics.api.dto;

import com.webizon.analytics.model.AnalyticsEventType;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

/**
 * Client-submitted analytics event. The controller adds the caller's
 * {@code profileId} before handing this to {@code AnalyticsRecorder}
 * so clients can't spoof identity — they only describe what
 * happened, not who it happened to.
 *
 * <p>{@code sessionId} is nullable because the same endpoint serves
 * landing-page views that don't yet have a session. {@code eventId}
 * is nullable because the recorder can look it up from
 * {@code sessionId} when needed. {@code offsetSeconds} is only
 * meaningful for in-session milestones / CTA interactions and is
 * ignored otherwise.
 */
public record TrackEventRequest(
        UUID eventId,
        UUID sessionId,
        String clientKey,
        @NotNull AnalyticsEventType type,
        Integer offsetSeconds,
        Map<String, Object> metadata
) {}
