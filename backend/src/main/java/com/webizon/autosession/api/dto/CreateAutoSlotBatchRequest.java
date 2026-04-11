package com.webizon.autosession.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Request body for the bulk slot-creation endpoint.
 *
 * <p>The common admin workflow is "run this recording three times over
 * the next week" — rather than making three separate calls, the admin
 * UI submits the full list and the service validates each entry
 * together. Any individual failure aborts the whole batch so the admin
 * never ends up with a half-scheduled playlist.
 */
public record CreateAutoSlotBatchRequest(
        @NotNull UUID sourceLiveSessionId,
        @NotEmpty List<@NotNull Instant> startTimes
) {}
