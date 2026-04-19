package com.webizon.tenancy.model;

/** Whether a tenant membership is currently effective. */
public enum MembershipStatus {
    /** Active member; can sign in and operate within the tenant. */
    ACTIVE,
    /** Invite sent; pending email acceptance. */
    INVITED,
    /** Temporarily disabled by an admin; retainable. */
    SUSPENDED,
    /** Soft-deleted; kept for audit but excluded from all queries. */
    REMOVED
}
