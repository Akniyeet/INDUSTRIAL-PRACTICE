package com.webizon.tenancy.api.dto;

import java.util.List;

/**
 * Response for {@code POST /api/v1/auth/bootstrap}. Returns everything the
 * frontend needs to route the user: the mirrored {@code User} record plus
 * the complete list of tenant memberships so the UI can either drop straight
 * into a single workspace or render a picker.
 */
public record BootstrapResponse(
        UserResponse user,
        List<MembershipResponse> memberships
) {}
