package com.webizon.room.service;

import com.webizon.analytics.repo.SessionAttendanceRepository;
import com.webizon.analytics.service.AttendanceTracker;
import com.webizon.chat.model.ChatMessage;
import com.webizon.chat.model.EventChatSettings;
import com.webizon.chat.repo.ChatMessageRepository;
import com.webizon.chat.service.ChatSettingsService;
import com.webizon.cta.api.dto.CtaResponse;
import com.webizon.cta.model.EventCta;
import com.webizon.cta.repo.EventCtaRepository;
import com.webizon.events.api.dto.EventResponse;
import com.webizon.events.api.dto.SessionResponse;
import com.webizon.events.model.Event;
import com.webizon.events.model.Session;
import com.webizon.events.model.SessionType;
import com.webizon.events.repo.EventRepository;
import com.webizon.events.repo.SessionRepository;
import com.webizon.realtime.ChannelKind;
import com.webizon.realtime.ChannelNameFactory;
import com.webizon.room.api.dto.RoomBootstrapResponse;
import com.webizon.room.api.dto.RoomCapabilitiesView;
import com.webizon.room.api.dto.RoomChannelBundleView;
import com.webizon.room.api.dto.RoomChatMessageView;
import com.webizon.room.api.dto.RoomChatSettingsView;
import com.webizon.timeline.model.EventTimelineAction;
import com.webizon.timeline.model.TimelineActionType;
import com.webizon.timeline.repo.EventTimelineActionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Aggregates every piece of room state a viewer needs to render a
 * working session, and exposes it in a single bootstrap call.
 *
 * <p>Without this service the frontend would have to fan out five or
 * six GET requests before it could show anything (event, session,
 * chat settings, recent chat, active CTAs, attendance) — each one a
 * round trip across the CDN. Doing the fan-out on the server keeps
 * the number of public endpoints the frontend has to understand
 * small and lets us compute derived state (currently visible CTAs)
 * in one tenant-scoped transaction.
 *
 * <h2>Derived state: "currently visible CTAs"</h2>
 * The CTA table only tells us which CTAs an event <em>has</em>.
 * Which ones are <em>visible right now</em> is a function of the
 * timeline: fold every {@code CTA_SHOW} / {@code CTA_HIDE} row whose
 * offset is at or before the caller's current room offset, keyed by
 * {@code ctaId}, and the remaining {@code CTA_SHOW} entries are the
 * visible set. This works uniformly for LIVE and AUTO because both
 * modes use the same timeline rows as their source of truth.
 *
 * <h2>Auto-join</h2>
 * Bootstrap is the natural "I entered the room" moment, so the
 * service also calls {@link AttendanceTracker#join} here. The
 * analytics module stays decoupled from the room module — the room
 * service depends on the tracker, not the other way round.
 *
 * <h2>Final sessions</h2>
 * Bootstrapping an {@code ENDED} / {@code AUTO_ENDED} /
 * {@code CANCELLED} session is rejected. Viewers who click a stale
 * link should be sent back to the public resolver to see the current
 * state (e.g. "pick one of these upcoming slots"), not dropped into
 * a dead room.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoomService {

    /** Roles allowed to send chat messages beyond the policy chain's limits. */
    private static final Set<String> MODERATOR_ROLES = Set.of(
            "TENANT_OWNER",
            "TENANT_ADMIN",
            "TENANT_MODERATOR",
            "TENANT_PRESENTER"
    );

    /** Roles allowed to click "Show CTA" / "Hide CTA" at runtime. */
    private static final Set<String> CTA_TRIGGER_ROLES = Set.of(
            "TENANT_OWNER",
            "TENANT_ADMIN",
            "TENANT_MODERATOR",
            "TENANT_PRESENTER"
    );

    /** How many recent chat rows to seed the bootstrap pane with. */
    private static final int RECENT_CHAT_SEED = 50;

    private final SessionRepository sessionRepository;
    private final EventRepository eventRepository;
    private final ChatSettingsService chatSettingsService;
    private final ChatMessageRepository chatMessageRepository;
    private final EventCtaRepository eventCtaRepository;
    private final EventTimelineActionRepository timelineActionRepository;
    private final SessionAttendanceRepository sessionAttendanceRepository;
    private final AttendanceTracker attendanceTracker;
    private final ChannelNameFactory channelNameFactory;

    /**
     * Build the full bootstrap payload for {@code sessionId} on behalf
     * of the caller.
     *
     * @param sessionId the session the viewer is entering
     * @param profileId the authenticated Webizon profile id
     * @param role      the single-role claim from the JWT ({@code role})
     */
    @Transactional
    public RoomBootstrapResponse bootstrap(UUID sessionId, UUID profileId, String role) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        if (session.getStatus().isFinal()) {
            throw new IllegalStateException(
                    "Session " + sessionId + " is finalized (" + session.getStatus()
                            + "); nothing to join");
        }

        Event event = eventRepository.findById(session.getEventId())
                .orElseThrow(() -> new IllegalStateException(
                        "Session " + sessionId + " references missing event "
                                + session.getEventId()));

        Integer currentOffset = computeCurrentOffset(session);

        // Record the join early — if anything else in the bootstrap
        // throws, the viewer still shows up in live-room analytics,
        // which matches the real moment they entered the page. The
        // tracker is idempotent on re-entry.
        try {
            attendanceTracker.join(sessionId, profileId);
        } catch (Exception ex) {
            // Attendance is a best-effort side channel; we must not
            // break the bootstrap call when the tracker misbehaves.
            log.warn("Attendance join failed for session {} profile {}: {}",
                    sessionId, profileId, ex.getMessage());
        }

        EventChatSettings settings = chatSettingsService.findOrCreate(event.getId());
        List<RoomChatMessageView> recentChat = loadRecentChat(session);
        List<CtaResponse> activeCtas = resolveVisibleCtas(session, currentOffset);
        long presentNow = sessionAttendanceRepository.countPresentBySessionId(sessionId);

        RoomChannelBundleView channels = new RoomChannelBundleView(
                channelNameFactory.sessionChannel(session.getTenantId(), sessionId, ChannelKind.CHAT),
                channelNameFactory.sessionChannel(session.getTenantId(), sessionId, ChannelKind.CTA),
                channelNameFactory.sessionChannel(session.getTenantId(), sessionId, ChannelKind.PRESENCE),
                channelNameFactory.sessionChannel(session.getTenantId(), sessionId, ChannelKind.STATE)
        );

        RoomCapabilitiesView capabilities = buildCapabilities(role, settings);

        return new RoomBootstrapResponse(
                EventResponse.from(event),
                SessionResponse.from(session),
                RoomChatSettingsView.from(settings),
                recentChat,
                activeCtas,
                presentNow,
                currentOffset,
                channels,
                capabilities
        );
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Compute the room's current video offset, in seconds.
     *
     * <p>LIVE / AUTO_LIVE sessions have an {@code actualStartedAt}; we
     * derive the offset from wall-clock elapsed time, clamped at the
     * planned duration so a stale session right before ending does
     * not report a phantom offset past the recording's end.
     *
     * <p>Scheduled (not yet started) or waiting sessions have no
     * meaningful video offset — returning {@code null} lets the
     * frontend pick "show the countdown" rather than seeking the
     * video player to 00:00.
     */
    private Integer computeCurrentOffset(Session session) {
        Instant started = session.getActualStartedAt();
        if (started == null) {
            return null;
        }
        long elapsed = Duration.between(started, Instant.now()).getSeconds();
        if (elapsed < 0) {
            return 0;
        }
        int planned = session.getPlannedDurationSeconds();
        if (planned > 0 && elapsed > planned) {
            return planned;
        }
        return (int) elapsed;
    }

    /**
     * Load the latest {@link #RECENT_CHAT_SEED} visible chat rows for
     * this session, newest first — matches the order
     * {@code ChatMessageRepository.findLiveFeed} returns. The frontend
     * reverses this into chronological order when rendering.
     */
    private List<RoomChatMessageView> loadRecentChat(Session session) {
        List<ChatMessage> rows = chatMessageRepository.findLiveFeed(
                session.getId(), PageRequest.of(0, RECENT_CHAT_SEED));
        return rows.stream().map(RoomChatMessageView::from).toList();
    }

    /**
     * Resolve the set of CTAs a late-joining viewer should see as
     * "currently visible" at {@code offsetSeconds}.
     *
     * <p>Algorithm: take every active timeline row whose source session
     * is the LIVE session that drives playback (for LIVE that is the
     * session itself; for AUTO it is {@code sourceLiveSessionId}) and
     * whose offset is at or before the caller's current offset. Sort
     * by offset ascending and fold: each {@code CTA_SHOW} records the
     * CTA as visible, each {@code CTA_HIDE} removes it. What is left
     * is the visible set, ordered by descending CTA priority so the
     * frontend can stack-resolve with zero extra work.
     *
     * <p>Sessions that have not started yet have a {@code null}
     * current offset — in that case we return an empty list rather
     * than leaking the first-ever CTA_SHOW row before its time.
     */
    private List<CtaResponse> resolveVisibleCtas(Session session, Integer currentOffset) {
        if (currentOffset == null) {
            return List.of();
        }

        UUID driverSessionId = (session.getType() == SessionType.AUTO
                && session.getSourceLiveSessionId() != null)
                ? session.getSourceLiveSessionId()
                : session.getId();

        // Fetch every captured row for the driver session and filter
        // in memory by offset — timeline tables stay small (a few rows
        // per minute of broadcast) so the fold is trivially cheap.
        List<EventTimelineAction> captured = timelineActionRepository
                .findReplayQueueForSourceSession(driverSessionId);
        if (captured.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<UUID> visibleCtaIds = new LinkedHashSet<>();
        for (EventTimelineAction row : captured) {
            if (row.getOffsetSeconds() > currentOffset) {
                break;
            }
            TimelineActionType type = row.getActionType();
            if (type != TimelineActionType.CTA_SHOW && type != TimelineActionType.CTA_HIDE) {
                continue;
            }
            UUID ctaId = readUuid(row.getPayload(), "ctaId");
            if (ctaId == null) continue;
            if (type == TimelineActionType.CTA_SHOW) {
                visibleCtaIds.add(ctaId);
            } else {
                visibleCtaIds.remove(ctaId);
            }
        }

        if (visibleCtaIds.isEmpty()) {
            return List.of();
        }

        // Load the actual EventCta rows so we can return full
        // metadata (title, buttonText, placement...). Only active
        // CTAs are returned: if an admin deactivated a CTA after the
        // timeline row was captured, we honour the current state.
        List<EventCta> allActive = eventCtaRepository
                .findAllByEventIdAndActiveTrueOrderByPriorityDesc(session.getEventId());
        Map<UUID, EventCta> byId = allActive.stream()
                .collect(Collectors.toMap(EventCta::getId, c -> c));

        List<CtaResponse> ordered = new ArrayList<>(visibleCtaIds.size());
        for (UUID ctaId : visibleCtaIds) {
            EventCta cta = byId.get(ctaId);
            if (cta == null) continue;
            ordered.add(CtaResponse.from(cta));
        }
        // Sort by priority descending so the frontend can stack-
        // resolve without a second pass.
        ordered.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
        return Collections.unmodifiableList(ordered);
    }

    /**
     * Build the capability flags for the caller. The booleans are
     * hints for the UI only — every action is re-checked server-side.
     *
     * <p>A viewer with an empty or unknown role gets the plain-viewer
     * set: can chat (subject to policy), can reply, nothing else.
     */
    private RoomCapabilitiesView buildCapabilities(String role, EventChatSettings settings) {
        boolean isModerator = role != null && MODERATOR_ROLES.contains(role);
        boolean canTriggerCtas = role != null && CTA_TRIGGER_ROLES.contains(role);
        boolean bypassSlowMode = isModerator || settings.getSlowModeSeconds() == 0;
        return new RoomCapabilitiesView(
                true,
                true,
                isModerator,
                canTriggerCtas,
                bypassSlowMode
        );
    }

    /**
     * Parse a UUID-valued payload field. Mirrors the permissive logic
     * in {@code TimelineService.readUuid} — both String and already-
     * parsed UUID values are accepted; anything else yields null so
     * the caller can log-and-skip.
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
