package com.webizon.notifications.model;

/**
 * Transport used to deliver a notification.
 *
 * <p>Phase 12 implements {@link #EMAIL} only. {@link #SMS} and
 * {@link #PUSH} are reserved at the schema level so a later phase
 * can add a new {@code ChannelSender} bean and wire it into the
 * dispatcher without touching the outbox table or any of the
 * existing emitting sites.
 */
public enum NotificationChannel {
    EMAIL,
    SMS,
    PUSH
}
