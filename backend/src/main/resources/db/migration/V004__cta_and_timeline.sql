-- =====================================================================
-- V004 :: CTAs & Timeline Engine
-- ---------------------------------------------------------------------
-- Domain:
--   event_ctas              -- reusable CTA definitions per event. File,
--                              link, course, and form CTAs are ONE table
--                              (CLAUDE.md rule: "File/Button/Banner = ONE
--                              unified CTA system").
--   event_timeline_actions  -- offset-based action log. Captured during
--                              LIVE, replayed during AUTO. The payload is
--                              action-specific JSONB but queries are
--                              indexed by (session_id, offset_seconds) so
--                              replay stays O(log n) even for thousands
--                              of rows.
--
-- Critical product rules (CLAUDE.md §50-51, §57):
--   * Timeline is offset-based, not wall-clock. AUTO replay seeks by
--     video offset so a viewer who joins a slot at 09:03:17 still sees
--     the 00:30 CTA at their 30-second mark.
--   * CTAs have placement + priority. The stacking rule is enforced in
--     the application layer (CtaResolver) at read time — the DB just
--     stores the raw config.
--   * Timeline action rows are mutable (admins can edit offset or
--     deactivate them), but each row is uniquely identified by its id;
--     analytics references a frozen snapshot via (source_session_id,
--     offset_seconds, action_type).
-- =====================================================================

-- ---------------------------------------------------------------------
-- event_ctas
-- ---------------------------------------------------------------------
CREATE TABLE event_ctas (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID          NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    event_id            UUID          NOT NULL REFERENCES events(id)  ON DELETE CASCADE,
    title               VARCHAR(200)  NOT NULL,
    description         TEXT,
    type                VARCHAR(16)   NOT NULL,
    button_text         VARCHAR(80)   NOT NULL,
    action_url          VARCHAR(500),
    file_url            VARCHAR(500),
    placement           VARCHAR(16)   NOT NULL,
    priority            INTEGER       NOT NULL DEFAULT 100,
    allow_stack         BOOLEAN       NOT NULL DEFAULT FALSE,
    is_active           BOOLEAN       NOT NULL DEFAULT TRUE,
    created_by_user_id  UUID          NOT NULL REFERENCES users(id),
    version             BIGINT        NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT event_ctas_type_chk CHECK (
        type IN ('FILE','LINK','COURSE','FORM')
    ),
    CONSTRAINT event_ctas_placement_chk CHECK (
        placement IN ('INLINE','SIDEBAR','POPUP','BELOW_VIDEO')
    ),
    CONSTRAINT event_ctas_priority_chk CHECK (priority BETWEEN 0 AND 1000),
    -- Type-specific payload: FILE must carry a file_url; LINK/COURSE/FORM
    -- must carry an action_url. Enforced here so a malformed CTA cannot
    -- leak into a live session.
    CONSTRAINT event_ctas_payload_chk CHECK (
        (type = 'FILE' AND file_url IS NOT NULL)
        OR (type IN ('LINK','COURSE','FORM') AND action_url IS NOT NULL)
    )
);

CREATE INDEX event_ctas_tenant_idx       ON event_ctas (tenant_id);
CREATE INDEX event_ctas_event_active_idx ON event_ctas (event_id)
    WHERE is_active = TRUE;

COMMENT ON TABLE  event_ctas            IS 'Reusable CTA definitions per event. Live and AUTO sessions share the same CTA pool.';
COMMENT ON COLUMN event_ctas.placement  IS 'Where the CTA is rendered. The resolver stacks CTAs within the same placement by priority.';
COMMENT ON COLUMN event_ctas.priority   IS 'Higher wins when two CTAs compete for the same placement and stacking is off.';
COMMENT ON COLUMN event_ctas.allow_stack IS 'When true, this CTA can coexist with other CTAs at the same placement; otherwise only the highest-priority one shows.';

-- ---------------------------------------------------------------------
-- event_timeline_actions
-- ---------------------------------------------------------------------
-- Row-based storage is deliberate: a single JSON blob would prevent
-- per-row editing, partial indexing, and selective deactivation. The
-- payload_json column carries action-type-specific data (e.g. {"ctaId":
-- "…"} for CTA_SHOW, {"message": "…"} for ADMIN_MESSAGE_SHOW).
-- ---------------------------------------------------------------------
CREATE TABLE event_timeline_actions (
    id                   UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID          NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    event_id             UUID          NOT NULL REFERENCES events(id)  ON DELETE CASCADE,
    -- The LIVE session this action was captured in (or attached to, for
    -- manual entries). AUTO sessions replay actions whose
    -- source_session_id matches their sourceLiveSessionId.
    source_session_id    UUID          NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    offset_seconds       INTEGER       NOT NULL,
    action_type          VARCHAR(32)   NOT NULL,
    payload_json         JSONB         NOT NULL DEFAULT '{}'::jsonb,
    is_active            BOOLEAN       NOT NULL DEFAULT TRUE,
    created_by_user_id   UUID          NOT NULL REFERENCES users(id),
    version              BIGINT        NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT event_timeline_actions_type_chk CHECK (
        action_type IN (
            'CTA_SHOW','CTA_HIDE',
            'ADMIN_MESSAGE_SHOW','SYSTEM_MESSAGE_SHOW',
            'HISTORICAL_CHAT_REPLAY',
            'ROOM_STATE_CHANGE',
            'FUTURE_RESERVED'
        )
    ),
    CONSTRAINT event_timeline_actions_offset_chk CHECK (offset_seconds >= 0)
);

-- Hot replay path: "all active actions for a source session, ordered
-- by offset". Partial index so deactivated rows never appear in the
-- scan.
CREATE INDEX event_timeline_actions_replay_idx
    ON event_timeline_actions (source_session_id, offset_seconds)
    WHERE is_active = TRUE;

-- Admin review path: "every timeline row (including inactive) for an
-- event, grouped by type".
CREATE INDEX event_timeline_actions_event_type_idx
    ON event_timeline_actions (event_id, action_type);

CREATE INDEX event_timeline_actions_tenant_idx ON event_timeline_actions (tenant_id);

COMMENT ON TABLE  event_timeline_actions                  IS 'Offset-based action log. Captured during LIVE, replayed during AUTO.';
COMMENT ON COLUMN event_timeline_actions.offset_seconds   IS 'Seconds from video start. NOT wall-clock — replay is seek-based.';
COMMENT ON COLUMN event_timeline_actions.source_session_id IS 'The LIVE session this row was captured in. AUTO sessions replay rows whose source_session_id matches their source_live_session_id.';
COMMENT ON COLUMN event_timeline_actions.payload_json     IS 'Action-specific data. Schema is enforced in the application layer, not the DB.';

-- ---------------------------------------------------------------------
-- Row Level Security
-- ---------------------------------------------------------------------
ALTER TABLE event_ctas             ENABLE ROW LEVEL SECURITY;
ALTER TABLE event_ctas             FORCE  ROW LEVEL SECURITY;
ALTER TABLE event_timeline_actions ENABLE ROW LEVEL SECURITY;
ALTER TABLE event_timeline_actions FORCE  ROW LEVEL SECURITY;

CREATE POLICY event_ctas_tenant_isolation ON event_ctas
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

CREATE POLICY event_timeline_actions_tenant_isolation ON event_timeline_actions
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- ---------------------------------------------------------------------
-- updated_at triggers
-- ---------------------------------------------------------------------
CREATE TRIGGER event_ctas_updated_at
    BEFORE UPDATE ON event_ctas
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER event_timeline_actions_updated_at
    BEFORE UPDATE ON event_timeline_actions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
