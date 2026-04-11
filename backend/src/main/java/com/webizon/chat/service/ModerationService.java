package com.webizon.chat.service;

import com.webizon.chat.event.ModerationAppliedEvent;
import com.webizon.chat.model.ChatMessage;
import com.webizon.chat.model.ChatUserStatus;
import com.webizon.chat.model.ModerationAction;
import com.webizon.chat.model.ModerationActionType;
import com.webizon.chat.repo.ChatMessageRepository;
import com.webizon.chat.repo.ChatUserStatusRepository;
import com.webizon.chat.repo.ModerationActionRepository;
import com.webizon.events.model.Session;
import com.webizon.events.repo.SessionRepository;
import com.webizon.realtime.CentrifugoClient;
import com.webizon.realtime.ChannelKind;
import com.webizon.realtime.ChannelNameFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Moderator-side actions: warn, mute, ban, delete, slow-mode change.
 *
 * <p>Every method here:
 * <ol>
 *   <li>Mutates the relevant domain row ({@link ChatMessage},
 *       {@link ChatUserStatus}, or settings).</li>
 *   <li>Records an immutable {@link ModerationAction} row so the audit
 *       log always reflects what happened.</li>
 *   <li>Broadcasts the effect on the
 *       {@link com.webizon.realtime.ChannelKind#CONTROL} channel so the
 *       target user's client can react instantly (hide a message,
 *       disable the composer, show a private warning).</li>
 * </ol>
 *
 * <p>Authorisation is handled by the REST layer via
 * {@code @PreAuthorize}: only owner / admin / moderator / presenter
 * roles can hit these endpoints. This service does not double-check
 * — if it ran, the caller is already allowed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ModerationService {

    private final SessionRepository sessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatUserStatusRepository chatUserStatusRepository;
    private final ModerationActionRepository moderationActionRepository;
    private final CentrifugoClient centrifugoClient;
    private final ChannelNameFactory channelNameFactory;
    private final ApplicationEventPublisher applicationEventPublisher;

    // ------------------------------------------------------------------
    // User-targeted actions
    // ------------------------------------------------------------------

    @Transactional
    public ModerationAction warn(UUID sessionId, UUID targetUserId, UUID moderatorId, String reason) {
        Session session = requireSession(sessionId);
        ChatUserStatus status = findOrCreateStatus(session, targetUserId);
        status.setWarningCount(status.getWarningCount() + 1);

        ModerationAction action = persistAction(session, targetUserId, moderatorId,
                ModerationActionType.WARNING, null, reason, null);
        broadcastControl(session, action, Map.of(
                "reason", reason == null ? "" : reason,
                "warningCount", status.getWarningCount()
        ));
        return action;
    }

    @Transactional
    public ModerationAction mute(UUID sessionId, UUID targetUserId, UUID moderatorId,
                                 int durationSeconds, String reason) {
        if (durationSeconds <= 0) {
            throw new IllegalArgumentException("Mute duration must be positive");
        }
        Session session = requireSession(sessionId);
        ChatUserStatus status = findOrCreateStatus(session, targetUserId);

        Instant muteUntil = Instant.now().plusSeconds(durationSeconds);
        status.setMuted(true);
        status.setMuteUntil(muteUntil);

        ModerationAction action = persistAction(session, targetUserId, moderatorId,
                ModerationActionType.MUTE, null, reason, durationSeconds);
        broadcastControl(session, action, Map.of(
                "muteUntil", muteUntil.toString(),
                "reason", reason == null ? "" : reason
        ));
        return action;
    }

    @Transactional
    public ModerationAction chatBan(UUID sessionId, UUID targetUserId, UUID moderatorId, String reason) {
        Session session = requireSession(sessionId);
        ChatUserStatus status = findOrCreateStatus(session, targetUserId);
        status.setChatBanned(true);
        status.setCanSendMessages(false);

        ModerationAction action = persistAction(session, targetUserId, moderatorId,
                ModerationActionType.CHAT_BAN, null, reason, null);
        broadcastControl(session, action, Map.of("reason", reason == null ? "" : reason));
        return action;
    }

    @Transactional
    public ModerationAction roomRemove(UUID sessionId, UUID targetUserId, UUID moderatorId, String reason) {
        Session session = requireSession(sessionId);
        ChatUserStatus status = findOrCreateStatus(session, targetUserId);
        status.setRoomBanned(true);
        status.setCanSendMessages(false);

        ModerationAction action = persistAction(session, targetUserId, moderatorId,
                ModerationActionType.ROOM_REMOVE, null, reason, null);
        broadcastControl(session, action, Map.of("reason", reason == null ? "" : reason));
        return action;
    }

    @Transactional
    public ModerationAction fullBan(UUID sessionId, UUID targetUserId, UUID moderatorId, String reason) {
        Session session = requireSession(sessionId);
        ChatUserStatus status = findOrCreateStatus(session, targetUserId);
        status.setChatBanned(true);
        status.setRoomBanned(true);
        status.setCanSendMessages(false);

        ModerationAction action = persistAction(session, targetUserId, moderatorId,
                ModerationActionType.FULL_BAN, null, reason, null);
        broadcastControl(session, action, Map.of("reason", reason == null ? "" : reason));
        return action;
    }

    // ------------------------------------------------------------------
    // Message-targeted actions
    // ------------------------------------------------------------------

    @Transactional
    public ModerationAction deleteMessage(UUID sessionId, UUID messageId, UUID moderatorId, String reason) {
        Session session = requireSession(sessionId);
        ChatMessage message = requireMessageInSession(messageId, sessionId);

        if (!message.isDeleted()) {
            message.setDeleted(true);
            message.setDeletedByUserId(moderatorId);
            message.setDeletedAt(Instant.now());
        }

        ModerationAction action = persistAction(session, message.getUserId(), moderatorId,
                ModerationActionType.MESSAGE_DELETE, message.getId(), reason, null);
        broadcastControl(session, action, Map.of("messageId", message.getId().toString()));
        return action;
    }

    @Transactional
    public ModerationAction hideMessage(UUID sessionId, UUID messageId, UUID moderatorId, String reason) {
        Session session = requireSession(sessionId);
        ChatMessage message = requireMessageInSession(messageId, sessionId);

        message.setHidden(true);

        ModerationAction action = persistAction(session, message.getUserId(), moderatorId,
                ModerationActionType.MESSAGE_HIDE, message.getId(), reason, null);
        broadcastControl(session, action, Map.of("messageId", message.getId().toString()));
        return action;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Session requireSession(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
    }

    private ChatMessage requireMessageInSession(UUID messageId, UUID sessionId) {
        ChatMessage msg = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found: " + messageId));
        if (!msg.getSessionId().equals(sessionId)) {
            throw new IllegalArgumentException("Message belongs to a different session");
        }
        return msg;
    }

    private ChatUserStatus findOrCreateStatus(Session session, UUID userId) {
        return chatUserStatusRepository
                .findBySessionIdAndUserId(session.getId(), userId)
                .orElseGet(() -> {
                    ChatUserStatus fresh = new ChatUserStatus();
                    fresh.setEventId(session.getEventId());
                    fresh.setSessionId(session.getId());
                    fresh.setUserId(userId);
                    return chatUserStatusRepository.save(fresh);
                });
    }

    private ModerationAction persistAction(Session session, UUID targetUserId, UUID moderatorId,
                                            ModerationActionType type, UUID targetMessageId,
                                            String reason, Integer durationSeconds) {
        ModerationAction action = new ModerationAction();
        action.setEventId(session.getEventId());
        action.setSessionId(session.getId());
        action.setTargetUserId(targetUserId);
        action.setModeratorUserId(moderatorId);
        action.setActionType(type);
        action.setTargetMessageId(targetMessageId);
        action.setReason(reason);
        action.setDurationSeconds(durationSeconds);
        ModerationAction saved = moderationActionRepository.save(action);

        // Fan out to the analytics pipeline. The listener resolves
        // the correct behavioural event type (WARNING_RECEIVED,
        // MUTED, etc.) and feeds it into the negative-signal lane of
        // the lead evaluator.
        applicationEventPublisher.publishEvent(new ModerationAppliedEvent(
                session.getTenantId(),
                session.getEventId(),
                session.getId(),
                targetUserId,
                moderatorId,
                type,
                targetMessageId
        ));

        return saved;
    }

    private void broadcastControl(Session session, ModerationAction action, Map<String, Object> extras) {
        String channel = channelNameFactory.sessionChannel(
                session.getTenantId(), session.getId(), ChannelKind.CONTROL);

        java.util.Map<String, Object> envelope = new java.util.HashMap<>();
        envelope.put("actionId", action.getId().toString());
        envelope.put("type", action.getActionType().name());
        envelope.put("targetUserId", action.getTargetUserId() == null ? "" : action.getTargetUserId().toString());
        envelope.put("at", Instant.now().toString());
        envelope.putAll(extras);

        boolean published = centrifugoClient.publish(channel, envelope);
        if (!published) {
            log.warn("Centrifugo control publish failed for action {} on channel {}", action.getId(), channel);
        }
    }
}
