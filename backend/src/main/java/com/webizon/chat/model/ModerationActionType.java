package com.webizon.chat.model;

/**
 * Every kind of moderator act Webizon records in the audit log.
 *
 * <p>The enum is intentionally flat and closed: adding a new action
 * requires a migration (to update the CHECK constraint on
 * {@code moderation_actions.action_type}) and an application release
 * in lockstep, which is the right friction for a domain with legal
 * audit implications.
 *
 * <p>Actions fall into three target categories, enforced by the
 * {@code moderation_actions_target_chk} database constraint:
 * <ul>
 *   <li><b>User-targeted</b>: WARNING, MUTE, CHAT_BAN, ROOM_REMOVE, FULL_BAN</li>
 *   <li><b>Message-targeted</b>: MESSAGE_DELETE, MESSAGE_HIDE</li>
 *   <li><b>Session-targeted</b>: SLOW_MODE_CHANGE</li>
 * </ul>
 */
public enum ModerationActionType {

    /** Private warning shown only to the offending user. Does not mute. */
    WARNING,

    /** Temporary mute — {@code duration_seconds} must be set. */
    MUTE,

    /** Indefinite chat ban for this session; user may still watch the video. */
    CHAT_BAN,

    /** Disconnect from the session; user may re-join unless also banned. */
    ROOM_REMOVE,

    /** Full ban for this session; blocks chat and room for the rest of the run. */
    FULL_BAN,

    /** Soft-delete a specific message. Row stays, is_deleted flips to true. */
    MESSAGE_DELETE,

    /** Hide a message from the live feed but keep it visible to moderators. */
    MESSAGE_HIDE,

    /** Change the session-wide slow mode. {@code target_user_id} is null. */
    SLOW_MODE_CHANGE
}
