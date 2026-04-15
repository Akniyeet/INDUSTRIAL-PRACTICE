package com.webizon.chat.api;

import com.webizon.auth.CurrentUser;
import com.webizon.chat.api.dto.ChatMessageResponse;
import com.webizon.chat.api.dto.SendMessageRequest;
import com.webizon.chat.model.ChatMessage;
import com.webizon.chat.policy.ChatPolicyViolation;
import com.webizon.chat.repo.ChatMessageRepository;
import com.webizon.chat.service.ChatService;
import com.webizon.tenancy.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST surface for live chat.
 *
 * <p>Supports two pagination modes:
 * <ol>
 *   <li><strong>Initial load</strong> — no cursor, returns newest N messages</li>
 *   <li><strong>Cursor-based (keyset)</strong> — pass {@code before}/{@code beforeId}
 *       to load older messages, or {@code after}/{@code afterId} to load newer.
 *       This avoids O(offset) scans at high message counts.</li>
 * </ol>
 *
 * <p>Real-time fan-out uses Centrifugo; this controller handles writes
 * (through the policy chain) and paginated history reads.
 */
@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/chat")
@RequiredArgsConstructor
public class ChatController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 50;

    private final ChatService chatService;
    private final ChatMessageRepository chatMessageRepository;
    private final UserService userService;

    @PostMapping("/messages")
    @PreAuthorize("isAuthenticated()")
    public ChatMessageResponse send(
            @PathVariable UUID sessionId,
            @Valid @RequestBody SendMessageRequest request) {

        UUID userId = userService.requireByKeycloakId(CurrentUser.keycloakId()).getId();
        String role = CurrentUser.role();

        var saved = chatService.sendMessage(new ChatService.SendMessageCommand(
                sessionId, userId, role,
                request.text(), request.replyToMessageId()
        ));
        return ChatMessageResponse.from(saved);
    }

    /**
     * Paginated chat history with cursor support.
     *
     * <p>Usage examples:
     * <pre>
     * GET /messages?limit=50                          → initial load (newest 50)
     * GET /messages?limit=50&before=2026-04-12T10:00:00Z&beforeId=abc-123
     *                                                 → older messages (scroll up)
     * GET /messages?limit=50&after=2026-04-12T10:05:00Z&afterId=def-456
     *                                                 → newer messages (reconnect catch-up)
     * </pre>
     */
    @GetMapping("/messages")
    @PreAuthorize("isAuthenticated()")
    public ChatHistoryResponse history(
            @PathVariable UUID sessionId,
            @RequestParam(name = "limit", defaultValue = "50") int limit,
            @RequestParam(name = "before", required = false) Instant before,
            @RequestParam(name = "beforeId", required = false) UUID beforeId,
            @RequestParam(name = "after", required = false) Instant after,
            @RequestParam(name = "afterId", required = false) UUID afterId) {

        int capped = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
        PageRequest page = PageRequest.of(0, capped);

        List<ChatMessage> messages;

        if (before != null && beforeId != null) {
            // Keyset: load older messages (scroll up)
            messages = chatMessageRepository.findLiveFeedBefore(sessionId, before, beforeId, page);
        } else if (after != null && afterId != null) {
            // Keyset: load newer messages (reconnect catch-up)
            messages = chatMessageRepository.findLiveFeedAfter(sessionId, after, afterId, page);
        } else {
            // Initial load: newest messages
            messages = chatMessageRepository.findLiveFeed(sessionId, page);
        }

        List<ChatMessageResponse> items = messages.stream()
                .map(ChatMessageResponse::from)
                .toList();

        // Build cursor for the client
        String nextCursor = null;
        String nextCursorId = null;
        if (!messages.isEmpty() && messages.size() == capped) {
            ChatMessage last = messages.get(messages.size() - 1);
            nextCursor = last.getCreatedAt().toString();
            nextCursorId = last.getId().toString();
        }

        return new ChatHistoryResponse(items, nextCursor, nextCursorId, messages.size() == capped);
    }

    /**
     * Response wrapper with cursor metadata for pagination.
     */
    public record ChatHistoryResponse(
            List<ChatMessageResponse> messages,
            String nextCursor,
            String nextCursorId,
            boolean hasMore
    ) {}

    @ExceptionHandler(ChatPolicyViolation.class)
    public ProblemDetail onPolicyViolation(ChatPolicyViolation ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        pd.setType(URI.create("https://webizon.kz/problems/chat-policy"));
        pd.setTitle("Chat policy violation");
        pd.setProperty("code", ex.code());
        return pd;
    }
}
