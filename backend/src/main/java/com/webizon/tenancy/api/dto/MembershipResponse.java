package com.webizon.tenancy.api.dto;

import com.webizon.tenancy.model.TenantUser;

import java.time.Instant;
import java.util.UUID;

public record MembershipResponse(
        UUID id,
        UUID tenantId,
        String tenantSlug,
        String tenantDisplayName,
        UUID userId,
        String role,
        String status,
        Instant joinedAt
) {
    public static MembershipResponse from(TenantUser tu, String tenantSlug, String tenantDisplayName) {
        return new MembershipResponse(
                tu.getId(),
                tu.getTenantId(),
                tenantSlug,
                tenantDisplayName,
                tu.getUserId(),
                tu.getRole().name(),
                tu.getStatus().name(),
                tu.getJoinedAt()
        );
    }
}
