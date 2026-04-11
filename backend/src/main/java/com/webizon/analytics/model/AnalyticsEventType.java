package com.webizon.analytics.model;

/**
 * Every behavioural event the product tracks.
 *
 * <p>Naming is intentionally verb-past tense ({@code LANDING_PAGE_VIEW},
 * {@code ROOM_ENTERED}) so the log reads like a factual history of
 * what happened — never like an imperative. New event types must be
 * added here and in no other enum; the DB column is {@code VARCHAR} so
 * old rows remain readable after a schema evolution.
 *
 * <p>Grouped by funnel stage for readability; the underlying string is
 * the {@link #name()} and is what gets stored on the row.
 */
public enum AnalyticsEventType {

    // --- pre-auth funnel ---
    /** A guest hit the landing page. profile_id is null; client_key may be present. */
    LANDING_PAGE_VIEW,
    /** Guest clicked the join CTA and was redirected to Keycloak. */
    AUTH_REDIRECT,
    /** User completed auth and returned to the event page. */
    AUTH_RETURNED,

    // --- room attendance ---
    ROOM_ENTERED,
    ROOM_LEFT,
    HEARTBEAT,
    WATCH_MILESTONE,

    // --- chat / interaction ---
    CHAT_MESSAGE_SENT,
    CHAT_REPLY_SENT,

    // --- moderation (from the user's side) ---
    WARNING_RECEIVED,
    MUTED,
    CHAT_BANNED,
    ROOM_REMOVED,
    FULL_BANNED,

    // --- CTA funnel ---
    CTA_IMPRESSION,
    CTA_CLICK,
    CTA_DOWNLOAD,
    CTA_DISMISS,

    // --- auto / replay ---
    AUTO_SLOT_SELECTED,
    HISTORICAL_CHAT_REPLAY_SEEN,

    // --- terminal ---
    SESSION_ENDED_FOR_USER
}
