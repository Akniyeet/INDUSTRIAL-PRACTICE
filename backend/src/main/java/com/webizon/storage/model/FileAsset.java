package com.webizon.storage.model;

import com.webizon.tenancy.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata handle on a single MinIO-backed object.
 *
 * <p>See {@code V007__file_assets.sql} for the rationale behind
 * having a dedicated table rather than scattering URL columns across
 * domain tables. In short: the two-step presigned-upload flow, the
 * orphan sweeper, and tenant-billing by bytes all require
 * authoritative lifecycle state per object, and the only honest place
 * to keep that state is its own table.
 *
 * <p>The row is the single source of truth for "where is the blob?"
 * — {@code bucket} + {@code objectKey} together are the physical
 * coordinates, and the application never synthesises those values
 * elsewhere. When StorageService needs to presign a URL it always
 * loads the row first, trusts nothing else.
 */
@Entity
@Table(name = "file_assets")
@Getter
@Setter
public class FileAsset extends TenantAwareEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 16)
    private AssetPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 16)
    private AssetState state = AssetState.PENDING;

    @Column(name = "bucket", nullable = false, length = 64)
    private String bucket;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(name = "content_type", nullable = false, length = 128)
    private String contentType;

    /** Authoritative byte size after confirm(); null while PENDING. */
    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    /** Cap chosen at slot-reservation time based on purpose. */
    @Column(name = "max_size_bytes", nullable = false)
    private long maxSizeBytes;

    /** GC horizon for PENDING rows; null for UPLOADED / DELETED. */
    @Column(name = "pending_expires_at")
    private Instant pendingExpiresAt;

    /** Reverse link set by StorageService when binding to an event. */
    @Column(name = "linked_event_id", columnDefinition = "UUID")
    private UUID linkedEventId;

    /** Reverse link set by StorageService when binding to a FILE-type CTA. */
    @Column(name = "linked_cta_id", columnDefinition = "UUID")
    private UUID linkedCtaId;

    @Column(name = "uploaded_by_user_id", nullable = false, updatable = false, columnDefinition = "UUID")
    private UUID uploadedByUserId;
}
