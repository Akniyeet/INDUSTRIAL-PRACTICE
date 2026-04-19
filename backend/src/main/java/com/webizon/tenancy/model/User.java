package com.webizon.tenancy.model;

import com.webizon.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Mirror of a Keycloak identity inside Webizon.
 *
 * <p><strong>Global entity</strong> — a single human can belong to multiple tenants.
 * The Keycloak subject ({@code sub} claim) is the immutable link.
 *
 * <p>This table does NOT store credentials. Keycloak is the source of truth for
 * authentication; we mirror only the fields we need for joins, analytics, and
 * display.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Column(name = "keycloak_id", nullable = false, unique = true, columnDefinition = "UUID")
    private UUID keycloakId;

    @Column(name = "email", nullable = false, unique = true, columnDefinition = "CITEXT")
    private String email;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    @Column(name = "full_name", length = 255)
    private String fullName;

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    @Column(name = "locale", nullable = false, length = 8)
    @Builder.Default
    private String locale = "ru-KZ";

    @Column(name = "timezone", nullable = false, length = 64)
    @Builder.Default
    private String timezone = "Asia/Almaty";

    @Column(name = "is_platform_admin", nullable = false)
    @Builder.Default
    private boolean platformAdmin = false;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;
}
