package com.webizon.tenancy.api.dto;

import com.webizon.tenancy.model.MembershipRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Admin-facing body for {@code POST /api/v1/invites}. A single
 * invite addresses exactly one email and one role; bulk-invite is
 * a future concern and should build on top of this endpoint rather
 * than complicate it.
 *
 * @param email target email address, frozen on create
 * @param role  tenant-scoped role the recipient will hold once they accept
 * @param message optional free-form note shown on the accept landing
 *                page; capped at 1 kB to keep the email body tidy
 */
public record InviteCreateRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotNull MembershipRole role,
        @Size(max = 1024) String message
) {}
