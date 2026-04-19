package com.webizon.timeline.service;

import com.webizon.cta.model.EventCta;
import com.webizon.cta.repo.EventCtaRepository;
import com.webizon.cta.service.CtaBroadcaster;
import com.webizon.events.model.Session;
import com.webizon.events.repo.SessionRepository;
import com.webizon.timeline.model.EventTimelineAction;
import com.webizon.timeline.model.TimelineActionType;
import com.webizon.timeline.repo.EventTimelineActionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The heart of the Timeline Engine.
 *
 * <p>There are three orthogonal jobs here:
 *
 * <ol>
 *   <li><strong>Capture</strong> — when an admin performs an action
 *       during a LIVE session (CTA show, admin message, etc.), the
 *       caller records a timeline row via {@link #captureAction}. The
 *       row is keyed by {@code sourceSessionId = liveSession.id} and
 *       carries the video-relative offset.</li>
 *   <li><strong>Edit</strong> — admins can tweak offsets, update
 *       payloads, or deactivate rows before an AUTO session is
 *       scheduled. {@link #updateAction}, {@link #setActive}, and
 *       {@link #deleteAction} handle those flows.</li>
 *   <li><strong>Replay</strong> — during an AUTO session, the room's
 *       playback loop calls {@link #replayAction} whenever it passes
 *       a row's offset. This method does the action-type dispatch:
 *       CTA rows publish through {@link CtaBroadcaster}, message rows
 *       go through {@code ChatService}, etc.</li>
 * </ol>
 *
 * <p>The capture and replay paths are deliberately separated to avoid
 * a feedback loop: a replayed CTA_SHOW must NOT create a fresh
 * timeline row, because that row would itself be replayed in the
 * next AUTO session, and the table would grow without bound.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TimelineService {

    private final EventTimelineActionRepository timelineRepository;
    private final EventCtaRepository ctaRepository;
    private final SessionRepository sessionRepository;
    private final CtaBroadcaster ctaBroadcaster;

    // ------------------------------------------------------------------
    // Capture (LIVE side)
    // ------------------------------------------------------------------

    /**
     * Persist a new timeline row tied to a LIVE session.
     *
     * <p>Callers are responsible for having already validated that the
     * session is in a state where capture is legal ({@code LIVE},
     * {@code actualStartedAt != null}) — this method trusts its
     * caller and only validates that the session row exists.
     */
    @Transactional
    public EventTimelineAction captureAction(Session session,
                                              int offsetSeconds,
                                              TimelineActionType type,
                                              Map<String, Object> payload,
                                              UUID creatorUserId) {
        if (offsetSeconds < 0) {
            throw new IllegalArgumentException("offsetSeconds must be >= 0");
        }

        EventTimelineAction row = new EventTimelineAction();
        row.setEventId(session.getEventId());
        row.setSourceSessionId(session.getId());
        row.setOffsetSeconds(offsetSeconds);
        row.setActionType(type);
        row.setPayload(payload == null ? new HashMap<>() : new HashMap<>(payload));
        row.setActive(true);
        row.setCreatedByUserId(creatorUserId);
        return timelineRepository.save(row);
    }

    /**
     * Manual admin entry — the admin authors a timeline row without
     * having to "live" the offset in real time. Used for pre-building
     * the timeline of a pre-recorded event before its first AUTO
     * slot.
     */
    @Transactional
    public EventTimelineAction manualInsert(UUID eventId,
                                             UUID sourceSessionId,
                                             int offsetSeconds,
                                             TimelineActionType type,
                                             Map<String, Object> payload,
                                             UUID creatorUserId) {
        Session source = sessionRepository.findById(sourceSessionId)
                .orElseThrow(() -> new IllegalArgumentException("Source session not found: " + sourceSessionId));
        if (!source.getEventId().equals(eventId)) {
            throw new IllegalArgumentException("Source session belongs to a different event");
        }
        return captureAction(source, offsetSeconds, type, payload, creatorUserId);
    }

    // ------------------------------------------------------------------
    // Admin edit
    // ------------------------------------------------------------------

    @Transactional
    public EventTimelineAction updateAction(UUID actionId,
                                             Integer offsetSeconds,
                                             Map<String, Object> payload) {
        EventTimelineAction row = requireAction(actionId);
        if (offsetSeconds != null) {
            if (offsetSeconds < 0) {
                throw new IllegalArgumentException("offsetSeconds must be >= 0");
            }
            row.setOffsetSeconds(offsetSeconds);
        }
        if (payload != null) {
            row.setPayload(new HashMap<>(payload));
        }
        return row;
    }

    @Transactional
    public EventTimelineAction setActive(UUID actionId, boolean active) {
        EventTimelineAction row = requireAction(actionId);
        row.setActive(active);
        return row;
    }

    @Transactional
    public void deleteAction(UUID actionId) {
        EventTimelineAction row = requireAction(actionId);
        timelineRepository.delete(row);
    }

    // ------------------------------------------------------------------
    // Reads
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<EventTimelineAction> listForSourceSession(UUID sourceSessionId) {
        return timelineRepository.findAllBySourceSessionIdOrderByOffsetSecondsAsc(sourceSessionId);
    }

    @Transactional(readOnly = true)
    public List<EventTimelineAction> replayQueue(UUID sourceSessionId) {
        return timelineRepository.findReplayQueueForSourceSession(sourceSessionId);
    }

    /**
     * Actions a late-joining AUTO viewer needs to catch up on, starting
     * from their current offset. The frontend uses this after a
     * reconnect so viewers arriving 3 minutes into a slot get the
     * CTA that was already shown at 00:45.
     */
    @Transactional(readOnly = true)
    public List<EventTimelineAction> replayQueueFromOffset(UUID sourceSessionId, int fromOffset) {
        return timelineRepository.findReplayQueueFromOffset(sourceSessionId, Math.max(0, fromOffset));
    }

    // ------------------------------------------------------------------
    // Replay (AUTO side)
    // ------------------------------------------------------------------

    /**
     * Emit one timeline row on behalf of a running AUTO session.
     *
     * <p>The offset at which this row actually fires inside the AUTO
     * session is the row's own {@code offsetSeconds} by design — we
     * intentionally use the captured offset rather than "seconds
     * since AUTO start" so re-joining viewers see CTAs at the correct
     * video moment even if their local playback clock has drifted.
     *
     * <p>This method dispatches on {@code actionType}; unknown types
     * are logged and ignored so an old service version never crashes
     * on a row inserted by a newer writer.
     */
    @Transactional
    public void replayAction(Session autoSession, EventTimelineAction row) {
        if (row == null || !row.isActive()) {
            return;
        }
        switch (row.getActionType()) {
            case CTA_SHOW -> replayCtaShow(autoSession, row);
            case CTA_HIDE -> replayCtaHide(autoSession, row);
            case ADMIN_MESSAGE_SHOW,
                 SYSTEM_MESSAGE_SHOW,
                 HISTORICAL_CHAT_REPLAY,
                 ROOM_STATE_CHANGE ->
                    // These action types are delivered by other subsystems
                    // (ChatService, RoomStateService). Phase 5 keeps them
                    // as structural placeholders so the DB schema is
                    // stable; the wiring will land in Phase 6 alongside
                    // the analytics pipeline that consumes them.
                    log.debug("Timeline row {} of type {} is a no-op in Phase 5",
                            row.getId(), row.getActionType());
            case FUTURE_RESERVED ->
                    log.debug("Skipping FUTURE_RESERVED timeline row {}", row.getId());
        }
    }

    private void replayCtaShow(Session autoSession, EventTimelineAction row) {
        UUID ctaId = readUuid(row.getPayload(), "ctaId");
        if (ctaId == null) {
            log.warn("CTA_SHOW timeline row {} has no ctaId payload; skipping", row.getId());
            return;
        }
        EventCta cta = ctaRepository.findByIdAndEventId(ctaId, autoSession.getEventId())
                .orElse(null);
        if (cta == null) {
            log.warn("CTA_SHOW timeline row {} references missing CTA {}; skipping",
                    row.getId(), ctaId);
            return;
        }
        if (!cta.isActive()) {
            // The CTA was deactivated after the timeline row was captured.
            // Honour the current state rather than the frozen row.
            return;
        }
        ctaBroadcaster.broadcastShow(autoSession, cta, row.getOffsetSeconds());
    }

    private void replayCtaHide(Session autoSession, EventTimelineAction row) {
        UUID ctaId = readUuid(row.getPayload(), "ctaId");
        if (ctaId == null) {
            log.warn("CTA_HIDE timeline row {} has no ctaId payload; skipping", row.getId());
            return;
        }
        EventCta cta = ctaRepository.findByIdAndEventId(ctaId, autoSession.getEventId())
                .orElse(null);
        if (cta == null) {
            // Missing CTA on a hide pulse is fine — there is nothing to hide.
            return;
        }
        ctaBroadcaster.broadcastHide(autoSession, cta, row.getOffsetSeconds());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private EventTimelineAction requireAction(UUID actionId) {
        return timelineRepository.findById(actionId)
                .orElseThrow(() -> new IllegalArgumentException("Timeline action not found: " + actionId));
    }

    /**
     * Read a UUID-valued field from a JSONB payload. Both String and
     * already-parsed UUID values are accepted; anything else returns
     * null so the caller can log-and-skip gracefully.
     */
    private static UUID readUuid(Map<String, Object> payload, String key) {
        if (payload == null) return null;
        Object raw = payload.get(key);
        if (raw == null) return null;
        if (raw instanceof UUID u) return u;
        if (raw instanceof String s) {
            try {
                return UUID.fromString(s);
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
        return null;
    }
}
