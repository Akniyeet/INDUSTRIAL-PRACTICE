package com.webizon.notifications.model;

import java.util.Set;

/**
 * Lifecycle of a {@link NotificationOutboxEntry}. See V009 comment
 * block for the authoritative state diagram.
 *
 * <pre>
 *                ┌───────────────────────────────┐
 *                v                               │
 *   PENDING ──► SENDING ──► SENT                │
 *                 │                              │
 *                 └─► FAILED ──┬──► PENDING (retry backoff)
 *                              │
 *                              └──► DEAD (max_attempts exceeded)
 * </pre>
 *
 * <p>Note that {@code SENDING} is <em>advisory</em>: if the process
 * dies after flipping to SENDING but before the SMTP call returns,
 * the row is stuck in a non-dispatchable state. The dispatcher query
 * therefore accepts both PENDING and FAILED as "dispatchable", and
 * the healer path (see {@code NotificationDispatcher#reclaimStaleSending})
 * claws stale SENDING rows back to PENDING after a timeout.
 */
public enum NotificationStatus {
    PENDING,
    SENDING,
    SENT,
    FAILED,
    DEAD;

    /** Statuses the dispatcher can pick up on a tick. */
    public static final Set<NotificationStatus> DISPATCHABLE =
            Set.of(PENDING, FAILED);

    public boolean isTerminal() {
        return this == SENT || this == DEAD;
    }
}
