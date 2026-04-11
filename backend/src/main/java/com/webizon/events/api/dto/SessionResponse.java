package com.webizon.events.api.dto;

import com.webizon.events.model.Session;
import com.webizon.events.model.SessionStatus;
import com.webizon.events.model.SessionType;

import java.time.Instant;
import java.util.UUID;

/**
 * Full admin-facing view of a {@link Session}.
 */
public record SessionResponse(
        UUID id,
        UUID eventId,
        SessionType type,
        SessionStatus status,
        Instant startTime,
        int plannedDurationSeconds,
        String youtubeUrl,
        String youtubeVideoId,
        String youtubeEmbedUrl,
        UUID sourceLiveSessionId,
        Instant actualStartedAt,
        Instant actualEndedAt,
        Instant finalizedAt,
        UUID createdByUserId,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public static SessionResponse from(Session s) {
        return new SessionResponse(
                s.getId(),
                s.getEventId(),
                s.getType(),
                s.getStatus(),
                s.getStartTime(),
                s.getPlannedDurationSeconds(),
                s.getYoutubeUrl(),
                s.getYoutubeVideoId(),
                s.getYoutubeEmbedUrl(),
                s.getSourceLiveSessionId(),
                s.getActualStartedAt(),
                s.getActualEndedAt(),
                s.getFinalizedAt(),
                s.getCreatedByUserId(),
                s.getCreatedAt(),
                s.getUpdatedAt(),
                s.getVersion()
        );
    }
}
