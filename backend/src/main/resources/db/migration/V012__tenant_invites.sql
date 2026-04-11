-- =====================================================================
-- V012 :: Tenant invites
-- ---------------------------------------------------------------------
-- Scope:
--   Records a pending invite from an existing tenant admin to a
--   future staff member (owner/admin/moderator/presenter/analyst).
--   An invite is a standalone row, not a "pre-created" tenant_users
--   row, because:
--     * the target may not yet exist as a Webizon user at all —
--       tenant_users.user_id is NOT NULL and FKs users(id), so we
--       cannot plant a placeholder row without violating the
--       referential integrity of the core tenancy table;
--     * an invite has its own lifecycle (PENDING → ACCEPTED | REVOKED
--       | EXPIRED) that is orthogonal to membership lifecycle
--       (ACTIVE | SUSPENDED | REMOVED) and mixing them would bloat
--       tenant_users with state it does not need;
--     * admins routinely revoke or reissue invites before the
--       recipient ever clicks the link, and tracking that history
--       belongs on its own table.
--
-- Token design:
--   The invite is addressed by a long random token, base64url-encoded
--   and stored raw in token_hash via sha256. The plaintext is returned
--   to the admin exactly once in the create response and never stored
--   in the database. This mirrors the OAuth "bearer token at rest is
--   already pwnage" mindset: an attacker who exfiltrates the DB
--   cannot replay live invite links.
--
-- Email freezing:
--   The target email is captured at invite creation time. On accept,
--   the accepter's JWT email must match (case-insensitive) so a
--   leaked link cannot be replayed by a different user. The check
--   happens application-side because we cannot join against Keycloak
--   identity from SQL.
--
-- Status lifecycle:
--   PENDING   — freshly created; awaiting the recipient's click.
--   ACCEPTED  — recipient has claimed the token; a tenant_users row
--               now exists in ACTIVE state. Terminal.
--   REVOKED   — admin cancelled the invite before accept. Terminal.
--   EXPIRED   — expires_at has passed; the accept endpoint rejects
--               the token with a 410 Gone. Terminal.
--
-- Row Level Security:
--   Tenant-scoped like every other tenancy table. The accept
--   endpoint crosses tenants intentionally (the recipient has no
--   tenant context yet) and pivots via @AllowCrossTenant plus an
--   explicit SET LOCAL before the read.
-- =====================================================================

CREATE TABLE tenant_invites (
    id                  UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID           NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,

    -- SHA-256 of the plaintext token. 64 hex chars.
    token_hash          CHAR(64)       NOT NULL,

    -- Email the invite was addressed to. Frozen at creation time;
    -- the accept path checks the current JWT email against this
    -- value so a leaked link cannot be replayed by a different user.
    email               CITEXT         NOT NULL,

    role                VARCHAR(32)    NOT NULL,
    status              VARCHAR(16)    NOT NULL DEFAULT 'PENDING',

    invited_by_user_id  UUID           NOT NULL REFERENCES users(id),

    -- Links to the resulting tenant_users row ONLY after ACCEPTED.
    -- Null in any other state.
    accepted_by_user_id UUID           REFERENCES users(id),
    accepted_membership_id UUID,

    revoked_by_user_id  UUID           REFERENCES users(id),

    expires_at          TIMESTAMPTZ    NOT NULL,
    accepted_at         TIMESTAMPTZ,
    revoked_at          TIMESTAMPTZ,

    -- Optional free-form note from the inviting admin, shown to
    -- the recipient on the accept landing page.
    message             TEXT,

    version             BIGINT         NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT tenant_invites_role_chk   CHECK (role IN (
        'TENANT_OWNER','TENANT_ADMIN','TENANT_MODERATOR','TENANT_PRESENTER','TENANT_ANALYST'
    )),
    CONSTRAINT tenant_invites_status_chk CHECK (status IN (
        'PENDING','ACCEPTED','REVOKED','EXPIRED'
    )),
    CONSTRAINT tenant_invites_lifecycle_chk CHECK (
        (status = 'ACCEPTED' AND accepted_at IS NOT NULL AND accepted_by_user_id IS NOT NULL) OR
        (status = 'REVOKED'  AND revoked_at  IS NOT NULL AND revoked_by_user_id  IS NOT NULL) OR
        (status IN ('PENDING','EXPIRED') AND accepted_at IS NULL AND revoked_at IS NULL)
    )
);

