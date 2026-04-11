package com.webizon.storage.api.dto;

import com.webizon.storage.model.AssetPurpose;
import com.webizon.storage.model.AssetState;
import com.webizon.storage.model.FileAsset;

import java.time.Instant;
import java.util.UUID;

/**
 * Public projection of a {@link FileAsset}. Intentionally omits
 * {@code bucket} and {@code objectKey} — those are physical-storage
 * coordinates the client has no business knowing. Downloads always
 * go through {@code GET /api/v1/storage/{assetId}/download-url}
 * which mints a fresh presigned URL.
 */
public record FileAssetResponse(
        UUID id,
        AssetPurpose purpose,
        AssetState state,
        String contentType,
        Long sizeBytes,
        UUID linkedEventId,
        UUID linkedCtaId,
        UUID uploadedByUserId,
        Instant createdAt,
        Instant updatedAt
) {
    public static FileAssetResponse from(FileAsset asset) {
        return new FileAssetResponse(
                asset.getId(),
                asset.getPurpose(),
                asset.getState(),
                asset.getContentType(),
                asset.getSizeBytes(),
                asset.getLinkedEventId(),
                asset.getLinkedCtaId(),
                asset.getUploadedByUserId(),
                asset.getCreatedAt(),
                asset.getUpdatedAt());
    }
}
