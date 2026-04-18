package com.webizon.platform.dto;

import com.webizon.tenancy.model.Tenant;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Tenant row with cross-tenant aggregate stats for the platform dashboard.
 */
public record PlatformTenantResponse(
        UUID id,
        String slug,
        String displayName,
        String status,
        Instant trialEndsAt,
        Instant createdAt,

        // Owner info (from tenant_users + users join)
        String ownerEmail,
        String ownerFullName,

        // Stats
        long memberCount,
        long eventCount,
        long sessionCount,
        long currentlyLive,

        // Billing
        BigDecimal totalPaidKzt,
        BigDecimal totalOutstandingKzt,
        Instant lastPaymentAt
) {
    public static PlatformTenantResponse minimal(Tenant t) {
        return new PlatformTenantResponse(
                t.getId(), t.getSlug(), t.getDisplayName(),
                t.getStatus().name(), t.getTrialEndsAt(), t.getCreatedAt(),
                null, null, 0L, 0L, 0L, 0L,
                BigDecimal.ZERO, BigDecimal.ZERO, null);
    }
}
