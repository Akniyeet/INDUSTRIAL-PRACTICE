package com.webizon.events.model;

import java.util.Set;

/**
 * Full session lifecycle covering both LIVE and AUTO runs.
 *
 * <p>LIVE state machine:
 * <pre>
 *   SCHEDULED ─────▶ LIVE ─────▶ ENDED
 *        │                        ▲
 *        └────────── CANCELLED ───┘(terminal)
 * </pre>
 *
 * <p>AUTO state machine:
 * <pre>
 *   AUTO_SCHEDULED ─▶ AUTO_LIVE ─▶ AUTO_ENDED
 *        │
 *        └────── CANCELLED (terminal)
 * </pre>
 *
 * <p>Any session whose status is in {@link #FINAL_STATUSES} is immutable —
 * creating a new run means inserting a brand new Session row, never
 * flipping a finished row back to "upcoming". This guarantee is what keeps
 * analytics and CRM attribution honest (see CLAUDE.md §42, §48).
 */
public enum SessionStatus {
    SCHEDULED,
    LIVE,
    ENDED,
    CANCELLED,
    AUTO_SCHEDULED,
    AUTO_LIVE,
    AUTO_ENDED;

    public static final Set<SessionStatus> AIRING =
            Set.of(LIVE, AUTO_LIVE);

    public static final Set<SessionStatus> FINAL_STATUSES =
            Set.of(ENDED, AUTO_ENDED, CANCELLED);

    public static final Set<SessionStatus> UPCOMING =
            Set.of(SCHEDULED, AUTO_SCHEDULED);

    public boolean isAiring() {
        return AIRING.contains(this);
    }

    public boolean isFinal() {
        return FINAL_STATUSES.contains(this);
    }

    public boolean isUpcoming() {
        return UPCOMING.contains(this);
    }
}
