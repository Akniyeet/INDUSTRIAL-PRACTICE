package com.webizon.analytics.model;

/**
 * Computed interest signals consumed by CRM pipelines.
 *
 * <p>Unlike {@link AnalyticsEventType} which records what literally
 * happened, a lead signal is the product's interpretation of a
 * pattern of behaviour — it may be derived from a single event
 * (CTA click) or from an aggregate (watched more than 30 minutes).
 * Signals feed lead scoring, segmentation, and re-engagement flows.
 *
 * <p>Each signal carries a numeric {@code score} contribution on the
 * {@code lead_signals} row; the scoring model is documented on
 * {@code LeadSignalEvaluator}. Negative scores (BAD_BEHAVIOR) are
 * disqualifiers.
 */
public enum LeadSignalType {
    ATTENDED,
    WATCHED_LONG,
    CTA_COURSE_CLICK,
    CTA_FILE_DOWNLOAD,
    CHAT_ENGAGED,
    RETURNED_FOR_AUTO,
    BAD_BEHAVIOR
}
