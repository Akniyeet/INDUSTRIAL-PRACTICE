package com.webizon.chat.api.dto;

import com.webizon.chat.model.ChatMessage;
import com.webizon.chat.model.MessageType;

import java.time.Instant;
import java.util.UUID;

/**
 * Extended projection of a {@link ChatMessage} for the admin historical
 * chat curation view. Includes {@code excludedFromReplay} so admins can
 * toggle which messages replay in future AUTO sessions.
 */
public record HistoricalChatMessageResponse(
        UUID id,
        UUID sessionId,
        UUID userId,
        MessageType messageType,
        UUID replyToMessageId,
        String text,
        Integer offsetSeconds,
        boolean excludedFromReplay,
        Instant createdAt
) {

    public static HistoricalChatMessageResponse from(ChatMessage m) {
        return new HistoricalChatMessageResponse(
                m.getId(),
                m.getSessionId(),
                m.getUserId(),
                m.getMessageType(),
                m.getReplyToMessageId(),
                m.getText(),
                m.getOffsetSeconds(),
                m.isExcludedFromReplay(),
                m.getCreatedAt()
        );
    }
}
