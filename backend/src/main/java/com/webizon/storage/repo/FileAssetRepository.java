package com.webizon.storage.repo;

import com.webizon.storage.model.AssetPurpose;
import com.webizon.storage.model.AssetState;
import com.webizon.storage.model.FileAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link FileAsset}.
 *
 * <p>Every finder runs under the tenant RLS policy — there is no
 * "find by id" that crosses tenant boundaries. Cross-tenant lookups
 * (e.g. the orphan sweeper that enumerates all tenants) must pivot
 * through {@code TenantContext.runWith} first.
 */
public interface FileAssetRepository extends JpaRepository<FileAsset, UUID> {

    /**
     * Find by id within the current tenant. Returns empty if the row
     * belongs to another tenant (RLS will filter it out) or if it has
     * never existed.
     */
    Optional<FileAsset> findById(UUID id);

    /**
     * Find an asset by its physical coordinates. Used when MinIO
     * lifecycle events (e.g. delete notifications) need to reconcile
     * with the database.
     */
    Optional<FileAsset> findByBucketAndObjectKey(String bucket, String objectKey);

    /**
     * Page of PENDING rows whose reservation has expired. The sweeper
     * uses this to hunt orphaned uploads: it removes the underlying
     * MinIO object (if any) and marks the row DELETED.
     *
     * <p>The query hits the partial index {@code file_assets_pending_gc_idx}.
     * Keep the limit small ({@code Pageable}) so the sweeper processes
     * orphans in tractable batches.
     */
    @Query("""
            SELECT fa FROM FileAsset fa
             WHERE fa.state = :state
               AND fa.pendingExpiresAt < :cutoff
            """)
    List<FileAsset> findExpiredPending(
            @Param("state") AssetState state,
            @Param("cutoff") Instant cutoff);

    /**
     * All UPLOADED assets linked to a given event. Used when an event
     * is archived / deleted so StorageService can decide which assets
     * to unlink or cascade-delete.
     */
    List<FileAsset> findAllByLinkedEventIdAndState(UUID linkedEventId, AssetState state);

    /**
     * UPLOADED assets of a given purpose, used by the billing module
     * to sum {@code sizeBytes} for GB-month calculations.
     */
    List<FileAsset> findAllByPurposeAndState(AssetPurpose purpose, AssetState state);
}
