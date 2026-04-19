-- =====================================================================
-- V009 :: Notification Outbox
-- ---------------------------------------------------------------------
-- Scope:
--   One row per pending-or-delivered user notification. The outbox is
--   the durable tail-end of every notification-emitting business
--   operation in the system: an invoice is issued → one outbox row;
--   a session is about to start → one outbox row per registered
--   viewer. A background dispatcher drains rows whose
--   next_attempt_at <= now(), sends them through the configured
--   channel (EMAIL today, SMS/PUSH later), and marks them SENT or
--   bumps the retry counter.
--
-- Why the outbox pattern (and not direct SMTP in the calling code)?
--   1. Transactional consistency. A notification must be sent if and
--      only if the business operation committed. Direct SMTP from
--      inside a service call either (a) fires before the tx commits
--      and leaks a notification for a rolled-back operation, or (b)
--      fires after the tx commits and risks being lost if the process
--      dies between commit and send. Inserting an outbox row inside
--      the same tx and draining it asynchronously eliminates both
--      failure modes — the insert rides on the same commit as the
--      business change, and the drain is idempotent.
--
--   2. Retry & backoff. SMTP is flaky: the remote MX can temporarily
--      reject, grey-list, or 4xx a message. An outbox row carries
--      attempt_count and next_attempt_at so the dispatcher can
--      exponentially back off (1m → 5m → 30m → 2h → dead) without
--      losing the business intent.
--
--   3. Auditability & replay. Every notification that was ever
--      attempted is a row, queryable by tenant/kind/status. An admin
--      can inspect why a given tenant did not receive an invoice
--      email and replay the row by flipping its status back to
--      PENDING. A fire-and-forget SMTP call leaves no trace.
--
--   4. Idempotency across retries. The (tenant_id, idempotency_key)
--      unique constraint lets every emitting site compute a stable
--      key (e.g. "invoice-issued:{invoiceId}") and safely re-enqueue
--      on retry without duplicating the user-visible email. The
--      first enqueue wins; subsequent attempts are swallowed by the
--      unique constraint and treated as no-ops.
--
-- Kind enumeration:
--   INVOICE_ISSUED  -- billing: a DRAFT invoice transitioned to
--                      ISSUED. Sent to TENANT_OWNER email with the
--                      invoice number, total, and a link to the PDF.
--   INVOICE_PAID    -- billing: an ISSUED invoice transitioned to
--                      PAID. Confirmation receipt to TENANT_OWNER.
--   EVENT_REMINDER  -- events: a scheduled session starts in N
--                      minutes. Sent to every registered viewer.
--                      (Phase 13+ wires the sender; the kind is
--                      reserved here so V009 is the only schema
--                      migration notifications need.)
--   SESSION_STARTING-- events: a LIVE or AUTO session just
--                      transitioned to LIVE. Higher-urgency variant
--                      of EVENT_REMINDER with a "join now" CTA.
--   WELCOME         -- tenancy: new user accepted an invite. Sent to
--                      the new member's personal email.
--
-- Channel enumeration:
--   EMAIL   -- Spring Mail → SMTP. The only channel implemented in
--              Phase 12.
--   SMS     -- reserved. A future SmsSender bean will be resolved
--              off this value.
--   PUSH    -- reserved. Mobile push via a future provider.
--
-- Status lifecycle:
--   PENDING  -- freshly enqueued or ready for retry after a backoff
--               window. Dispatcher picks these up.
--   SENDING  -- dispatcher has claimed this row for the current
--               tick. Transient; if the process dies mid-send the
--               next tick picks it back up by treating it as PENDING
--               (we do not lock at the DB level — exactly-once is
--               a fantasy over SMTP, at-least-once is the achievable
--               target).
--   SENT     -- terminal success. sent_at carries the wall clock.
--   FAILED   -- transient failure, retry scheduled. next_attempt_at
--               carries the back-off deadline and attempt_count is
--               incremented. Remains in FAILED until the dispatcher
--               picks it up again and flips it to SENDING.
--   DEAD     -- terminal failure after max_attempts. Requires manual
--               admin intervention to replay.
-- =====================================================================

