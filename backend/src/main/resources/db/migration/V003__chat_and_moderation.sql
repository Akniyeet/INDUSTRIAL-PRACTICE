-- =====================================================================
-- V003 :: Chat & Moderation
-- ---------------------------------------------------------------------
-- Domain:
--   event_chat_settings  -- per-event chat configuration (slow mode,
--                           welcome message, link policy, …). Sessions
--                           inherit these; per-session overrides can be
--                           added in a later migration if needed.
--   chat_messages        -- every chat line, live and historical. Tuned
--                           for heavy read/write under 50k+ concurrent
--                           viewers.
--   chat_user_statuses   -- effective per-user chat state inside a
--                           session (muted, chat-banned, warning count).
--                           One row per (session_id, user_id), upserted.
--   moderation_actions   -- immutable audit log of every moderator act.
--
-- Critical product rules (CLAUDE.md §15-20, §52-55):
--   * Chat is session-scoped. Replayed "historical" chat in AUTO
--     sessions is stored as its own rows with message_type=HISTORICAL
--     and the original offset_seconds preserved.
--   * Reply is one-level only — reply_to_message_id must never chain.
--   * Deletions are soft (is_deleted = true) so moderators can review.
--   * chat_user_statuses is tenant-scoped via tenant_id + RLS, like
--     everything else. A user banned in one tenant is NOT banned in
--     another.
-- =====================================================================

-- ---------------------------------------------------------------------
-- event_chat_settings
-- ---------------------------------------------------------------------
CREATE TABLE event_chat_settings (
    id                     UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id              UUID          NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    event_id               UUID          NOT NULL REFERENCES events(id)  ON DELETE CASCADE,
    allow_links            BOOLEAN       NOT NULL DEFAULT FALSE,
    slow_mode_seconds      INTEGER       NOT NULL DEFAULT 0,
    show_participant_count BOOLEAN       NOT NULL DEFAULT TRUE,
    show_participant_names BOOLEAN       NOT NULL DEFAULT TRUE,
    welcome_message        TEXT,
    premoderation_enabled  BOOLEAN       NOT NULL DEFAULT FALSE,
    profanity_filter_enabled BOOLEAN     NOT NULL DEFAULT TRUE,
    anti_spam_enabled      BOOLEAN       NOT NULL DEFAULT TRUE,
    version                BIGINT        NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT event_chat_settings_event_uniq UNIQUE (event_id),
    CONSTRAINT event_chat_settings_slow_chk   CHECK (slow_mode_seconds BETWEEN 0 AND 600)
);

CREATE INDEX event_chat_settings_tenant_idx ON event_chat_settings (tenant_id);

COMMENT ON TABLE  event_chat_settings                   IS 'Chat configuration per event. Sessions inherit these values at runtime.';
COMMENT ON COLUMN event_chat_settings.slow_mode_seconds IS '0 = off. Applies to non-moderator users; moderators bypass slow mode.';
COMMENT ON COLUMN event_chat_settings.premoderation_enabled IS 'When true, messages require moderator approval before broadcasting.';

-- ---------------------------------------------------------------------
-- chat_messages
-- ---------------------------------------------------------------------
CREATE TABLE chat_messages (
    id                     UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id              UUID          NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    event_id               UUID          NOT NULL REFERENCES events(id)  ON DELETE CASCADE,
    session_id             UUID          NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    user_id                UUID          REFERENCES users(id),
    -- user_id is NULL for SYSTEM messages (no human author)
    message_type           VARCHAR(16)   NOT NULL,
    reply_to_message_id    UUID          REFERENCES chat_messages(id),
    text                   VARCHAR(2000) NOT NULL,
    offset_seconds         INTEGER,
    is_deleted             BOOLEAN       NOT NULL DEFAULT FALSE,
    is_hidden              BOOLEAN       NOT NULL DEFAULT FALSE,
    deleted_by_user_id     UUID          REFERENCES users(id),
    deleted_at             TIMESTAMPTZ,
    version                BIGINT        NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT chat_messages_type_chk CHECK (
        message_type IN ('USER','ADMIN','SYSTEM','HISTORICAL')
    ),
    CONSTRAINT chat_messages_text_chk    CHECK (char_length(text) > 0),
    CONSTRAINT chat_messages_author_chk  CHECK (
        -- SYSTEM may have NULL user_id; everything else must be attributed
        message_type = 'SYSTEM' OR user_id IS NOT NULL
    ),
    CONSTRAINT chat_messages_offset_chk  CHECK (
        offset_seconds IS NULL OR offset_seconds >= 0
    )
);

