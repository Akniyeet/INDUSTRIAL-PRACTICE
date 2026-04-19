package com.webizon.analytics.kafka;

/**
 * Canonical topic names for the analytics pipeline.
 *
 * <p>All topics are versioned with a trailing {@code .v1} so a future
 * schema change can ship as {@code .v2} and run alongside the old
 * topic during the migration window. Consumers should pin on the
 * exact versioned name, never the unversioned prefix.
 */
public final class AnalyticsTopics {

    /**
     * Every recorded {@code AnalyticsEvent} is mirrored here for
     * downstream consumers (BI warehouse, CRM exporters, realtime
     * dashboards). Keyed by {@code sessionId} so same-session events
     * land on the same partition for ordered replay.
     */
    public static final String ANALYTICS_EVENTS = "webizon.analytics.events.v1";

    /**
     * Computed lead signals destined for CRM pipelines. Keyed by
     * {@code profileId} so all signals for one user share a
     * partition and arrive in order at a downstream aggregator.
     */
    public static final String LEAD_SIGNALS = "webizon.analytics.lead-signals.v1";

    private AnalyticsTopics() {
        throw new UnsupportedOperationException("constants holder");
    }
}
