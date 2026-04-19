-- =====================================================================
-- V001 :: Core tenancy & identity
-- ---------------------------------------------------------------------
-- Establishes the three pillars of the Webizon data model:
--   1. tenants          -- paying customers (schools, coaches, companies)
--   2. users            -- global identity, unique by email
--   3. tenant_users     -- membership + role inside a tenant
-- ---------------------------------------------------------------------
-- Multi-tenancy strategy: shared schema + tenant_id discriminator.
--   * Every tenant-scoped table has tenant_id UUID NOT NULL
--   * PostgreSQL RLS policies enforce isolation at the DB level
--   * Application sets `app.current_tenant` via SET LOCAL per request
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "citext";

-- ---------------------------------------------------------------------
-- tenants
-- ---------------------------------------------------------------------
CREATE TABLE tenants (
    id                  UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    slug                VARCHAR(64)    NOT NULL UNIQUE,
    display_name        VARCHAR(255)   NOT NULL,
    legal_name          VARCHAR(255),
    country_code        CHAR(2)        NOT NULL DEFAULT 'KZ',
    default_currency    CHAR(3)        NOT NULL DEFAULT 'KZT',
    default_locale      VARCHAR(8)     NOT NULL DEFAULT 'ru-KZ',
    default_timezone    VARCHAR(64)    NOT NULL DEFAULT 'Asia/Almaty',
    status              VARCHAR(32)    NOT NULL DEFAULT 'TRIAL',
    trial_ends_at       TIMESTAMPTZ,
    version             BIGINT         NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT tenants_status_chk CHECK (
        status IN ('TRIAL', 'ACTIVE', 'PAST_DUE', 'SUSPENDED', 'CANCELLED', 'ARCHIVED')
    ),
    CONSTRAINT tenants_slug_chk CHECK (slug ~ '^[a-z0-9][a-z0-9-]{1,62}[a-z0-9]$')
);

COMMENT ON TABLE  tenants                   IS 'Paying customers of the Webizon platform.';
COMMENT ON COLUMN tenants.slug              IS 'URL-safe public identifier, used in *.webizon.kz subdomains.';
COMMENT ON COLUMN tenants.default_currency  IS 'ISO-4217. Platform is KZT-first but schema does not hardcode it.';
COMMENT ON COLUMN tenants.status            IS 'Lifecycle: TRIAL -> ACTIVE -> PAST_DUE -> SUSPENDED -> CANCELLED -> ARCHIVED.';

-- ---------------------------------------------------------------------
-- users (global identity)
-- ---------------------------------------------------------------------
-- Users are GLOBAL — a single human can belong to multiple tenants.
-- Keycloak is the source of truth for credentials; we mirror the
-- minimum viable profile for fast joins and reporting.
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id                  UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    keycloak_id         UUID           NOT NULL UNIQUE,
    email               CITEXT         NOT NULL UNIQUE,
    email_verified      BOOLEAN        NOT NULL DEFAULT FALSE,
    full_name           VARCHAR(255),
    avatar_url          TEXT,
    locale              VARCHAR(8)     NOT NULL DEFAULT 'ru-KZ',
    timezone            VARCHAR(64)    NOT NULL DEFAULT 'Asia/Almaty',
    is_platform_admin   BOOLEAN        NOT NULL DEFAULT FALSE,
    last_login_at       TIMESTAMPTZ,
    version             BIGINT         NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now()
);

COMMENT ON TABLE  users             IS 'Global user identity, mirrored from Keycloak.';
COMMENT ON COLUMN users.keycloak_id IS 'Stable Keycloak subject (sub claim). Never mutates.';
COMMENT ON COLUMN users.email       IS 'Case-insensitive (citext). Unique globally.';

-- ---------------------------------------------------------------------
-- tenant_users (membership)
-- ---------------------------------------------------------------------
CREATE TABLE tenant_users (
    id                  UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID           NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id             UUID           NOT NULL REFERENCES users(id)   ON DELETE CASCADE,
    role                VARCHAR(32)    NOT NULL,
    status              VARCHAR(16)    NOT NULL DEFAULT 'ACTIVE',
    invited_by_user_id  UUID           REFERENCES users(id),
    joined_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version             BIGINT         NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT tenant_users_uniq        UNIQUE (tenant_id, user_id),
    CONSTRAINT tenant_users_role_chk    CHECK (role IN (
        'TENANT_OWNER', 'TENANT_ADMIN', 'TENANT_MODERATOR', 'TENANT_PRESENTER', 'TENANT_ANALYST'
    )),
    CONSTRAINT tenant_users_status_chk  CHECK (status IN ('ACTIVE', 'INVITED', 'SUSPENDED', 'REMOVED'))
);

CREATE INDEX tenant_users_tenant_idx ON tenant_users (tenant_id);
CREATE INDEX tenant_users_user_idx   ON tenant_users (user_id);

COMMENT ON TABLE tenant_users IS 'Many-to-many between users and tenants, carrying role + status.';

