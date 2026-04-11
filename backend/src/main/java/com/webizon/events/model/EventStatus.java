package com.webizon.events.model;

/**
 * Lifecycle of an {@link Event}.
 *
 * <ul>
 *   <li>{@link #DRAFT} — invisible to the public, admin can edit freely</li>
 *   <li>{@link #PUBLISHED} — landing page is live, sessions can be scheduled</li>
 *   <li>{@link #ARCHIVED} — read-only, kept for analytics and replay</li>
 * </ul>
 */
public enum EventStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED
}
