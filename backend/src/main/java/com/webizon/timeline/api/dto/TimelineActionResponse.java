package com.webizon.timeline.api.dto;

import com.webizon.timeline.model.EventTimelineAction;
import com.webizon.timeline.model.TimelineActionType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TimelineActionResponse(
        UUID id,
        UUID eventId,
        UUID sourceSessionId,
        int offsetSeconds,
        TimelineActionType actionType,
        Map<String, Object> payload,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {

    public static TimelineActionResponse from(EventTimelineAction a) {
        return new TimelineActionResponse(
                a.getId(),
                a.getEventId(),
                a.getSourceSessionId(),
                a.getOffsetSeconds(),
                a.getActionType(),
                a.getPayload(),
                a.isActive(),
                a.getCreatedAt(),
                a.getUpdatedAt()
        );
    }
}
