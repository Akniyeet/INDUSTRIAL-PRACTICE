package com.webizon.storage.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
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
    }
}
