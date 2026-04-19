package com.webizon.chat.api.dto;

import com.webizon.chat.model.ModerationAction;
import com.webizon.chat.model.ModerationActionType;

import java.time.Instant;
import java.util.UUID;

public record ModerationActionResponse(
        UUID id,
        UUID sessionId,
        UUID targetUserId,
        UUID moderatorUserId,
        ModerationActionType actionType,
        UUID targetMessageId,
        String reason,
        Integer durationSeconds,
        Instant createdAt
) {

    public static ModerationActionResponse from(ModerationAction a) {
        return new ModerationActionResponse(
                a.getId(),
                a.getSessionId(),
                a.getTargetUserId(),
                a.getModeratorUserId(),
                a.getActionType(),
                a.getTargetMessageId(),
                a.getReason(),
                a.getDurationSeconds(),
                a.getCreatedAt()
        );
    }
}
