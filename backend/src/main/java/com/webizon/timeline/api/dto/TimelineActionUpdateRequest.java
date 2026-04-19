package com.webizon.timeline.api.dto;

import jakarta.validation.constraints.Min;

import java.util.Map;

/**
 * Partial update for a timeline row. Nulls are ignored so admins can
 * tweak one field at a time (e.g. shift offset without rewriting the
 * payload).
 */
public record TimelineActionUpdateRequest(
        @Min(0) Integer offsetSeconds,
        Map<String, Object> payload
) {}
