package com.webizon.chat.api.dto;

import com.webizon.chat.model.ChatMessage;
import com.webizon.chat.model.MessageType;

import java.time.Instant;
import java.util.UUID;

/**
 * Public projection of a {@link ChatMessage} for REST responses.
 *
 * <p>Deleted and hidden messages are filtered out at the query level
 * so this DTO does not need to carry those flags. The one exception
 * is the moderation audit view, which uses a separate DTO that does
 * expose them.
 */
public record ChatMessageResponse(
        UUID id,
        UUID sessionId,
        UUID userId,
        MessageType type,
        UUID replyToMessageId,
        String text,
        Integer offsetSeconds,
        Instant createdAt
) {

    public static ChatMessageResponse from(ChatMessage m) {
        return new ChatMessageResponse(
                m.getId(),
                m.getSessionId(),
                m.getUserId(),
                m.getMessageType(),
                m.getReplyToMessageId(),
                m.getText(),
                m.getOffsetSeconds(),
                m.getCreatedAt()
        );
    }
}
