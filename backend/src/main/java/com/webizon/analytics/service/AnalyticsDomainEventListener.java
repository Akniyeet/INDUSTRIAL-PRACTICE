package com.webizon.analytics.service;

import com.webizon.analytics.model.AnalyticsEventType;
import com.webizon.chat.event.ChatMessageSentEvent;
import com.webizon.chat.event.ModerationAppliedEvent;
import com.webizon.chat.model.ModerationActionType;
import com.webizon.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * Bridges Spring application events from the chat / moderation module
 * into the analytics pipeline.
 *
 * <p>Chat and moderation publish plain domain records; this listener
 * is the only place that knows how they map to the analytics event
 * vocabulary. The split matters because:
 *
 * <ul>
 *   <li>Chat can be unit-tested with no analytics beans in the
 *       context — the events just fall on deaf ears.</li>
 *   <li>Changing the {@link AnalyticsEventType} taxonomy never
 *       touches chat code.</li>
 *   <li>A future CRM sink can subscribe to the same domain events
 *       without going through analytics at all.</li>
 * </ul>
 *
 * <h2>After-commit phase</h2>
 * Both listeners run {@link TransactionPhase#AFTER_COMMIT}. That's
 * deliberate: we only want to count chat and moderation events that
 * actually reached the database. If the originating transaction rolls
 * back, the event is discarded and no analytics row is created.
 *
 * <h2>Tenant context</h2>
 * Spring's transactional event listeners run on the publisher's
 * thread, so {@link TenantContext} is still set at dispatch time —
 * the listener therefore doesn't need to re-set it. The event still
 * carries {@code tenantId} as a safety net for any future async
 * listener variants.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyticsDomainEventListener {

    private final AnalyticsRecorder analyticsRecorder;

    /**
     * Chat writes produce one of two behavioural types. Replies are
     * flagged separately so retention reports can distinguish "first
     * message sent" from "joined an existing thread", which have
     * very different engagement signals.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChatMessageSent(ChatMessageSentEvent evt) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("messageId", evt.messageId().toString());
        metadata.put("isReply", evt.isReply());

        analyticsRecorder.record(new AnalyticsRecorder.RecordCommand(
                evt.eventId(),
                evt.sessionId(),
                evt.profileId(),
                null,
                evt.isReply() ? AnalyticsEventType.CHAT_REPLY_SENT
                              : AnalyticsEventType.CHAT_MESSAGE_SENT,
                evt.offsetSeconds(),
                metadata
        ));
    }

    /**
     * Moderation translates to a fixed mapping. Message-scoped
     * actions ({@code MESSAGE_DELETE}, {@code MESSAGE_HIDE}) don't
     * produce a behavioural row on the target user — they're about
     * the message, not the author — and return {@code null} below
     * which skips the recorder call.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onModerationApplied(ModerationAppliedEvent evt) {
        AnalyticsEventType type = mapActionType(evt.actionType());
        if (type == null) {
            // Nothing to record — still fine, the DB row and audit
            // log carry the full action history.
            return;
        }
        if (evt.targetUserId() == null) {
            log.debug("Moderation event {} has no target user — skipping analytics record",
                    evt.actionType());
            return;
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("moderatorId", evt.moderatorUserId() == null
                ? "" : evt.moderatorUserId().toString());
        metadata.put("actionType", evt.actionType().name());
        if (evt.targetMessageId() != null) {
            metadata.put("messageId", evt.targetMessageId().toString());
        }

        analyticsRecorder.record(new AnalyticsRecorder.RecordCommand(
                evt.eventId(),
                evt.sessionId(),
                evt.targetUserId(),
                null,
                type,
                null,
                metadata
        ));
    }

    /**
     * Fixed moderation-action → analytics-event mapping. The method
     * returns {@code null} for message-level actions because they do
     * not represent behaviour on a user and the analytics pipeline
     * would treat them incorrectly.
     */
    private static AnalyticsEventType mapActionType(ModerationActionType actionType) {
        return switch (actionType) {
            case WARNING      -> AnalyticsEventType.WARNING_RECEIVED;
            case MUTE         -> AnalyticsEventType.MUTED;
            case CHAT_BAN     -> AnalyticsEventType.CHAT_BANNED;
            case ROOM_REMOVE  -> AnalyticsEventType.ROOM_REMOVED;
            case FULL_BAN     -> AnalyticsEventType.FULL_BANNED;
            case MESSAGE_DELETE, MESSAGE_HIDE, SLOW_MODE_CHANGE -> null;
        };
    }
}
