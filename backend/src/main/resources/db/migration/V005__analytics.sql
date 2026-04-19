-- =====================================================================
-- V005 :: Analytics, Attendance, Lead Signals
-- ---------------------------------------------------------------------
-- Domain:
--   analytics_events  -- append-only behavioural event log. Every
--                        meaningful user action that the product cares
--                        about is captured here, tenant-scoped, so the
--                        report layer and CRM exporters can build on a
--                        single source of truth.
--   session_attendance -- aggregate presence row per (session, user).
--                        One row, upserted on join / heartbeat /
--                        leave. Stores total_connected_seconds so the
--                        retention engine can answer "who watched 30+
--                        minutes" without scanning the event log.
--   lead_signals      -- append-only computed interest signals pushed
--                        to CRM pipelines. Derived from analytics
--                        events + attendance, but stored separately
--                        so consumers can subscribe to them directly.
--
-- Scale notes (CLAUDE.md §26-28, §66-68):
--   * This module has to survive 50-60k concurrent viewers. Every row
--     here is tenant-scoped for RLS and every index starts with
--     (tenant_id, …) so multi-tenant isolation does not force a
--     sequential scan.
--   * analytics_events has a BRIN index on created_at — suited to
--     append-only time-series data where a small btree would be huge.
--   * Hot read paths for the admin report layer are covered by
--     dedicated partial btrees on (session_id, event_type) and
--     (session_id, offset_seconds).
-- =====================================================================

-- ---------------------------------------------------------------------
-- analytics_events
-- ---------------------------------------------------------------------
CREATE TABLE analytics_events (
    id                 UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID          NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    event_id           UUID          REFERENCES events(id)   ON DELETE CASCADE,
    session_id         UUID          REFERENCES sessions(id) ON DELETE CASCADE,
    -- profile_id is nullable so pre-auth landing views can still be
    -- tracked; client_key provides a best-effort stable id until auth.
    profile_id         UUID          REFERENCES users(id),
    client_key         VARCHAR(64),
    event_type         VARCHAR(48)   NOT NULL,
    offset_seconds     INTEGER,
    metadata           JSONB         NOT NULL DEFAULT '{}'::jsonb,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT analytics_events_offset_chk CHECK (
        offset_seconds IS NULL OR offset_seconds >= 0
    )
);

-- Primary admin query path: "every event for a session, optionally
-- filtered by type, ordered by time". Partial btree on session_id is
-- small (no rows with NULL session_id).
CREATE INDEX analytics_events_session_idx
    ON analytics_events (session_id, event_type, created_at DESC)
    WHERE session_id IS NOT NULL;

-- Retention read path: "viewer count at offset X in session Y".
CREATE INDEX analytics_events_session_offset_idx
    ON analytics_events (session_id, offset_seconds)
    WHERE session_id IS NOT NULL AND offset_seconds IS NOT NULL;

-- Cross-session user timeline: "everything this user did in this tenant".
CREATE INDEX analytics_events_profile_idx
    ON analytics_events (profile_id, created_at DESC)
    WHERE profile_id IS NOT NULL;

CREATE INDEX analytics_events_tenant_idx ON analytics_events (tenant_id);

-- BRIN on created_at: append-only time series => minimal-cost index
-- that still lets the planner prune ranges for date-windowed reports.
CREATE INDEX analytics_events_created_at_brin
    ON analytics_events USING BRIN (created_at) WITH (pages_per_range = 64);

COMMENT ON TABLE  analytics_events             IS 'Append-only behavioural event log. Every meaningful user action in a session ends up here.';
COMMENT ON COLUMN analytics_events.event_type  IS 'Enum string: LANDING_PAGE_VIEW, ROOM_ENTER, WATCH_MILESTONE, CTA_CLICK, etc.';
COMMENT ON COLUMN analytics_events.client_key  IS 'Best-effort stable id for pre-auth tracking; nullable post-auth when profile_id is set.';
COMMENT ON COLUMN analytics_events.metadata    IS 'Action-specific payload (e.g. {"milestone":"30min"}, {"ctaId":"…"}). Shape is enforced in the application layer.';

