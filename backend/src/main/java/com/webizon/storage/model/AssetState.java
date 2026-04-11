package com.webizon.storage.model;

/**
 * Upload lifecycle state of a {@link FileAsset}.
 *
 * <p>Transitions form a one-way graph:
 * <pre>
 *     PENDING --confirm()--> UPLOADED --delete()--> DELETED
 *         \                                       ^
 *          \------------ sweeper (GC) -----------/
 * </pre>
 *
 * <p><b>PENDING</b> — A slot has been reserved and the client holds
 * a presigned PUT URL. The underlying object may or may not exist in
 * MinIO yet; the backend has not stat'd it, so {@code size_bytes} and
 * {@code checksum_sha256} are null. Rows in this state are eligible
 * for garbage collection after {@code pending_expires_at}.
 *
 * <p><b>UPLOADED</b> — The client called {@code confirm()}, the
 * backend successfully stat'd the object, verified the size against
 * {@code max_size_bytes}, and persisted authoritative size /
 * checksum. Only rows in this state may be referenced by other
 * tables ({@code linked_event_id}, {@code linked_cta_id}) and only
 * these rows are counted for tenant billing.
 *
 * <p><b>DELETED</b> — Soft-deleted. Either an explicit delete by the
 * owner or an orphan cleanup by the sweeper. The underlying MinIO
 * object has been removed (or is queued for removal); the row is
 * retained for audit.
 */
public enum AssetState {
    PENDING,
    UPLOADED,
    DELETED
}
