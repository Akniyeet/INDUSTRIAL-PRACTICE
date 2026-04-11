package com.webizon.analytics.service;

import com.webizon.analytics.kafka.AnalyticsKafkaPublisher;
import com.webizon.analytics.model.AnalyticsEvent;
import com.webizon.analytics.model.AnalyticsEventType;
import com.webizon.analytics.model.LeadSignal;
import com.webizon.analytics.model.LeadSignalType;
import com.webizon.analytics.repo.LeadSignalRepository;
import com.webizon.analytics.repo.SessionAttendanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns raw analytics events into {@link LeadSignal}s.
 *
 * <p>Rules are declarative and small — every new raw event is run
 * through {@link #evaluate} which inspects the event type plus the
 * current attendance row and emits zero or more signals. Each rule
 * is idempotent: it checks
 * {@link LeadSignalRepository#existsBySessionIdAndProfileIdAndSignalType}
 * before inserting so repeated triggers (e.g. a user who hits the
 * 30-minute milestone more than once due to a refresh) don't flood
 * the CRM with duplicate rows.
 *
 * <p>The scoring model is deliberately simple in MVP — enough to
 * produce a useful "hot" segment, not enough to over-fit a specific
 * campaign. Tuning happens later in Phase 11 against real data:
 *
 * <table>
 *   <caption>Default signal scores</caption>
 *   <tr><th>Signal</th><th>Score</th><th>Trigger</th></tr>
 *   <tr><td>ATTENDED</td>           <td>+5</td>  <td>First ROOM_ENTERED in a session</td></tr>
 *   <tr><td>WATCHED_LONG</td>       <td>+25</td> <td>WATCH_MILESTONE of at least 30 minutes</td></tr>
 *   <tr><td>CHAT_ENGAGED</td>       <td>+10</td> <td>First CHAT_MESSAGE_SENT in a session</td></tr>
 *   <tr><td>CTA_COURSE_CLICK</td>   <td>+30</td> <td>CTA_CLICK whose CTA is a COURSE type</td></tr>
 *   <tr><td>CTA_FILE_DOWNLOAD</td>  <td>+15</td> <td>CTA_DOWNLOAD on a FILE CTA</td></tr>
 *   <tr><td>RETURNED_FOR_AUTO</td>  <td>+20</td> <td>Profile has attended more than one session for the same event</td></tr>
 *   <tr><td>BAD_BEHAVIOR</td>       <td>-40</td> <td>MUTED / CHAT_BANNED / FULL_BANNED</td></tr>
 * </table>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LeadSignalEvaluator {

    /** Minimum watch duration (in seconds) that counts as "watched long". */
    private static final int WATCHED_LONG_THRESHOLD_SECONDS = 30 * 60;

    private final LeadSignalRepository leadSignalRepository;
    private final SessionAttendanceRepository attendanceRepository;
    private final AnalyticsKafkaPublisher kafkaPublisher;

    /**
     * Evaluate a freshly-persisted analytics event. The caller must
     * have already stored the event and attendance row; this method
     * only reads them and conditionally inserts signal rows.
     *
     * <p>Runs in the caller's transaction so signal inserts roll
     * back together with the event on failure.
     */
    @Transactional
    public void evaluate(AnalyticsEvent event) {
        if (event.getProfileId() == null) {
            return; // pre-auth events don't generate lead signals
        }

        switch (event.getEventType()) {
            case ROOM_ENTERED -> emitIfMissing(event, LeadSignalType.ATTENDED, 5);

            case WATCH_MILESTONE -> {
                Integer offset = event.getOffsetSeconds();
                if (offset != null && offset >= WATCHED_LONG_THRESHOLD_SECONDS) {
                    emitIfMissing(event, LeadSignalType.WATCHED_LONG, 25);
                }
            }

            case CHAT_MESSAGE_SENT, CHAT_REPLY_SENT ->
                    emitIfMissing(event, LeadSignalType.CHAT_ENGAGED, 10);

            case CTA_CLICK -> {
                String ctaType = stringFromMetadata(event, "ctaType");
                if ("COURSE".equals(ctaType)) {
                    emitIfMissing(event, LeadSignalType.CTA_COURSE_CLICK, 30);
                }
            }

            case CTA_DOWNLOAD ->
                    emitIfMissing(event, LeadSignalType.CTA_FILE_DOWNLOAD, 15);

            case MUTED, CHAT_BANNED, FULL_BANNED ->
                    emitIfMissing(event, LeadSignalType.BAD_BEHAVIOR, -40);

            default -> {
                // Other event types don't currently drive signals.
            }
        }

        // Cross-session rule: a profile that has attended more than one
        // session of the same event is engaged. We evaluate it on every
        // ROOM_ENTERED and rely on the existsBy… check to keep it
        // idempotent.
        if (event.getEventType() == AnalyticsEventType.ROOM_ENTERED
                && event.getEventId() != null) {
            evaluateReturnedForAuto(event);
        }
    }

    private void evaluateReturnedForAuto(AnalyticsEvent event) {
        List<com.webizon.analytics.model.SessionAttendance> history =
                attendanceRepository.findAllByProfileIdOrderByFirstJoinedAtDesc(event.getProfileId());
        long sameEventAttendance = history.stream()
                .filter(a -> a.getEventId().equals(event.getEventId()))
                .count();
        if (sameEventAttendance >= 2) {
            emitIfMissing(event, LeadSignalType.RETURNED_FOR_AUTO, 20);
        }
    }

    private void emitIfMissing(AnalyticsEvent event, LeadSignalType type, int score) {
        if (event.getSessionId() != null && leadSignalRepository
                .existsBySessionIdAndProfileIdAndSignalType(
                        event.getSessionId(), event.getProfileId(), type)) {
            return;
        }

        LeadSignal signal = new LeadSignal();
        signal.setProfileId(event.getProfileId());
        signal.setEventId(event.getEventId());
        signal.setSessionId(event.getSessionId());
        signal.setSignalType(type);
        signal.setScore(score);
        signal.setSourceEventId(event.getId());
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("triggerEventType", event.getEventType().name());
        if (event.getOffsetSeconds() != null) {
            metadata.put("offsetSeconds", event.getOffsetSeconds());
        }
        signal.setMetadata(metadata);

        LeadSignal saved = leadSignalRepository.save(signal);
        kafkaPublisher.publishLeadSignal(saved);

        log.debug("Emitted lead signal {} for profile {} in session {}",
                type, event.getProfileId(), event.getSessionId());
    }

    private static String stringFromMetadata(AnalyticsEvent event, String key) {
        Object raw = event.getMetadata() == null ? null : event.getMetadata().get(key);
        return raw == null ? null : raw.toString();
    }

    /**
     * Compute a single additive score for a user across a session.
     * Useful for the dashboard "hot list" without running an SQL
     * aggregate. O(n) over the session's signals which is fine for
     * dashboards; the Kafka consumer should materialise this into a
     * warehouse table for cross-session ranking.
     */
    @Transactional(readOnly = true)
    public int computeProfileSessionScore(UUID sessionId, UUID profileId) {
        return leadSignalRepository.findAllByProfileIdOrderByCreatedAtDesc(profileId).stream()
                .filter(s -> sessionId.equals(s.getSessionId()))
                .mapToInt(LeadSignal::getScore)
                .sum();
    }
}
