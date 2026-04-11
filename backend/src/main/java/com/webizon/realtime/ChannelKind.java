package com.webizon.realtime;

/**
 * The distinct logical streams that make up a session's real-time layer.
 *
 * <p>Every channel name produced by {@link ChannelNameFactory} has exactly
 * one of these as its trailing segment. Splitting the traffic across
 * dedicated channels keeps fan-out clean: moderators can ban a user from
 * chat without dropping their CTA subscription, and the server can publish
 * a control command without triggering every client's chat renderer.
 */
public enum ChannelKind {

    /** User-visible chat messages, deletions, and moderation notices. */
    CHAT,

    /** CTA show/hide/update events driven by the timeline engine. */
    CTA,

    /** Presence heartbeats: join / leave / still-here pings. */
    PRESENCE,

    /** Session lifecycle transitions: SCHEDULED → LIVE → ENDED. */
    STATE,

    /** Admin/moderator-only control channel: chat bans, slow-mode changes. */
    CONTROL;

    public String token() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static ChannelKind fromToken(String token) {
        return ChannelKind.valueOf(token.toUpperCase(java.util.Locale.ROOT));
    }
}
