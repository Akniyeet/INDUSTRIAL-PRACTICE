package com.webizon.chat.policy;

import com.webizon.chat.model.ChatUserStatus;
import com.webizon.chat.model.EventChatSettings;

import java.time.Instant;
import java.util.UUID;

/**
 * Everything a {@link ChatPolicy} needs to make a yes/no decision about
 * a candidate message, gathered once per send request by
 * {@code ChatService} before the chain runs.
 *
 * <p>Context is intentionally a flat, immutable record rather than a
 * bag-of-getters: every policy gets exactly the same view of the
 * decision, and no policy can accidentally mutate a field the next one
 * relies on. It is also trivially cacheable if we ever want to run the
 * chain outside the request thread.
 *
 * <p><b>Moderator bypass</b> — callers set {@link #isModerator} based on
 * the caller's role. Policies that bypass for moderators check this
 * flag explicitly; there is no implicit "privileged short-circuit" to
 * avoid surprises.
 *
 * @param tenantId     the acting user's tenant (never null)
 * @param sessionId    the target session (never null)
 * @param userId       the message author (never null — SYSTEM messages
 *                     bypass the policy chain entirely)
 * @param text         the candidate message text as the user typed it
 * @param now          the reference timestamp for slow-mode / mute checks
 * @param userStatus   the user's existing row, or {@code null} if this
 *                     is their first message in this session
 * @param settings     the resolved {@link EventChatSettings} for this
 *                     session's event (never null — a default row is
 *                     lazily created by the settings service)
 * @param isModerator  whether the caller holds a moderator-level role
 *                     and is therefore exempt from mute, slow-mode,
 *                     link, and word-filter checks
 */
public record ChatPolicyContext(
        UUID tenantId,
        UUID sessionId,
        UUID userId,
        String text,
        Instant now,
        ChatUserStatus userStatus,
        EventChatSettings settings,
        boolean isModerator
) {}
