-- =====================================================================
-- V008 :: Billing — usage records & invoices
-- ---------------------------------------------------------------------
-- Scope:
--   1. sessions.billing_metered_at    -- "we already billed this" flag
--      that makes the usage sweeper idempotent even after backend
--      restarts and cross-deploy races.
--   2. billing_usage_records          -- one row per finalized session
--      per usage kind, holding the aggregated seat-seconds and the
--      snapshotted rate at close time.
--   3. billing_invoices               -- one row per tenant per
--      billing period (typically one calendar month), with status
--      machine and a totals ledger.
--   4. billing_invoice_lines          -- ordered line items under an
--      invoice, each pointing at the raw usage kind that produced it
--      so the tenant dashboard can drill down from total to sessions.
--
-- Why a new table (and not "just charge the rate card at query time")?
--   Rate cards change. A tenant who consumed 10,000 seat-minutes of
--   LIVE traffic in January at 15 KZT/min must still be billed 15
--   KZT/min in February's invoice even if we raised the rate to 18
--   on February 1. The usage record snapshots the rate that applied
--   at meter time so re-running the invoice build on any future date
--   produces the same number.
--
-- Money representation:
--   Every monetary column is BIGINT "KZT * 1000" (i.e. millicents).
--   PostgreSQL NUMERIC would work too but is slower for aggregation
--   and harder to index; for a currency where the smallest real unit
--   is 1 KZT, BIGINT millis give us four decimal digits of headroom
--   while keeping SUM(...) cheap. VAT math is done in millis and
--   only converted to human KZT at the presentation layer.
--
-- Why aggregate per session (not per profile)?
--   A session with 50,000 concurrent viewers would produce 50,000
--   per-profile billing rows per run. Per-session aggregation (one
--   row holding the summed seat_seconds across all profiles) keeps
--   the billing table two orders of magnitude smaller and leaves
--   the per-profile breakdown in session_attendance where it
--   belongs. Audit still works because the sum is derivable from
--   SessionAttendance at any time until the attendance row ages out.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. sessions.billing_metered_at
-- ---------------------------------------------------------------------
-- Null until the usage sweeper processes the finalized session and
-- inserts its billing_usage_records. A partial index lets the sweeper
-- find un-metered finalized rows in O(unbilled) instead of scanning
-- the whole sessions table.
ALTER TABLE sessions
    ADD COLUMN billing_metered_at TIMESTAMPTZ;

COMMENT ON COLUMN sessions.billing_metered_at IS
    'Non-null once the billing usage sweeper has converted this session''s attendance into billing_usage_records. Used as the idempotency marker so double-ticks never double-bill.';

CREATE INDEX sessions_billing_unmetered_idx
    ON sessions (finalized_at)
    WHERE finalized_at IS NOT NULL
      AND billing_metered_at IS NULL;

-- ---------------------------------------------------------------------
-- 2. billing_usage_records
-- ---------------------------------------------------------------------
CREATE TABLE billing_usage_records (
    id                     UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id              UUID         NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,

    -- The source session. Nullable because future storage-usage rows
    -- (GB-month accrual) will not be tied to a session. Sessions that
    -- are deleted cascade through this column too — if a tenant is
    -- cascaded-deleted the usage goes with it, which is what we want
    -- because the parent tenants row already lost all its data.
    session_id             UUID         REFERENCES sessions(id) ON DELETE CASCADE,
    event_id               UUID         REFERENCES events(id)   ON DELETE SET NULL,

    kind                   VARCHAR(24)  NOT NULL,

    -- Quantity, in the natural unit of the kind. For SEAT_LIVE /
    -- SEAT_AUTO this is seat-seconds. For STORAGE_GB_MONTH it is
    -- gigabyte-seconds (converted to GB-months at invoice time).
    -- BIGINT because a single 50k-viewer session can easily push
    -- 180,000,000 seat-seconds.
    quantity               BIGINT       NOT NULL,

    -- Rate the sweeper observed at meter time, snapshotted as
    -- "KZT-millis per minute of seat time" so the invoice builder
    -- never has to go back to BillingProperties to reconstruct it.
    -- Rate semantics depend on kind:
    --   SEAT_LIVE / SEAT_AUTO    -> KZT-millis per seat-minute
    --   STORAGE_GB_MONTH         -> KZT-millis per GB-month
    rate_millis_per_unit   BIGINT       NOT NULL,

    -- amount_millis is precomputed at meter time from quantity and
    -- the rate. Storing it explicitly gives the invoice builder a
    -- single column to SUM() without doing cross-unit math per row.
    amount_millis          BIGINT       NOT NULL,

    -- When the sweeper wrote this row. Not the same as created_at
    -- (which is JPA audit) because the sweeper batches rows and we
    -- want the business timestamp to be the session end, not the
    -- sweep tick.
    billed_for_instant     TIMESTAMPTZ  NOT NULL,

    -- One invoice may later claim this record. The FK is nullable
    -- because usage rows exist before any invoice does, and a
    -- current-period usage query walks records with invoice_id IS NULL.
    invoice_id             UUID,

    version                BIGINT       NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT billing_usage_kind_chk CHECK (
        kind IN ('SEAT_LIVE','SEAT_AUTO','STORAGE_GB_MONTH')
    ),
    CONSTRAINT billing_usage_quantity_chk CHECK (quantity >= 0),
    CONSTRAINT billing_usage_rate_chk     CHECK (rate_millis_per_unit >= 0),
    CONSTRAINT billing_usage_amount_chk   CHECK (amount_millis >= 0),

    -- Idempotency: at most one (session, kind) usage row per tenant.
    -- The sweeper re-running is safe because the unique constraint
    -- will reject a duplicate insert; the sweeper catches the
    -- integrity violation and moves on.
    CONSTRAINT billing_usage_session_kind_uniq UNIQUE (tenant_id, session_id, kind)
);

