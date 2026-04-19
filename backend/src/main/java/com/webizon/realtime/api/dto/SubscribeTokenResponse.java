package com.webizon.realtime.api.dto;

import java.time.Instant;

/**
 * Response for {@code POST /api/v1/realtime/subscribe-token}.
 *
 * @param token     the signed HS256 JWT the front-end hands to
 *                  {@code centrifuge.subscribe(channel, {token})}
 * @param expiresAt absolute expiry instant; the client should request
 *                  a fresh token on every new subscribe rather than
 *                  caching old ones
 */
public record SubscribeTokenResponse(String token, Instant expiresAt) {}
