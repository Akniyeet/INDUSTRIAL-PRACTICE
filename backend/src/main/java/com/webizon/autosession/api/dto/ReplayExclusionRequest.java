package com.webizon.autosession.api.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for toggling the {@code excludedFromReplay} flag on a
 * historical chat row. A single boolean keeps the endpoint idempotent
 * — the same body is valid for both "exclude" and "restore", and
 * re-sending the same value is a no-op in the service layer.
 */
public record ReplayExclusionRequest(
        @NotNull Boolean excluded
) {}
