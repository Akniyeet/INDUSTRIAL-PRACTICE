package com.webizon.timeline.api.dto;

import jakarta.validation.constraints.NotNull;

public record TimelineActionActiveRequest(@NotNull Boolean active) {}
