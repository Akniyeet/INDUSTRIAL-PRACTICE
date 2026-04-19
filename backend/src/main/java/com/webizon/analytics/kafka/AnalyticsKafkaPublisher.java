package com.webizon.analytics.kafka;

import com.webizon.analytics.model.AnalyticsEvent;
import com.webizon.analytics.model.LeadSignal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Envelope-shaping Kafka publisher for the analytics topics.
 *
 * <p>Splitting this out of the recorder means the write path can
 * publish fire-and-forget without dragging Kafka concerns into the
 * domain service. On send failure we log at {@code WARN} and move on
 * — the primary copy is the DB row, and Phase 10 adds a retry
 * outbox if we need one.
 *
 * <p>Key choices:
 * <ul>
 *   <li>Analytics events are keyed by {@code sessionId.toString()} so
 *       same-session events land on the same partition in the order
 *       they were produced; a consumer building a per-session
 *       timeline never has to merge across partitions.</li>
 *   <li>Lead signals are keyed by {@code profileId.toString()} so a
 *       per-user CRM aggregator sees them in order even across
 *       sessions.</li>
 * </ul>
 *
 * <p>The envelope is a flat {@link LinkedHashMap}: stable key order
 * for debugging, no nested domain types, and every id serialised as a
 * string so downstream languages don't have to handle UUID types.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyticsKafkaPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishEvent(AnalyticsEvent event) {
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("id", event.getId() == null ? null : event.getId().toString());
            envelope.put("tenantId", event.getTenantId() == null ? null : event.getTenantId().toString());
            envelope.put("eventId", event.getEventId() == null ? null : event.getEventId().toString());
            envelope.put("sessionId", event.getSessionId() == null ? null : event.getSessionId().toString());
            envelope.put("profileId", event.getProfileId() == null ? null : event.getProfileId().toString());
            envelope.put("clientKey", event.getClientKey());
            envelope.put("eventType", event.getEventType().name());
            envelope.put("offsetSeconds", event.getOffsetSeconds());
            envelope.put("metadata", event.getMetadata());
            envelope.put("createdAt",
                    event.getCreatedAt() == null ? Instant.now().toString() : event.getCreatedAt().toString());

            String key = event.getSessionId() == null
                    ? (event.getProfileId() == null ? "none" : event.getProfileId().toString())
                    : event.getSessionId().toString();

            kafkaTemplate.send(AnalyticsTopics.ANALYTICS_EVENTS, key, envelope);
        } catch (Exception ex) {
            log.warn("Failed to publish analytics event {} to Kafka: {}",
                    event.getId(), ex.getMessage());
        }
    }

    public void publishLeadSignal(LeadSignal signal) {
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("id", signal.getId() == null ? null : signal.getId().toString());
            envelope.put("tenantId", signal.getTenantId() == null ? null : signal.getTenantId().toString());
            envelope.put("profileId", signal.getProfileId() == null ? null : signal.getProfileId().toString());
            envelope.put("eventId", signal.getEventId() == null ? null : signal.getEventId().toString());
            envelope.put("sessionId", signal.getSessionId() == null ? null : signal.getSessionId().toString());
            envelope.put("signalType", signal.getSignalType().name());
            envelope.put("score", signal.getScore());
            envelope.put("sourceEventId",
                    signal.getSourceEventId() == null ? null : signal.getSourceEventId().toString());
            envelope.put("metadata", signal.getMetadata());
            envelope.put("createdAt",
                    signal.getCreatedAt() == null ? Instant.now().toString() : signal.getCreatedAt().toString());

            String key = signal.getProfileId() == null ? "none" : signal.getProfileId().toString();
            kafkaTemplate.send(AnalyticsTopics.LEAD_SIGNALS, key, envelope);
        } catch (Exception ex) {
            log.warn("Failed to publish lead signal {} to Kafka: {}",
                    signal.getId(), ex.getMessage());
        }
    }
}
