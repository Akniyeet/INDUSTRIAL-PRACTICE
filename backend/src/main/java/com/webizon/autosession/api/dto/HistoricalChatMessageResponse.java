package com.webizon.autosession.api.dto;

import com.webizon.chat.model.ChatMessage;
import com.webizon.chat.model.MessageType;

import java.time.Instant;
import java.util.UUID;

/**
 * Admin curation view of one historical chat row.
 *
 * <p>The frontend lists these in offset order so moderators can click
 * through the transcript and toggle {@link #excludedFromReplay} on
 * rows that should not appear in future AUTO sessions. All fields are
 * trivial projections of {@link ChatMessage}; soft-delete state is
 * excluded on purpose because deleted rows never reach this list.
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
