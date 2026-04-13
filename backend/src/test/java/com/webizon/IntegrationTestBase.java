package com.webizon;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for integration tests.
 *
 * <p>Spins up PostgreSQL, Redis, Kafka, ClickHouse, and Centrifugo via
 * Testcontainers. Each test class extending this base gets a shared set
 * of containers (reused across test methods via {@code @Container} static
 * lifecycle).
 *
 * <p>Spring properties are injected dynamically via
 * {@code @DynamicPropertySource} so the app context connects to the
 * ephemeral container ports.
 *
 * <h2>Container versions</h2>
 * Match the versions in docker-compose.yml to avoid surprises.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    // ── PostgreSQL ──────────────────────────────────────────────────────
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("webizon_test")
            .withUsername("test")
            .withPassword("test");

    // ── Redis ───────────────────────────────────────────────────────────
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    // ── Kafka ───────────────────────────────────────────────────────────
    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    // ── ClickHouse ──────────────────────────────────────────────────────
    @Container
    static final GenericContainer<?> CLICKHOUSE = new GenericContainer<>(
            DockerImageName.parse("clickhouse/clickhouse-server:24.3"))
            .withExposedPorts(8123, 9000);

    // ── Centrifugo ──────────────────────────────────────────────────────
    @Container
    static final GenericContainer<?> CENTRIFUGO = new GenericContainer<>(
            DockerImageName.parse("centrifugo/centrifugo:v5"))
            .withExposedPorts(8000)
            .withEnv("CENTRIFUGO_TOKEN_HMAC_SECRET_KEY", "test_secret_key_for_integration_tests_32x")
            .withEnv("CENTRIFUGO_ALLOW_SUBSCRIBE_FOR_CLIENT", "true")
            .withCommand("centrifugo", "--health");

    // ── Property injection ──────────────────────────────────────────────
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);

        // Redis
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        // Kafka
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);

        // ClickHouse
        registry.add("webizon.clickhouse.url", () -> String.format(
                "jdbc:clickhouse://%s:%d/webizon_analytics",
                CLICKHOUSE.getHost(), CLICKHOUSE.getMappedPort(8123)));

        // Centrifugo
        registry.add("webizon.centrifugo.api-url", () -> String.format(
                "http://%s:%d/api", CENTRIFUGO.getHost(), CENTRIFUGO.getMappedPort(8000)));
        registry.add("webizon.centrifugo.token-hmac-secret",
                () -> "test_secret_key_for_integration_tests_32x");

        // Disable Keycloak for tests (use mock JWT)
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> "http://localhost:9999/realms/test");

        // Disable mail for tests
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> "25");
    }
}
