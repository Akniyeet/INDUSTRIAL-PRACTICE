package com.webizon.tenancy.model;

import com.webizon.tenancy.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Membership of a {@link User} in a {@link Tenant}.
 *
 * <p><strong>Tenant-scoped</strong> — protected by Hibernate {@code @TenantId}
 * + JPA listener + PostgreSQL RLS.
 *
 * <p>{@code userId} references {@code users.id} but is stored as a plain UUID
 * (not a JPA relationship) to keep queries lean and avoid accidental lazy
 * loads across the tenant boundary.
 */
@Entity
@Table(
        name = "tenant_users",
        uniqueConstraints = @UniqueConstraint(name = "tenant_users_uniq", columnNames = {"tenant_id", "user_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantUser extends TenantAwareEntity {

    @Column(name = "user_id", nullable = false, columnDefinition = "UUID")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private MembershipRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private MembershipStatus status = MembershipStatus.ACTIVE;

    @Column(name = "invited_by_user_id", columnDefinition = "UUID")
    private UUID invitedByUserId;

    @Column(name = "joined_at", nullable = false)
    @Builder.Default
    private Instant joinedAt = Instant.now();
}
