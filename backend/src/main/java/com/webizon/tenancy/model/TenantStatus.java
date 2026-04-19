package com.webizon.tenancy.model;

/**
 * Lifecycle of a paying tenant.
 *
 * <p>Transitions are strictly forward except for {@code PAST_DUE → ACTIVE} on
 * successful retry. The billing pipeline is the only writer of this column.
 *
 * <pre>
 *   TRIAL ──► ACTIVE ──► PAST_DUE ──► SUSPENDED ──► CANCELLED ──► ARCHIVED
 *                   ▲         │
 *                   └─────────┘
 * </pre>
 */
public enum TenantStatus {
    /** Free 14-day evaluation period. */
    TRIAL,
    /** Paid, in good standing. */
    ACTIVE,
    /** Payment failed; retry window. Read-only features may degrade. */
    PAST_DUE,
    /** Grace period over; features locked but data retained. */
    SUSPENDED,
    /** Customer cancelled; data retained for refund/export window. */
    CANCELLED,
    /** Retention window over; data is cold-archived. */
    ARCHIVED
}
