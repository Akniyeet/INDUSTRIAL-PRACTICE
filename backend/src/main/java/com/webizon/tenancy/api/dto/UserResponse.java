package com.webizon.tenancy.api.dto;

import com.webizon.tenancy.model.User;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        UUID keycloakId,
        String email,
        boolean emailVerified,
        String fullName,
        String avatarUrl,
        String locale,
        String timezone,
        boolean platformAdmin,
        Instant lastLoginAt,
        Instant createdAt
) {
    public static UserResponse from(User u) {
        return new UserResponse(
                u.getId(),
                u.getKeycloakId(),
                u.getEmail(),
                u.isEmailVerified(),
                u.getFullName(),
                u.getAvatarUrl(),
                u.getLocale(),
                u.getTimezone(),
                u.isPlatformAdmin(),
                u.getLastLoginAt(),
                u.getCreatedAt()
        );
    }
}
