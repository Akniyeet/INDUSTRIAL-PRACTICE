package com.webizon.tenancy.api.dto;

import com.webizon.tenancy.model.TenantUser;

import java.time.Instant;
import java.util.UUID;

public record MembershipResponse(
        UUID id,
        UUID tenantId,
        UUID userId,
        String role,
        String status,
        Instant joinedAt
) {
    public static MembershipResponse from(TenantUser tu) {
        return new MembershipResponse(
                tu.getId(),
                tu.getTenantId(),
                tu.getUserId(),
                tu.getRole().name(),
                tu.getStatus().name(),
                tu.getJoinedAt()
        );
    }
}
