package com.webizon.cta.service;

import com.webizon.config.CacheConfig;
import com.webizon.cta.model.CtaPlacement;
import com.webizon.cta.model.CtaType;
import com.webizon.cta.model.EventCta;
import com.webizon.cta.repo.EventCtaRepository;
import com.webizon.events.model.Event;
import com.webizon.events.model.Session;
import com.webizon.events.model.SessionStatus;
import com.webizon.events.model.SessionType;
import com.webizon.events.repo.EventRepository;
import com.webizon.events.repo.SessionRepository;
import com.webizon.timeline.model.EventTimelineAction;
import com.webizon.timeline.model.TimelineActionType;
import com.webizon.timeline.service.TimelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin-side CTA lifecycle plus the live "show" / "hide" pulses that
 * drive the room's CTA layer.
 *
 * <p>The service has two halves:
 *
 * <ol>
 *   <li><strong>CRUD</strong> — create, update, toggle, delete. These
 *       write through to {@code event_ctas} and never touch the
 *       real-time layer. Deactivation is the safe default: we only
 *       hard-delete when the caller explicitly asks for it, because
 *       any CTA that ever showed leaves references in analytics and
 *       historical timeline rows.</li>
 *   <li><strong>Live orchestration</strong> — {@link #showCta} and
 *       {@link #hideCta} are what an admin presses during a LIVE
 *       broadcast. They atomically (a) capture a timeline action so
 *       the same pulse replays in every AUTO session of this event,
 *       and (b) publish a {@link ChannelKind#CTA} envelope so every
 *       connected viewer sees the state change instantly.</li>
 * </ol>
 *
 * <p>During AUTO sessions the timeline replay path calls
 * {@link #broadcastShow} / {@link #broadcastHide} directly — it must
 * NOT re-capture a timeline row because the row is what triggered the
 * pulse in the first place. Capture is gated by the caller calling
 * {@code showCta}/{@code hideCta} vs {@code broadcastShow}/{@code
 * broadcastHide}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CtaService {

    private final EventCtaRepository ctaRepository;
    private final EventRepository eventRepository;
    private final SessionRepository sessionRepository;
    private final TimelineService timelineService;
    private final CtaBroadcaster ctaBroadcaster;

    // ------------------------------------------------------------------
    // CRUD
    // ------------------------------------------------------------------

    @CacheEvict(cacheNames = CacheConfig.CACHE_ACTIVE_CTAS, key = "#eventId")
    @Transactional
    public EventCta create(UUID eventId, UUID creatorUserId, CtaCreateCommand cmd) {
        Event event = requireEvent(eventId);
        validatePayload(cmd.type(), cmd.actionUrl(), cmd.fileUrl());

        EventCta cta = new EventCta();
        cta.setEventId(event.getId());
        cta.setTitle(cmd.title());
        cta.setDescription(cmd.description());
        cta.setType(cmd.type());
        cta.setButtonText(cmd.buttonText());
        cta.setActionUrl(cmd.actionUrl());
        cta.setFileUrl(cmd.fileUrl());
        cta.setPlacement(cmd.placement());
        cta.setPriority(cmd.priority());
        cta.setAllowStack(cmd.allowStack());
        cta.setActive(true);
        cta.setCreatedByUserId(creatorUserId);

        return ctaRepository.save(cta);
    }

    @CacheEvict(cacheNames = CacheConfig.CACHE_ACTIVE_CTAS, key = "#eventId")
    @Transactional
    public EventCta update(UUID eventId, UUID ctaId, CtaUpdateCommand cmd) {
        EventCta cta = requireCta(eventId, ctaId);
        if (cmd.title() != null)       cta.setTitle(cmd.title());
        if (cmd.description() != null) cta.setDescription(cmd.description());
        if (cmd.buttonText() != null)  cta.setButtonText(cmd.buttonText());
        if (cmd.actionUrl() != null)   cta.setActionUrl(cmd.actionUrl());
        if (cmd.fileUrl() != null)     cta.setFileUrl(cmd.fileUrl());
        if (cmd.placement() != null)   cta.setPlacement(cmd.placement());
        if (cmd.priority() != null)    cta.setPriority(cmd.priority());
        if (cmd.allowStack() != null)  cta.setAllowStack(cmd.allowStack());
        // Re-validate payload with whatever shape the CTA now has.
        validatePayload(cta.getType(), cta.getActionUrl(), cta.getFileUrl());
        return cta;
    }

    @CacheEvict(cacheNames = CacheConfig.CACHE_ACTIVE_CTAS, key = "#eventId")
    @Transactional
    public EventCta toggleActive(UUID eventId, UUID ctaId, boolean active) {
        EventCta cta = requireCta(eventId, ctaId);
        cta.setActive(active);
        return cta;
    }

    @CacheEvict(cacheNames = CacheConfig.CACHE_ACTIVE_CTAS, key = "#eventId")
    @Transactional
    public void delete(UUID eventId, UUID ctaId) {
        EventCta cta = requireCta(eventId, ctaId);
        ctaRepository.delete(cta);
    }

    @Transactional(readOnly = true)
    public List<EventCta> list(UUID eventId) {
        requireEvent(eventId);
        return ctaRepository.findAllByEventIdOrderByPriorityDesc(eventId);
    }

    /**
     * Active CTAs for an event, ordered by priority. Cached under
     * {@link CacheConfig#CACHE_ACTIVE_CTAS} keyed by {@code eventId}:
     * {@code RoomService.bootstrap} calls this on every room join to
     * seed the client's visible-CTA list, so a busy event's join
     * burst used to serialise on this query.
     *
     * <p>The cache is evicted whenever a CTA is created, updated,
     * toggled, or deleted for the event, so admin panel changes flip
     * in without a restart.
     */
    @Cacheable(cacheNames = CacheConfig.CACHE_ACTIVE_CTAS, key = "#eventId")
    @Transactional(readOnly = true)
    public List<EventCta> listActive(UUID eventId) {
        requireEvent(eventId);
        return ctaRepository.findAllByEventIdAndActiveTrueOrderByPriorityDesc(eventId);
    }

    // ------------------------------------------------------------------
    // Live orchestration
    // ------------------------------------------------------------------

    /**
     * Admin clicks "Show CTA" during a LIVE session. Captures a
     * timeline row (so AUTO replays will re-emit this pulse) and
     * broadcasts the show envelope to connected viewers.
     */
    @Transactional
    public EventTimelineAction showCta(UUID sessionId, UUID ctaId, UUID moderatorId) {
        Session session = requireSession(sessionId);
        assertSessionAcceptsTimelineCapture(session);
        EventCta cta = requireCta(session.getEventId(), ctaId);

        int offset = computeOffsetSeconds(session);
        EventTimelineAction row = timelineService.captureAction(
                session, offset, TimelineActionType.CTA_SHOW,
                Map.of("ctaId", cta.getId().toString()),
                moderatorId);

        ctaBroadcaster.broadcastShow(session, cta, offset);
        return row;
    }

    /**
     * Admin clicks "Hide CTA". Captures a CTA_HIDE timeline row and
     * broadcasts the hide envelope so every connected client drops
     * the CTA from its placement immediately.
     */
    @Transactional
    public EventTimelineAction hideCta(UUID sessionId, UUID ctaId, UUID moderatorId) {
        Session session = requireSession(sessionId);
        assertSessionAcceptsTimelineCapture(session);
        EventCta cta = requireCta(session.getEventId(), ctaId);

        int offset = computeOffsetSeconds(session);
        EventTimelineAction row = timelineService.captureAction(
                session, offset, TimelineActionType.CTA_HIDE,
                Map.of("ctaId", cta.getId().toString()),
                moderatorId);

        ctaBroadcaster.broadcastHide(session, cta, offset);
        return row;
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

    private EventCta requireCta(UUID eventId, UUID ctaId) {
        return ctaRepository.findByIdAndEventId(ctaId, eventId)
                .orElseThrow(() -> new IllegalArgumentException("CTA not found: " + ctaId));
    }

    /**
     * Timeline capture is only legal while a LIVE session is airing
     * (status = LIVE). AUTO sessions must never capture — they are
     * the replay side of the equation — and a session that has not
     * started yet has no meaningful offset.
     */
    private void assertSessionAcceptsTimelineCapture(Session session) {
        if (session.getType() != SessionType.LIVE) {
            throw new IllegalStateException("Timeline actions can only be captured in LIVE sessions");
        }
        if (session.getStatus() != SessionStatus.LIVE) {
            throw new IllegalStateException("Session is not currently airing");
        }
        if (session.getActualStartedAt() == null) {
            throw new IllegalStateException("Session has no actualStartedAt — cannot compute offset");
        }
    }

    private int computeOffsetSeconds(Session session) {
        long seconds = java.time.Duration.between(
                session.getActualStartedAt(), Instant.now()).getSeconds();
        return seconds < 0 ? 0 : (int) seconds;
    }

    /**
     * Payload constraints mirror the {@code event_ctas_payload_chk} DB
     * CHECK: FILE requires fileUrl, everything else requires actionUrl.
     */
    private void validatePayload(CtaType type, String actionUrl, String fileUrl) {
        if (type == CtaType.FILE) {
            if (fileUrl == null || fileUrl.isBlank()) {
                throw new IllegalArgumentException("FILE CTA requires a fileUrl");
            }
        } else {
            if (actionUrl == null || actionUrl.isBlank()) {
                throw new IllegalArgumentException(type + " CTA requires an actionUrl");
            }
        }
    }

    // ------------------------------------------------------------------
    // Command records
    // ------------------------------------------------------------------

    public record CtaCreateCommand(
            String title,
            String description,
            CtaType type,
            String buttonText,
            String actionUrl,
            String fileUrl,
            CtaPlacement placement,
            int priority,
            boolean allowStack
    ) {}

    public record CtaUpdateCommand(
            String title,
            String description,
            String buttonText,
            String actionUrl,
            String fileUrl,
            CtaPlacement placement,
            Integer priority,
            Boolean allowStack
    ) {}
}
