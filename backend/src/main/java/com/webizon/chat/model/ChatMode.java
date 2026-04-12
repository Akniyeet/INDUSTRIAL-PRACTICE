package com.webizon.chat.model;

/**
 * Global chat send permission mode for an event.
 *
 * <ul>
 *   <li>{@code EVERYONE} — all authenticated users may send messages
 *       (subject to per-user bans, mutes, slow mode).</li>
 *   <li>{@code ADMINS_ONLY} — only users whose tenant role is in the
 *       {@code MODERATOR_ROLES} set may send.</li>
 *   <li>{@code DISABLED} — the send endpoint returns 403 for everyone.
 *       Useful during breaks or post-event.</li>
 * </ul>
 */
public enum ChatMode {
    EVERYONE,
    ADMINS_ONLY,
    DISABLED
}
