package com.webizon.autosession.api.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Request body for scheduling a single AUTO replay slot.
 *
 * <p>The event id comes from the path (so admins cannot mis-target
 * another event by accident), while the source LIVE session id and
 * the wall-clock start time live in the body. Both are validated by
 * {@code AutoSessionService.createSlot}.
 */
public record CreateAutoSlotRequest(
        @NotNull UUID sourceLiveSessionId,
        @NotNull Instant startTime
) {}
