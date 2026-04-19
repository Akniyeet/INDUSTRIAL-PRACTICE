-- =====================================================================
-- V006 :: Auto Sessions & Historical Chat Replay
-- ---------------------------------------------------------------------
-- Scope:
--   1. sessions.last_replay_offset_seconds  -- resumable replay cursor
--      for AUTO sessions. The TimelineReplayEngine wakes up on a tick
--      loop, looks at "what offset did I already dispatch up to?",
--      and fetches the next window of timeline actions and chat
--      messages. Persisting the cursor means a backend restart does
--      not re-fire every CTA from t=0.
--
--   2. chat_messages.excluded_from_replay -- admin curation flag that
--      removes a live-origin message from future AUTO replays
--      WITHOUT deleting it from the historical record. A message can
--      be perfectly acceptable for the live audit trail while still
--      being unsuitable for reuse in a scheduled replay slot (off-topic
--      banter, an answered question that would be confusing out of
--      context, a link that expired). This is distinct from
--      is_deleted / is_hidden because those have their own semantics.
--
-- Why not reuse is_deleted / is_hidden for replay exclusion?
--   is_deleted  = message was soft-removed by a moderator; should not
--                 appear in the live audit view at all (except the
--                 admin's "show deleted" toggle).
--   is_hidden   = message is hidden from the live feed but kept visible
--                 to moderators — used for borderline content during
--                 review.
--   excluded_from_replay = the message stays fully visible everywhere
--                 the historical audit is read, but the replay engine
--                 skips it. Three orthogonal flags, three orthogonal
--                 reasons.
--
-- =====================================================================

-- ---------------------------------------------------------------------
-- sessions.last_replay_offset_seconds
-- ---------------------------------------------------------------------
-- Defaults to 0 so newly-started AUTO sessions begin from the top of
-- the recording. NOT NULL because the replay engine does "cursor
-- window = (last_replay_offset, current_offset]" on every tick and a
-- null would silently make the window empty.
ALTER TABLE sessions
    ADD COLUMN last_replay_offset_seconds INTEGER NOT NULL DEFAULT 0;

ALTER TABLE sessions
    ADD CONSTRAINT sessions_last_replay_offset_chk
        CHECK (last_replay_offset_seconds >= 0);

COMMENT ON COLUMN sessions.last_replay_offset_seconds IS
    'Highest offset (sec) the TimelineReplayEngine has dispatched for this AUTO session. Advances monotonically per tick; survives backend restarts so replay resumes cleanly.';

-- ---------------------------------------------------------------------
-- chat_messages.excluded_from_replay
-- ---------------------------------------------------------------------
-- Column default is FALSE so every existing live-origin message
-- remains replayable unless an admin explicitly opts it out.
ALTER TABLE chat_messages
    ADD COLUMN excluded_from_replay BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN chat_messages.excluded_from_replay IS
    'When TRUE, the message is skipped by the historical chat replay engine in AUTO sessions. Orthogonal to is_deleted/is_hidden: the message stays visible in the historical audit view; only the replay pipeline ignores it.';

-- Partial index tuned for the replay-window query: "give me every
-- non-deleted, non-excluded message for source session S whose offset
-- falls in (lo, hi]". Bounded by the partial WHERE so the index
-- footprint stays small even as a session accumulates moderation
-- noise.
CREATE INDEX chat_messages_replay_window_idx
    ON chat_messages (session_id, offset_seconds)
    WHERE is_deleted = FALSE
      AND is_hidden = FALSE
      AND excluded_from_replay = FALSE
      AND offset_seconds IS NOT NULL;
