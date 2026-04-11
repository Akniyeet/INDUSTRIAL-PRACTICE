package com.webizon.storage.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the MinIO SDK into the Spring context.
 *
 * <p>Two distinct clients are produced:
 * <ul>
 *   <li>{@code internalMinioClient} — points at {@link MinioProperties#endpoint()},
 *       used by the backend for {@code putObject}, {@code statObject},
 *       {@code removeObject}. Routes over the internal Docker network.</li>
 *   <li>{@code publicMinioClient} — points at {@link MinioProperties#publicEndpoint()},
 *       used only to <em>generate presigned URLs</em>. Signatures are
 *       bound to the hostname at sign time, so a URL signed against
 *       the internal endpoint would not validate when replayed by the
 *       browser against the public CDN.</li>
 * </ul>
 *
 * <p>On startup, the {@code @PostConstruct} hook uses the internal
 * client to create each configured bucket if it does not already
 * exist. This is idempotent and safe to run on every boot — it means
 * a fresh MinIO container needs no manual {@code mc mb} commands.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class MinioClientConfig {

    private final MinioProperties properties;

    /**
     * Client used for all server-side object operations (upload,
     * stat, delete). Bound to the internal hostname so traffic does
     * not hairpin through the public load balancer.
     */
    @Bean(name = "internalMinioClient")
    public MinioClient internalMinioClient() {
        return MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }

    /**
     * Client used exclusively to mint presigned URLs handed to the
     * browser. Must be bound to the public endpoint — the browser
     * cannot resolve the internal hostname and S3 V4 signatures bake
     * the hostname into the signature, so a URL signed against the
     * wrong endpoint will be rejected as invalid.
     */
    @Bean(name = "publicMinioClient")
    public MinioClient publicMinioClient() {
        return MinioClient.builder()
                .endpoint(properties.publicEndpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }

    /**
     * Ensures every configured bucket exists on startup.
     *
     * <p>Uses the internal client. Any failure here is logged but not
     * fatal — a missing bucket will surface as a putObject error later
     * and get its own diagnostic — because failing application boot
     * because a single MinIO connection flaked during compose-up
     * would be an unhelpful cascade.
     */
    @PostConstruct
    void ensureBuckets() {
        MinioClient client = internalMinioClient();
        MinioProperties.Buckets b = properties.buckets();
        ensureBucket(client, b.covers());
        ensureBucket(client, b.ctaFiles());
        ensureBucket(client, b.recordings());
        ensureBucket(client, b.invoices());
    }

    private void ensureBucket(MinioClient client, String bucket) {
        try {
            boolean exists = client.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("MinIO bucket created: {}", bucket);
            } else {
                log.debug("MinIO bucket already present: {}", bucket);
            }
        } catch (Exception ex) {
            // Do not fail boot — a transient MinIO outage at startup
            // should not take the whole API down. Upload attempts will
            // surface the real error if the bucket is still missing.
            log.warn("Failed to ensure MinIO bucket {}: {}", bucket, ex.getMessage());
        }
    }
}
