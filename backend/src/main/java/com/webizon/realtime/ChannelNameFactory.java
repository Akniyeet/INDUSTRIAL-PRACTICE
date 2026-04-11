package com.webizon.realtime;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Single source of truth for Centrifugo channel names.
 *
 * <p>Channels MUST only be constructed and parsed through this factory.
 * Grepping for the string {@code "tenant."} outside this file (or its
 * tests) is a code review failure.
 *
 * <p>The canonical shape is:
 * <pre>
 *   tenant.{tenantId}.session.{sessionId}.{kind}
 * </pre>
 * where {@code tenantId} and {@code sessionId} are both lowercase UUIDs
 * and {@code kind} is one of {@link ChannelKind}'s tokens.
 *
 * <p>Private per-user channels use Centrifugo's {@code '#'} user-limited
 * syntax so that the Centrifugo server itself refuses delivery to any
 * client whose connect token does not match the user segment. This is a
 * belt-and-braces defence on top of the subscribe-token check.
 *
 * <p>See ADR-0003 for the naming convention and rationale.
 */
@Component
public final class ChannelNameFactory {

    /**
     * Strict pattern: lowercase UUID v4 shape for both ids, lowercase
     * token for the kind. Anything else is rejected by {@link #parse(String)}.
     */
    private static final Pattern SESSION_CHANNEL_PATTERN = Pattern.compile(
            "^tenant\\.([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})"
                    + "\\.session\\.([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})"
                    + "\\.([a-z]+)$"
    );

    /**
     * Build the channel name for a given session and kind.
     *
     * @throws NullPointerException if any argument is {@code null}
     */
    public String sessionChannel(UUID tenantId, UUID sessionId, ChannelKind kind) {
        java.util.Objects.requireNonNull(tenantId, "tenantId");
        java.util.Objects.requireNonNull(sessionId, "sessionId");
        java.util.Objects.requireNonNull(kind, "kind");
        return "tenant." + tenantId + ".session." + sessionId + "." + kind.token();
    }

    /**
     * Private per-user channel.
     *
     * <p>The {@code '#'} suffix is Centrifugo's user-limited channel syntax:
     * only the connection whose token {@code sub} matches {@code userId}
     * is allowed to subscribe. This means we do not need a separate
     * subscribe token for private channels — the connect token is enough.
     */
    public String privateUserChannel(UUID tenantId, UUID userId) {
        java.util.Objects.requireNonNull(tenantId, "tenantId");
        java.util.Objects.requireNonNull(userId, "userId");
        return "tenant." + tenantId + ".user#" + userId;
    }

    /**
     * Parse a channel name back into its components.
     *
     * <p>Returns {@code null} if the channel does not match the canonical
     * session-channel shape — callers should treat that as "not one of
     * ours" and reject the subscribe request.
     */
    public ParsedChannel parse(String channel) {
        if (channel == null || channel.isEmpty()) {
            return null;
        }
        Matcher m = SESSION_CHANNEL_PATTERN.matcher(channel);
        if (!m.matches()) {
            return null;
        }
        UUID tenantId;
        UUID sessionId;
        ChannelKind kind;
        try {
            tenantId = UUID.fromString(m.group(1));
            sessionId = UUID.fromString(m.group(2));
            kind = ChannelKind.fromToken(m.group(3));
        } catch (IllegalArgumentException ex) {
            // Regex passed but UUID/enum rejected — treat as malformed.
            return null;
        }
        return new ParsedChannel(tenantId, sessionId, kind);
    }

    /**
     * Structured view of a parsed session channel. Returned by
     * {@link #parse(String)}; never constructed directly by callers.
     */
    public record ParsedChannel(UUID tenantId, UUID sessionId, ChannelKind kind) {}
}
