package com.webizon.analytics.service;

import com.webizon.analytics.model.AnalyticsEventType;
import com.webizon.analytics.model.SessionAttendance;
import com.webizon.analytics.repo.SessionAttendanceRepository;
import com.webizon.events.model.Session;
import com.webizon.events.repo.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Presence / attendance tracker for a session.
 *
 * <p>Three entry points, one per stage of a viewer's lifecycle:
 * <ul>
 *   <li>{@link #join(UUID, UUID)} — user opened the room. Upserts the
 *       {@code session_attendance} row, increments {@code entry_count}
 *       on a returning viewer, and records a {@code ROOM_ENTERED}
 *       analytics event.</li>
 *   <li>{@link #heartbeat(UUID, UUID, Integer)} — periodic ping from
 *       the client. Bumps {@code last_seen_at}, refreshes
 *       {@code max_offset_seconds}, and (when the offset crosses the
 *       30-minute threshold) emits a {@code WATCH_MILESTONE} event so
 *       the lead signal evaluator can promote the user to
 *       {@code WATCHED_LONG}. A missed heartbeat is not fatal — the
 *       row just stops updating and the dashboard infers the user
 *       has left after a staleness window.</li>
 *   <li>{@link #leave(UUID, UUID)} — user closed the tab or
 *       disconnected. Stamps {@code left_at}, finalises
 *       {@code total_connected_seconds}, and records
 *       {@code ROOM_LEFT}.</li>
 * </ul>
 *
 * <h2>Concurrency</h2>
 * Two browser tabs from the same profile hit this class simultaneously
 * during a refresh storm. The unique index on
 * {@code (session_id, profile_id)} and the optimistic
 * {@code @Version} on {@link SessionAttendance} keep the row
 * consistent: a losing insert falls through to the
 * {@link DataIntegrityViolationException} branch and re-reads the
 * winning row. This is the same pattern the chat flood filter uses.
 *
 * <h2>Why per-profile, not per-connection?</h2>
 * Multiple tabs are one viewer. {@code entry_count} captures how many
 * times they reconnected, but the running clock (total watch time) is
 * one monotonic number per profile. Per-connection tracking belongs
 * in the Centrifugo presence layer, not here.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AttendanceTracker {

    /** Crosses this threshold once — emits a {@code WATCH_MILESTONE} event. */
    private static final int WATCHED_LONG_MILESTONE_SECONDS = 30 * 60;

    private final SessionAttendanceRepository attendanceRepository;
    private final SessionRepository sessionRepository;
    private final AnalyticsRecorder analyticsRecorder;

    /**
     * Record that a profile has joined the room. Idempotent across
     * refreshes: a returning viewer updates the existing row and
     * bumps {@code entry_count} instead of creating a duplicate. The
     * first successful join of a session triggers
     * {@link AnalyticsEventType#ROOM_ENTERED} which in turn produces
     * the {@code ATTENDED} lead signal.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SessionAttendance join(UUID sessionId, UUID profileId) {
        Session session = requireSession(sessionId);
        Instant now = Instant.now();

        SessionAttendance row;
        Optional<SessionAttendance> existing =
                attendanceRepository.findBySessionIdAndProfileId(sessionId, profileId);

        if (existing.isPresent()) {
            row = existing.get();
            row.setLastSeenAt(now);
            row.setLeftAt(null);
            row.setEntryCount(row.getEntryCount() + 1);
        } else {
            row = new SessionAttendance();
            row.setEventId(session.getEventId());
            row.setSessionId(sessionId);
            row.setProfileId(profileId);
            row.setFirstJoinedAt(now);
            row.setLastSeenAt(now);
            row.setEntryCount(1);
            row.setTotalConnectedSeconds(0);
            try {
                row = attendanceRepository.save(row);
            } catch (DataIntegrityViolationException race) {
                // Lost the insert race against a sibling tab — fall
                // back to the existing row and treat this as a
                // re-join.
                row = attendanceRepository
                        .findBySessionIdAndProfileId(sessionId, profileId)
                        .orElseThrow(() -> race);
                row.setLastSeenAt(now);
                row.setLeftAt(null);
                row.setEntryCount(row.getEntryCount() + 1);
            }
        }

        SessionAttendance saved = attendanceRepository.save(row);

        analyticsRecorder.record(
                sessionId, profileId, AnalyticsEventType.ROOM_ENTERED,
                currentOffsetSeconds(session), Map.of("entryCount", saved.getEntryCount()));

        return saved;
    }

    /**
     * Periodic keep-alive from the client. {@code offsetSeconds} is
     * the current video playback position — only recorded if it
     * advances {@code max_offset_seconds}, which keeps the column
     * monotonic even when the player seeks backwards. Crossing the
     * 30-minute threshold once emits a {@code WATCH_MILESTONE} event
     * so the lead signal evaluator can fire {@code WATCHED_LONG}.
     */
    @Transactional
    public SessionAttendance heartbeat(UUID sessionId, UUID profileId, Integer offsetSeconds) {
        SessionAttendance row = attendanceRepository
                .findBySessionIdAndProfileId(sessionId, profileId)
                .orElseGet(() -> join(sessionId, profileId));

        Instant now = Instant.now();
        long delta = Duration.between(row.getLastSeenAt(), now).getSeconds();
        // Guard against clock skew and very long gaps: a gap longer
        // than 2 minutes means the viewer was actually away and we
        // should stop counting seconds. The dashboard will still see
        // them as "connected" — the row has no left_at yet — but
        // their running watch total won't balloon from idle time.
        if (delta > 0 && delta <= 120) {
            row.setTotalConnectedSeconds(row.getTotalConnectedSeconds() + (int) delta);
        }
        row.setLastSeenAt(now);

        boolean crossedMilestone = false;
        if (offsetSeconds != null && offsetSeconds >= 0) {
            Integer prevMax = row.getMaxOffsetSeconds();
            if (prevMax == null || offsetSeconds > prevMax) {
                row.setMaxOffsetSeconds(offsetSeconds);
            }
            if ((prevMax == null || prevMax < WATCHED_LONG_MILESTONE_SECONDS)
                    && offsetSeconds >= WATCHED_LONG_MILESTONE_SECONDS) {
                crossedMilestone = true;
            }
        }

        SessionAttendance saved = attendanceRepository.save(row);

        if (crossedMilestone) {
            Map<String, Object> meta = new HashMap<>();
            meta.put("thresholdSeconds", WATCHED_LONG_MILESTONE_SECONDS);
            meta.put("totalConnectedSeconds", saved.getTotalConnectedSeconds());
            analyticsRecorder.record(
                    sessionId, profileId, AnalyticsEventType.WATCH_MILESTONE,
                    offsetSeconds, meta);
        }

        return saved;
    }

    /**
     * Viewer left the room — tab closed, connection dropped, or
     * navigated away. The row is kept for historical reporting; we
     * just stamp {@code left_at} and finalise the connected-time
     * counter.
     */
    @Transactional
    public Optional<SessionAttendance> leave(UUID sessionId, UUID profileId) {
        Optional<SessionAttendance> maybe =
                attendanceRepository.findBySessionIdAndProfileId(sessionId, profileId);
        if (maybe.isEmpty()) {
            // Nothing to do — never joined, or already cleaned up.
            return Optional.empty();
        }

        SessionAttendance row = maybe.get();
        Instant now = Instant.now();

        long delta = Duration.between(row.getLastSeenAt(), now).getSeconds();
        if (delta > 0 && delta <= 120) {
            row.setTotalConnectedSeconds(row.getTotalConnectedSeconds() + (int) delta);
        }
        row.setLastSeenAt(now);
        row.setLeftAt(now);

        SessionAttendance saved = attendanceRepository.save(row);

        Integer offset = saved.getMaxOffsetSeconds();
        Map<String, Object> meta = new HashMap<>();
        meta.put("totalConnectedSeconds", saved.getTotalConnectedSeconds());
        meta.put("entryCount", saved.getEntryCount());
        analyticsRecorder.record(
                sessionId, profileId, AnalyticsEventType.ROOM_LEFT, offset, meta);

        return Optional.of(saved);
    }

    private Session requireSession(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
    }

    /**
     * Best-effort offset for a session: for a LIVE session that has
     * started, wall-clock elapsed from {@code actualStartedAt}; for
     * anything else we don't have a canonical offset and return null
     * so the analytics row simply stores no offset.
     */
    private static Integer currentOffsetSeconds(Session session) {
        Instant started = session.getActualStartedAt();
        if (started == null) return null;
        long secs = Duration.between(started, Instant.now()).getSeconds();
        return secs < 0 ? 0 : (int) secs;
    }
}
