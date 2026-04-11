package com.webizon.tenancy.model;

import com.webizon.tenancy.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Pending, accepted, revoked, or expired invite for a future tenant staff
 * member. See {@code V012__tenant_invites.sql} for the full design rationale
 * — in short, invites live in their own table (rather than as "placeholder"
 * {@link TenantUser} rows) because:
 *
 * <ul>
 *   <li>the target email may not yet exist as a Webizon {@link User} — we
 *       cannot plant a {@code tenant_users} row whose {@code user_id} is NULL
 *       because the column is NOT NULL;</li>
 *   <li>invite lifecycle (PENDING → ACCEPTED / REVOKED / EXPIRED) is
 *       orthogonal to membership lifecycle (ACTIVE / SUSPENDED / REMOVED);</li>
 *   <li>admins routinely re-issue or revoke invites before any membership is
 *       ever created — keeping that history separate keeps
 *       {@code tenant_users} clean.</li>
 * </ul>
 *
 * <p><strong>Token handling.</strong> The {@link #tokenHash} column is a
 * SHA-256 of the plaintext bearer token. The plaintext is only ever returned
 * once, at create time, and never stored on the server. An attacker with a DB
 * dump cannot replay live invite links.
 *
 * <p><strong>Email freezing.</strong> The target {@link #email} is frozen at
 * create time. On accept, the accepter's JWT email claim must match
 * (case-insensitive) so a leaked URL cannot be replayed by a different user.
 * The check lives in {@code InviteService#accept}; it cannot live in the DB
 * because Postgres has no visibility into Keycloak.
 */
@Entity
@Table(name = "tenant_invites")
@Getter
@Setter
public class TenantInvite extends TenantAwareEntity {

    /**
     * SHA-256 of the plaintext bearer token, hex-encoded (64 chars).
     * Unique across ALL tenants — the accept endpoint has no tenant context
     * at the moment the recipient clicks the link.
     */
    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    /**
     * Target email, frozen at invite creation. On accept the accepter's JWT
     * email must match (case-insensitive). Column is {@code CITEXT}.
     */
    @Column(name = "email", nullable = false, columnDefinition = "CITEXT", updatable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32, updatable = false)
    private MembershipRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private InviteStatus status = InviteStatus.PENDING;

    @Column(name = "invited_by_user_id", nullable = false, updatable = false, columnDefinition = "UUID")
    private UUID invitedByUserId;

    /** Filled only after ACCEPTED. */
    @Column(name = "accepted_by_user_id", columnDefinition = "UUID")
    private UUID acceptedByUserId;

    /** Links to the {@link TenantUser} row created at accept time. */
    @Column(name = "accepted_membership_id", columnDefinition = "UUID")
    private UUID acceptedMembershipId;

    @Column(name = "revoked_by_user_id", columnDefinition = "UUID")
    private UUID revokedByUserId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** Free-form note from the inviting admin, shown to the recipient. */
    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    // ------------------------------------------------------------------
    // Lifecycle helpers — enforce the ordering the DB CHECK constraint
    // also enforces, so bugs surface in Java before they ever reach the
    // SQL layer.
    // ------------------------------------------------------------------

    public boolean isPending() {
        return status == InviteStatus.PENDING;
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    /**
     * True when the invite can still be clicked — pending AND not past
     * {@code expires_at}. Used by the accept endpoint and by the admin
     * revoke path (revoking an already-terminal invite is a no-op).
     */
    public boolean isActionable(Instant now) {
        return isPending() && !isExpired(now);
    }

    public void markAccepted(UUID userId, UUID membershipId, Instant at) {
        this.status = InviteStatus.ACCEPTED;
        this.acceptedByUserId = userId;
        this.acceptedMembershipId = membershipId;
        this.acceptedAt = at;
    }

    public void markRevoked(UUID userId, Instant at) {
        this.status = InviteStatus.REVOKED;
        this.revokedByUserId = userId;
        this.revokedAt = at;
    }

    public void markExpired() {
        this.status = InviteStatus.EXPIRED;
    }
}
