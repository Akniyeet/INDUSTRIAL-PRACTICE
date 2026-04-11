package com.webizon.tenancy.api.dto;

import com.webizon.tenancy.model.MembershipRole;

import java.util.UUID;

/**
 * Returned by {@code POST /api/v1/invites/accept/{token}} after a
 * recipient successfully claims an invite.
 *
 * <p>Carries just enough information for the frontend to drop the
 * newly-joined user straight into their new workspace without a
 * second round trip — tenant id + slug + display name + membership
 * role. The JWT still needs to be re-minted with a fresh
 * {@code tenant_id} claim, which the frontend does via its usual
 * Keycloak refresh flow immediately after seeing this response.
 */
public record InviteAcceptResponse(
        UUID tenantId,
        String tenantSlug,
        String tenantDisplayName,
        MembershipRole role,
        UUID membershipId
) {}
