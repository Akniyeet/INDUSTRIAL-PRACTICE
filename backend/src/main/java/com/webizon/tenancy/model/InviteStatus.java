package com.webizon.tenancy.model;

/**
 * Lifecycle of a {@link TenantInvite}.
 *
 * <p>An invite starts in {@link #PENDING} and moves exactly once into one of
 * the three terminal states — {@link #ACCEPTED}, {@link #REVOKED},
 * {@link #EXPIRED}. The transitions are enforced both by
 * {@code InviteService} (which is the only writer) and by the
 * {@code tenant_invites_lifecycle_chk} CHECK constraint on the table, which
 * guarantees that the timestamp / actor columns are consistent with the
 * status value.
 *
 * <p>{@link #PENDING} and {@link #EXPIRED} look alike from a lifecycle point
 * of view (no {@code accepted_at}, no {@code revoked_at}), but they differ in
 * intent: expired invites are ones the accept endpoint has already rejected
 * with a 410, whereas pending invites are still clickable. The sweeper that
 * flips PENDING → EXPIRED runs once the {@code expires_at} timestamp has
 * passed; until then a pending invite is indistinguishable from an expired
 * one only in wall-clock terms, and the accept endpoint double-checks
 * {@code expires_at} on every click anyway.
 */
public enum InviteStatus {
    /** Freshly created; awaiting the recipient's click. Only non-terminal state. */
    PENDING,
    /** Recipient has claimed the token; a {@link TenantUser} row now exists in ACTIVE state. */
    ACCEPTED,
    /** Admin cancelled the invite before it was accepted. */
    REVOKED,
    /** {@code expires_at} has passed without an accept. */
    EXPIRED;

    public boolean isTerminal() {
        return this != PENDING;
    }
}
