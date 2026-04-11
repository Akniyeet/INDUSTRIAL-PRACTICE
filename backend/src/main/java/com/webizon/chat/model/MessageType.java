package com.webizon.chat.model;

/**
 * Classifies a {@link ChatMessage} by its origin and intended rendering.
 *
 * <p>The enum lives on the row itself rather than being derived at read
 * time because the chat policy chain and the historical replay engine
 * branch on it hot-path: a single {@code switch} is cheaper than a role
 * lookup or string comparison.
 */
public enum MessageType {

    /** Written by a normal participant during a session. */
    USER,

    /** Written by a moderator or admin; rendered with an "admin" badge. */
    ADMIN,

    /** Automated line (welcome message, cooldown notice, etc). No author. */
    SYSTEM,

    /**
     * Message that originated in a source LIVE session and is being
     * replayed inside an AUTO session at the appropriate offset. Carries
     * the same {@code offset_seconds} as the original row.
     */
    HISTORICAL
}
