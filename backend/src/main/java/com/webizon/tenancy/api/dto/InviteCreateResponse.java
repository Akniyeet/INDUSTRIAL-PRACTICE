package com.webizon.tenancy.api.dto;

/**
 * Returned by {@code POST /api/v1/invites} — extends
 * {@link InviteResponse} with the {@code acceptUrl} (the one-time link
 * the admin can copy-paste into a private channel if they prefer not
 * to rely on the automatic invite email).
 *
 * <p>This is the <strong>only</strong> place the plaintext bearer
 * token ever leaves the server. It is neither logged nor stored, and
 * subsequent reads of the same invite (listing, detail) return a
 * regular {@link InviteResponse} with no token at all.
 *
 * @param invite    the usual invite projection
 * @param acceptUrl full deep link to the frontend accept page,
 *                  including the plaintext token as the path tail
 */
public record InviteCreateResponse(
        InviteResponse invite,
        String acceptUrl
) {}
