package com.webizon.storage.api;

import com.webizon.auth.CurrentUser;
import com.webizon.storage.api.dto.CreateUploadSlotRequest;
import com.webizon.storage.api.dto.DownloadUrlResponse;
import com.webizon.storage.api.dto.FileAssetResponse;
import com.webizon.storage.api.dto.UploadSlotResponse;
import com.webizon.storage.service.StorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * HTTP surface for the two-step upload flow and its ancillary
 * operations.
 *
 * <p>The flow a client follows:
 * <ol>
 *   <li>{@code POST /api/v1/storage/uploads} — pick a purpose and
 *       declared content type, get back a presigned PUT URL and the
 *       new asset id.</li>
 *   <li>Upload the bytes directly to MinIO via HTTP PUT to the URL
 *       returned above. <b>The client must send the exact same
 *       {@code Content-Type} header on the PUT</b> — S3 V4 signatures
 *       include it, and a mismatch will be rejected by MinIO as
 *       "SignatureDoesNotMatch".</li>
 *   <li>{@code POST /api/v1/storage/uploads/{assetId}/confirm} — tell
 *       the backend you are done. The backend stats the object,
 *       verifies the size cap, and flips the row to UPLOADED.</li>
 *   <li>{@code GET /api/v1/storage/{assetId}} — inspect metadata for
 *       any state.</li>
 *   <li>{@code GET /api/v1/storage/{assetId}/download-url} — mint a
 *       short-lived presigned GET URL for the browser.</li>
 *   <li>{@code DELETE /api/v1/storage/{assetId}} — soft-delete; also
 *       removes the underlying MinIO object.</li>
 * </ol>
 *
 * <p>Every endpoint requires authentication. Slot creation is open
 * to presenters and above (TENANT_PRESENTER, MODERATOR, ADMIN,
 * OWNER) — moderators may upload avatar-style assets when editing
 * their profile, presenters upload covers/recordings, admins upload
 * everything. Deletes are gated more tightly to OWNER/ADMIN since
 * they cascade through reverse links.
 */
@RestController
@RequestMapping("/api/v1/storage")
@RequiredArgsConstructor
public class StorageController {

    private final StorageService storageService;

    // ------------------------------------------------------------------
    // Two-step upload
    // ------------------------------------------------------------------

    @PostMapping("/uploads")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_MODERATOR','TENANT_PRESENTER')")
    public UploadSlotResponse createUploadSlot(@Valid @RequestBody CreateUploadSlotRequest req) {
        StorageService.UploadSlot slot = storageService.createUploadSlot(
                req.purpose(),
                req.contentType(),
                CurrentUser.profileId());
        return UploadSlotResponse.from(slot);
    }

    @PostMapping("/uploads/{assetId}/confirm")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_MODERATOR','TENANT_PRESENTER')")
    public FileAssetResponse confirmUpload(@PathVariable UUID assetId) {
        return FileAssetResponse.from(storageService.confirmUpload(assetId));
    }

    // ------------------------------------------------------------------
    // Lookup / download
    // ------------------------------------------------------------------

    @GetMapping("/{assetId}")
    @PreAuthorize("isAuthenticated()")
    public FileAssetResponse get(@PathVariable UUID assetId) {
        return FileAssetResponse.from(storageService.getMetadata(assetId));
    }

    @GetMapping("/{assetId}/download-url")
    @PreAuthorize("isAuthenticated()")
    public DownloadUrlResponse downloadUrl(@PathVariable UUID assetId) {
        return DownloadUrlResponse.from(storageService.presignDownload(assetId));
    }

    // ------------------------------------------------------------------
    // Delete
    // ------------------------------------------------------------------

    @DeleteMapping("/{assetId}")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN')")
    public void delete(@PathVariable UUID assetId) {
        storageService.delete(assetId);
    }
}
