package com.webizon.storage.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Runtime configuration for the MinIO / S3-compatible object store.
 *
 * <p>All values are sourced from {@code webizon.minio.*} (see
 * {@code application.yml}). Production deployments must override every
 * value via environment variables — the defaults in the YAML are only
 * correct for local Docker Compose.
 *
 * <p><b>Why two endpoints?</b>
 * The backend talks to MinIO over the internal Docker network
 * ({@code http://minio:9000}), while the browser talks to MinIO through
 * the public gateway ({@code https://cdn.webizon.kz}). Presigned URLs
 * handed back to clients must be signed against the <em>public</em>
 * endpoint — signing against the internal hostname would produce URLs
 * the browser cannot resolve. Server-side stat / putObject calls, on
 * the other hand, must go through the internal endpoint to avoid
 * routing every byte through the public load balancer.
 *
 * <p>See {@link MinioClientConfig} for how this split is wired up.
 *
 * @param endpoint       internal base URL used by the backend for
 *                       {@code putObject}, {@code statObject}, and
 *                       {@code removeObject} calls
 * @param publicEndpoint public base URL against which presigned URLs
 *                       are generated before they are handed to the
 *                       browser
 * @param accessKey      MinIO root / service account access key
 * @param secretKey      MinIO root / service account secret key
 * @param buckets        the four logical bucket handles; each of these
 *                       is created on startup if it does not already
 *                       exist (see {@link MinioClientConfig})
 */
@ConfigurationProperties(prefix = "webizon.minio")
@Validated
public record MinioProperties(
        @NotBlank String endpoint,
        @NotBlank String publicEndpoint,
        @NotBlank String accessKey,
        @NotBlank String secretKey,
        @Valid @NotNull Buckets buckets
) {
    /**
     * Logical bucket names, one per {@code AssetPurpose}.
     *
     * <p>Keeping them in config (rather than hard-coding) lets us
     * re-map a purpose to a different physical bucket per environment
     * — e.g. staging and prod can share one MinIO with different
     * bucket prefixes without touching code.
     */
    public record Buckets(
            @NotBlank String covers,
            @NotBlank String ctaFiles,
            @NotBlank String recordings,
            @NotBlank String invoices
    ) {}
}
