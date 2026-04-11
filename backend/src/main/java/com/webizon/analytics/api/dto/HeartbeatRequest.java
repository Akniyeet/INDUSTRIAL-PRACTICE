package com.webizon.analytics.api.dto;

/**
 * Periodic client-to-server keep-alive carrying the current video
 * offset. The offset is optional — a client that can't read the
 * player's current time can still send a ping with {@code null} and
 * keep the {@code last_seen_at} clock ticking on the server side.
 */
public record HeartbeatRequest(Integer offsetSeconds) {}
