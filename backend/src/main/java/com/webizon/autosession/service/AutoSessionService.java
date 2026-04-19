package com.webizon.autosession.service;

import com.webizon.events.model.Event;
import com.webizon.events.model.EventStatus;
import com.webizon.events.model.Session;
import com.webizon.events.model.SessionStatus;
import com.webizon.events.model.SessionType;
import com.webizon.events.repo.EventRepository;
import com.webizon.events.repo.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Admin surface for creating and listing {@code AUTO} replay sessions.
 *
 * <p>This sits alongside {@code SessionService} but focuses exclusively
 * on the replay path:
 *
 * <ol>
 *   <li>After a {@code LIVE} session ends, an admin can schedule one
 *       or more {@code AUTO} sessions that will replay the same
 *       recording — and the same timeline of CTAs / admin messages /
 *       chat — at future wall-clock times.</li>
 *   <li>Each AUTO session is a brand new {@link Session} row with
 *       {@code type = AUTO}, {@code status = AUTO_SCHEDULED}, and
 *       {@code sourceLiveSessionId} pointing at the completed LIVE
 *       run. The video URL is inherited from the source; admins can't
 *       edit it because the timeline offsets are already anchored to
 *       that recording.</li>
 *   <li>Public viewers see these as "next showing" slots in the
 *       landing-page resolver and pick whichever one fits their
 *       schedule.</li>
 * </ol>
 *
 * <h2>Why the source LIVE session must be ENDED</h2>
 * Until the source is ENDED we don't have a complete timeline or a
 * stable chat transcript — the replay engine would dispatch a partial
 * history and the real-time CTA ordering could still change. The
 * check mirrors {@code SessionService.create} but is repeated here so
 * the call site reads cleanly.
 *
 * <h2>Why not reuse {@code SessionService.create}?</h2>
 * It covers both LIVE and AUTO cases by type-switching on a single
 * request DTO. For Phase 8 the AUTO flow gains extra behaviour
 * (bulk slot creation, constraint that the source must be the
 * latest completed LIVE, etc.) that doesn't belong in the LIVE
 * path. Splitting keeps each surface simple.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutoSessionService {

    /** AUTO slots further away than this are rejected — a sanity guard. */
    private static final Duration MAX_SLOT_LEAD_TIME = Duration.ofDays(365);

    /** Slots closer than this to "now" are rejected — prevents accidental live-fire. */
    private static final Duration MIN_SLOT_LEAD_TIME = Duration.ofMinutes(1);

    private final EventRepository eventRepository;
    private final SessionRepository sessionRepository;

    /**
     * Create a single AUTO replay slot from an existing ENDED LIVE
     * session.
     *
     * @param eventId             target event — must own both the
     *                            source session and the new slot
     * @param sourceLiveSessionId the ENDED live session whose
     *                            recording and timeline will replay
     * @param startTime           wall-clock start for the new slot;
     *                            must be at least {@link #MIN_SLOT_LEAD_TIME}
     *                            in the future and no more than
     *                            {@link #MAX_SLOT_LEAD_TIME} away
     * @param createdByUserId     admin who scheduled the slot
     * @return the new AUTO_SCHEDULED session row
     */
    @Transactional
    public Session createSlot(UUID eventId,
                              UUID sourceLiveSessionId,
                              Instant startTime,
                              UUID createdByUserId) {
        Event event = requireEvent(eventId);
        if (event.getStatus() == EventStatus.ARCHIVED) {
            throw new IllegalStateException("Cannot schedule AUTO slots on an archived event");
        }

        Session source = requireSession(sourceLiveSessionId);
        if (!source.getEventId().equals(event.getId())) {
            throw new IllegalArgumentException("Source session belongs to a different event");
        }
        if (source.getType() != SessionType.LIVE) {
            throw new IllegalArgumentException(
                    "sourceLiveSessionId must point to a LIVE session, was " + source.getType());
        }
        if (source.getStatus() != SessionStatus.ENDED) {
            throw new IllegalStateException(
                    "Source LIVE session must be ENDED before it can be replayed (was "
                            + source.getStatus() + ")");
        }

        Instant now = Instant.now();
        if (startTime == null) {
            throw new IllegalArgumentException("startTime is required");
        }
        if (startTime.isBefore(now.plus(MIN_SLOT_LEAD_TIME))) {
            throw new IllegalArgumentException(
                    "AUTO slot must be at least " + MIN_SLOT_LEAD_TIME.toMinutes() + " minute(s) in the future");
        }
        if (startTime.isAfter(now.plus(MAX_SLOT_LEAD_TIME))) {
            throw new IllegalArgumentException(
                    "AUTO slot must be within " + MAX_SLOT_LEAD_TIME.toDays() + " days of now");
        }

        // Planned duration defaults to the source session's own
        // planned duration so the AUTO run matches the original video
        // length. Admins can shorten the source's planned duration
        // before scheduling slots if they want a cut-down version.
        Session slot = new Session();
        slot.setEventId(event.getId());
        slot.setType(SessionType.AUTO);
        slot.setStatus(SessionStatus.AUTO_SCHEDULED);
        slot.setStartTime(startTime);
        slot.setPlannedDurationSeconds(source.getPlannedDurationSeconds());
        slot.setYoutubeUrl(source.getYoutubeUrl());
        slot.setYoutubeVideoId(source.getYoutubeVideoId());
        slot.setYoutubeEmbedUrl(source.getYoutubeEmbedUrl());
        slot.setSourceLiveSessionId(source.getId());
        slot.setCreatedByUserId(createdByUserId);
        slot.setLastReplayOffsetSeconds(0);

        Session saved = sessionRepository.save(slot);
        log.info("Scheduled AUTO slot {} for event {} at {} (source live {})",
                saved.getId(), event.getId(), startTime, source.getId());
        return saved;
    }

    /**
     * Create several AUTO slots in a single call — the common admin
     * workflow is "run this recording 3 times over the next week".
     * Any individual validation failure aborts the whole operation
     * so admins don't end up with half a schedule.
     */
    @Transactional
    public List<Session> createSlots(UUID eventId,
                                     UUID sourceLiveSessionId,
                                     List<Instant> startTimes,
                                     UUID createdByUserId) {
        if (startTimes == null || startTimes.isEmpty()) {
            throw new IllegalArgumentException("startTimes must not be empty");
        }
        return startTimes.stream()
                .map(st -> createSlot(eventId, sourceLiveSessionId, st, createdByUserId))
                .toList();
    }

    /**
     * List upcoming AUTO slots for an event — the slot selector on
     * the landing page calls this after a LIVE session has ended.
     */
    @Transactional(readOnly = true)
    public List<Session> listUpcomingSlots(UUID eventId) {
        return sessionRepository.findUpcomingAutoSlotsByEventId(eventId, Instant.now());
    }

    /**
     * Cancel an AUTO slot that hasn't started yet. Running the
     * {@code cancel} transition via {@code SessionService} works
     * identically, but exposing it here keeps the admin UI's auto-
     * session screen free of cross-service calls.
     */
    @Transactional
    public Session cancelSlot(UUID sessionId) {
        Session slot = requireSession(sessionId);
        if (slot.getType() != SessionType.AUTO) {
            throw new IllegalStateException("Can only cancel AUTO slots through AutoSessionService");
        }
        if (slot.isFinalized()) {
            throw new IllegalStateException("Slot is already finalized");
        }
        if (slot.getStatus() != SessionStatus.AUTO_SCHEDULED) {
            throw new IllegalStateException(
                    "Can only cancel slots in AUTO_SCHEDULED status (was " + slot.getStatus() + ")");
        }
        Instant now = Instant.now();
        slot.setStatus(SessionStatus.CANCELLED);
        slot.setFinalizedAt(now);
        return slot;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Event requireEvent(UUID eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
    }

    private Session requireSession(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
    }
}
