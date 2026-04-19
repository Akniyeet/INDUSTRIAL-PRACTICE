package com.webizon.tenancy.api.dto;

import com.webizon.tenancy.model.Tenant;

import java.time.Instant;
import java.util.UUID;

public record TenantResponse(
        UUID id,
        String slug,
        String displayName,
        String status,
        String countryCode,
        String defaultCurrency,
        String defaultLocale,
        String defaultTimezone,
        Instant trialEndsAt,
        Instant createdAt
) {
    public static TenantResponse from(Tenant t) {
        return new TenantResponse(
                t.getId(),
                t.getSlug(),
                t.getDisplayName(),
                t.getStatus().name(),
                t.getCountryCode(),
                t.getDefaultCurrency(),
                t.getDefaultLocale(),
                t.getDefaultTimezone(),
                t.getTrialEndsAt(),
                t.getCreatedAt()
        );
    }
}
