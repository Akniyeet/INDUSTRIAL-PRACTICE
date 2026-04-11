package com.webizon.tenancy.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload for {@code POST /api/v1/tenants}. Validated with Jakarta Bean
 * Validation before the controller method is invoked.
 */
public record TenantCreateRequest(
        @NotBlank
        @Size(min = 3, max = 64)
        @Pattern(regexp = "^[a-z0-9][a-z0-9-]{1,62}[a-z0-9]$",
                 message = "slug must be lowercase alphanumerics and dashes, 3-64 chars")
        String slug,

        @NotBlank
        @Size(min = 2, max = 255)
        String displayName
) {}
