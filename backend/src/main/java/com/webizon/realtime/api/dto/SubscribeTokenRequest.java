package com.webizon.realtime.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/realtime/subscribe-token}.
 *
 * <p>Only one field: the exact channel name the client wants to
 * subscribe to. The server rebuilds and re-validates it with
 * {@code ChannelNameFactory} — the client is never trusted to have
 * constructed a safe channel name.
 *
 * @param channel the Centrifugo channel name, unmodified from what the
 *                front-end intends to pass to {@code centrifuge.subscribe}
 */
public record SubscribeTokenRequest(
        @NotBlank
        @Size(max = 256)
        String channel
) {}
