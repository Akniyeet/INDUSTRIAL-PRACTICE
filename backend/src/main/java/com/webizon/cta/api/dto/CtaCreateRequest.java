package com.webizon.cta.api.dto;

import com.webizon.cta.model.CtaPlacement;
import com.webizon.cta.model.CtaType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CtaCreateRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 5_000) String description,
        @NotNull CtaType type,
        @NotBlank @Size(max = 80) String buttonText,
        @Size(max = 500) String actionUrl,
        @Size(max = 500) String fileUrl,
        @NotNull CtaPlacement placement,
        @Min(0) @Max(1_000) int priority,
        boolean allowStack
) {}
