package com.webizon.room.api.dto;

import com.webizon.chat.model.ChatMessage;
import com.webizon.chat.model.MessageType;

import java.time.Instant;
import java.util.UUID;

/**
 * Room-bootstrap view of one chat row.
 *
 * <p>Shape is deliberately identical to the Centrifugo envelope
 * {@code ChatService.publishToChannel} sends on the CHAT channel so
 * the frontend can drop bootstrapped rows into the same renderer it
 * uses for live deliveries, without a separate code path.
 */
public record RoomChatMessageView(
        UUID id,
        UUID userId,
        MessageType messageType,
        UUID replyToMessageId,
        String text,
        Integer offsetSeconds,
        Instant createdAt
) {
    public static RoomChatMessageView from(ChatMessage m) {
        return new RoomChatMessageView(
                m.getId(),
                m.getUserId(),
                m.getMessageType(),
                m.getReplyToMessageId(),
                m.getText(),
                m.getOffsetSeconds(),
                m.getCreatedAt()
        );
    }
}
