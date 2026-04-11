package com.webizon.analytics.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declarative topic definitions for the analytics pipeline.
 *
 * <p>Spring's {@code KafkaAdmin} auto-creates topics described by
 * {@link NewTopic} beans on application startup, so the broker ends
 * up with exactly the layout we document here. The values below are
 * MVP-sensible defaults and will be re-tuned during Phase 10 load
 * testing:
 *
 * <ul>
 *   <li><strong>Partitions=12</strong> gives room to scale consumers
 *       horizontally for a few years of growth without re-sharding.</li>
 *   <li><strong>Replication=3</strong> is the production target;
 *       dev and CI override it to 1 via broker defaults because the
 *       single-broker test image has no followers to replicate to.</li>
 *   <li><strong>Retention=14d</strong> on events is long enough for
 *       replay after an outage but short enough to keep disk cheap;
 *       the warehouse is the source of truth for long-term.</li>
 *   <li><strong>Retention=90d</strong> on lead signals matches the
 *       CRM window our re-engagement workflows need.</li>
 * </ul>
 */
@Configuration
public class AnalyticsKafkaConfig {

    private static final int PARTITIONS = 12;
    private static final short REPLICATION = 3;

    @Bean
    NewTopic analyticsEventsTopic() {
        return TopicBuilder.name(AnalyticsTopics.ANALYTICS_EVENTS)
                .partitions(PARTITIONS)
                .replicas(REPLICATION)
                .config("retention.ms", String.valueOf(14L * 24 * 60 * 60 * 1000)) // 14 days
                .config("cleanup.policy", "delete")
                .config("compression.type", "producer")
                .build();
    }

    @Bean
    NewTopic leadSignalsTopic() {
        return TopicBuilder.name(AnalyticsTopics.LEAD_SIGNALS)
                .partitions(PARTITIONS)
                .replicas(REPLICATION)
                .config("retention.ms", String.valueOf(90L * 24 * 60 * 60 * 1000)) // 90 days
                .config("cleanup.policy", "delete")
                .config("compression.type", "producer")
                .build();
    }
}