-- ---------------------------------------------------------------------
-- plans (billing catalog)
-- ---------------------------------------------------------------------
-- Webizon is pay-per-use: plans describe the UNIT RATES and any caps.
-- Actual charges come from the usage meter (see V002).
-- ---------------------------------------------------------------------
CREATE TABLE plans (
    id                          UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    code                        VARCHAR(32)    NOT NULL UNIQUE,
    display_name                VARCHAR(128)   NOT NULL,
    description                 TEXT,
    currency                    CHAR(3)        NOT NULL DEFAULT 'KZT',
    -- Rates are stored in minor units (tiyn for KZT). Use BIGINT to avoid float drift.
    live_seat_rate_minor        BIGINT         NOT NULL,
    auto_seat_rate_minor        BIGINT         NOT NULL,
    storage_gb_month_rate_minor BIGINT         NOT NULL,
    payment_fee_bps             INTEGER        NOT NULL DEFAULT 300,  -- basis points, 300 = 3.00%
    vat_bps                     INTEGER        NOT NULL DEFAULT 1200, -- 12% Kazakhstan VAT
    trial_days                  INTEGER        NOT NULL DEFAULT 14,
    is_public                   BOOLEAN        NOT NULL DEFAULT TRUE,
    version                     BIGINT         NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT plans_currency_chk CHECK (currency IN ('KZT', 'USD', 'EUR', 'RUB')),
    CONSTRAINT plans_rate_chk     CHECK (
        live_seat_rate_minor        >= 0 AND
        auto_seat_rate_minor        >= 0 AND
        storage_gb_month_rate_minor >= 0 AND
        payment_fee_bps             BETWEEN 0 AND 10000 AND
        vat_bps                     BETWEEN 0 AND 10000
    )
);

COMMENT ON TABLE  plans                           IS 'Pay-per-use rate cards. Actual usage is metered per event.';
COMMENT ON COLUMN plans.live_seat_rate_minor      IS 'Tiyn per live viewer-seat (15 KZT = 1500).';
COMMENT ON COLUMN plans.auto_seat_rate_minor      IS 'Tiyn per auto-replay viewer-seat.';
COMMENT ON COLUMN plans.storage_gb_month_rate_minor IS 'Tiyn per GB of recording storage per month.';

-- Seed the default KZT plan that mirrors the pricing in CLAUDE.md.
INSERT INTO plans (
    code, display_name, description, currency,
    live_seat_rate_minor, auto_seat_rate_minor, storage_gb_month_rate_minor,
    payment_fee_bps, vat_bps, trial_days
) VALUES (
    'webizon-kz-default',
    'Webizon — базовый (KZT)',
    'Pay-per-use. 15 ₸ за живого зрителя, 10 ₸ за auto-replay, 200 ₸/ГБ/мес хранения. 14 дней бесплатно.',
    'KZT',
    1500,    -- 15.00 KZT
    1000,    -- 10.00 KZT
    20000,   -- 200.00 KZT
    300,     -- 3%
    1200,    -- 12% VAT
    14
);

-- ---------------------------------------------------------------------
-- subscriptions
-- ---------------------------------------------------------------------
CREATE TABLE subscriptions (
    id                  UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID           NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    plan_id             UUID           NOT NULL REFERENCES plans(id),
    status              VARCHAR(32)    NOT NULL DEFAULT 'TRIAL',
    started_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    current_period_start TIMESTAMPTZ   NOT NULL DEFAULT now(),
    current_period_end   TIMESTAMPTZ   NOT NULL,
    trial_ends_at       TIMESTAMPTZ,
    cancelled_at        TIMESTAMPTZ,
    cancellation_reason VARCHAR(255),
    external_provider   VARCHAR(32),
    external_id         VARCHAR(128),
    version             BIGINT         NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT subscriptions_status_chk CHECK (
        status IN ('TRIAL', 'ACTIVE', 'PAST_DUE', 'SUSPENDED', 'CANCELLED', 'EXPIRED')
    ),
    CONSTRAINT subscriptions_period_chk CHECK (current_period_end > current_period_start)
);

-- Only one active subscription per tenant.
CREATE UNIQUE INDEX subscriptions_active_uniq
    ON subscriptions (tenant_id)
    WHERE status IN ('TRIAL', 'ACTIVE', 'PAST_DUE');

CREATE INDEX subscriptions_tenant_idx ON subscriptions (tenant_id);
CREATE INDEX subscriptions_period_idx ON subscriptions (current_period_end);

COMMENT ON TABLE subscriptions IS 'One active subscription per tenant. Usage is metered separately.';

-- ---------------------------------------------------------------------
-- Row Level Security
-- ---------------------------------------------------------------------
-- Layer 3 of the multi-tenancy defense (the other two are the JPA
-- TenantEntityListener and the Hibernate @Filter). Application code
-- MUST set `app.current_tenant` on every transaction:
--
--     SET LOCAL app.current_tenant = '<tenant uuid>';
--
-- If the setting is missing, RLS denies all tenant-scoped queries.
-- ---------------------------------------------------------------------

-- tenant_users
ALTER TABLE tenant_users ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_users FORCE  ROW LEVEL SECURITY;

CREATE POLICY tenant_users_isolation ON tenant_users
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- subscriptions
ALTER TABLE subscriptions ENABLE ROW LEVEL SECURITY;
ALTER TABLE subscriptions FORCE  ROW LEVEL SECURITY;

CREATE POLICY subscriptions_isolation ON subscriptions
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- tenants and users are GLOBAL tables; they do not use RLS by tenant.
-- Access control is enforced by role in the application layer.

-- ---------------------------------------------------------------------
-- updated_at triggers
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tenants_updated_at        BEFORE UPDATE ON tenants        FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER users_updated_at          BEFORE UPDATE ON users          FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER tenant_users_updated_at   BEFORE UPDATE ON tenant_users   FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER plans_updated_at          BEFORE UPDATE ON plans          FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER subscriptions_updated_at  BEFORE UPDATE ON subscriptions  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
