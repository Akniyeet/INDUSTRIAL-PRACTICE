-- =====================================================================
-- V010 :: Session Registrations
-- ---------------------------------------------------------------------
-- Scope:
--   One row per (session, profile) pair representing a viewer's
--   intent to attend a specific Session. Registrations are the link
--   between the public event landing page and the notification
--   outbox: when a user clicks "Remind me", we insert a row here,
--   and the SessionReminderScheduler later fans out an
--   EVENT_REMINDER notification to every registered viewer N
--   minutes before the session starts.
--
-- Why a separate table instead of reusing attendance?
--   Attendance (Phase 5, analytics_sessions/event_sessions) is
--   recorded WHEN a viewer actually enters the live room — it is a
--   measurement of what happened. Registration is a measurement of
--   INTENT, recorded before the session exists as a live broadcast.
--   A user may register and then miss the airing; the two signals
--   have completely different downstream consumers:
--     * registrations drive reminder dispatch
--     * attendance drives billing meters and retention analytics
--   Conflating them would break both pipelines.
--
-- Why tenant-scoped?
--   Even though the session_id already uniquely identifies the
--   workspace, we carry tenant_id explicitly to (1) participate in
--   the same RLS isolation as every other tenant-scoped table and
--   (2) give the dispatcher scheduler a cheap way to fan out per
--   tenant without joining back to sessions for every row.
--
-- Lifecycle:
--   registered_at  -- set on first insert; never updated
--   unregistered_at-- set if the viewer opts out; null while active
--   reminder_sent_at -- set when SessionReminderScheduler emits the
--                      EVENT_REMINDER notification for this row.
--                      Used as the idempotency marker so a restart
--                      of the scheduler cannot double-send.
--
-- Uniqueness:
--   A user can only have one active registration per session. We
--   enforce this with a partial unique index on (session_id,
--   profile_id) WHERE unregistered_at IS NULL — so an opt-out +
--   re-opt-in cycle produces a fresh row without tripping the
--   constraint.
-- =====================================================================

CREATE TABLE session_registrations (
    id                  UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID           NOT NULL REFERENCES tenants(id)  ON DELETE CASCADE,
    session_id          UUID           NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    event_id            UUID           NOT NULL REFERENCES events(id)   ON DELETE CASCADE,

    -- The Webizon profile the registration belongs to. Required;
    -- there is no anonymous registration path (see CLAUDE rule:
    -- viewing always requires authentication).
    profile_id          UUID           NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- Delivery target frozen at registration time. Mirroring the
    -- outbox convention — a later profile email change does not
    -- retroactively misroute a scheduled reminder.
    notify_email        VARCHAR(320)   NOT NULL,

    -- Explicit opt-in flag. Defaults TRUE because clicking the
    -- "Remind me" button IS the consent. Users can opt out later
    -- through the unsubscribe link in the reminder email.
    email_reminders_enabled BOOLEAN    NOT NULL DEFAULT TRUE,

    registered_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    unregistered_at     TIMESTAMPTZ,

    -- Populated by SessionReminderScheduler. Non-null means the
    -- EVENT_REMINDER notification has already been enqueued; the
    -- scheduler skips such rows on subsequent ticks. Idempotency
    -- key in the outbox still guards the ultimate duplicate, but
    -- this column lets the scheduler avoid re-hitting the outbox
    -- for every row on every tick once the reminder fan-out is
    -- complete.
    reminder_sent_at    TIMESTAMPTZ,

    version             BIGINT         NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT session_registrations_lifecycle_chk CHECK (
        unregistered_at IS NULL OR unregistered_at >= registered_at
    )
);

-- Active-only uniqueness: (session_id, profile_id) can appear many
-- times across history (opt-in → opt-out → opt-in again) but at
-- most once in an active state.
CREATE UNIQUE INDEX session_registrations_active_uniq
    ON session_registrations (session_id, profile_id)
    WHERE unregistered_at IS NULL;

-- Viewer-facing "my upcoming registrations" query path.
CREATE INDEX session_registrations_profile_idx
    ON session_registrations (profile_id, registered_at DESC)
    WHERE unregistered_at IS NULL;

-- Reminder scheduler hot query: "all active registrations for a
-- specific session whose reminder has not yet been sent". Bounded
-- to active + not-yet-notified rows so a fully-processed session
-- drops out of the index entirely.
CREATE INDEX session_registrations_pending_reminder_idx
    ON session_registrations (session_id)
    WHERE unregistered_at IS NULL
      AND reminder_sent_at IS NULL;

-- Admin count / event dashboard — "how many people registered for
-- this event across all its sessions".
CREATE INDEX session_registrations_event_idx
    ON session_registrations (event_id, registered_at DESC);

COMMENT ON TABLE  session_registrations                IS 'Opt-in registrations for future sessions. Drives reminder fan-out in Phase 13.';
COMMENT ON COLUMN session_registrations.notify_email   IS 'Frozen delivery target; immune to later profile email changes.';
COMMENT ON COLUMN session_registrations.reminder_sent_at IS 'Idempotency marker for SessionReminderScheduler — non-null means EVENT_REMINDER has been enqueued on the outbox.';
COMMENT ON COLUMN session_registrations.unregistered_at IS 'Set on opt-out; active constraints treat NULL as "currently registered".';

-- ---------------------------------------------------------------------
-- Row Level Security
-- ---------------------------------------------------------------------
ALTER TABLE session_registrations ENABLE ROW LEVEL SECURITY;
ALTER TABLE session_registrations FORCE  ROW LEVEL SECURITY;

CREATE POLICY session_registrations_tenant_isolation ON session_registrations
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- ---------------------------------------------------------------------
-- updated_at trigger
-- ---------------------------------------------------------------------
CREATE TRIGGER session_registrations_updated_at
    BEFORE UPDATE ON session_registrations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
