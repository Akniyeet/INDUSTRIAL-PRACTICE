package com.webizon.storage.api.dto;

import com.webizon.storage.model.AssetPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Client-facing request body for {@code POST /api/v1/storage/uploads}.
 *
 * @param purpose            which asset family this upload belongs to;
 *                           determines the bucket, size cap, and
 *                           allowed content types
 * @param contentType        MIME type the client intends to upload;
 *                           must match the purpose's allow-list, and
 *                           the client MUST send the same
 *                           {@code Content-Type} header on its PUT
 *                           request or MinIO will reject the signature
 */
public record CreateUploadSlotRequest(
        @NotNull AssetPurpose purpose,
        @NotBlank String contentType
) {}
