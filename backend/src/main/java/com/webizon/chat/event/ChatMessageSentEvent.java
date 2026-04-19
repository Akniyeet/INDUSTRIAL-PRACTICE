package com.webizon.chat.event;

import java.util.UUID;

/**
 * Published by {@code ChatService} every time a user-authored chat
 * message is successfully persisted and broadcast.
 *
 * <p>This is the decoupling seam between chat and analytics: the chat
 * module doesn't know or care that analytics exists, it just fires a
 * Spring {@link org.springframework.context.ApplicationEvent} after
 * commit and moves on. A listener in the analytics module translates
 * the event into an {@code AnalyticsEvent} row.
 *
 * <p>Emitted only for real user messages ({@code USER}) and their
 * replies. SYSTEM-authored banners are not traffic and would pollute
 * chat engagement metrics, so they're excluded.
 *
 * @param tenantId     owning tenant
 * @param eventId      parent event
 * @param sessionId    live/auto session where the message was sent
 * @param profileId    author
 * @param messageId    persisted chat message id
 * @param isReply      {@code true} if {@code replyToMessageId} was set
 * @param offsetSeconds video-relative offset when the message was
 *                     sent ({@code null} before the session starts)
 */
public record ChatMessageSentEvent(
        UUID tenantId,
        UUID eventId,
        UUID sessionId,
        UUID profileId,
        UUID messageId,
        boolean isReply,
        Integer offsetSeconds
) {}
