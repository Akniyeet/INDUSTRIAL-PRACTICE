package com.webizon.chat.event;

import com.webizon.chat.model.ModerationActionType;

import java.util.UUID;

/**
 * Published by {@code ModerationService} whenever a moderation action
 * is persisted and broadcast.
 *
 * <p>Translates to exactly one behavioural event on the target user:
 * {@code WARNING_RECEIVED}, {@code MUTED}, {@code CHAT_BANNED},
 * {@code ROOM_REMOVED}, or {@code FULL_BANNED}. Message-level actions
 * ({@code MESSAGE_DELETE}, {@code MESSAGE_HIDE}) are also propagated
 * so admin activity dashboards can show moderation volume, but they
 * don't feed the negative lead-signal path because a deleted message
 * is a signal about the message, not the author.
 *
 * @param tenantId        owning tenant
 * @param eventId         parent event
 * @param sessionId       session in which the action occurred
 * @param targetUserId    user the action was applied to (may be null
 *                        for message-scoped actions if the original
 *                        sender is unknown — shouldn't happen in
 *                        practice but we keep the field nullable for
 *                        safety)
 * @param moderatorUserId moderator who performed the action
 * @param actionType      kind of action
 * @param targetMessageId for message-scoped actions only
 */
public record ModerationAppliedEvent(
        UUID tenantId,
        UUID eventId,
        UUID sessionId,
        UUID targetUserId,
        UUID moderatorUserId,
        ModerationActionType actionType,
        UUID targetMessageId
) {}
