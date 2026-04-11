package com.webizon.tenancy.model;

/**
 * Role a user holds within a tenant.
 *
 * <p>These roles are <em>tenant-scoped</em> — a single human can be a
 * {@code TENANT_OWNER} of one tenant and a {@code TENANT_PRESENTER} of another.
 * They are distinct from platform-level roles like {@code PLATFORM_ADMIN}
 * which live on the user record itself.
 */
public enum MembershipRole {
    /** Full control, including billing. Exactly one per tenant at creation time. */
    TENANT_OWNER,
    /** Manage events, sessions, members, settings. Cannot delete the tenant. */
    TENANT_ADMIN,
    /** Live room moderation: chat, warnings, mute, ban, CTA toggle. */
    TENANT_MODERATOR,
    /** Runs the live broadcast: start/stop sessions, pin messages, trigger CTA. */
    TENANT_PRESENTER,
    /** Read-only access to events, analytics, reports. */
    TENANT_ANALYST
}
