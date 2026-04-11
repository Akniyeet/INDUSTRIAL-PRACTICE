package com.webizon.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables Spring Data JPA auditing so that {@code @CreatedDate} and
 * {@code @LastModifiedDate} on {@link BaseEntity} are populated automatically.
 *
 * <p>We do not use {@code @CreatedBy} / {@code @LastModifiedBy} — audit trails
 * are emitted to the event log (Kafka → ClickHouse) instead of being
 * denormalized into every row.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "instantDateTimeProvider")
public class JpaAuditingConfig {
}
