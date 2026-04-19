package com.webizon.events.api.dto;

import com.webizon.events.model.SessionType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * Admin payload for {@code POST /api/v1/events/{eventId}/sessions}.
 *
 * <p>For {@link SessionType#AUTO} sessions, {@link #sourceLiveSessionId()} is
 * required — AUTO sessions must be anchored to a recorded LIVE session to
 * have something to replay. The service layer enforces this invariant,
 * which is also captured by the database check constraint
 * {@code sessions_auto_source_chk} in {@code V002}.
 *
 * <p>For {@link SessionType#LIVE} sessions, a YouTube URL is required so
 * the room can embed the video immediately when the broadcast starts.
 */
public record SessionCreateRequest(

        @NotNull
        SessionType type,

        @NotNull
        @Future
        Instant startTime,

        @NotNull
        @Min(60)
        Integer plannedDurationSeconds,

        @Size(max = 500)
        String youtubeUrl,

        UUID sourceLiveSessionId
) {}
