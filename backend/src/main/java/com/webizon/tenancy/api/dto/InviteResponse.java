package com.webizon.tenancy.api.dto;

import com.webizon.tenancy.model.InviteStatus;
import com.webizon.tenancy.model.MembershipRole;
import com.webizon.tenancy.model.TenantInvite;

import java.time.Instant;
import java.util.UUID;

/**
 * Admin-facing projection of a {@link TenantInvite}. Never exposes
 * {@code token_hash} — the plaintext lives only in
 * {@link InviteCreateResponse} at create time and is never sent again.
 */
public record InviteResponse(
        UUID id,
        String email,
        MembershipRole role,
        InviteStatus status,
        UUID invitedByUserId,
        UUID acceptedByUserId,
        UUID revokedByUserId,
        Instant expiresAt,
        Instant acceptedAt,
        Instant revokedAt,
        String message,
        Instant createdAt,
        Instant updatedAt
) {
    public static InviteResponse from(TenantInvite invite) {
        return new InviteResponse(
                invite.getId(),
                invite.getEmail(),
                invite.getRole(),
                invite.getStatus(),
                invite.getInvitedByUserId(),
                invite.getAcceptedByUserId(),
                invite.getRevokedByUserId(),
                invite.getExpiresAt(),
                invite.getAcceptedAt(),
                invite.getRevokedAt(),
                invite.getMessage(),
                invite.getCreatedAt(),
                invite.getUpdatedAt());
    }
}
