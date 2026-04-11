package com.webizon.chat.service;

import com.webizon.chat.model.ChatMessage;
import com.webizon.chat.model.ChatUserStatus;
import com.webizon.chat.model.EventChatSettings;
import com.webizon.chat.model.MessageType;
import com.webizon.chat.policy.ChatPolicy;
import com.webizon.chat.policy.ChatPolicyContext;
import com.webizon.chat.repo.ChatMessageRepository;
import com.webizon.chat.repo.ChatUserStatusRepository;
import com.webizon.events.model.Session;
import com.webizon.events.model.SessionStatus;
import com.webizon.events.repo.SessionRepository;
import com.webizon.realtime.CentrifugoClient;
import com.webizon.realtime.ChannelKind;
import com.webizon.realtime.ChannelNameFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestrates the send path for a chat message.
 *
 * <p>Every user-initiated chat write goes through {@link #sendMessage}.
 * The steps are fixed and run in a single transaction so partial
 * state (e.g. "persisted but not published") cannot leak:
 *
 * <ol>
 *   <li>Load the target session; reject if it is in a final status
 *       (chat is forbidden after a session is finalized).</li>
 *   <li>Resolve the event's {@link EventChatSettings}, lazy-creating
 *       defaults if the row does not exist yet.</li>
 *   <li>Load the caller's {@link ChatUserStatus}; if none, treat as a
 *       fresh row with default values.</li>
 *   <li>Build a {@link ChatPolicyContext} and run every
 *       {@link ChatPolicy} bean in {@code @Order} sequence. The first
 *       violation aborts the transaction via an exception.</li>
 *   <li>Enforce the one-level reply rule: the target message must exist,
 *       belong to the same session, and itself not be a reply.</li>
 *   <li>Persist the {@link ChatMessage}, computing
 *       {@code offset_seconds} from the session's
 *       {@code actualStartedAt} when it is airing.</li>
 *   <li>Update the user status row so slow-mode and flood checks see
 *       the new {@code last_message_at}.</li>
 *   <li>Publish the message envelope to the session's
 *       {@link ChannelKind#CHAT} Centrifugo channel. Publish failures
 *       are logged but do NOT roll back the persisted row — a failed
 *       broadcast is a transient delivery problem, not a domain error.</li>
 * </ol>
 *
 * <p>SYSTEM-authored messages (welcome banner, cooldown notice) use
 * {@link #postSystemMessage} which skips the policy chain and
 * user-status bookkeeping entirely.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    /**
     * Roles considered moderators for chat-policy bypass purposes.
     * Kept in sync with {@code ChannelAccessService.CONTROL_ROLES}.
     */
    private static final Set<String> MODERATOR_ROLES = Set.of(
            "TENANT_OWNER",
            "TENANT_ADMIN",
            "TENANT_MODERATOR",
            "TENANT_PRESENTER"
    );

    private final SessionRepository sessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatUserStatusRepository chatUserStatusRepository;
    private final ChatSettingsService chatSettingsService;
    private final List<ChatPolicy> policies;
    private final CentrifugoClient centrifugoClient;
    private final ChannelNameFactory channelNameFactory;

    @Transactional
    public ChatMessage sendMessage(SendMessageCommand cmd) {
        Session session = sessionRepository.findById(cmd.sessionId())
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + cmd.sessionId()));

        assertSessionAcceptsChat(session);

        EventChatSettings settings = chatSettingsService.findOrCreate(session.getEventId());

        Instant now = Instant.now();
        ChatUserStatus status = chatUserStatusRepository
                .findBySessionIdAndUserId(session.getId(), cmd.userId())
                .orElse(null);

        boolean isModerator = cmd.callerRole() != null && MODERATOR_ROLES.contains(cmd.callerRole());

        ChatPolicyContext ctx = new ChatPolicyContext(
                session.getTenantId(),
                session.getId(),
                cmd.userId(),
                cmd.text(),
                now,
                status,
                settings,
                isModerator
        );
        for (ChatPolicy policy : policies) {
            policy.check(ctx);
        }

        UUID replyTo = resolveReplyTarget(cmd.replyToMessageId(), session.getId());

        ChatMessage message = new ChatMessage();
        message.setEventId(session.getEventId());
        message.setSessionId(session.getId());
        message.setUserId(cmd.userId());
        message.setMessageType(isModerator ? MessageType.ADMIN : MessageType.USER);
        message.setReplyToMessageId(replyTo);
        message.setText(cmd.text().trim());
        message.setOffsetSeconds(computeOffsetSeconds(session, now));

        ChatMessage saved = chatMessageRepository.save(message);

        upsertUserStatusAfterSend(status, session, cmd.userId(), now);

        publishToChannel(session, saved);

        return saved;
    }

    /**
     * Post a SYSTEM-authored message, bypassing the policy chain and
     * user-status updates. Used for welcome banners, slow-mode
     * announcements, and cooldown notices.
     */
    @Transactional
    public ChatMessage postSystemMessage(UUID sessionId, String text) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        assertSessionAcceptsChat(session);

        ChatMessage message = new ChatMessage();
        message.setEventId(session.getEventId());
        message.setSessionId(session.getId());
        message.setUserId(null);
        message.setMessageType(MessageType.SYSTEM);
        message.setText(text);
        message.setOffsetSeconds(computeOffsetSeconds(session, Instant.now()));

        ChatMessage saved = chatMessageRepository.save(message);
        publishToChannel(session, saved);
        return saved;
    }

    private void assertSessionAcceptsChat(Session session) {
        SessionStatus s = session.getStatus();
        if (s == null || SessionStatus.FINAL_STATUSES.contains(s)) {
            throw new IllegalStateException("Chat is closed for this session");
        }
    }

    private UUID resolveReplyTarget(UUID candidate, UUID sessionId) {
        if (candidate == null) {
            return null;
        }
        ChatMessage target = chatMessageRepository.findById(candidate)
                .orElseThrow(() -> new IllegalArgumentException("Reply target not found"));
        if (!target.getSessionId().equals(sessionId)) {
            throw new IllegalArgumentException("Reply target belongs to a different session");
        }
        if (target.isDeleted() || target.isHidden()) {
            throw new IllegalArgumentException("Reply target is no longer available");
        }
        if (target.getReplyToMessageId() != null) {
            // One-level reply only — no chaining.
            throw new IllegalArgumentException("Replies cannot chain; reply to the original message instead");
        }
        return candidate;
    }

    private Integer computeOffsetSeconds(Session session, Instant now) {
        Instant startedAt = session.getActualStartedAt();
        if (startedAt == null) {
            return null;
        }
        long seconds = java.time.Duration.between(startedAt, now).getSeconds();
        if (seconds < 0) {
            return 0;
        }
        return (int) seconds;
    }

    private void upsertUserStatusAfterSend(ChatUserStatus existing, Session session, UUID userId, Instant now) {
        if (existing != null) {
            existing.setLastMessageAt(now);
            return;
        }
        ChatUserStatus fresh = new ChatUserStatus();
        fresh.setEventId(session.getEventId());
        fresh.setSessionId(session.getId());
        fresh.setUserId(userId);
        fresh.setLastMessageAt(now);
        chatUserStatusRepository.save(fresh);
    }

    private void publishToChannel(Session session, ChatMessage message) {
        String channel = channelNameFactory.sessionChannel(
                session.getTenantId(), session.getId(), ChannelKind.CHAT);
        Map<String, Object> envelope = Map.of(
                "id", message.getId().toString(),
                "userId", message.getUserId() == null ? "" : message.getUserId().toString(),
                "type", message.getMessageType().name(),
                "text", message.getText(),
                "replyTo", message.getReplyToMessageId() == null ? "" : message.getReplyToMessageId().toString(),
                "offsetSeconds", message.getOffsetSeconds() == null ? -1 : message.getOffsetSeconds(),
                "createdAt", message.getCreatedAt() == null ? Instant.now().toString() : message.getCreatedAt().toString()
        );
        boolean published = centrifugoClient.publish(channel, envelope);
        if (!published) {
            log.warn("Centrifugo publish failed for message {} on channel {}", message.getId(), channel);
        }
    }

    /**
     * Command envelope for {@link #sendMessage}. Flat record so the
     * REST and (future) websocket adapters can build it the same way.
     */
    public record SendMessageCommand(
            UUID sessionId,
            UUID userId,
            String callerRole,
            String text,
            UUID replyToMessageId
    ) {}
}
