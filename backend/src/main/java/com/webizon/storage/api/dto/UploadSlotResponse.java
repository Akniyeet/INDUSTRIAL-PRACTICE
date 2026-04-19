package com.webizon.storage.api.dto;

import com.webizon.storage.service.StorageService;

import java.time.Instant;
import java.util.UUID;

/**
 * Response to a successful slot reservation. The client uploads to
 * {@code uploadUrl} via HTTP PUT, then calls
 * {@code POST /api/v1/storage/uploads/{assetId}/confirm} to flip the
 * row to UPLOADED.
 *
 * <p>If the client fails to confirm before {@code expiresAt}, the
 * orphan sweeper will reclaim the slot and remove any partially
 * uploaded bytes.
 */
public record UploadSlotResponse(
        UUID assetId,
        String uploadUrl,
        long maxSizeBytes,
        long uploadWindowSeconds,
        Instant expiresAt
) {
    public static UploadSlotResponse from(StorageService.UploadSlot slot) {
        return new UploadSlotResponse(
                slot.assetId(),
                slot.uploadUrl(),
                slot.maxSizeBytes(),
                slot.uploadWindowSeconds(),
                slot.expiresAt());
    }
}
