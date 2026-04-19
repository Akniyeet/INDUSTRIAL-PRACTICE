package com.webizon.cta.api.dto;

import com.webizon.cta.model.CtaPlacement;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Partial update. Any field left {@code null} keeps its existing value —
 * only non-null fields are applied. The {@code type} is intentionally
 * NOT updatable; change it by deleting and recreating the CTA so that
 * historical timeline rows keep referencing a row of a stable shape.
 */
public record CtaUpdateRequest(
        @Size(max = 200)  String title,
        @Size(max = 5_000) String description,
        @Size(max = 80)   String buttonText,
        @Size(max = 500)  String actionUrl,
        @Size(max = 500)  String fileUrl,
        CtaPlacement placement,
        @Min(0) @Max(1_000) Integer priority,
        Boolean allowStack
) {}
