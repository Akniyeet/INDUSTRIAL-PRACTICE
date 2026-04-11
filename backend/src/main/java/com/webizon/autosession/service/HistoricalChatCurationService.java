package com.webizon.autosession.service;

import com.webizon.chat.model.ChatMessage;
import com.webizon.chat.repo.ChatMessageRepository;
import com.webizon.events.model.Session;
import com.webizon.events.model.SessionStatus;
import com.webizon.events.model.SessionType;
import com.webizon.events.repo.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Admin-side editing of the historical chat transcript that future
 * AUTO sessions will replay.
 *
 * <p>When a LIVE session ends, its chat row set is frozen for
 * attendance analytics but still feeds the AUTO replay pipeline.
 * Admins need to clean that transcript <em>before</em> the first AUTO
 * slot airs so that off-topic jokes, expired links, and answered
 * questions do not reappear in every future showing.
 *
 * <h2>Why a dedicated flag</h2>
 * The existing {@code deleted} and {@code hidden} columns also hide
 * rows from replay, but they also change the row's visibility in the
 * live audit trail — a moderator who needs to review what really
 * happened during the LIVE run loses context. {@code excludedFromReplay}
 * is intentionally orthogonal: a message is fully preserved in the
 * live record and moderation analytics, but the replay window query
 * filters it out.
 *
 * <h2>Guardrails</h2>
 * <ul>
 *   <li>The source session must be a LIVE session that has finished
 *       ({@code ENDED}). Editing the transcript of an in-flight LIVE
 *       session would let an admin race the real-time chat writers and
 *       silently drop a message mid-broadcast.</li>
 *   <li>The target message must belong to the named source session.
 *       Cross-session curation is rejected as a mistake.</li>
 *   <li>Tenancy is enforced by the Hibernate filter / RLS layer — every
 *       {@code findById} runs with {@code app.current_tenant} bound to
 *       the caller's workspace, so IDs from other tenants simply
 *       do not exist.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HistoricalChatCurationService {

    private final SessionRepository sessionRepository;
    private final ChatMessageRepository chatMessageRepository;

    /**
     * List every replay-eligible message for a source LIVE session
     * (not deleted, not hidden, has an offset) ordered by offset.
     *
     * <p>Messages marked {@link ChatMessage#isExcludedFromReplay()} are
     * still returned — the admin UI needs to see them so it can
     * toggle exclusion off again. The replay engine uses a different
     * query path ({@link ChatMessageRepository#findReplayWindow})
     * that does filter exclusions out.
     */
    @Transactional(readOnly = true)
    public List<ChatMessage> listTranscript(UUID sourceSessionId) {
        requireEndedLiveSession(sourceSessionId);
        return chatMessageRepository.findHistoricalTranscript(sourceSessionId);
    }

    /**
     * Flip {@link ChatMessage#isExcludedFromReplay()} to {@code true}.
     * Idempotent — a second call is a no-op.
     */
    @Transactional
    public ChatMessage excludeFromReplay(UUID sourceSessionId, UUID messageId) {
        ChatMessage message = requireMessageInSession(sourceSessionId, messageId);
        if (!message.isExcludedFromReplay()) {
            message.setExcludedFromReplay(true);
            log.info("Excluded chat message {} from replay (source session {})",
                    messageId, sourceSessionId);
        }
        return message;
    }

    /**
     * Flip {@link ChatMessage#isExcludedFromReplay()} to {@code false}.
     * Idempotent.
     */
    @Transactional
    public ChatMessage restoreToReplay(UUID sourceSessionId, UUID messageId) {
        ChatMessage message = requireMessageInSession(sourceSessionId, messageId);
        if (message.isExcludedFromReplay()) {
            message.setExcludedFromReplay(false);
            log.info("Restored chat message {} to replay (source session {})",
                    messageId, sourceSessionId);
        }
        return message;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Session requireEndedLiveSession(UUID sourceSessionId) {
        Session source = sessionRepository.findById(sourceSessionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Source session not found: " + sourceSessionId));
        if (source.getType() != SessionType.LIVE) {
            throw new IllegalStateException(
                    "Transcript curation is only valid for LIVE sessions (was " + source.getType() + ")");
        }
        if (source.getStatus() != SessionStatus.ENDED) {
            throw new IllegalStateException(
                    "Source LIVE session must be ENDED before the transcript can be edited (was "
                            + source.getStatus() + ")");
        }
        return source;
    }

    private ChatMessage requireMessageInSession(UUID sourceSessionId, UUID messageId) {
        requireEndedLiveSession(sourceSessionId);
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Chat message not found: " + messageId));
        if (!message.getSessionId().equals(sourceSessionId)) {
            throw new IllegalArgumentException(
                    "Message " + messageId + " does not belong to session " + sourceSessionId);
        }
        return message;
    }
}
