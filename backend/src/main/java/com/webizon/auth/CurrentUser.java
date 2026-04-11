package com.webizon.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;
import java.util.UUID;

/**
 * Static helpers for reading identity claims off the currently-authenticated
 * JWT. Intentionally not a bean — the authentication is request-scoped and
 * lives in the {@link SecurityContextHolder}.
 *
 * <p>Every getter throws {@link IllegalStateException} if there is no
 * authenticated JWT; that's a routing bug (a protected endpoint was hit
 * without auth) and should be impossible after {@code SecurityConfig}
 * requires authentication on everything except the public whitelist.
 */
public final class CurrentUser {

    private CurrentUser() {
        throw new UnsupportedOperationException("utility class");
    }

    public static Jwt jwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken();
        }
        throw new IllegalStateException("No authenticated JWT in SecurityContext");
    }

    public static Optional<Jwt> jwtOptional() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return Optional.of(jwtAuth.getToken());
        }
        return Optional.empty();
    }

    /** Keycloak subject ({@code sub} claim). Stable for the lifetime of the user. */
    public static UUID keycloakId() {
        return UUID.fromString(jwt().getSubject());
    }

    public static String email() {
        String email = jwt().getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new IllegalStateException("JWT has no email claim");
        }
        return email;
    }

    public static boolean emailVerified() {
        Boolean verified = jwt().getClaim("email_verified");
        return verified != null && verified;
    }

    public static String fullName() {
        String name = jwt().getClaimAsString("name");
        if (name == null || name.isBlank()) {
            String given = jwt().getClaimAsString("given_name");
            String family = jwt().getClaimAsString("family_name");
            if (given != null || family != null) {
                return String.format("%s %s", given == null ? "" : given, family == null ? "" : family).trim();
            }
            return jwt().getClaimAsString("preferred_username");
        }
        return name;
    }

    public static Optional<UUID> tenantId() {
        String raw = jwt().getClaimAsString("tenant_id");
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
