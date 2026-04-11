-- =====================================================================
-- V002 :: Events & Sessions
-- ---------------------------------------------------------------------
-- Domain:
--   events   -- the reusable content unit (landing page, title, speaker)
--   sessions -- a specific scheduled run of an event (LIVE or AUTO)
--
-- Critical product rules (see CLAUDE.md §48-50):
--   * An event has many sessions; a session always belongs to exactly
--     one event. Auto sessions point back to the LIVE session whose
--     recording they replay via source_live_session_id.
--   * Finalized sessions (finalized_at IS NOT NULL) are IMMUTABLE.
--     "Re-showing" a finished session means inserting a new AUTO row.
--   * Slugs are per-tenant, not global, so two different schools can
--     both have /event/free-lesson without collision.
-- =====================================================================

-- ---------------------------------------------------------------------
-- events
-- ---------------------------------------------------------------------
CREATE TABLE events (
    id                  UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID           NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    slug                VARCHAR(64)    NOT NULL,
    title               VARCHAR(200)   NOT NULL,
    description         TEXT,
    speaker_name        VARCHAR(120),
    speaker_bio         TEXT,
    cover_image_url     VARCHAR(500),
    timezone            VARCHAR(64)    NOT NULL DEFAULT 'Asia/Almaty',
    language            VARCHAR(16)    NOT NULL DEFAULT 'ru',
    status              VARCHAR(16)    NOT NULL DEFAULT 'DRAFT',
    created_by_user_id  UUID           NOT NULL REFERENCES users(id),
    version             BIGINT         NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT events_slug_uniq   UNIQUE (tenant_id, slug),
    CONSTRAINT events_slug_chk    CHECK (slug ~ '^[a-z0-9][a-z0-9-]{1,62}[a-z0-9]$'),
    CONSTRAINT events_status_chk  CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED'))
);

CREATE INDEX events_tenant_status_idx ON events (tenant_id, status);

COMMENT ON TABLE  events             IS 'Reusable content unit. A single Event can be broadcast many times as Sessions.';
COMMENT ON COLUMN events.slug        IS 'Public URL identifier — unique WITHIN a tenant, not globally.';
COMMENT ON COLUMN events.status      IS 'DRAFT (hidden) -> PUBLISHED (landing visible) -> ARCHIVED (read-only).';
COMMENT ON COLUMN events.timezone    IS 'IANA timezone used for formatting schedule on the landing page.';

-- ---------------------------------------------------------------------
-- sessions
-- ---------------------------------------------------------------------
CREATE TABLE sessions (
    id                       UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                UUID           NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    event_id                 UUID           NOT NULL REFERENCES events(id)  ON DELETE CASCADE,
    type                     VARCHAR(8)     NOT NULL,
    status                   VARCHAR(20)    NOT NULL,
    start_time               TIMESTAMPTZ    NOT NULL,
    planned_duration_seconds INTEGER        NOT NULL,
    youtube_url              VARCHAR(500),
    youtube_video_id         VARCHAR(32),
    youtube_embed_url        VARCHAR(500),
    source_live_session_id   UUID           REFERENCES sessions(id),
    actual_started_at        TIMESTAMPTZ,
    actual_ended_at          TIMESTAMPTZ,
    finalized_at             TIMESTAMPTZ,
    created_by_user_id       UUID           NOT NULL REFERENCES users(id),
    version                  BIGINT         NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT sessions_type_chk     CHECK (type IN ('LIVE','AUTO')),
    CONSTRAINT sessions_status_chk   CHECK (status IN (
        'SCHEDULED','LIVE','ENDED','CANCELLED',
        'AUTO_SCHEDULED','AUTO_LIVE','AUTO_ENDED'
    )),
    CONSTRAINT sessions_duration_chk CHECK (planned_duration_seconds > 0),
    CONSTRAINT sessions_live_status_chk CHECK (
        (type = 'LIVE' AND status IN ('SCHEDULED','LIVE','ENDED','CANCELLED')) OR
        (type = 'AUTO' AND status IN ('AUTO_SCHEDULED','AUTO_LIVE','AUTO_ENDED','CANCELLED'))
    ),
    -- AUTO sessions must be anchored to a LIVE recording.
    CONSTRAINT sessions_auto_source_chk CHECK (
        type = 'LIVE' OR source_live_session_id IS NOT NULL
    )
);

CREATE INDEX sessions_tenant_idx              ON sessions (tenant_id);
CREATE INDEX sessions_event_idx               ON sessions (event_id);
CREATE INDEX sessions_tenant_status_start_idx ON sessions (tenant_id, status, start_time);
CREATE INDEX sessions_source_live_idx         ON sessions (source_live_session_id)
    WHERE source_live_session_id IS NOT NULL;

-- At most one currently-airing session per event (LIVE or AUTO_LIVE).
-- Prevents double-starting a broadcast or having two replays overlap.
CREATE UNIQUE INDEX sessions_one_airing_per_event
    ON sessions (event_id)
    WHERE status IN ('LIVE','AUTO_LIVE');

COMMENT ON TABLE  sessions                        IS 'A single scheduled occurrence of an Event. Never reused — finished sessions are immutable.';
COMMENT ON COLUMN sessions.type                   IS 'LIVE = original broadcast with captured timeline. AUTO = scheduled replay of a LIVE recording.';
COMMENT ON COLUMN sessions.source_live_session_id IS 'For AUTO sessions: the LIVE session whose recording, chat, and timeline this replays.';
COMMENT ON COLUMN sessions.finalized_at           IS 'Non-null once the session has ended. Finalized rows are immutable — use a new row for re-broadcasts.';
COMMENT ON COLUMN sessions.youtube_video_id       IS 'Canonical video identifier extracted from youtube_url (11 chars).';

-- ---------------------------------------------------------------------
-- Row Level Security
-- ---------------------------------------------------------------------
ALTER TABLE events   ENABLE ROW LEVEL SECURITY;
ALTER TABLE events   FORCE  ROW LEVEL SECURITY;
ALTER TABLE sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE sessions FORCE  ROW LEVEL SECURITY;

CREATE POLICY events_tenant_isolation ON events
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

CREATE POLICY sessions_tenant_isolation ON sessions
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- ---------------------------------------------------------------------
-- updated_at triggers
-- ---------------------------------------------------------------------
CREATE TRIGGER events_updated_at   BEFORE UPDATE ON events   FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER sessions_updated_at BEFORE UPDATE ON sessions FOR EACH ROW EXECUTE FUNCTION set_updated_at();
