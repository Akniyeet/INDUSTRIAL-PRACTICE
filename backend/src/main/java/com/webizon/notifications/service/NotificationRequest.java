package com.webizon.notifications.service;

import com.webizon.notifications.model.NotificationChannel;
import com.webizon.notifications.model.NotificationKind;

import java.util.UUID;

/**
 * Immutable enqueue request accepted by
 * {@link NotificationService#enqueue(NotificationRequest)}.
 *
 * <p>Every caller constructs one of these inside the business
 * transaction and hands it off. The request intentionally carries
 * the <em>already-rendered</em> subject and bodies — Phase 12 does
 * not ship a template engine. A future template module can add a
 * sibling {@code NotificationTemplateRequest} that renders into a
 * {@code NotificationRequest} without any change to the service or
 * dispatcher.
 *
 * @param profileId       optional recipient profile; null for
 *                        system-origin mails addressed to a raw
 *                        address only
 * @param kind            business reason, drives {@code maxAttempts}
 *                        and the retry policy
 * @param channel         delivery transport; only EMAIL works in Phase 12
 * @param toAddress       delivery target; frozen at enqueue time
 * @param subject         pre-rendered, must fit 255 chars
 * @param bodyText        pre-rendered plain-text body, required
 * @param bodyHtml        optional HTML body; null means "send as text/plain"
 * @param payloadJson     optional JSON blob for admin-UI rendering;
 *                        defaults to {@code "{}"} if null
 * @param idempotencyKey  optional dedupe key; strongly recommended
 *                        for every production call site
 */
public record NotificationRequest(
        UUID profileId,
        NotificationKind kind,
        NotificationChannel channel,
        String toAddress,
        String subject,
        String bodyText,
        String bodyHtml,
        String payloadJson,
        String idempotencyKey
) {

    /**
     * Convenience factory for the most common shape: a text-only
     * email with a dedupe key. Use this instead of the full
     * constructor unless you actually need HTML or an attached
     * payload.
     */
    public static NotificationRequest email(
            UUID profileId,
            NotificationKind kind,
            String toAddress,
            String subject,
            String bodyText,
            String idempotencyKey
    ) {
        return new NotificationRequest(
                profileId,
                kind,
                NotificationChannel.EMAIL,
                toAddress,
                subject,
                bodyText,
                null,
                "{}",
                idempotencyKey);
    }
}
