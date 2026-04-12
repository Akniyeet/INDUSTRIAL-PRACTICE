package com.webizon.chat.api;

import com.webizon.chat.api.dto.HistoricalChatMessageResponse;
import com.webizon.chat.model.ChatMessage;
import com.webizon.chat.repo.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Admin endpoint for reviewing and curating the historical chat of a
 * LIVE session before it replays in AUTO sessions.
 *
 * <p>The transcript view shows all non-deleted, non-hidden messages that
 * have an offset (replay-eligible). Admins can toggle individual messages
 * in or out of the replay stream via {@code POST /{id}/toggle-replay}.
 *
 * <p>Gated to owner / admin / moderator — the same roles that handle
 * moderation during the live broadcast.
 */
@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/historical-chat")
@RequiredArgsConstructor
public class HistoricalChatController {

    private final ChatMessageRepository chatMessageRepository;

    /**
     * Full historical transcript ordered by offset. Includes messages
     * with {@code excludedFromReplay = true} so admins can toggle them
     * back on.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_MODERATOR')")
    public List<HistoricalChatMessageResponse> transcript(@PathVariable UUID sessionId) {
        return chatMessageRepository.findHistoricalTranscript(sessionId)
                .stream()
                .map(HistoricalChatMessageResponse::from)
                .toList();
    }

    /**
     * Toggle a message's replay-exclusion flag. If currently included,
     * it gets excluded; if currently excluded, it gets included.
     *
     * @return the updated message projection
     */
    @PostMapping("/{messageId}/toggle-replay")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_MODERATOR')")
    @Transactional
    public HistoricalChatMessageResponse toggleReplay(
            @PathVariable UUID sessionId,
            @PathVariable UUID messageId) {

        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));

        if (!message.getSessionId().equals(sessionId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message does not belong to this session");
        }

        message.setExcludedFromReplay(!message.isExcludedFromReplay());
        ChatMessage saved = chatMessageRepository.save(message);
        return HistoricalChatMessageResponse.from(saved);
    }

    /**
     * Bulk-exclude messages from replay. Useful for cleaning up a
     * noisy live chat before scheduling auto sessions.
     */
    @PostMapping("/bulk-exclude")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_MODERATOR')")
    @Transactional
    public int bulkExclude(
            @PathVariable UUID sessionId,
            @RequestBody List<UUID> messageIds) {

        int count = 0;
        for (UUID id : messageIds) {
            ChatMessage msg = chatMessageRepository.findById(id).orElse(null);
            if (msg != null && msg.getSessionId().equals(sessionId) && !msg.isExcludedFromReplay()) {
                msg.setExcludedFromReplay(true);
                chatMessageRepository.save(msg);
                count++;
            }
        }
        return count;
    }

    /**
     * Bulk-include messages back into replay.
     */
    @PostMapping("/bulk-include")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_MODERATOR')")
    @Transactional
    public int bulkInclude(
            @PathVariable UUID sessionId,
            @RequestBody List<UUID> messageIds) {

        int count = 0;
        for (UUID id : messageIds) {
            ChatMessage msg = chatMessageRepository.findById(id).orElse(null);
            if (msg != null && msg.getSessionId().equals(sessionId) && msg.isExcludedFromReplay()) {
                msg.setExcludedFromReplay(false);
                chatMessageRepository.save(msg);
                count++;
            }
        }
        return count;
    }
}