-- ---------------------------------------------------------------------
-- notification_outbox
-- ---------------------------------------------------------------------
CREATE TABLE notification_outbox (
    id                  UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID           NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,

    -- Who the notification is for. Nullable because system-origin
    -- notifications (tenant-wide announcements, owner-only billing
    -- mail) target a raw email address rather than a profile.
    profile_id          UUID           REFERENCES users(id) ON DELETE SET NULL,

    kind                VARCHAR(32)    NOT NULL,
    channel             VARCHAR(16)    NOT NULL DEFAULT 'EMAIL',
    status              VARCHAR(16)    NOT NULL DEFAULT 'PENDING',

    -- Delivery target. Captured AT enqueue time so a later profile
    -- email change cannot silently misroute a retried notification.
    to_address          VARCHAR(320)   NOT NULL,
    subject             VARCHAR(255)   NOT NULL,
    body_text           TEXT           NOT NULL,
    body_html           TEXT,

    -- Free-form payload the sender can attach (the invoice number,
    -- the event slug, etc.). Not used by the dispatcher — intended
    -- for future template engines and for the admin UI to render
    -- richer outbox rows without joining back to the source table.
    payload_json        JSONB          NOT NULL DEFAULT '{}'::jsonb,

    -- Idempotency key chosen by the caller. Format convention:
    --   "<kind-slug>:<business-id>[:<channel>]"
    -- The first enqueue wins; duplicates on the same key are
    -- treated as no-ops. NULL is allowed for pure-ad-hoc sends
    -- (admin debug console) but is discouraged in production code.
    idempotency_key     VARCHAR(128),

    -- Retry state.
    attempt_count       INTEGER        NOT NULL DEFAULT 0,
    max_attempts        INTEGER        NOT NULL DEFAULT 5,
    next_attempt_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    last_error          TEXT,

    sent_at             TIMESTAMPTZ,

    version             BIGINT         NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT notification_outbox_kind_chk CHECK (
        kind IN ('INVOICE_ISSUED','INVOICE_PAID','EVENT_REMINDER','SESSION_STARTING','WELCOME')
    ),
    CONSTRAINT notification_outbox_channel_chk CHECK (
        channel IN ('EMAIL','SMS','PUSH')
    ),
    CONSTRAINT notification_outbox_status_chk CHECK (
        status IN ('PENDING','SENDING','SENT','FAILED','DEAD')
    ),
    CONSTRAINT notification_outbox_attempt_chk CHECK (
        attempt_count >= 0 AND max_attempts > 0 AND attempt_count <= max_attempts
    ),
    -- SENT rows must carry sent_at; every other status must not.
    CONSTRAINT notification_outbox_sent_at_chk CHECK (
        (status = 'SENT' AND sent_at IS NOT NULL) OR
        (status <> 'SENT' AND sent_at IS NULL)
    )
);

-- ---------------------------------------------------------------------
-- Indexes
-- ---------------------------------------------------------------------

-- The dispatcher's hot query is "give me every PENDING/FAILED row
-- whose next_attempt_at has passed, ordered by next_attempt_at asc,
-- limited to a tick batch". A partial index on just the dispatchable
-- states keeps this O(due) even as SENT/DEAD rows accumulate.
CREATE INDEX notification_outbox_dispatch_idx
    ON notification_outbox (next_attempt_at, tenant_id)
    WHERE status IN ('PENDING','FAILED');

-- Admin outbox UI lists by tenant, ordered by most-recent-first.
CREATE INDEX notification_outbox_tenant_created_idx
    ON notification_outbox (tenant_id, created_at DESC);

-- Audit queries: "all invoice notifications for this tenant".
CREATE INDEX notification_outbox_tenant_kind_idx
    ON notification_outbox (tenant_id, kind, created_at DESC);

-- Dead-letter queue lookup.
CREATE INDEX notification_outbox_dead_idx
    ON notification_outbox (tenant_id, updated_at DESC)
    WHERE status = 'DEAD';

-- Idempotency guard. Partial because NULL keys are legitimately
-- non-deduplicated (admin ad-hoc sends).
CREATE UNIQUE INDEX notification_outbox_idempotency_idx
    ON notification_outbox (tenant_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

COMMENT ON TABLE  notification_outbox                 IS 'Durable per-tenant notification queue. Inserted inside the business transaction, drained by NotificationDispatcher.';
COMMENT ON COLUMN notification_outbox.kind            IS 'Business reason for this notification. See class-level javadoc on NotificationKind for the authoritative list.';
COMMENT ON COLUMN notification_outbox.status          IS 'Lifecycle: PENDING -> SENDING -> (SENT | FAILED -> PENDING retry | DEAD).';
COMMENT ON COLUMN notification_outbox.to_address      IS 'Delivery target frozen at enqueue time. A later profile email change does not retroactively re-route pending notifications.';
COMMENT ON COLUMN notification_outbox.idempotency_key IS 'Caller-chosen dedupe key. Format convention: "<kind-slug>:<business-id>[:<channel>]".';
COMMENT ON COLUMN notification_outbox.next_attempt_at IS 'Dispatcher wakes when now() >= next_attempt_at. Bumped on FAILED to enforce exponential back-off.';
COMMENT ON COLUMN notification_outbox.max_attempts    IS 'Hard cap — once attempt_count reaches this, the row is marked DEAD and only manual intervention can replay it.';

-- ---------------------------------------------------------------------
-- Row Level Security
-- ---------------------------------------------------------------------
ALTER TABLE notification_outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification_outbox FORCE  ROW LEVEL SECURITY;

CREATE POLICY notification_outbox_tenant_isolation ON notification_outbox
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- ---------------------------------------------------------------------
-- updated_at trigger
-- ---------------------------------------------------------------------
CREATE TRIGGER notification_outbox_updated_at
    BEFORE UPDATE ON notification_outbox
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
