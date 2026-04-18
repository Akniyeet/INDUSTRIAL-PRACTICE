package com.webizon.storage.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

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
 * <p>Bucket initialization is handled by {@link MinioBucketInitializer},
 * a separate component that fires on {@link ApplicationReadyEvent} to
 * avoid the self-referential {@code @Bean} call that caused a circular
 * dependency when the init was done in {@code @PostConstruct}.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class MinioClientConfig {

    private final MinioProperties properties;

    /**
     * Region hard-pinned on both clients. MinIO has no real concept of
     * regions (it accepts any value) but the Java SDK, if region is
     * left blank, calls {@code GET /?location} on the endpoint before
     * signing every presigned URL. That round-trip is fine for the
     * internal endpoint but catastrophic for the public one in local
     * dev: the public endpoint hostname (e.g. {@code localhost:9010},
     * {@code host.docker.internal:9010}) is not always reachable from
     * inside the backend container — only the browser needs to reach
     * it. Pinning the region skips the lookup, so presign becomes a
     * pure local operation (HMAC over the request line) and the
     * backend no longer needs a split-horizon route to MinIO.
     */
    private static final String FIXED_REGION = "us-east-1";

    /**
     * Client used for all server-side object operations (upload,
     * stat, delete). Bound to the internal hostname so traffic does
     * not hairpin through the public load balancer.
     */
    @Bean(name = "internalMinioClient")
    public MinioClient internalMinioClient() {
        return MinioClient.builder()
                .endpoint(properties.endpoint())
                .region(FIXED_REGION)
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }

    /**
     * Client used exclusively to mint presigned URLs handed to the
     * browser. The endpoint hostname is the one baked into the
     * signature that the browser replays; {@link #FIXED_REGION} skips
     * the preflight {@code getBucketLocation} call so the backend never
     * has to reach this hostname itself.
     */
    @Bean(name = "publicMinioClient")
    public MinioClient publicMinioClient() {
        return MinioClient.builder()
                .endpoint(properties.publicEndpoint())
                .region(FIXED_REGION)
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }

    /**
     * Ensures every configured bucket exists once the application is
     * fully started. Separated from {@link MinioClientConfig} so that
     * the {@code @Bean} methods on the config class are not called
     * self-referentially during {@code @PostConstruct}, which would
     * create a circular dependency with beans that inject the clients.
     */
    @Component
    @Slf4j
    static class MinioBucketInitializer implements ApplicationListener<ApplicationReadyEvent> {

        private final MinioProperties properties;
        private final MinioClient internalClient;

        MinioBucketInitializer(MinioProperties properties,
                               @Qualifier("internalMinioClient") MinioClient internalClient) {
            this.properties = properties;
            this.internalClient = internalClient;
        }

        @Override
        public void onApplicationEvent(ApplicationReadyEvent event) {
            MinioProperties.Buckets b = properties.buckets();
            ensureBucket(b.covers());
            ensureBucket(b.ctaFiles());
            ensureBucket(b.recordings());
            ensureBucket(b.invoices());
            // Cover images are public-by-design (the landing page is
            // unauthenticated) so we grant anonymous read on the
            // covers bucket. Without this the browser would need a
            // presigned URL whose signature expires 15 minutes after
            // upload, breaking the landing page every time.
            makePublicReadOnly(b.covers());
        }

        private void ensureBucket(String bucket) {
            try {
                boolean exists = internalClient.bucketExists(
                        BucketExistsArgs.builder().bucket(bucket).build());
                if (!exists) {
                    internalClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                    log.info("MinIO bucket created: {}", bucket);
                } else {
                    log.debug("MinIO bucket already present: {}", bucket);
                }
            } catch (Exception ex) {
                log.warn("Failed to ensure MinIO bucket {}: {}", bucket, ex.getMessage());
            }
        }

        /**
         * Grant anonymous GET permission on every object in the bucket.
         * Used for buckets whose contents are legitimately public
         * (cover images shown on landing pages). Idempotent — safe to
         * re-apply on every startup.
         */
        private void makePublicReadOnly(String bucket) {
            String policy = "{"
                    + "\"Version\":\"2012-10-17\","
                    + "\"Statement\":[{"
                    + "\"Effect\":\"Allow\","
                    + "\"Principal\":{\"AWS\":[\"*\"]},"
                    + "\"Action\":[\"s3:GetObject\"],"
                    + "\"Resource\":[\"arn:aws:s3:::" + bucket + "/*\"]"
                    + "}]}";
            try {
                internalClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                        .bucket(bucket)
                        .config(policy)
                        .build());
                log.info("MinIO bucket {} set to public-read", bucket);
            } catch (Exception ex) {
                log.warn("Failed to set public-read policy on {}: {}", bucket, ex.getMessage());
            }
        }
    }
}
