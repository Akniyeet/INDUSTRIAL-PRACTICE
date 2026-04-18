package com.webizon.autosession.service;

import com.webizon.chat.model.ChatMessage;
import com.webizon.chat.repo.ChatMessageRepository;
import com.webizon.events.model.Session;
import com.webizon.events.model.SessionStatus;
import com.webizon.events.repo.SessionRepository;
import com.webizon.realtime.CentrifugoClient;
import com.webizon.realtime.ChannelKind;
import com.webizon.realtime.ChannelNameFactory;
import com.webizon.tenancy.AllowCrossTenant;
import com.webizon.tenancy.TenantContext;
import com.webizon.tenancy.model.Tenant;
import com.webizon.tenancy.repo.TenantRepository;
import com.webizon.timeline.model.EventTimelineAction;
import com.webizon.timeline.repo.EventTimelineActionRepository;
import com.webizon.timeline.service.TimelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Drives playback of AUTO sessions one tick at a time.
 *
 * <p>An AUTO session is a pre-recorded YouTube broadcast whose admin
 * actions (CTA show/hide, chat that happened during the original LIVE
 * run) have been captured with video-relative offsets. This engine
 * replays that captured timeline at wall-clock speed so viewers of the
 * AUTO session experience the same rhythm as the original audience,
 * without an operator in the room.
 *
 * <h2>Separation of concerns</h2>
 * <ul>
 *   <li>{@link AutoSessionLifecycleScheduler} decides <em>when</em> a
 *       session is in {@code AUTO_LIVE} at all. Its tick is coarse
 *       (2 s) and cheap — a handful of state transitions per tenant.</li>
 *   <li>{@link TimelineReplayEngine} (this class) decides <em>what
 *       plays</em> during the {@code AUTO_LIVE} window. Its tick is
 *       finer (1 s) because CTA and chat dispatches need to hit their
 *       mark within a second or two of the captured offset.</li>
 * </ul>
 * The two loops could share a scheduler, but separating them means
 * lifecycle work never stalls when a chat window is fat, and replay
 * never waits on a global lock held by lifecycle transitions.
 *
 * <h2>Cursor model</h2>
 * Each AUTO session carries {@code lastReplayOffsetSeconds} — the
 * highest video-offset the engine has already dispatched. Every tick
 * does three things for one session:
 * <ol>
 *   <li>Computes {@code currentOffsetSeconds = now − actualStartedAt},
 *       capped at the source's planned duration so clock drift cannot
 *       read past the end of the recording.</li>
 *   <li>Fetches timeline rows and replay-eligible chat rows whose
 *       offsets fall inside the half-open window
 *       {@code (lastReplayOffsetSeconds, currentOffsetSeconds]}. The
 *       left edge is exclusive so a row dispatched on tick N cannot be
 *       re-dispatched on tick N+1, even when the cursor is advanced to
 *       exactly that row's offset.</li>
 *   <li>Publishes the rows (CTAs via {@link TimelineService#replayAction},
 *       historical chat via a direct Centrifugo publish to the AUTO
 *       session's CHAT channel), then persists the new cursor value in
 *       the same transaction. If the publish succeeds but the cursor
 *       save fails, the next tick re-publishes — at-least-once is
 *       acceptable because the client de-duplicates by message id.</li>
 * </ol>
 *
 * <h2>Tenant iteration</h2>
 * Like the lifecycle scheduler, this runs outside any HTTP request and
 * uses {@code TenantRepository.findAll()} (the {@code tenants} table
 * has no RLS) before pivoting into each tenant's scope via
 * {@link TenantContext#runWith(UUID, Runnable)}. Per-tenant work runs
 * in its own transaction so a corrupt row in one workspace cannot
 * roll back playback for another.
 *
 * <h2>Historical chat envelope</h2>
 * Replayed chat rows are published on the AUTO session's CHAT channel
 * (not the source LIVE session's channel) using the same field layout
 * as {@code ChatService.publishToChannel} plus a {@code historical}
 * flag so clients can visually differentiate replay content from
 * current-viewer chat in the same stream.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@AllowCrossTenant(reason = "Background job: enumerates all tenants and replays AUTO session timelines.")
public class TimelineReplayEngine {

    /**
     * Hard cap on a single tick's window size. If the engine fell
     * behind (GC pause, DB stall) we still never try to dispatch more
     * than this many seconds at once — anything older is skipped and
     * logged, on the theory that late-delivered CTAs are worse than
     * missed ones.
     */
    private static final int MAX_WINDOW_SECONDS = 600;

    private final TenantRepository tenantRepository;
    private final SessionRepository sessionRepository;
    private final EventTimelineActionRepository timelineActionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final TimelineService timelineService;
    private final CentrifugoClient centrifugoClient;
    private final ChannelNameFactory channelNameFactory;
    private final TransactionTemplate transactionTemplate;

    // ------------------------------------------------------------------
    // Tenant-list cache
    // ------------------------------------------------------------------
    // Tenants rarely appear / disappear during a 60-second window, yet
    // the raw replay loop used to reload the full set every tick. That
    // was one cross-tenant SELECT on the `tenants` table per second
    // regardless of traffic. We now cache for up to 60s unless the
    // engine successfully processed zero AUTO_LIVE sessions AND the
    // cache is older than the shorter refresh window. The cache is only
    // read from one scheduler thread so volatile is enough — no lock.
    private static final long TENANT_CACHE_TTL_MS = 60_000L;
    private volatile List<UUID> cachedTenantIds = List.of();
    private volatile long cachedTenantIdsAtMs = 0L;

    @Scheduled(fixedDelayString = "${webizon.auto-session.replay-tick-ms:1000}",
               initialDelayString = "${webizon.auto-session.replay-initial-delay-ms:6000}")
    public void tick() {
        List<UUID> tenantIds = loadTenantIds();
        if (tenantIds.isEmpty()) return;

        for (UUID tenantId : tenantIds) {
            try {
                TenantContext.runWith(tenantId,
                        () -> transactionTemplate.executeWithoutResult(
                                status -> runForTenant(tenantId)));
            } catch (Exception ex) {
                // Isolated failure per tenant — next tick retries the
                // same cursor position, so a transient DB or Centrifugo
                // hiccup self-heals without a supervisor restart.
                log.error("Timeline replay tick failed for tenant {}: {}",
                        tenantId, ex.getMessage(), ex);
            }
        }
    }

    /**
     * Loads the full set of tenant IDs with a 60-second in-memory cache.
     *
     * <p>At steady state the replay loop only cares that the cache is
     * "fresh enough" — a tenant created just now can wait 60s for its
     * first AUTO session replay; the alternative is 1 qps against the
     * tenants table for the life of the process.
     *
     * <p>If the DB read fails we keep serving the previous cached list
     * rather than collapsing to empty, so a transient DB blip does not
     * starve in-flight AUTO sessions of replay ticks.
     */
    private List<UUID> loadTenantIds() {
        long now = System.currentTimeMillis();
        if (now - cachedTenantIdsAtMs < TENANT_CACHE_TTL_MS && !cachedTenantIds.isEmpty()) {
            return cachedTenantIds;
        }
        try {
            List<UUID> fresh = transactionTemplate.execute(status ->
                    tenantRepository.findAll().stream().map(Tenant::getId).toList());
            if (fresh != null) {
                cachedTenantIds = fresh;
                cachedTenantIdsAtMs = now;
            }
            return cachedTenantIds;
        } catch (Exception ex) {
            log.error("Failed to enumerate tenants for timeline replay tick: {}",
                    ex.getMessage(), ex);
            // Fall back to whatever we had cached — never leave the
            // loop empty on a transient failure.
            return cachedTenantIds;
        }
    }

    private void runForTenant(UUID tenantId) {
        Instant now = Instant.now();
        List<Session> airing = sessionRepository.findAllByStatus(SessionStatus.AUTO_LIVE);
        for (Session session : airing) {
            try {
                replayOneSession(session, now);
            } catch (Exception ex) {
                // Do NOT let one broken AUTO session poison the rest of
                // the tenant's loop — the containing transaction still
                // commits cursor advances for the sessions processed
                // before this one.
                log.error("Replay failed for session {} (tenant {}): {}",
                        session.getId(), tenantId, ex.getMessage(), ex);
            }
        }
    }

    private void replayOneSession(Session session, Instant now) {
        Instant started = session.getActualStartedAt();
        if (started == null) {
            // Defensive: an AUTO_LIVE row should always have this set
            // by the lifecycle scheduler's promotion step. Skip without
            // crashing the loop.
            return;
        }
        UUID sourceId = session.getSourceLiveSessionId();
        if (sourceId == null) {
            log.warn("AUTO session {} has no sourceLiveSessionId; skipping replay", session.getId());
            return;
        }

        long elapsedRaw = Duration.between(started, now).getSeconds();
        if (elapsedRaw <= 0) {
            return;
        }
        // Never advance past the recording's own length — anything
        // beyond plannedDurationSeconds is "after the video ended" and
        // has no timeline rows anyway. Capping the cursor also stops
        // clock skew from inventing phantom offsets.
        int planned = session.getPlannedDurationSeconds();
        int currentOffset = (int) Math.min(elapsedRaw, planned);
        int lastOffset = session.getLastReplayOffsetSeconds();
        if (currentOffset <= lastOffset) {
            return;
        }

        int windowStart = lastOffset;
        if (currentOffset - windowStart > MAX_WINDOW_SECONDS) {
            // Fell behind badly. Log loudly, skip the old range, and
            // jump forward to the latest window so the session does
            // not burn minutes of replay trying to catch up.
            int newStart = currentOffset - MAX_WINDOW_SECONDS;
            log.warn("Replay window for session {} was {}s wide (max {}s); "
                            + "jumping cursor from {} to {} and dropping intermediate rows",
                    session.getId(),
                    currentOffset - windowStart,
                    MAX_WINDOW_SECONDS,
                    windowStart,
                    newStart);
            windowStart = newStart;
        }

        dispatchTimelineActions(session, sourceId, windowStart, currentOffset);
        dispatchHistoricalChat(session, sourceId, windowStart, currentOffset);

        session.setLastReplayOffsetSeconds(currentOffset);
        sessionRepository.save(session);
    }

    private void dispatchTimelineActions(Session autoSession,
                                          UUID sourceSessionId,
                                          int fromExclusive,
                                          int toInclusive) {
        List<EventTimelineAction> rows = timelineActionRepository.findReplayWindow(
                sourceSessionId, fromExclusive, toInclusive);
        for (EventTimelineAction row : rows) {
            // Delegate to the existing TimelineService dispatcher so
            // LIVE-side admin clicks and AUTO-side replays share one
            // envelope shape. replayAction is @Transactional(REQUIRED)
            // and therefore joins our current tenant-scoped tx.
            timelineService.replayAction(autoSession, row);
        }
    }

    private void dispatchHistoricalChat(Session autoSession,
                                         UUID sourceSessionId,
                                         int fromExclusive,
                                         int toInclusive) {
        List<ChatMessage> messages = chatMessageRepository.findReplayWindow(
                sourceSessionId, fromExclusive, toInclusive);
        if (messages.isEmpty()) {
            return;
        }
        String channel = channelNameFactory.sessionChannel(
                autoSession.getTenantId(), autoSession.getId(), ChannelKind.CHAT);
        for (ChatMessage message : messages) {
            centrifugoClient.publish(channel, buildReplayEnvelope(message));
        }
    }

    /**
     * Build the Centrifugo payload for one replayed chat row.
     *
     * <p>Fields mirror {@code ChatService.publishToChannel} so clients
     * can run a single renderer for both live and replayed traffic.
     * Two fields are added:
     * <ul>
     *   <li>{@code historical=true} — lets the UI tag the bubble as a
     *       replay of the original audience's message.</li>
     *   <li>{@code sourceSessionId} — debug aid, also lets clients
     *       deduplicate if a message was already shown by some other
     *       path (e.g. a late join catch-up fetch).</li>
     * </ul>
     *
     * <p>{@link Map#of} does not accept more than 10 entries and does
     * not preserve insertion order — {@link LinkedHashMap} keeps the
     * envelope readable in logs and Centrifugo dashboards.
     */
    private Map<String, Object> buildReplayEnvelope(ChatMessage message) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("id", message.getId().toString());
        envelope.put("userId", message.getUserId() == null ? "" : message.getUserId().toString());
        envelope.put("type", message.getMessageType().name());
        envelope.put("text", message.getText());
        envelope.put("replyTo", message.getReplyToMessageId() == null
                ? "" : message.getReplyToMessageId().toString());
        envelope.put("offsetSeconds",
                message.getOffsetSeconds() == null ? -1 : message.getOffsetSeconds());
        envelope.put("createdAt",
                message.getCreatedAt() == null ? Instant.now().toString() : message.getCreatedAt().toString());
        envelope.put("historical", true);
        envelope.put("sourceSessionId", message.getSessionId().toString());
        return envelope;
    }
}
