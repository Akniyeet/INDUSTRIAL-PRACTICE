package com.webizon.storage.api.dto;

import com.webizon.storage.service.StorageService;

import java.util.UUID;

/**
 * Short-lived presigned GET URL a client may follow to download an
 * UPLOADED asset directly from MinIO.
 *
 * <p>The URL is signed against the public MinIO endpoint with a TTL
 * of a few minutes. Clients should request a fresh URL on every
 * download attempt and must not cache these values.
 */
public record DownloadUrlResponse(
        UUID assetId,
        String url,
        long ttlSeconds,
        Long sizeBytes,
        String contentType
) {
    public static DownloadUrlResponse from(StorageService.DownloadUrl dl) {
        return new DownloadUrlResponse(
                dl.assetId(),
                dl.url(),
                dl.ttlSeconds(),
                dl.sizeBytes(),
                dl.contentType());
    }
}
