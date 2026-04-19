-- =====================================================================
-- V007 :: File Assets (MinIO-backed object storage metadata)
-- ---------------------------------------------------------------------
-- Scope:
--   One row per physical object stored in MinIO/S3. The row is the
--   tenant-scoped, queryable, auditable handle on a binary blob —
--   the application never touches MinIO object keys directly, it
--   always resolves a file_assets row first and then trusts the
--   row's bucket + object_key to build a presigned URL.
--
-- Why a dedicated table (and not just a URL column on events/ctas)?
--   1. Two-step upload. Direct browser-to-MinIO uploads are the only
--      way a single backend node can sustain 50k concurrent viewers
--      uploading avatars / materials without the backend becoming the
--      bottleneck. The client requests a presigned PUT URL, uploads
--      directly to MinIO, then calls back to "confirm" the upload.
--      Between those two calls we need a row to hold the pending
--      object_key, size cap, content-type, and owner. A URL column
--      on events cannot represent the "PENDING" state.
--
--   2. Lifecycle & garbage collection. Orphaned uploads (user starts
--      an upload, never confirms it) need to be found and swept. A
--      background job wakes up, selects PENDING rows older than N
--      minutes, deletes the underlying object, and marks the row
--      DELETED. That scan is impossible without a dedicated table.
--
--   3. Tenant billing. We bill per GB-month of storage. The row
--      carries size_bytes AT confirm time, which is authoritative
--      (we pull stat from MinIO after upload rather than trusting
--      the client). A URL column cannot be billed.
--
--   4. Multi-purpose in one place. Covers, CTA materials, recordings,
--      and tenant invoices all live in separate buckets but share the
--      same lifecycle state machine (PENDING -> UPLOADED -> DELETED),
--      the same soft-delete semantics, and the same presign flow.
--      Collapsing them into one table lets StorageService handle all
--      four with a single code path.
--
-- Purpose enumeration:
--   COVER       -- event cover image, displayed on landing pages and
--                 session thumbnails. Goes in webizon-covers bucket.
--                 Small (< 5 MB), public-ish (presigned GET with long TTL).
--   CTA_FILE    -- downloadable material attached to a FILE-type CTA
--                 (PDF checklist, worksheet, bonus). Goes in
--                 webizon-cta-files bucket. Access presigned per
--                 click so we can rate-limit and track downloads.
--   RECORDING   -- post-event MP4 recording of a LIVE session, used
--                 as the video source for AUTO replays. Goes in
--                 webizon-recordings bucket. Large (hundreds of MB
--                 to GBs), accessed via presigned HLS/MP4 URL.
--   INVOICE     -- tenant billing PDF, generated server-side. Goes in
--                 webizon-invoices bucket. Access restricted to
--                 TENANT_OWNER/TENANT_ADMIN roles.
--
-- State machine:
--   PENDING   -- row created, presigned PUT URL handed out, client
--                has not confirmed upload completion yet. Eligible
--                for garbage collection after pending_expires_at.
--   UPLOADED  -- client called confirm(), backend stat'd the object,
--                size_bytes and checksum are authoritative. This is
--                the only state in which other tables may link to
--                the asset via linked_*.
--   DELETED   -- soft-deleted. The underlying MinIO object is either
--                already gone or pending deletion by the sweeper.
--                Linked rows must have been nulled out before the
--                transition to DELETED.
--
-- Linkage strategy:
--   Rather than having events.cover_asset_id / event_ctas.file_asset_id
--   FK into this table, file_assets carries OPTIONAL reverse links
--   (linked_event_id, linked_cta_id). Rationale: the forward FK would
--   make cover swaps messy (drop old asset, INSERT new, UPDATE event
--   in one tx) and would leak a physical-storage concern into the
--   domain tables. Reverse links let StorageService own the
--   association and the sweeper can find orphaned assets by scanning
--   for "linked to nothing AND created > N hours ago".
--
--   A row may have both linked_event_id AND linked_cta_id set (a file
--   CTA is also linked to its event), or only one, or neither (invoice,
--   or freshly-uploaded asset awaiting association).
-- =====================================================================

