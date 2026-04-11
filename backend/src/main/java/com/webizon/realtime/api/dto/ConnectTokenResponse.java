package com.webizon.realtime.api.dto;

import java.time.Instant;

/**
 * Response for {@code POST /api/v1/realtime/connect-token}.
 *
 * @param token     the signed HS256 JWT the front-end hands to Centrifugo
 *                  as the {@code token} parameter on the {@code connect}
 *                  frame
 * @param expiresAt absolute expiry instant; the client should refresh
 *                  at least a few seconds before this value
 */
public record ConnectTokenResponse(String token, Instant expiresAt) {}
