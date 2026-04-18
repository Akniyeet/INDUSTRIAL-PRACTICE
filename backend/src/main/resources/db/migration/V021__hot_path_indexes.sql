-- ═══════════════════════════════════════════════════════════════════════════
-- V021 — Hot-path indexes for the real-time room and analytics queries
-- ═══════════════════════════════════════════════════════════════════════════
--
-- Added after a performance audit surfaced four P0/P1 bottlenecks that all
-- trace back to missing or over-broad indexes on queries the live room hits
-- on every message / every join / every tick:
--
--   1. TimelineReplayEngine.tick() fans out to every tenant every second.
--      Per-tenant "SELECT * FROM sessions WHERE status = 'AUTO_LIVE'"
--      currently falls through sessions_tenant_status_start_idx but still
--      has to walk a lot of cold rows once the tenant count grows. A
--      partial index on the two active statuses collapses the scan to
--      nearly nothing regardless of tenant count.
--
--   2. LeadSignalEvaluator.evaluateReturnedForAuto() used to hydrate the
--      profile's ENTIRE attendance history per ROOM_ENTERED. We are
--      replacing that with a count query in the service layer — this
--      migration adds the supporting composite index.
--
--   3. SessionAttendanceRepository.countPresentBySessionId filters on
--      (session_id, left_at IS NULL). The broad session_id index works but
--      has to re-filter the finalized rows; a partial index is free on
--      finalized sessions and cheap on live ones.
--
--   4. Analytics CTA CTR queries filter on metadata->>'ctaId' with no
--      supporting index. An expression index that's scoped to the three
--      CTA event types keeps it tiny while still answering every CTA
--      analytics question we have.
--
-- None of these indexes block writes — PostgreSQL builds them concurrently
-- in our deploy pipeline (Flyway baseline + CONCURRENTLY wrapper handled
-- by the ops playbook; plain CREATE INDEX is used here because Flyway
-- cannot run CONCURRENTLY inside its transaction).
-- ═══════════════════════════════════════════════════════════════════════════

-- -------------------------------------------------------------------------
-- 1. Sessions — airing-status partial index
-- -------------------------------------------------------------------------
-- The auto-session lifecycle + timeline replay loops both query for the
-- same two statuses every second. A partial index only holds rows in
-- those statuses, which at steady state is at most ~a few per tenant.
CREATE INDEX IF NOT EXISTS sessions_airing_partial_idx
    ON sessions (tenant_id, id)
    WHERE status IN ('LIVE', 'AUTO_LIVE');

-- -------------------------------------------------------------------------
-- 2. Session attendance — (profile_id, event_id) composite for lead scoring
-- -------------------------------------------------------------------------
-- Supports the new count-query replacement inside LeadSignalEvaluator:
--   SELECT COUNT(*) FROM session_attendance
--   WHERE profile_id = ? AND event_id = ?
-- Used on every ROOM_ENTERED so the index must be tight.
CREATE INDEX IF NOT EXISTS session_attendance_profile_event_idx
    ON session_attendance (profile_id, event_id);

-- -------------------------------------------------------------------------
-- 3. Session attendance — "currently present" partial index
-- -------------------------------------------------------------------------
-- countPresentBySessionId is called from every RoomService.bootstrap and
-- every presence refresh. left_at IS NULL thins out to just the viewers
-- still in the room, which is orders of magnitude smaller than the total
-- attendance history after a session ends.
CREATE INDEX IF NOT EXISTS session_attendance_present_idx
    ON session_attendance (session_id)
    WHERE left_at IS NULL;

-- -------------------------------------------------------------------------
-- 4. Analytics events — CTA id expression index
-- -------------------------------------------------------------------------
-- The CTA CTR dashboard filters on metadata->>'ctaId' for the three CTA
-- event types only. A scoped expression index is ~10x cheaper to maintain
-- than a GIN on the whole metadata jsonb and sufficient for every CTA
-- analytics use case we have today.
CREATE INDEX IF NOT EXISTS analytics_events_cta_id_idx
    ON analytics_events ((metadata->>'ctaId'))
    WHERE event_type IN ('CTA_IMPRESSION', 'CTA_CLICK', 'CTA_DOWNLOAD');
