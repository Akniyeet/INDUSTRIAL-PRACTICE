package com.webizon.events.api.dto;

import com.webizon.events.model.Session;
import com.webizon.events.model.SessionStatus;
import com.webizon.events.model.SessionType;

import java.time.Instant;
import java.util.UUID;

/**
 * Safe public projection of a {@link Session}. Tells the frontend what to
 * render (countdown, waiting room, live room) without leaking who created
 * the session or which LIVE recording an AUTO replay is using.
 */
public record PublicSessionView(
        UUID id,
        SessionType type,
        SessionStatus status,
        Instant startTime,
        int plannedDurationSeconds,
        String youtubeEmbedUrl
) {
    public static PublicSessionView from(Session s) {
        return new PublicSessionView(
                s.getId(),
                s.getType(),
                s.getStatus(),
                s.getStartTime(),
                s.getPlannedDurationSeconds(),
                s.getYoutubeEmbedUrl()
        );
    }
}