-- Primary live-chat read pattern: "newest N messages for a session,
-- excluding deleted". Partial index on is_deleted = false keeps the hot
-- live feed lookup tiny even after heavy moderation.
CREATE INDEX chat_messages_session_live_idx
    ON chat_messages (session_id, created_at DESC)
    WHERE is_deleted = FALSE;

-- Historical replay read pattern: "all non-deleted messages ordered by
-- offset_seconds" when building the replay sequence for an AUTO session.
CREATE INDEX chat_messages_session_offset_idx
    ON chat_messages (session_id, offset_seconds)
    WHERE is_deleted = FALSE AND offset_seconds IS NOT NULL;

-- Tenant + moderation review queries.
CREATE INDEX chat_messages_tenant_idx       ON chat_messages (tenant_id);
CREATE INDEX chat_messages_reply_idx        ON chat_messages (reply_to_message_id)
    WHERE reply_to_message_id IS NOT NULL;
CREATE INDEX chat_messages_user_session_idx ON chat_messages (user_id, session_id)
    WHERE user_id IS NOT NULL;

COMMENT ON TABLE  chat_messages                IS 'Every chat line. Soft-deleted for moderation audit. Partitioning is a future optimisation.';
COMMENT ON COLUMN chat_messages.offset_seconds IS 'Video-relative offset in seconds. Required for messages that should replay in AUTO sessions; nullable for pure current-session chat.';
COMMENT ON COLUMN chat_messages.message_type   IS 'USER=participant, ADMIN=moderator visible, SYSTEM=automated, HISTORICAL=replayed from a source LIVE session.';

-- ---------------------------------------------------------------------
-- chat_user_statuses
-- ---------------------------------------------------------------------
CREATE TABLE chat_user_statuses (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID         NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    event_id           UUID         NOT NULL REFERENCES events(id)  ON DELETE CASCADE,
    session_id         UUID         NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    user_id            UUID         NOT NULL REFERENCES users(id),
    can_send_messages  BOOLEAN      NOT NULL DEFAULT TRUE,
    is_muted           BOOLEAN      NOT NULL DEFAULT FALSE,
    mute_until         TIMESTAMPTZ,
    is_chat_banned     BOOLEAN      NOT NULL DEFAULT FALSE,
    is_room_banned     BOOLEAN      NOT NULL DEFAULT FALSE,
    warning_count      INTEGER      NOT NULL DEFAULT 0,
    last_message_at    TIMESTAMPTZ,
    version            BIGINT       NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chat_user_statuses_session_user_uniq UNIQUE (session_id, user_id),
    CONSTRAINT chat_user_statuses_warn_chk CHECK (warning_count >= 0)
);

CREATE INDEX chat_user_statuses_tenant_idx ON chat_user_statuses (tenant_id);
CREATE INDEX chat_user_statuses_user_idx   ON chat_user_statuses (user_id);

COMMENT ON TABLE  chat_user_statuses                 IS 'Effective chat state of a user inside a session. Upserted on every moderation action and slow-mode cooldown refresh.';
COMMENT ON COLUMN chat_user_statuses.last_message_at IS 'Last time this user sent a message — used for slow-mode enforcement.';
COMMENT ON COLUMN chat_user_statuses.mute_until      IS 'When non-null and in the future, user is muted regardless of is_muted flag. Expired mutes are cleared lazily.';

