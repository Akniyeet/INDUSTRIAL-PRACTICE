-- =====================================================================
-- V022 :: Dev workspace seed
-- ---------------------------------------------------------------------
-- Seeds a "webizon" workspace and links the seed admin user
-- (webizon365@gmail.com) to it as TENANT_OWNER.
--
-- Keycloak side (webizon-realm.json) is responsible for:
--   • The user having the realm role `tenant_owner`
--   • The user attribute `tenant_id` carrying this same UUID (so it
--     ends up in the access token as the `tenant_id` claim)
--
-- Without this seed, a fresh clone of the project would leave the
-- seed admin logged in but unable to read tenant-scoped endpoints
-- (GET /api/v1/events returned 403 because the user had no membership).
--
-- Idempotent:
--   • The tenant row uses ON CONFLICT DO NOTHING (no target) so that a
--     pre-existing row with the same slug OR id is simply skipped —
--     earlier manual seeds during dev could leave a row with the same
--     slug but a different UUID, which a slug-specific conflict would
--     miss. (We still pin the UUID for Keycloak attribute parity.)
--   • The tenant_users row is only inserted if the user has already
--     been bootstrapped into our mirror via UserService.bootstrapFromJwt().
-- =====================================================================

-- Demo workspace — UUID pinned so Keycloak's tenant_id attribute can
-- reference the same value declaratively in the realm export.
INSERT INTO tenants (
    id, slug, display_name,
    country_code, default_currency, default_locale, default_timezone,
    status, trial_ends_at
) VALUES (
    '11111111-1111-1111-1111-111111111111',
    'webizon',
    'Webizon Workspace',
    'KZ', 'KZT', 'ru-KZ', 'Asia/Almaty',
    'ACTIVE',
    now() + INTERVAL '14 days'
) ON CONFLICT DO NOTHING;

-- Link the seed admin as TENANT_OWNER of the demo workspace.
-- Skipped if the user row is not yet present (they haven't logged in
-- on this machine yet) — on their first login UserService will create
-- the row, but the membership will not backfill automatically; rerun
-- this file manually or re-login after the first bootstrap if you
-- hit 403 on tenant-scoped endpoints.
INSERT INTO tenant_users (id, tenant_id, user_id, role, status, joined_at)
SELECT
    gen_random_uuid(),
    '11111111-1111-1111-1111-111111111111',
    u.id,
    'TENANT_OWNER',
    'ACTIVE',
    now()
FROM users u
WHERE lower(u.email) = 'webizon365@gmail.com'
  AND NOT EXISTS (
      SELECT 1 FROM tenant_users tu
      WHERE tu.tenant_id = '11111111-1111-1111-1111-111111111111'
        AND tu.user_id   = u.id
  );