-- ---------------------------------------------------------------------
-- Indexes
-- ---------------------------------------------------------------------

-- Token lookup is the hot path on accept. The token_hash column is
-- globally unique (256 bits of entropy — collisions are not a
-- realistic concern), not tenant-scoped, because the accept endpoint
-- has no tenant context when the recipient first clicks the link.
CREATE UNIQUE INDEX tenant_invites_token_uniq
    ON tenant_invites (token_hash);

-- Admin "pending invites for this tenant" listing.
CREATE INDEX tenant_invites_tenant_status_idx
    ON tenant_invites (tenant_id, status, created_at DESC);

-- Admin "is there already a pending invite for this email?" probe —
-- used to short-circuit re-invites cleanly instead of piling up
-- duplicate rows.
CREATE UNIQUE INDEX tenant_invites_pending_email_uniq
    ON tenant_invites (tenant_id, email)
    WHERE status = 'PENDING';

COMMENT ON TABLE  tenant_invites                IS 'Pending invites from tenant admins to future staff members. Consumed by the accept endpoint and by the WELCOME notification hook.';
COMMENT ON COLUMN tenant_invites.token_hash     IS 'SHA-256 of the plaintext bearer token. Plaintext is only ever returned once, at create time.';
COMMENT ON COLUMN tenant_invites.email          IS 'Target email frozen at invite time. Case-insensitive match required on accept to prevent token replay by a different user.';
COMMENT ON COLUMN tenant_invites.expires_at     IS 'Hard expiry (default 7 days). After this the accept endpoint returns 410 Gone even if the token has not been revoked.';

-- ---------------------------------------------------------------------
-- Row Level Security
-- ---------------------------------------------------------------------
-- Two policies:
--   1. The standard tenant isolation policy used by every other tenancy
--      table — admin listing, revoke, and the sweeper that flips PENDING
--      → EXPIRED all run under a resolved tenant and see only their own
--      rows.
--   2. A narrow "invite lookup" SELECT-only escape policy that fires
--      ONLY when the connection has the session variable
--      `app.invite_lookup = 'on'`. This is the accept endpoint: the
--      recipient has no tenant context yet (they may not be a member of
--      any Webizon tenant), and the only piece of information they
--      carry is the plaintext bearer token. The service layer sets
--      `app.invite_lookup = 'on'` on the connection for the single
--      lookup by token_hash, reads the row, pivots to the invite's
--      tenant via `SET LOCAL app.current_tenant = ?`, then clears the
--      lookup flag before doing any further work. Because the policy is
--      SELECT-only, a rogue lookup-mode connection cannot mutate
--      anything.
-- ---------------------------------------------------------------------
ALTER TABLE tenant_invites ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_invites FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_invites_isolation ON tenant_invites
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

CREATE POLICY tenant_invites_lookup_mode ON tenant_invites
    FOR SELECT
    USING (current_setting('app.invite_lookup', true) = 'on');

-- ---------------------------------------------------------------------
-- updated_at trigger
-- ---------------------------------------------------------------------
CREATE TRIGGER tenant_invites_updated_at
    BEFORE UPDATE ON tenant_invites
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------
-- Notification kind: the V009 kind list is a CHECK constraint, so
-- adding TENANT_INVITE requires a DROP/ADD cycle here. This is the
-- cleanest way to extend a CHECK list in Postgres; no data migration
-- is needed because no row carries the new value yet.
-- ---------------------------------------------------------------------
ALTER TABLE notification_outbox
    DROP CONSTRAINT notification_outbox_kind_chk;

ALTER TABLE notification_outbox
    ADD  CONSTRAINT notification_outbox_kind_chk CHECK (
        kind IN (
            'INVOICE_ISSUED',
            'INVOICE_PAID',
            'EVENT_REMINDER',
            'SESSION_STARTING',
            'WELCOME',
            'TENANT_INVITE'
        )
    );