-- ---------------------------------------------------------------------
-- moderation_actions
-- ---------------------------------------------------------------------
CREATE TABLE moderation_actions (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID         NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    event_id             UUID         NOT NULL REFERENCES events(id)  ON DELETE CASCADE,
    session_id           UUID         NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    target_user_id       UUID         REFERENCES users(id),
    moderator_user_id    UUID         NOT NULL REFERENCES users(id),
    action_type          VARCHAR(20)  NOT NULL,
    target_message_id    UUID         REFERENCES chat_messages(id),
    reason               VARCHAR(500),
    duration_seconds     INTEGER,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT moderation_actions_type_chk CHECK (action_type IN (
        'WARNING','MUTE','CHAT_BAN','ROOM_REMOVE','FULL_BAN',
        'MESSAGE_DELETE','MESSAGE_HIDE','SLOW_MODE_CHANGE'
    )),
    -- Message-targeted actions must carry a target_message_id; user-targeted
    -- actions must carry a target_user_id. SLOW_MODE_CHANGE targets neither.
    CONSTRAINT moderation_actions_target_chk CHECK (
        (action_type IN ('MESSAGE_DELETE','MESSAGE_HIDE') AND target_message_id IS NOT NULL)
        OR (action_type IN ('WARNING','MUTE','CHAT_BAN','ROOM_REMOVE','FULL_BAN') AND target_user_id IS NOT NULL)
        OR  action_type = 'SLOW_MODE_CHANGE'
    ),
    CONSTRAINT moderation_actions_duration_chk CHECK (
        duration_seconds IS NULL OR duration_seconds > 0
    )
);

CREATE INDEX moderation_actions_session_idx       ON moderation_actions (session_id, created_at DESC);
CREATE INDEX moderation_actions_tenant_idx        ON moderation_actions (tenant_id);
CREATE INDEX moderation_actions_target_user_idx   ON moderation_actions (target_user_id) WHERE target_user_id IS NOT NULL;
CREATE INDEX moderation_actions_moderator_idx     ON moderation_actions (moderator_user_id);

COMMENT ON TABLE  moderation_actions              IS 'Append-only audit log of moderator acts. Never updated, never deleted.';
COMMENT ON COLUMN moderation_actions.duration_seconds IS 'For time-bounded actions (MUTE). Null for permanent or instantaneous actions.';

-- ---------------------------------------------------------------------
-- Row Level Security
-- ---------------------------------------------------------------------
ALTER TABLE event_chat_settings  ENABLE ROW LEVEL SECURITY;
ALTER TABLE event_chat_settings  FORCE  ROW LEVEL SECURITY;
ALTER TABLE chat_messages        ENABLE ROW LEVEL SECURITY;
ALTER TABLE chat_messages        FORCE  ROW LEVEL SECURITY;
ALTER TABLE chat_user_statuses   ENABLE ROW LEVEL SECURITY;
ALTER TABLE chat_user_statuses   FORCE  ROW LEVEL SECURITY;
ALTER TABLE moderation_actions   ENABLE ROW LEVEL SECURITY;
ALTER TABLE moderation_actions   FORCE  ROW LEVEL SECURITY;

CREATE POLICY event_chat_settings_tenant_isolation ON event_chat_settings
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

CREATE POLICY chat_messages_tenant_isolation ON chat_messages
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

CREATE POLICY chat_user_statuses_tenant_isolation ON chat_user_statuses
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

CREATE POLICY moderation_actions_tenant_isolation ON moderation_actions
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- ---------------------------------------------------------------------
-- updated_at triggers (moderation_actions is append-only, no trigger)
-- ---------------------------------------------------------------------
CREATE TRIGGER event_chat_settings_updated_at BEFORE UPDATE ON event_chat_settings FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER chat_messages_updated_at       BEFORE UPDATE ON chat_messages       FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER chat_user_statuses_updated_at  BEFORE UPDATE ON chat_user_statuses  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