CREATE INDEX billing_usage_tenant_instant_idx ON billing_usage_records (tenant_id, billed_for_instant);
CREATE INDEX billing_usage_tenant_open_idx    ON billing_usage_records (tenant_id)
    WHERE invoice_id IS NULL;
CREATE INDEX billing_usage_invoice_idx        ON billing_usage_records (invoice_id)
    WHERE invoice_id IS NOT NULL;

COMMENT ON TABLE  billing_usage_records                  IS 'Append-only ledger of metered usage. One row per (session, kind), snapshotted with the rate that applied at meter time.';
COMMENT ON COLUMN billing_usage_records.kind             IS 'SEAT_LIVE / SEAT_AUTO = seat-minutes of a LIVE or AUTO session. STORAGE_GB_MONTH = GB-months of file_assets held for the tenant.';
COMMENT ON COLUMN billing_usage_records.quantity         IS 'Natural unit: seat-seconds for SEAT_*, GB-seconds for STORAGE_*.';
COMMENT ON COLUMN billing_usage_records.rate_millis_per_unit IS 'Rate snapshotted at meter time. KZT-millis per (minute for SEAT_*, GB-month for STORAGE_*).';
COMMENT ON COLUMN billing_usage_records.amount_millis    IS 'Precomputed line amount in KZT-millis. Summed directly by the invoice builder.';
COMMENT ON COLUMN billing_usage_records.invoice_id       IS 'Set when the invoice builder claims this record. Rows with NULL are "open" — counted in current-period estimates.';

-- ---------------------------------------------------------------------
-- 3. billing_invoices
-- ---------------------------------------------------------------------
CREATE TABLE billing_invoices (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID          NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,

    -- Human-readable sequential number within the tenant, assigned at
    -- issue time. Draft invoices do not have a number (the sequence
    -- must only advance when the invoice is actually issued to the
    -- customer, otherwise voided drafts would leave gaps).
    invoice_number      VARCHAR(32),

    period_start        TIMESTAMPTZ   NOT NULL,
    period_end          TIMESTAMPTZ   NOT NULL,

    status              VARCHAR(16)   NOT NULL DEFAULT 'DRAFT',
    currency            CHAR(3)       NOT NULL DEFAULT 'KZT',

    subtotal_millis     BIGINT        NOT NULL DEFAULT 0,
    vat_millis          BIGINT        NOT NULL DEFAULT 0,
    total_millis        BIGINT        NOT NULL DEFAULT 0,
    vat_percent_snapshot NUMERIC(5,2) NOT NULL,

    -- Optional link to a PDF rendered by the billing module after
    -- issue. Nullable while the invoice is still DRAFT.
    pdf_asset_id        UUID          REFERENCES file_assets(id) ON DELETE SET NULL,

    issued_at           TIMESTAMPTZ,
    paid_at             TIMESTAMPTZ,
    voided_at           TIMESTAMPTZ,

    version             BIGINT        NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT billing_invoices_status_chk CHECK (
        status IN ('DRAFT','ISSUED','PAID','OVERDUE','VOID')
    ),
    CONSTRAINT billing_invoices_period_chk CHECK (period_start < period_end),
    CONSTRAINT billing_invoices_subtotal_chk CHECK (subtotal_millis >= 0),
    CONSTRAINT billing_invoices_vat_chk      CHECK (vat_millis >= 0),
    CONSTRAINT billing_invoices_total_chk    CHECK (total_millis >= 0),
    -- Issued / Paid invoices MUST carry a number. DRAFT and VOID may
    -- or may not — a voided draft never had one, a voided issued
    -- invoice keeps the number for audit.
    CONSTRAINT billing_invoices_issued_number_chk CHECK (
        status IN ('DRAFT') OR invoice_number IS NOT NULL
    ),
    -- A tenant cannot have two invoices covering exactly the same
    -- period. Prevents accidental double-close on a cron retry.
    CONSTRAINT billing_invoices_period_uniq UNIQUE (tenant_id, period_start, period_end)
);