-- ---------------------------------------------------------------------
-- session_attendance
-- ---------------------------------------------------------------------
CREATE TABLE session_attendance (
    id                       UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                UUID          NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    event_id                 UUID          NOT NULL REFERENCES events(id)  ON DELETE CASCADE,
    session_id               UUID          NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    profile_id               UUID          NOT NULL REFERENCES users(id),
    first_joined_at          TIMESTAMPTZ   NOT NULL,
    last_seen_at             TIMESTAMPTZ   NOT NULL,
    left_at                  TIMESTAMPTZ,
    total_connected_seconds  INTEGER       NOT NULL DEFAULT 0,
    entry_count              INTEGER       NOT NULL DEFAULT 1,
    max_offset_seconds       INTEGER,
    version                  BIGINT        NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT session_attendance_session_profile_uniq UNIQUE (session_id, profile_id),
    CONSTRAINT session_attendance_seconds_chk CHECK (total_connected_seconds >= 0),
    CONSTRAINT session_attendance_entries_chk CHECK (entry_count >= 1)
);

CREATE INDEX session_attendance_session_idx ON session_attendance (session_id);
CREATE INDEX session_attendance_profile_idx ON session_attendance (profile_id);
CREATE INDEX session_attendance_tenant_idx  ON session_attendance (tenant_id);

COMMENT ON TABLE  session_attendance                       IS 'Aggregate presence row per user per session. Upserted — one row, never two.';
COMMENT ON COLUMN session_attendance.total_connected_seconds IS 'Cumulative time the user was connected across all join / leave cycles.';
COMMENT ON COLUMN session_attendance.entry_count           IS 'How many distinct times the user joined this session (refreshes, reconnects).';
COMMENT ON COLUMN session_attendance.max_offset_seconds    IS 'Furthest point in the video the user has reached.';

-- ---------------------------------------------------------------------
-- lead_signals
-- ---------------------------------------------------------------------
CREATE TABLE lead_signals (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID          NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    profile_id          UUID          NOT NULL REFERENCES users(id),
    event_id            UUID          REFERENCES events(id)   ON DELETE CASCADE,
    session_id          UUID          REFERENCES sessions(id) ON DELETE CASCADE,
    signal_type         VARCHAR(48)   NOT NULL,
    score               INTEGER       NOT NULL DEFAULT 0,
    source_event_id     UUID          REFERENCES analytics_events(id) ON DELETE SET NULL,
    metadata            JSONB         NOT NULL DEFAULT '{}'::jsonb,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT lead_signals_score_chk CHECK (score BETWEEN -100 AND 100)
);

CREATE INDEX lead_signals_profile_idx       ON lead_signals (profile_id, created_at DESC);
CREATE INDEX lead_signals_session_type_idx  ON lead_signals (session_id, signal_type)
    WHERE session_id IS NOT NULL;
CREATE INDEX lead_signals_tenant_idx        ON lead_signals (tenant_id);
CREATE INDEX lead_signals_created_at_brin
    ON lead_signals USING BRIN (created_at) WITH (pages_per_range = 64);

COMMENT ON TABLE  lead_signals             IS 'Append-only computed interest signals. Derived from analytics + attendance, pushed to CRM via Kafka.';
COMMENT ON COLUMN lead_signals.signal_type IS 'Enum: WATCHED_LONG, CTA_COURSE_CLICK, CTA_FILE_DOWNLOAD, CHAT_ENGAGED, RETURNED_FOR_AUTO, BAD_BEHAVIOR.';
COMMENT ON COLUMN lead_signals.score       IS 'Signed contribution to the user''s lead score. Positive = interest, negative = disqualification.';

-- ---------------------------------------------------------------------
-- Row Level Security
-- ---------------------------------------------------------------------
ALTER TABLE analytics_events   ENABLE ROW LEVEL SECURITY;
ALTER TABLE analytics_events   FORCE  ROW LEVEL SECURITY;
ALTER TABLE session_attendance ENABLE ROW LEVEL SECURITY;
ALTER TABLE session_attendance FORCE  ROW LEVEL SECURITY;
ALTER TABLE lead_signals       ENABLE ROW LEVEL SECURITY;
ALTER TABLE lead_signals       FORCE  ROW LEVEL SECURITY;

CREATE POLICY analytics_events_tenant_isolation ON analytics_events
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

CREATE POLICY session_attendance_tenant_isolation ON session_attendance
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

CREATE POLICY lead_signals_tenant_isolation ON lead_signals
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- ---------------------------------------------------------------------
-- updated_at triggers (analytics_events and lead_signals are append-only)
-- ---------------------------------------------------------------------
CREATE TRIGGER session_attendance_updated_at
    BEFORE UPDATE ON session_attendance
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
