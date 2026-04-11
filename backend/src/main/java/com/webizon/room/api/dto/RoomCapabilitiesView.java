package com.webizon.room.api.dto;

/**
 * Role-derived capability flags for the room UI.
 *
 * <p>The backend still enforces every action through its own
 * {@code @PreAuthorize} rules — these booleans exist purely so the
 * frontend can hide or disable controls the caller is not allowed
 * to use. A client forging {@code canModerate=true} gains nothing;
 * the real moderation endpoints re-check the JWT role.
 */
public record RoomCapabilitiesView(
        boolean canSendChat,
        boolean canReplyInChat,
        boolean canModerate,
        boolean canTriggerCtas,
        boolean bypassSlowMode
) {
}
