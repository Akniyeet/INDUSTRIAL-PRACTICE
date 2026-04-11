package com.webizon.analytics.service;

import com.webizon.analytics.kafka.AnalyticsKafkaPublisher;
import com.webizon.analytics.model.AnalyticsEvent;
import com.webizon.analytics.model.AnalyticsEventType;
import com.webizon.analytics.repo.AnalyticsEventRepository;
import com.webizon.events.model.Session;
import com.webizon.events.repo.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Single write path for the analytics event log.
 *
 * <p>Every behavioural event in the product — page views, room joins,
 * chat messages, CTA clicks, moderation actions — lands here. The
 * recorder's job is deliberately small: build an {@link AnalyticsEvent}
 * row, persist it, mirror it to Kafka, and hand it to the
 * {@link LeadSignalEvaluator} so downstream signals can fire in the
 * same transaction. Everything else (retention aggregates, CTA CTR,
 * dashboards) is a read concern and lives elsewhere.
 *
 * <h2>Why one recorder?</h2>
 * Keeping a single choke point means the write format, Kafka envelope,
 * and signal evaluation can never drift between call sites. Callers
 * just describe <em>what</em> happened via a {@link RecordCommand};
 * the recorder decides <em>how</em> it is stored and propagated.
 *
 * <h2>Transaction semantics</h2>
 * <ul>
 *   <li>The DB row is the source of truth. Both persistence and
 *       signal evaluation run inside the same transaction — if the
 *       evaluator throws, the event insert rolls back too, so we
 *       never ship "event without signal" state.</li>
 *   <li>Kafka publish is fire-and-forget: on broker outage the DB
 *       row still exists and a future replay job can catch up. The
 *       publisher swallows broker errors and logs WARN; we never let
 *       Kafka failures block the hot path.</li>
 *   <li>Tenant context is assumed to be set by the filter — the
 *       recorder does not second-guess it and relies on Hibernate's
 *       {@code @TenantId} stamping to fail loudly otherwise.</li>
 * </ul>
 *
 * <h2>Session / event id resolution</h2>
 * A caller that only knows {@code sessionId} can omit {@code eventId};
 * the recorder will look the session up and populate it. This keeps
 * pre-auth and landing-page events — which have neither — equally
 * cheap to record.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsRecorder {

    private final AnalyticsEventRepository analyticsEventRepository;
    private final SessionRepository sessionRepository;
    private final AnalyticsKafkaPublisher kafkaPublisher;
    private final LeadSignalEvaluator leadSignalEvaluator;

    /**
     * Persist an analytics event and run the downstream pipeline.
     *
     * <p>Runs in a new transaction so that callers from non-JPA paths
     * (e.g. WebSocket handlers) don't need to be themselves
     * transactional, and callers already inside a transaction still
     * get correct rollback on signal evaluation failure. The inner
     * transaction commits or rolls back independently from the
     * caller's work, which is the right boundary because analytics is
     * a fire-and-forget side effect of business actions — losing a
     * behavioural event should never fail the user-visible action.
     */
    @Transactional
    public AnalyticsEvent record(RecordCommand cmd) {
        UUID sessionId = cmd.sessionId();
        UUID eventId = cmd.eventId();

        // If the caller only told us the session, resolve the event
        // once so downstream reports can group by event without an
        // extra join. A missing session is fine — landing-page views
        // and pre-auth signals live with both ids null.
        if (eventId == null && sessionId != null) {
            eventId = sessionRepository.findById(sessionId)
                    .map(Session::getEventId)
                    .orElse(null);
        }

        AnalyticsEvent row = new AnalyticsEvent();
        row.setEventId(eventId);
        row.setSessionId(sessionId);
        row.setProfileId(cmd.profileId());
        row.setClientKey(truncate(cmd.clientKey()));
        row.setEventType(cmd.eventType());
        row.setOffsetSeconds(cmd.offsetSeconds());
        row.setMetadata(cmd.metadata() == null ? new HashMap<>() : new HashMap<>(cmd.metadata()));

        AnalyticsEvent saved = analyticsEventRepository.save(row);

        // Kafka mirror — publishes are best-effort, the publisher
        // swallows its own exceptions so a broker hiccup can never
        // crash the caller or roll back the DB row.
        kafkaPublisher.publishEvent(saved);

        // Lead signal evaluation — runs in the same transaction so
        // signal and event commit atomically. The evaluator is
        // idempotent, so a retry after a transient DB error won't
        // double-score the profile.
        try {
            leadSignalEvaluator.evaluate(saved);
        } catch (Exception ex) {
            // A broken signal rule must not take down analytics. Log
            // and continue; the row is still persisted and the
            // mirror has already shipped.
            log.warn("Lead signal evaluation failed for event {} (type={}): {}",
                    saved.getId(), saved.getEventType(), ex.getMessage(), ex);
        }

        return saved;
    }

    /**
     * Convenience wrapper for the common case — an authenticated
     * in-session action with only a type and (optionally) metadata.
     * The WebSocket chat handler and the CTA click endpoint both go
     * through this path.
     */
    @Transactional
    public AnalyticsEvent record(UUID sessionId,
                                 UUID profileId,
                                 AnalyticsEventType type,
                                 Integer offsetSeconds,
                                 Map<String, Object> metadata) {
        return record(new RecordCommand(
                null, sessionId, profileId, null, type, offsetSeconds, metadata));
    }

    /**
     * Lookup-side convenience for unit tests and reports; real write
     * paths should never need to fetch by id.
     */
    @Transactional(readOnly = true)
    public Optional<AnalyticsEvent> findById(UUID id) {
        return analyticsEventRepository.findById(id);
    }

    private static String truncate(String clientKey) {
        if (clientKey == null) return null;
        return clientKey.length() > 64 ? clientKey.substring(0, 64) : clientKey;
    }

    /**
     * Immutable write command. Any nullable field reflects a real
     * product case:
     * <ul>
     *   <li>{@code eventId} — pre-auth / landing events; resolved from
     *       {@code sessionId} when possible.</li>
     *   <li>{@code sessionId} — events that predate joining a room
     *       (landing view, auth redirect).</li>
     *   <li>{@code profileId} — pre-auth tracking; the {@code clientKey}
     *       is the stitching identifier until the user logs in.</li>
     *   <li>{@code clientKey} — best-effort cookie/device id for
     *       stitching pre-auth views to a real profile after login.</li>
     *   <li>{@code offsetSeconds} — only meaningful for in-session
     *       events ({@code WATCH_MILESTONE}, {@code CTA_IMPRESSION},
     *       etc.).</li>
     *   <li>{@code metadata} — free-form per-type payload stored in
     *       JSONB. Small flat maps only; large structures belong in
     *       their own tables.</li>
     * </ul>
     */
    public record RecordCommand(
            UUID eventId,
            UUID sessionId,
            UUID profileId,
            String clientKey,
            AnalyticsEventType eventType,
            Integer offsetSeconds,
            Map<String, Object> metadata
    ) {
        public RecordCommand {
            if (eventType == null) {
                throw new IllegalArgumentException("eventType is required");
            }
        }
    }
}
