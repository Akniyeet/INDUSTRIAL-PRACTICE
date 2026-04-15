-- V020: Align users.id with keycloak_id
--
-- Problem: BaseEntity generates a random UUID for users.id, but the JWT
-- profile_id claim = Keycloak UUID (sub). All FK constraints on profile_id
-- reference users.id, so every attendance/analytics insert fails with a
-- FK violation because the Keycloak UUID ≠ users.id.
--
-- Fix: Use keycloak_id as the canonical users.id so the JWT profile_id
-- claim resolves directly to a valid users.id row.
--
-- Migration steps (all inside one transaction):
--   1. Update all FK columns in child tables from old app UUID → keycloak UUID
--   2. Update users.id = keycloak_id
--
-- This is safe for dev data. In production this would need more care for
-- tables with many rows, but for dev the tables are empty or have minimal data.

BEGIN;

-- 1. tenant_users: user_id and invited_by_user_id
UPDATE tenant_users tu
   SET user_id = u.keycloak_id
  FROM users u
 WHERE tu.user_id = u.id
   AND u.id <> u.keycloak_id;

UPDATE tenant_users tu
   SET invited_by_user_id = u.keycloak_id
  FROM users u
 WHERE tu.invited_by_user_id = u.id
   AND u.id <> u.keycloak_id;

-- 2. events: created_by_user_id
UPDATE events e
   SET created_by_user_id = u.keycloak_id
  FROM users u
 WHERE e.created_by_user_id = u.id
   AND u.id <> u.keycloak_id;

-- 3. sessions: created_by_user_id
UPDATE sessions s
   SET created_by_user_id = u.keycloak_id
  FROM users u
 WHERE s.created_by_user_id = u.id
   AND u.id <> u.keycloak_id;

-- 4. event_ctas: created_by_user_id
UPDATE event_ctas c
   SET created_by_user_id = u.keycloak_id
  FROM users u
 WHERE c.created_by_user_id = u.id
   AND u.id <> u.keycloak_id;

-- 5. event_timeline_actions: created_by_user_id
UPDATE event_timeline_actions t
   SET created_by_user_id = u.keycloak_id
  FROM users u
 WHERE t.created_by_user_id = u.id
   AND u.id <> u.keycloak_id;

-- 6. file_assets: uploaded_by_user_id
UPDATE file_assets f
   SET uploaded_by_user_id = u.keycloak_id
  FROM users u
 WHERE f.uploaded_by_user_id = u.id
   AND u.id <> u.keycloak_id;

-- 7. chat_messages: user_id, deleted_by_user_id
UPDATE chat_messages cm
   SET user_id = u.keycloak_id
  FROM users u
 WHERE cm.user_id = u.id
   AND u.id <> u.keycloak_id;

UPDATE chat_messages cm
   SET deleted_by_user_id = u.keycloak_id
  FROM users u
 WHERE cm.deleted_by_user_id = u.id
   AND u.id <> u.keycloak_id;

-- 8. chat_user_statuses: user_id
UPDATE chat_user_statuses cs
   SET user_id = u.keycloak_id
  FROM users u
 WHERE cs.user_id = u.id
   AND u.id <> u.keycloak_id;

-- 9. moderation_actions: target_user_id, moderator_user_id
UPDATE moderation_actions m
   SET target_user_id = u.keycloak_id
  FROM users u
 WHERE m.target_user_id = u.id
   AND u.id <> u.keycloak_id;

UPDATE moderation_actions m
   SET moderator_user_id = u.keycloak_id
  FROM users u
 WHERE m.moderator_user_id = u.id
   AND u.id <> u.keycloak_id;

-- 10. session_attendance: profile_id
UPDATE session_attendance sa
   SET profile_id = u.keycloak_id
  FROM users u
 WHERE sa.profile_id = u.id
   AND u.id <> u.keycloak_id;

-- 11. session_registrations: profile_id
UPDATE session_registrations sr
   SET profile_id = u.keycloak_id
  FROM users u
 WHERE sr.profile_id = u.id
   AND u.id <> u.keycloak_id;

-- 12. analytics_events: profile_id
UPDATE analytics_events ae
   SET profile_id = u.keycloak_id
  FROM users u
 WHERE ae.profile_id = u.id
   AND u.id <> u.keycloak_id;

-- 13. lead_signals: profile_id
UPDATE lead_signals ls
   SET profile_id = u.keycloak_id
  FROM users u
 WHERE ls.profile_id = u.id
   AND u.id <> u.keycloak_id;

-- 14. ai_lead_scores: profile_id
UPDATE ai_lead_scores als
   SET profile_id = u.keycloak_id
  FROM users u
 WHERE als.profile_id = u.id
   AND u.id <> u.keycloak_id;

-- 15. notification_outbox: profile_id
UPDATE notification_outbox no2
   SET profile_id = u.keycloak_id
  FROM users u
 WHERE no2.profile_id = u.id
   AND u.id <> u.keycloak_id;

-- 16. tenant_invites: invited_by_user_id, accepted_by_user_id, revoked_by_user_id
UPDATE tenant_invites ti
   SET invited_by_user_id = u.keycloak_id
  FROM users u
 WHERE ti.invited_by_user_id = u.id
   AND u.id <> u.keycloak_id;

UPDATE tenant_invites ti
   SET accepted_by_user_id = u.keycloak_id
  FROM users u
 WHERE ti.accepted_by_user_id = u.id
   AND u.id <> u.keycloak_id;

UPDATE tenant_invites ti
   SET revoked_by_user_id = u.keycloak_id
  FROM users u
 WHERE ti.revoked_by_user_id = u.id
   AND u.id <> u.keycloak_id;

-- 17. Finally, update users.id = keycloak_id
--     (no child-table FK will be violated since we just updated them all above)
UPDATE users SET id = keycloak_id WHERE id <> keycloak_id;

COMMIT;
