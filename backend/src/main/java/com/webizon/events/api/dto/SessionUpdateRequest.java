package com.webizon.events.api.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Admin payload for {@code PATCH /api/v1/sessions/{id}}. All fields are
 * optional. The service layer refuses edits on any session whose status is
 * in {@code SessionStatus.FINAL_STATUSES} or currently airing — finalized
 * sessions are immutable, and changing the start time of a live broadcast
 * would corrupt attendance analytics.
 */
public record SessionUpdateRequest(

        @Future
        Instant startTime,

        @Min(60)
        Integer plannedDurationSeconds,

        @Size(max = 500)
        String youtubeUrl
) {}
