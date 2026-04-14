package com.webizon.platform.dto;

import com.webizon.tenancy.model.User;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * User row with cross-tenant membership summary.
 */
public record PlatformUserResponse(
        UUID id,
        String email,
        String fullName,
        boolean emailVerified,
        boolean platformAdmin,
        Instant lastLoginAt,
        Instant createdAt,
        List<String> tenantSlugs,
        int tenantCount
) {
    public static PlatformUserResponse from(User u, List<String> slugs) {
        return new PlatformUserResponse(
                u.getId(), u.getEmail(), u.getFullName(),
                u.isEmailVerified(), u.isPlatformAdmin(),
                u.getLastLoginAt(), u.getCreatedAt(),
                slugs, slugs.size());
    }
}
