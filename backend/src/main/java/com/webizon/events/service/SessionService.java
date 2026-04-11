package com.webizon.events.service;

import com.webizon.events.api.dto.SessionCreateRequest;
import com.webizon.events.api.dto.SessionUpdateRequest;
import com.webizon.events.model.Event;
import com.webizon.events.model.EventStatus;
import com.webizon.events.model.Session;
import com.webizon.events.model.SessionStatus;
import com.webizon.events.model.SessionType;
import com.webizon.events.repo.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Lifecycle + state-machine logic for {@link Session}.
 *
 * <p>The critical product invariants (CLAUDE.md §42, §48, §50) are enforced
 * here, not in the controllers:
 *
 * <ol>
 *   <li>Finished sessions are immutable. {@code finalized_at} is set on
 *       end/cancel and guards every write.</li>
 *   <li>Re-airing an event must create a new {@link SessionType#AUTO}
 *       session; you can never flip a finished row back to
 *       {@link SessionStatus#SCHEDULED}.</li>
 *   <li>AUTO sessions must be anchored to a recorded LIVE session via
 *       {@code source_live_session_id} — this service rejects any other
 *       combination, matching the DB {@code sessions_auto_source_chk}.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SessionService {

    private final SessionRepository sessionRepository;
    private final EventService eventService;

    // ---------------------------------------------------------------
    // Queries
    // ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public Session requireById(UUID id) {
        return sessionRepository.findById(id).orElseThrow(
                () -> new IllegalArgumentException("Session not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<Session> listByEvent(UUID eventId) {
        return sessionRepository.findAllByEventIdOrderByStartTimeAsc(eventId);
    }

    // ---------------------------------------------------------------
    // Creation
    // ---------------------------------------------------------------

    public Session create(UUID eventId, SessionCreateRequest request, UUID createdByUserId) {
        Event event = eventService.requireById(eventId);
        if (event.getStatus() == EventStatus.ARCHIVED) {
            throw new IllegalStateException("Cannot schedule sessions on an archived event");
        }

        Session session = new Session();
        session.setEventId(event.getId());
        session.setType(request.type());
        session.setStartTime(request.startTime());
        session.setPlannedDurationSeconds(request.plannedDurationSeconds());
        session.setCreatedByUserId(createdByUserId);

        switch (request.type()) {
            case LIVE -> {
                session.setStatus(SessionStatus.SCHEDULED);
                applyYoutubeUrl(session, request.youtubeUrl(), /*required*/ true);
                if (request.sourceLiveSessionId() != null) {
                    throw new IllegalArgumentException(
                            "LIVE sessions must not have a sourceLiveSessionId");
                }
            }
            case AUTO -> {
                session.setStatus(SessionStatus.AUTO_SCHEDULED);
                if (request.sourceLiveSessionId() == null) {
                    throw new IllegalArgumentException(
                            "AUTO sessions require sourceLiveSessionId (the LIVE recording to replay)");
                }
                Session source = requireById(request.sourceLiveSessionId());
                if (source.getType() != SessionType.LIVE) {
                    throw new IllegalArgumentException(
                            "sourceLiveSessionId must point to a LIVE session, was " + source.getType());
                }
                if (source.getStatus() != SessionStatus.ENDED) {
                    throw new IllegalStateException(
                            "Source LIVE session must be ENDED before it can be replayed (was " + source.getStatus() + ")");
                }
                if (!source.getEventId().equals(event.getId())) {
                    throw new IllegalArgumentException(
                            "sourceLiveSessionId belongs to a different event");
                }
                // An AUTO replay inherits the video from its source LIVE session.
                session.setYoutubeUrl(source.getYoutubeUrl());
                session.setYoutubeVideoId(source.getYoutubeVideoId());
                session.setYoutubeEmbedUrl(source.getYoutubeEmbedUrl());
                session.setSourceLiveSessionId(source.getId());
            }
        }

        return sessionRepository.save(session);
    }

    // ---------------------------------------------------------------
    // Update (pre-airing only)
    // ---------------------------------------------------------------

    public Session update(UUID id, SessionUpdateRequest request) {
        Session session = requireById(id);
        assertMutable(session);

        if (request.startTime() != null)               session.setStartTime(request.startTime());
        if (request.plannedDurationSeconds() != null)  session.setPlannedDurationSeconds(request.plannedDurationSeconds());

        // The video URL of an AUTO session is locked to its source LIVE — admins
        // must schedule a different source if they want a different recording.
        if (request.youtubeUrl() != null) {
            if (session.getType() == SessionType.AUTO) {
                throw new IllegalStateException(
                        "Cannot change the video URL of an AUTO session; change the source LIVE instead");
            }
            applyYoutubeUrl(session, request.youtubeUrl(), /*required*/ true);
        }
        return session;
    }

    // ---------------------------------------------------------------
    // State machine
    // ---------------------------------------------------------------

    public Session startLive(UUID id) {
        Session session = requireById(id);
        if (session.getType() != SessionType.LIVE) {
            throw new IllegalStateException("Only LIVE sessions can be started with startLive()");
        }
        if (session.getStatus() != SessionStatus.SCHEDULED) {
            throw new IllegalStateException("Cannot start a LIVE session in status " + session.getStatus());
        }
        session.setStatus(SessionStatus.LIVE);
        session.setActualStartedAt(Instant.now());
        return session;
    }

    public Session endLive(UUID id) {
        Session session = requireById(id);
        if (session.getType() != SessionType.LIVE) {
            throw new IllegalStateException("Only LIVE sessions can be ended with endLive()");
        }
        if (session.getStatus() != SessionStatus.LIVE) {
            throw new IllegalStateException("Cannot end a LIVE session in status " + session.getStatus());
        }
        Instant now = Instant.now();
        session.setStatus(SessionStatus.ENDED);
        session.setActualEndedAt(now);
        session.setFinalizedAt(now);
        return session;
    }

    public Session startAuto(UUID id) {
        Session session = requireById(id);
        if (session.getType() != SessionType.AUTO) {
            throw new IllegalStateException("Only AUTO sessions can be started with startAuto()");
        }
        if (session.getStatus() != SessionStatus.AUTO_SCHEDULED) {
            throw new IllegalStateException("Cannot start an AUTO session in status " + session.getStatus());
        }
        session.setStatus(SessionStatus.AUTO_LIVE);
        session.setActualStartedAt(Instant.now());
        return session;
    }

    public Session endAuto(UUID id) {
        Session session = requireById(id);
        if (session.getType() != SessionType.AUTO) {
            throw new IllegalStateException("Only AUTO sessions can be ended with endAuto()");
        }
        if (session.getStatus() != SessionStatus.AUTO_LIVE) {
            throw new IllegalStateException("Cannot end an AUTO session in status " + session.getStatus());
        }
        Instant now = Instant.now();
        session.setStatus(SessionStatus.AUTO_ENDED);
        session.setActualEndedAt(now);
        session.setFinalizedAt(now);
        return session;
    }

    public Session cancel(UUID id) {
        Session session = requireById(id);
        if (session.isFinalized()) {
            throw new IllegalStateException("Cannot cancel a finalized session");
        }
        if (session.getStatus().isAiring()) {
            throw new IllegalStateException(
                    "Cannot cancel an airing session; end it with endLive/endAuto instead");
        }
        Instant now = Instant.now();
        session.setStatus(SessionStatus.CANCELLED);
        session.setFinalizedAt(now);
        return session;
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private void assertMutable(Session session) {
        if (session.isFinalized()) {
            throw new IllegalStateException("Session is finalized and cannot be modified");
        }
        if (session.getStatus().isAiring()) {
            throw new IllegalStateException(
                    "Session is currently airing; editing its schedule is not allowed");
        }
    }

    private void applyYoutubeUrl(Session session, String rawUrl, boolean required) {
        if (rawUrl == null || rawUrl.isBlank()) {
            if (required) {
                throw new IllegalArgumentException("LIVE sessions require a youtubeUrl");
            }
            return;
        }
        YouTubeUrl parsed = YouTubeUrl.parse(rawUrl);
        session.setYoutubeUrl(parsed.originalUrl());
        session.setYoutubeVideoId(parsed.videoId());
        session.setYoutubeEmbedUrl(parsed.embedUrl());
    }
}
