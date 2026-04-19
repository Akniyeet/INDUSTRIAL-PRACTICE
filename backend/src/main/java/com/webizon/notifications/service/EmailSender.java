package com.webizon.notifications.service;

import com.webizon.notifications.config.NotificationProperties;
import com.webizon.notifications.model.NotificationOutboxEntry;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * Thin wrapper around Spring's {@link JavaMailSender} that knows how
 * to turn a {@link NotificationOutboxEntry} into a MIME message and
 * hand it to SMTP.
 *
 * <p>This is the only class in the notifications module that talks
 * to the transport. The dispatcher calls it inside a try/catch and
 * interprets a thrown {@link EmailDeliveryException} as a retryable
 * failure — nothing else in the module has any opinion about SMTP.
 *
 * <p>Content policy:
 * <ul>
 *   <li>If the entry carries an HTML body, the message is sent as
 *       multipart/alternative with the plain-text body as the
 *       fallback. Gmail / Outlook preview the HTML; rare text-only
 *       clients see the fallback.</li>
 *   <li>If only the plain-text body is present, the message is sent
 *       as a single-part text/plain. We do NOT auto-convert text →
 *       HTML — caller intent wins.</li>
 * </ul>
 *
 * <p>All I/O exceptions (network, auth, remote reject) are
 * rewrapped as {@link EmailDeliveryException}. Spring's own
 * {@link MailException} hierarchy already distinguishes transient
 * from permanent failures, but the dispatcher only needs the binary
 * "retry vs don't retry" signal that the attempt-count ladder
 * provides — so we collapse everything to one exception type here
 * and let the ladder do the gatekeeping.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailSender {

    private final JavaMailSender mailSender;
    private final NotificationProperties properties;

    /**
     * Send a single outbox entry. Blocks until the remote SMTP
     * server has acknowledged the message (or the configured SMTP
     * timeout fires). Not thread-safe in the sense that calling it
     * from multiple threads with the same entry will cause
     * duplicate delivery; the dispatcher guards that with the
     * PENDING → SENDING status transition.
     *
     * @throws EmailDeliveryException if delivery failed for any
     *         reason; the caller should treat this as a retryable
     *         failure and bump the attempt counter.
     */
    public void send(NotificationOutboxEntry entry) throws EmailDeliveryException {
        MimeMessage mime = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(
                    mime,
                    entry.getBodyHtml() != null, // multipart only when HTML present
                    StandardCharsets.UTF_8.name());

            helper.setFrom(new InternetAddress(
                    properties.senderAddress(),
                    properties.senderName(),
                    StandardCharsets.UTF_8.name()));
            helper.setReplyTo(properties.senderAddress());
            helper.setTo(entry.getToAddress());
            helper.setSubject(entry.getSubject());

            if (entry.getBodyHtml() != null) {
                // (text, html) — Spring sets the alternative
                // parts in the right order for a multipart/alternative
                // body that degrades gracefully.
                helper.setText(entry.getBodyText(), entry.getBodyHtml());
            } else {
                helper.setText(entry.getBodyText(), false);
            }
        } catch (MessagingException | UnsupportedEncodingException ex) {
            // Construction failed — this is a bug in the caller's
            // inputs (bad address format, bad encoding) not a
            // transient transport error. Wrap it anyway so the
            // dispatcher's catch is uniform.
            throw new EmailDeliveryException(
                    "Failed to build MIME message for entry " + entry.getId(), ex);
        }

        try {
            mailSender.send(mime);
        } catch (MailException ex) {
            throw new EmailDeliveryException(
                    "SMTP delivery failed for entry " + entry.getId() + ": " + ex.getMessage(), ex);
        }
    }

    /**
     * Signalling exception raised for every delivery failure. The
     * dispatcher catches this, records the message in
     * {@code lastError}, and lets the retry ladder decide whether
     * to re-enqueue or mark DEAD.
     */
    public static final class EmailDeliveryException extends Exception {
        public EmailDeliveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
