package com.webizon.storage.service;

import com.webizon.storage.config.MinioProperties;
import com.webizon.storage.model.AssetPurpose;
import com.webizon.storage.model.AssetState;
import com.webizon.storage.model.FileAsset;
import com.webizon.storage.repo.FileAssetRepository;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.http.Method;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Central entry point for the two-step upload flow and everything
 * that hangs off it (confirm, presign download, soft-delete).
 *
 * <h2>The two-step upload flow</h2>
 * <ol>
 *   <li>Client calls {@link #createUploadSlot} with a purpose and a
 *       declared content type. The service creates a PENDING
 *       {@link FileAsset} row, mints a presigned PUT URL against the
 *       <em>public</em> endpoint, and returns both to the client.</li>
 *   <li>Client uploads the bytes directly to MinIO using the
 *       presigned URL — the backend never sees the bytes.</li>
 *   <li>Client calls {@link #confirmUpload}. The service stats the
 *       object on the internal endpoint, verifies the reported size
 *       against {@code maxSizeBytes}, writes authoritative
 *       {@code sizeBytes} / {@code contentType} back onto the row,
 *       and flips it to UPLOADED.</li>
 * </ol>
 *
 * <p>The split between the two MinIO clients (see
 * {@link com.webizon.storage.config.MinioClientConfig}) matters here:
 * the <b>public</b> client is used to mint presigned URLs the
 * browser will resolve, the <b>internal</b> client is used for
 * stat / remove operations that should not hairpin through the
 * public load balancer.
 *
 * <h2>Why size caps are enforced at confirm-time</h2>
 * Nothing stops a malicious client from uploading more than the
 * declared size through a presigned PUT — presigned URLs authorise a
 * method + path + auth, not a body length. MinIO itself enforces a
 * global per-request max size, but per-purpose caps (5 MB for a
 * cover, 2 GB for a recording) are a product rule that can only be
 * applied after we stat the object. Confirm() is where that
 * enforcement happens; if the object exceeds the cap we delete it
 * and reject the confirm.
 *
 * <h2>Tenant safety</h2>
 * Every read goes through {@link FileAssetRepository}, so the
 * Hibernate {@code @TenantId} filter and Postgres RLS prevent
 * cross-tenant access by construction. The orphan sweeper is the
 * only caller that pivots tenants; it lives in
 * {@code OrphanedAssetSweeper} and uses {@code TenantContext.runWith}.
 */
@Service
@Transactional
@Slf4j
public class StorageService {

    /**
     * Per-purpose defaults. Kept in one place so changing the cover
     * size cap means touching one line, not hunting through the
     * codebase.
     */
    private static final Map<AssetPurpose, PurposeDefaults> DEFAULTS;
    static {
        DEFAULTS = new EnumMap<>(AssetPurpose.class);
        // Cover images: 5 MB cap, JPEG/PNG/WEBP, 1-hour upload window.
        DEFAULTS.put(AssetPurpose.COVER, new PurposeDefaults(
                5L * 1024 * 1024,
                Set.of("image/jpeg", "image/png", "image/webp"),
                Duration.ofHours(1)));
        // CTA file downloads: 50 MB cap, common document formats,
        // 1-hour upload window.
        DEFAULTS.put(AssetPurpose.CTA_FILE, new PurposeDefaults(
                50L * 1024 * 1024,
                Set.of(
                        "application/pdf",
                        "application/zip",
                        "application/msword",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "application/vnd.ms-excel",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                Duration.ofHours(1)));
        // Recordings: 2 GB cap, MP4 only, 6-hour upload window to
        // accommodate multi-hour post-event uploads on slow links.
        DEFAULTS.put(AssetPurpose.RECORDING, new PurposeDefaults(
                2L * 1024 * 1024 * 1024,
                Set.of("video/mp4"),
                Duration.ofHours(6)));
        // Invoices are server-generated so in practice confirm() is
        // called by the billing module synchronously after
        // putObject, but we still keep a cap for sanity.
        DEFAULTS.put(AssetPurpose.INVOICE, new PurposeDefaults(
                10L * 1024 * 1024,
                Set.of("application/pdf"),
                Duration.ofMinutes(15)));
    }

    /** TTL of presigned download URLs returned to the browser. */
    private static final Duration DOWNLOAD_URL_TTL = Duration.ofMinutes(15);

    private final FileAssetRepository fileAssetRepository;
    private final MinioProperties minioProperties;
    private final MinioClient internalClient;
    private final MinioClient publicClient;

    /**
     * Explicit constructor so we can wire the two distinct
     * {@link MinioClient} beans via {@link Qualifier}. Lombok's
     * {@code @RequiredArgsConstructor} does not copy qualifier
     * annotations to the generated constructor unless configured,
     * so an explicit one is the simpler path.
     */
    public StorageService(
            FileAssetRepository fileAssetRepository,
            MinioProperties minioProperties,
            @Qualifier("internalMinioClient") MinioClient internalClient,
            @Qualifier("publicMinioClient") MinioClient publicClient) {
        this.fileAssetRepository = fileAssetRepository;
        this.minioProperties = minioProperties;
        this.internalClient = internalClient;
        this.publicClient = publicClient;
    }

    // ------------------------------------------------------------------
    // Step 1: slot reservation
    // ------------------------------------------------------------------

    /**
     * Reserve a new PENDING {@link FileAsset} and mint the presigned
     * PUT URL the client uploads to.
     *
     * <p>The object key is a UUID-prefixed path so re-uploads never
     * collide even if two users pick the same filename. The row is
     * created in a state that cannot be accidentally linked to
     * anything (no {@code linkedEventId} / {@code linkedCtaId}); the
     * caller must explicitly bind it after confirm.
     *
     * @param purpose          one of the {@link AssetPurpose} values; determines
     *                         bucket, size cap, allowed content types
     * @param declaredContentType what the client claims it will upload; must
     *                         be in the purpose's allow-list
     * @param uploadedByUserId the authenticated user id from the JWT
     * @return slot descriptor containing the asset id and the
     *         presigned PUT URL
     */
    public UploadSlot createUploadSlot(
            AssetPurpose purpose,
            String declaredContentType,
            UUID uploadedByUserId) {

        PurposeDefaults defaults = DEFAULTS.get(purpose);
        if (defaults == null) {
            throw new IllegalArgumentException("Unsupported asset purpose: " + purpose);
        }
        if (declaredContentType == null || declaredContentType.isBlank()) {
            throw new IllegalArgumentException("declaredContentType is required");
        }
        if (!defaults.allowedContentTypes.contains(declaredContentType)) {
            throw new IllegalArgumentException(
                    "Content type " + declaredContentType + " is not allowed for " + purpose);
        }

        String bucket = bucketFor(purpose);
        String objectKey = generateObjectKey(purpose);

        FileAsset asset = new FileAsset();
        asset.setPurpose(purpose);
        asset.setState(AssetState.PENDING);
        asset.setBucket(bucket);
        asset.setObjectKey(objectKey);
        asset.setContentType(declaredContentType);
        asset.setMaxSizeBytes(defaults.maxSizeBytes);
        asset.setPendingExpiresAt(Instant.now().plus(defaults.uploadWindow));
        asset.setUploadedByUserId(uploadedByUserId);
        asset = fileAssetRepository.save(asset);

        String uploadUrl = presignPutUrl(bucket, objectKey, defaults.uploadWindow);

        return new UploadSlot(
                asset.getId(),
                uploadUrl,
                defaults.maxSizeBytes,
                defaults.uploadWindow.getSeconds(),
                asset.getPendingExpiresAt());
    }

    // ------------------------------------------------------------------
    // Step 2: confirm upload
    // ------------------------------------------------------------------

    /**
     * Transition a PENDING slot to UPLOADED after the client finishes
     * pushing bytes. The service stats the object on the internal
     * endpoint to obtain authoritative size + content type, verifies
     * the cap, and writes them back onto the row.
     *
     * <p>If the stat fails (object missing) or the size exceeds the
     * cap, the row is marked DELETED and the method throws, giving
     * the client a clean error rather than a silent half-committed
     * row.
     *
     * @return the refreshed asset in UPLOADED state
     */
    public FileAsset confirmUpload(UUID assetId) {
        FileAsset asset = requireById(assetId);

        if (asset.getState() != AssetState.PENDING) {
            throw new IllegalStateException(
                    "Cannot confirm asset in state " + asset.getState() + ": " + assetId);
        }
        if (asset.getPendingExpiresAt() != null
                && asset.getPendingExpiresAt().isBefore(Instant.now())) {
            // Hand this off to the sweeper rather than racing it.
            throw new IllegalStateException(
                    "Upload slot expired: " + assetId);
        }

        StatObjectResponse stat;
        try {
            stat = internalClient.statObject(StatObjectArgs.builder()
                    .bucket(asset.getBucket())
                    .object(asset.getObjectKey())
                    .build());
        } catch (Exception ex) {
            // The object is not there — the client never finished the
            // PUT. Mark the row so it cannot be retried against a
            // (potentially later) uploaded object.
            markDeleted(asset);
            throw new IllegalStateException(
                    "Object not found for asset " + assetId + ": " + ex.getMessage(), ex);
        }

        long actualSize = stat.size();
        if (actualSize > asset.getMaxSizeBytes()) {
            // Remove the oversized object AND the row. We do not
            // keep the bytes: even if the client retries with a
            // smaller body, presigned URLs for the same key would
            // overwrite the object and leave a stale row pointing at
            // mismatched data.
            deletePhysicalObjectQuietly(asset.getBucket(), asset.getObjectKey());
            markDeleted(asset);
            throw new IllegalStateException(
                    "Uploaded object exceeds size cap (" + actualSize
                            + " > " + asset.getMaxSizeBytes() + " bytes) for asset " + assetId);
        }

        asset.setSizeBytes(actualSize);
        // Use the server-reported content type if it is present and
        // not the generic default; some clients (curl, fetch) forget
        // to set Content-Type on PUT and MinIO falls back to
        // application/octet-stream, which would overwrite the
        // declared type with something useless.
        if (stat.contentType() != null && !stat.contentType().isBlank()
                && !"application/octet-stream".equals(stat.contentType())) {
            asset.setContentType(stat.contentType());
        }
        if (stat.etag() != null) {
            // MinIO etag is NOT SHA-256 in multipart uploads, but for
            // single-part uploads < 5 MB it is the MD5 hex. We store
            // it anyway as a best-effort integrity token; the real
            // SHA-256 may be filled in later by an async scanner.
            asset.setChecksumSha256(stat.etag().replace("\"", ""));
        }
        asset.setState(AssetState.UPLOADED);
        asset.setPendingExpiresAt(null);
        return fileAssetRepository.save(asset);
    }

    // ------------------------------------------------------------------
    // Metadata lookup
    // ------------------------------------------------------------------

    /**
     * Load an asset's metadata regardless of its state. Used by the
     * HTTP {@code GET /{id}} endpoint so clients can poll a PENDING
     * row to see when it has been confirmed by some other flow.
     */
    @Transactional(readOnly = true)
    public FileAsset getMetadata(UUID assetId) {
        return requireById(assetId);
    }

    // ------------------------------------------------------------------
    // Presign GET
    // ------------------------------------------------------------------

    /**
     * Mint a short-lived presigned GET URL for an UPLOADED asset.
     *
     * <p>The TTL is deliberately short ({@link #DOWNLOAD_URL_TTL}) so
     * a URL leaked via a copy-paste or browser history is worthless
     * after 15 minutes. The caller is expected to ask for a fresh URL
     * on every download attempt.
     */
    @Transactional(readOnly = true)
    public DownloadUrl presignDownload(UUID assetId) {
        FileAsset asset = requireUploaded(assetId);
        String url = presignGetUrl(asset.getBucket(), asset.getObjectKey(), DOWNLOAD_URL_TTL);
        return new DownloadUrl(
                asset.getId(),
                url,
                DOWNLOAD_URL_TTL.getSeconds(),
                asset.getSizeBytes(),
                asset.getContentType());
    }

    // ------------------------------------------------------------------
    // Soft delete
    // ------------------------------------------------------------------

    /**
     * Soft-delete an asset: removes the underlying object and marks
     * the row DELETED. Any reverse links ({@code linkedEventId},
     * {@code linkedCtaId}) are nulled first so downstream domain
     * tables do not point at a ghost.
     *
     * <p>This is idempotent: deleting an already-DELETED row is a
     * no-op, not an error.
     */
    public void delete(UUID assetId) {
        FileAsset asset = requireById(assetId);
        if (asset.getState() == AssetState.DELETED) {
            return;
        }
        deletePhysicalObjectQuietly(asset.getBucket(), asset.getObjectKey());
        asset.setLinkedEventId(null);
        asset.setLinkedCtaId(null);
        markDeleted(asset);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private FileAsset requireById(UUID id) {
        return fileAssetRepository.findById(id).orElseThrow(
                () -> new IllegalArgumentException("File asset not found: " + id));
    }

    private FileAsset requireUploaded(UUID id) {
        FileAsset asset = requireById(id);
        if (asset.getState() != AssetState.UPLOADED) {
            throw new IllegalStateException(
                    "Asset " + id + " is in state " + asset.getState() + ", not UPLOADED");
        }
        return asset;
    }

    private void markDeleted(FileAsset asset) {
        asset.setState(AssetState.DELETED);
        asset.setPendingExpiresAt(null);
        fileAssetRepository.save(asset);
    }

    private String bucketFor(AssetPurpose purpose) {
        MinioProperties.Buckets b = minioProperties.buckets();
        return switch (purpose) {
            case COVER     -> b.covers();
            case CTA_FILE  -> b.ctaFiles();
            case RECORDING -> b.recordings();
            case INVOICE   -> b.invoices();
        };
    }

    /**
     * Build a UUID-prefixed object key. Putting the UUID at the start
     * (not embedded in the filename) keeps MinIO's prefix-based
     * listing efficient if we ever need to iterate a bucket.
     */
    private String generateObjectKey(AssetPurpose purpose) {
        return purpose.name().toLowerCase() + "/" + UUID.randomUUID();
    }

    private String presignPutUrl(String bucket, String objectKey, Duration ttl) {
        try {
            return publicClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry((int) ttl.toSeconds(), TimeUnit.SECONDS)
                    .build());
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Failed to presign PUT for " + bucket + "/" + objectKey + ": " + ex.getMessage(), ex);
        }
    }

    private String presignGetUrl(String bucket, String objectKey, Duration ttl) {
        try {
            return publicClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry((int) ttl.toSeconds(), TimeUnit.SECONDS)
                    .build());
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Failed to presign GET for " + bucket + "/" + objectKey + ": " + ex.getMessage(), ex);
        }
    }

    private void deletePhysicalObjectQuietly(String bucket, String objectKey) {
        try {
            internalClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception ex) {
            // Do not fail the business operation because the blob
            // was already gone or MinIO is flaking. The row still
            // transitions to DELETED and a background reconciliation
            // job can sweep the object later.
            log.warn("Failed to remove MinIO object {}/{}: {}", bucket, objectKey, ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // DTOs returned by this service
    // ------------------------------------------------------------------

    /**
     * Describes a freshly reserved upload slot. The client uploads to
     * {@code uploadUrl} using HTTP PUT and must then call
     * {@code confirmUpload(assetId)} within {@code uploadWindowSeconds}
     * seconds, or the slot is garbage-collected.
     */
    public record UploadSlot(
            UUID assetId,
            String uploadUrl,
            long maxSizeBytes,
            long uploadWindowSeconds,
            Instant expiresAt) {}

    /**
     * Presigned download URL descriptor returned to authenticated
     * clients for UPLOADED assets.
     */
    public record DownloadUrl(
            UUID assetId,
            String url,
            long ttlSeconds,
            @Nullable Long sizeBytes,
            String contentType) {}

    /**
     * Per-purpose constants wrapped so they can be iterated / queried
     * at runtime (instead of a dozen static final constants).
     */
    private record PurposeDefaults(
            long maxSizeBytes,
            Set<String> allowedContentTypes,
            Duration uploadWindow) {}
}
