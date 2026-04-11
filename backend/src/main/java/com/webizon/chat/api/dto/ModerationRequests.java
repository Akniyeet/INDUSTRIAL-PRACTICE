package com.webizon.chat.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request bodies for moderation endpoints, grouped in a single file to
 * keep the admin-facing DTO surface easy to review.
 */
public final class ModerationRequests {

    private ModerationRequests() {}

    public record WarnRequest(
            @NotNull UUID targetUserId,
            @Size(max = 500) String reason
    ) {}

    public record MuteRequest(
            @NotNull UUID targetUserId,
            @Min(1) @Max(86_400) int durationSeconds,
            @Size(max = 500) String reason
    ) {}

    public record BanRequest(
            @NotNull UUID targetUserId,
            @Size(max = 500) String reason
    ) {}

    public record DeleteMessageRequest(
            @NotNull UUID messageId,
            @Size(max = 500) String reason
    ) {}

    public record HideMessageRequest(
            @NotNull UUID messageId,
            @Size(max = 500) String reason
    ) {}
}
