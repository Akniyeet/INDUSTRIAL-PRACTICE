package com.webizon.analytics.api.dto;

import com.webizon.analytics.model.SessionAttendance;

import java.time.Instant;
import java.util.UUID;

/**
 * Snapshot of a viewer's attendance row — returned by the join,
 * heartbeat, and leave endpoints so the frontend can render the
 * "total watch time" badge without a second round-trip.
 */
public record AttendanceResponse(
        UUID sessionId,
        UUID profileId,
        Instant firstJoinedAt,
        Instant lastSeenAt,
        Instant leftAt,
        int totalConnectedSeconds,
        int entryCount,
        Integer maxOffsetSeconds,
        boolean currentlyPresent
) {

    public static AttendanceResponse from(SessionAttendance a) {
        return new AttendanceResponse(
                a.getSessionId(),
                a.getProfileId(),
                a.getFirstJoinedAt(),
                a.getLastSeenAt(),
                a.getLeftAt(),
                a.getTotalConnectedSeconds(),
                a.getEntryCount(),
                a.getMaxOffsetSeconds(),
                a.isCurrentlyPresent()
        );
    }
}