-- Tenant-scoped invoice number uniqueness (only when number present).
CREATE UNIQUE INDEX billing_invoices_number_uniq
    ON billing_invoices (tenant_id, invoice_number)
    WHERE invoice_number IS NOT NULL;

CREATE INDEX billing_invoices_tenant_status_idx ON billing_invoices (tenant_id, status);
CREATE INDEX billing_invoices_tenant_period_idx ON billing_invoices (tenant_id, period_start DESC);

COMMENT ON TABLE  billing_invoices              IS 'One invoice per tenant per billing period. Status machine: DRAFT -> ISSUED -> PAID/OVERDUE/VOID.';
COMMENT ON COLUMN billing_invoices.invoice_number IS 'Assigned at issue time from a per-tenant sequence. DRAFT invoices have NULL so voided drafts do not create gaps.';
COMMENT ON COLUMN billing_invoices.vat_percent_snapshot IS 'VAT rate that applied at issue time, snapshotted so historical re-renders match the original number.';
COMMENT ON COLUMN billing_invoices.pdf_asset_id IS 'Link to the file_assets row holding the generated PDF. NULL while DRAFT.';

-- ---------------------------------------------------------------------
-- 4. billing_invoice_lines
-- ---------------------------------------------------------------------
CREATE TABLE billing_invoice_lines (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID         NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,

    invoice_id           UUID         NOT NULL REFERENCES billing_invoices(id) ON DELETE CASCADE,

    line_number          INTEGER      NOT NULL,
    kind                 VARCHAR(24)  NOT NULL,
    description          VARCHAR(255) NOT NULL,

    quantity             BIGINT       NOT NULL,
    unit_label           VARCHAR(32)  NOT NULL,
    unit_rate_millis     BIGINT       NOT NULL,
    amount_millis        BIGINT       NOT NULL,

    version              BIGINT       NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT billing_invoice_lines_kind_chk CHECK (
        kind IN ('SEAT_LIVE','SEAT_AUTO','STORAGE_GB_MONTH','VAT','DISCOUNT','ADJUSTMENT')
    ),
    CONSTRAINT billing_invoice_lines_qty_chk  CHECK (quantity >= 0),
    CONSTRAINT billing_invoice_lines_rate_chk CHECK (unit_rate_millis >= 0),
    CONSTRAINT billing_invoice_lines_line_no_chk CHECK (line_number >= 1),
    CONSTRAINT billing_invoice_lines_no_uniq  UNIQUE (invoice_id, line_number)
);

CREATE INDEX billing_invoice_lines_invoice_idx ON billing_invoice_lines (invoice_id, line_number);

COMMENT ON TABLE  billing_invoice_lines         IS 'Itemised rows of a billing_invoice. One per kind (seat-live, seat-auto, storage, VAT).';
COMMENT ON COLUMN billing_invoice_lines.kind    IS 'Usage kind, plus VAT / DISCOUNT / ADJUSTMENT synthetic kinds that only appear on invoices (never in usage records).';
COMMENT ON COLUMN billing_invoice_lines.unit_label IS 'Display label: "seat-minutes", "GB-months", etc. Frozen at invoice time for historical stability.';

-- ---------------------------------------------------------------------
-- Foreign key from usage → invoice (delayed so invoices table exists)
-- ---------------------------------------------------------------------
ALTER TABLE billing_usage_records
    ADD CONSTRAINT billing_usage_invoice_fk
    FOREIGN KEY (invoice_id) REFERENCES billing_invoices(id) ON DELETE SET NULL;

-- ---------------------------------------------------------------------
-- Row Level Security
-- ---------------------------------------------------------------------
ALTER TABLE billing_usage_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE billing_usage_records FORCE  ROW LEVEL SECURITY;
ALTER TABLE billing_invoices      ENABLE ROW LEVEL SECURITY;
ALTER TABLE billing_invoices      FORCE  ROW LEVEL SECURITY;
ALTER TABLE billing_invoice_lines ENABLE ROW LEVEL SECURITY;
ALTER TABLE billing_invoice_lines FORCE  ROW LEVEL SECURITY;

CREATE POLICY billing_usage_records_tenant_isolation ON billing_usage_records
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

CREATE POLICY billing_invoices_tenant_isolation ON billing_invoices
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

CREATE POLICY billing_invoice_lines_tenant_isolation ON billing_invoice_lines
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- ---------------------------------------------------------------------
-- updated_at triggers
-- ---------------------------------------------------------------------
CREATE TRIGGER billing_usage_records_updated_at
    BEFORE UPDATE ON billing_usage_records
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER billing_invoices_updated_at
    BEFORE UPDATE ON billing_invoices
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER billing_invoice_lines_updated_at
    BEFORE UPDATE ON billing_invoice_lines
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
