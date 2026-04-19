-- =====================================================================
-- V011 :: Session-start notification fan-out marker
-- ---------------------------------------------------------------------
-- Scope:
--   Adds a single nullable column to the sessions table that records
--   the wall clock at which the SESSION_STARTING notification
--   fan-out completed for that session. Consumed exclusively by the
--   SessionStartingScheduler (Phase 14), which uses it as a
--   crash-safe cursor.
--
-- Why a column on sessions instead of per-registration marking?
--   The scheduler needs to answer "is this session's fan-out done?"
--   on every tick without scanning every registration row. A
--   session-level marker makes the hot filter a single index
--   lookup, and the outbox's (tenant_id, idempotency_key) unique
--   index is already the authoritative no-duplicate guard at the
--   registration level — the session column is purely an
--   efficiency signal, not a correctness signal. This means a
--   crash halfway through a fan-out is fully recoverable:
--     * the notifications already written are deduped by the
--       outbox unique index on retry
--     * the scheduler re-reads the session on the next tick
--       because session_start_notified_at is still null
--     * the fan-out replays from the beginning and only the
--       missing registrations actually land on the outbox
--
-- Why a partial index on the null-only case?
--   Once a session has been notified, it never needs to appear in
--   this query again — so a full B-tree on the column would waste
--   space on the long-running SENT rows. A partial index bounded
--   to NULL rows drops out completely after fan-out finishes,
--   keeping the scheduler's read O(pending) forever.
-- =====================================================================

ALTER TABLE sessions
    ADD COLUMN session_start_notified_at TIMESTAMPTZ;

COMMENT ON COLUMN sessions.session_start_notified_at IS
    'Wall clock when SessionStartingScheduler finished fanning out SESSION_STARTING notifications. '
    'NULL means the scheduler has not yet processed this session (or it has never been airing). '
    'Set once per session lifetime; never reset.';

-- Scheduler hot query: "find airing sessions whose fan-out is
-- still pending". Partial on IS NULL so finished sessions drop
-- out of the index entirely.
CREATE INDEX sessions_pending_start_notification_idx
    ON sessions (status)
    WHERE session_start_notified_at IS NULL
      AND status IN ('LIVE','AUTO_LIVE');
