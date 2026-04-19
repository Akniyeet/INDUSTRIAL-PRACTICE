package com.webizon.cta.api.dto;

import jakarta.validation.constraints.NotNull;

public record CtaActiveRequest(@NotNull Boolean active) {}
