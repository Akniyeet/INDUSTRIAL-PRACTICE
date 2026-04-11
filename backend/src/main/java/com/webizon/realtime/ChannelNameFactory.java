package com.webizon.realtime;

import java.util.UUID;

/**
 * Single source of truth for Centrifugo channel names.
 *
 * <p>Channels MUST only be constructed through this factory. Grepping for
 * {@code "tenant."} outside this file is a code review failure.
 *
 * <p>See ADR-0003 for the naming convention and rationale.
 */
public final class ChannelNameFactory {

    private static final String PREFIX = "tenant.";

    private ChannelNameFactory() {
        throw new UnsupportedOperationException("utility class");
    }

    public static String chat(UUID tenantId, long sessionId) {
        return PREFIX + tenantId + ".session." + sessionId + ".chat";
    }

    public static String system(UUID tenantId, long sessionId) {
        return PREFIX + tenantId + ".session." + sessionId + ".system";
    }

    public static String timeline(UUID tenantId, long sessionId) {
        return PREFIX + tenantId + ".session." + sessionId + ".timeline";
    }

    public static String presence(UUID tenantId, long sessionId) {
        return PREFIX + tenantId + ".session." + sessionId + ".presence";
    }

    /** Moderator-only channel. Subscription token must carry moderator role. */
    public static String adminOnly(UUID tenantId, long sessionId) {
        return PREFIX + tenantId + ".session." + sessionId + ".admin";
    }

    /** Private per-user channel (Centrifugo '$' prefix denotes private). */
    public static String privateUser(UUID tenantId, UUID userId) {
        return "$" + PREFIX + tenantId + ".user." + userId;
    }
}