-- ---------------------------------------------------------------------
-- file_assets
-- ---------------------------------------------------------------------
CREATE TABLE file_assets (
    id                  UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID           NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    purpose             VARCHAR(16)    NOT NULL,
    state               VARCHAR(16)    NOT NULL DEFAULT 'PENDING',

    -- Physical storage coordinates.
    bucket              VARCHAR(64)    NOT NULL,
    object_key          VARCHAR(512)   NOT NULL,
    content_type        VARCHAR(128)   NOT NULL,

    -- Authoritative AFTER confirm(). NULL while PENDING because we
    -- have not stat'd the object yet. size_bytes is used by the
    -- billing service for GB-month calculations.
    size_bytes          BIGINT,
    checksum_sha256     VARCHAR(64),

    -- Max size we're willing to accept at confirm-time. Set at
    -- createUploadSlot() based on purpose (e.g. 5 MB for COVER,
    -- 2 GB for RECORDING). If the stat'd size exceeds this cap the
    -- confirm is rejected, the object is deleted, and the row is
    -- marked DELETED. Prevents a client from reserving a tiny cover
    -- slot and uploading a 10 GB blob.
    max_size_bytes      BIGINT         NOT NULL,

    -- GC horizon for PENDING rows. After this instant the sweeper
    -- treats the slot as abandoned and removes it.
    pending_expires_at  TIMESTAMPTZ,

    -- Reverse links (see comment block above).
    linked_event_id     UUID           REFERENCES events(id)     ON DELETE SET NULL,
    linked_cta_id       UUID           REFERENCES event_ctas(id) ON DELETE SET NULL,

    -- Who uploaded it — needed for moderator review and audit.
    uploaded_by_user_id UUID           NOT NULL REFERENCES users(id),

    version             BIGINT         NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT file_assets_purpose_chk CHECK (
        purpose IN ('COVER','CTA_FILE','RECORDING','INVOICE')
    ),
    CONSTRAINT file_assets_state_chk CHECK (
        state IN ('PENDING','UPLOADED','DELETED')
    ),
    -- UPLOADED rows MUST have authoritative size & checksum; PENDING
    -- rows MUST NOT (enforces the confirm() semantics at the DB
    -- layer so bugs cannot fabricate false-confirmed rows).
    CONSTRAINT file_assets_uploaded_fields_chk CHECK (
        (state = 'UPLOADED' AND size_bytes IS NOT NULL) OR
        (state <> 'UPLOADED')
    ),
    CONSTRAINT file_assets_pending_expiry_chk CHECK (
        (state = 'PENDING' AND pending_expires_at IS NOT NULL) OR
        (state <> 'PENDING')
    ),
    CONSTRAINT file_assets_max_size_chk CHECK (max_size_bytes > 0),
    CONSTRAINT file_assets_size_chk     CHECK (size_bytes IS NULL OR size_bytes >= 0),
    -- (bucket, object_key) is the physical coordinate; must be
    -- globally unique so two logical rows never point at the same
    -- blob. A single tenant could theoretically re-upload the same
    -- object key, but StorageService generates UUID-prefixed keys
    -- so collisions are structurally impossible.
    CONSTRAINT file_assets_object_uniq  UNIQUE (bucket, object_key)
);

CREATE INDEX file_assets_tenant_purpose_idx  ON file_assets (tenant_id, purpose);
CREATE INDEX file_assets_tenant_state_idx    ON file_assets (tenant_id, state);
CREATE INDEX file_assets_linked_event_idx    ON file_assets (linked_event_id)
    WHERE linked_event_id IS NOT NULL;
CREATE INDEX file_assets_linked_cta_idx      ON file_assets (linked_cta_id)
    WHERE linked_cta_id IS NOT NULL;

-- Garbage-collection index: the sweeper's query is
-- "state = 'PENDING' AND pending_expires_at < now()". A partial
-- index on just pending rows keeps this lookup O(orphans) even as
-- the table grows to millions of UPLOADED rows.
CREATE INDEX file_assets_pending_gc_idx
    ON file_assets (pending_expires_at)
    WHERE state = 'PENDING';

COMMENT ON TABLE  file_assets                     IS 'Metadata for every MinIO-backed object. The authoritative handle for upload lifecycle, tenant billing, and cross-module linking.';
COMMENT ON COLUMN file_assets.purpose             IS 'Which bucket family this asset belongs to: COVER / CTA_FILE / RECORDING / INVOICE.';
COMMENT ON COLUMN file_assets.state               IS 'Upload lifecycle: PENDING (slot reserved, not confirmed) -> UPLOADED (confirmed, stat-verified) -> DELETED (soft-removed).';
COMMENT ON COLUMN file_assets.object_key          IS 'UUID-prefixed path inside the bucket. Globally unique so re-uploads never collide.';
COMMENT ON COLUMN file_assets.max_size_bytes      IS 'Size cap chosen at slot-reservation time (purpose-specific). Confirm() rejects objects that exceed this value.';
COMMENT ON COLUMN file_assets.pending_expires_at  IS 'GC horizon for PENDING slots. Sweeper removes rows whose horizon has passed without a confirm().';
COMMENT ON COLUMN file_assets.linked_event_id     IS 'Reverse link to events(id). Set by StorageService when the asset is bound to an event cover, recording, etc.';
COMMENT ON COLUMN file_assets.linked_cta_id       IS 'Reverse link to event_ctas(id). Set when the asset is bound to a FILE-type CTA as its downloadable material.';

-- ---------------------------------------------------------------------
-- Row Level Security
-- ---------------------------------------------------------------------
ALTER TABLE file_assets ENABLE ROW LEVEL SECURITY;
ALTER TABLE file_assets FORCE  ROW LEVEL SECURITY;

CREATE POLICY file_assets_tenant_isolation ON file_assets
    USING      (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- ---------------------------------------------------------------------
-- updated_at trigger
-- ---------------------------------------------------------------------
CREATE TRIGGER file_assets_updated_at
    BEFORE UPDATE ON file_assets
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
