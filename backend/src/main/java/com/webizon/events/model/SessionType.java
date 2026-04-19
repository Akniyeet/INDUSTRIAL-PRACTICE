package com.webizon.events.model;

/**
 * Discriminator between original broadcasts and scheduled replays.
 *
 * <p>A {@link Session} is either the first-time recording of an event
 * ({@link #LIVE}) or a new, separate run that replays a previous LIVE session
 * according to its captured timeline ({@link #AUTO}).
 */
public enum SessionType {
    LIVE,
    AUTO
}
