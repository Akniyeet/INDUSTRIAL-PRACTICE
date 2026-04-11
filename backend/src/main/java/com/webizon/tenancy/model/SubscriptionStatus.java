package com.webizon.tenancy.model;

/**
 * Lifecycle of a tenant's subscription to a {@link Plan}.
 *
 * <p>The billing pipeline is the only writer of this column. It transitions
 * subscriptions forward based on payment provider webhooks and nightly
 * reconciliation jobs.
 */
public enum SubscriptionStatus {
    /** Free trial period. No payment method required. */
    TRIAL,
    /** Paid and in good standing. */
    ACTIVE,
    /** Payment failed; retry window open. */
    PAST_DUE,
    /** Retry window exhausted; access throttled. */
    SUSPENDED,
    /** Cancelled by the customer. */
    CANCELLED,
    /** Expired without renewal. */
    EXPIRED
}
