package com.webizon.timeline.api.dto;

import com.webizon.timeline.model.TimelineActionType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record TimelineActionCreateRequest(
        @NotNull @Min(0) Integer offsetSeconds,
        @NotNull TimelineActionType actionType,
        @NotNull Map<String, Object> payload
) {}
