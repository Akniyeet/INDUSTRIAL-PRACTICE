package com.webizon.tenancy.service;

import com.webizon.auth.CurrentUser;
import com.webizon.tenancy.AllowCrossTenant;
import com.webizon.tenancy.model.User;
import com.webizon.tenancy.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Keeps the local {@code users} table in sync with Keycloak.
 *
 * <p>Called on every successful login through {@code POST /api/v1/auth/bootstrap}.
 * The user is a <em>global</em> entity — this service does not require a
 * tenant context and is annotated {@link AllowCrossTenant} accordingly.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@AllowCrossTenant(reason = "Global user identity; bootstraps before any tenant is attached.")
public class UserService {

    private final UserRepository userRepository;

    /**
     * Idempotently create-or-update the local mirror of the authenticated JWT.
     *
     * @return the persisted {@link User} record for the caller
     */
    @Transactional
    public User bootstrapFromJwt() {
        Jwt jwt = CurrentUser.jwt();
        UUID keycloakId = CurrentUser.keycloakId();
        String email = CurrentUser.email();

        Optional<User> existing = userRepository.findByKeycloakId(keycloakId);
        if (existing.isPresent()) {
            User user = existing.get();
            boolean dirty = false;

            if (!email.equalsIgnoreCase(user.getEmail())) {
                user.setEmail(email);
                dirty = true;
            }
            if (CurrentUser.emailVerified() != user.isEmailVerified()) {
                user.setEmailVerified(CurrentUser.emailVerified());
                dirty = true;
            }
            String name = CurrentUser.fullName();
            if (name != null && !name.equals(user.getFullName())) {
                user.setFullName(name);
                dirty = true;
            }

            user.setLastLoginAt(Instant.now());
            if (dirty) log.info("Synchronized mutable profile fields for user {}", user.getId());
            return user;
        }

        User created = User.builder()
                .keycloakId(keycloakId)
                .email(email)
                .emailVerified(CurrentUser.emailVerified())
                .fullName(CurrentUser.fullName())
                .locale(stringClaimOrDefault(jwt, "locale", "ru-KZ"))
                .timezone(stringClaimOrDefault(jwt, "zoneinfo", "Asia/Almaty"))
                .lastLoginAt(Instant.now())
                .build();

        User saved = userRepository.save(created);
        log.info("Created new user id={} email={} keycloakId={}", saved.getId(), saved.getEmail(), keycloakId);
        return saved;
    }

    @Transactional(readOnly = true)
    public User requireById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("User not found: " + id));
    }

    @Transactional(readOnly = true)
    public User requireByKeycloakId(UUID keycloakId) {
        return userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new IllegalStateException(
                        "No local mirror for Keycloak subject " + keycloakId
                                + ". Call POST /api/v1/auth/bootstrap first."));
    }

    private static String stringClaimOrDefault(Jwt jwt, String claim, String fallback) {
        String value = jwt.getClaimAsString(claim);
        return value == null || value.isBlank() ? fallback : value;
    }
}
