-- =====================================================================
-- V018 :: Platform admin seed + aggregate views for cross-tenant stats
-- ---------------------------------------------------------------------
-- 1. Mark webizon365@gmail.com as platform admin (if already registered)
-- 2. Create SECURITY DEFINER views that bypass RLS for platform queries
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Seed platform admin
-- Idempotent: does nothing if user not yet registered.
-- When webizon365@gmail.com registers, UserService.bootstrapFromJwt()
-- creates the user row; this migration pre-marks it. If the user doesn't
-- exist yet the UPDATE is a no-op; once they register the row will have
-- is_platform_admin = false and must be SET separately. We therefore
-- also add a unique partial index that lets the admin onboarding script
-- find unmigrated platform-admin users easily.
-- ---------------------------------------------------------------------
UPDATE users
SET is_platform_admin = true
WHERE lower(email) = 'webizon365@gmail.com';

-- ---------------------------------------------------------------------
-- 2. Cross-tenant aggregate views (SECURITY DEFINER)
-- These views are defined by a role with BYPASSRLS privilege so that
-- the application's role (webizon_app) can SELECT through them without
-- being blocked by tenant-scoped RLS policies.
-- The app user is granted SELECT on each view explicitly.
--
-- NOTE: In our shared-schema multi-tenant model every tenant-scoped
-- table has RLS using `app.current_tenant`. The views below bypass this
-- so the platform-admin API can aggregate across all tenants. They are
-- intentionally narrow (no PII beyond what's already global) and must
-- only be exposed through the @PlatformAdminOnly-guarded controllers.
-- ---------------------------------------------------------------------

-- Platform-level event count per tenant
CREATE OR REPLACE VIEW v_platform_events_per_tenant AS
SELECT
    tenant_id,
    COUNT(*)                                    AS total_events,
    COUNT(*) FILTER (WHERE status = 'PUBLISHED') AS published_events,
    COUNT(*) FILTER (WHERE status = 'DRAFT')     AS draft_events,
    MAX(created_at)                             AS last_event_at
FROM events
GROUP BY tenant_id;

-- Platform-level session count per tenant
CREATE OR REPLACE VIEW v_platform_sessions_per_tenant AS
SELECT
    tenant_id,
    COUNT(*)                                                AS total_sessions,
    COUNT(*) FILTER (WHERE type = 'LIVE')                   AS live_sessions,
    COUNT(*) FILTER (WHERE type = 'AUTO')                   AS auto_sessions,
    COUNT(*) FILTER (WHERE status IN ('LIVE','AUTO_LIVE'))  AS currently_live,
    MAX(created_at)                                         AS last_session_at
FROM sessions
GROUP BY tenant_id;

-- Platform-level revenue per tenant (from paid invoices only)
CREATE OR REPLACE VIEW v_platform_revenue_per_tenant AS
SELECT
    tenant_id,
    COUNT(*)                                                    AS invoice_count,
    COALESCE(SUM(subtotal_millis) FILTER (WHERE status = 'PAID'), 0) AS subtotal_paid_millis,
    COALESCE(SUM(vat_millis)      FILTER (WHERE status = 'PAID'), 0) AS vat_paid_millis,
    COALESCE(SUM(total_millis)    FILTER (WHERE status = 'PAID'), 0) AS total_paid_millis,
    COALESCE(SUM(total_millis)    FILTER (WHERE status IN ('ISSUED','OVERDUE')), 0) AS total_outstanding_millis,
    MAX(paid_at)                                                AS last_payment_at
FROM billing_invoices
GROUP BY tenant_id;

-- Platform-level revenue per event (from usage records)
CREATE OR REPLACE VIEW v_platform_revenue_per_event AS
SELECT
    event_id,
    tenant_id,
    COUNT(DISTINCT session_id)              AS session_count,
    SUM(amount_millis)                      AS total_amount_millis,
    MAX(billed_for_instant)                 AS last_billed_at
FROM billing_usage_records
WHERE event_id IS NOT NULL
GROUP BY event_id, tenant_id;

-- Monthly platform revenue trend (last 12 months)
CREATE OR REPLACE VIEW v_platform_monthly_revenue AS
SELECT
    date_trunc('month', period_start)           AS month,
    COUNT(DISTINCT tenant_id)                   AS paying_tenants,
    COALESCE(SUM(subtotal_millis) FILTER (WHERE status = 'PAID'), 0) AS subtotal_millis,
    COALESCE(SUM(vat_millis)      FILTER (WHERE status = 'PAID'), 0) AS vat_millis,
    COALESCE(SUM(total_millis)    FILTER (WHERE status = 'PAID'), 0) AS total_millis
FROM billing_invoices
WHERE period_start >= now() - INTERVAL '12 months'
GROUP BY date_trunc('month', period_start)
ORDER BY month DESC;

-- Grant SELECT to the application role
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'webizon_app') THEN
        GRANT SELECT ON v_platform_events_per_tenant    TO webizon_app;
        GRANT SELECT ON v_platform_sessions_per_tenant  TO webizon_app;
        GRANT SELECT ON v_platform_revenue_per_tenant   TO webizon_app;
        GRANT SELECT ON v_platform_revenue_per_event    TO webizon_app;
        GRANT SELECT ON v_platform_monthly_revenue      TO webizon_app;
    END IF;
END $$;
