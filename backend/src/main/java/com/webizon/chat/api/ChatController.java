package com.webizon.chat.api;

import com.webizon.auth.CurrentUser;
import com.webizon.chat.api.dto.ChatMessageResponse;
import com.webizon.chat.api.dto.SendMessageRequest;
import com.webizon.chat.policy.ChatPolicyViolation;
import com.webizon.chat.repo.ChatMessageRepository;
import com.webizon.chat.service.ChatService;
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
import java.util.List;
import java.util.UUID;

/**
 * REST surface for live chat.
 *
 * <p>In production the real-time fan-out happens via Centrifugo — this
 * controller only handles writes (which must go through policy
 * enforcement and persistence) and paginated history reads. The front
 * end calls:
 *
 * <ul>
 *   <li>{@code POST /api/v1/sessions/{id}/chat/messages} to send</li>
 *   <li>{@code GET /api/v1/sessions/{id}/chat/messages} to backfill on
 *       reconnect before the websocket catches up</li>
 * </ul>
 *
 * <p>Both endpoints require an authenticated user; the call to
 * {@link CurrentUser#tenantId()} fails fast if the JWT is missing a
 * {@code tenant_id} claim.
 */
@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/chat")
@RequiredArgsConstructor
public class ChatController {

    /** Maximum page size for history backfill. */
    private static final int MAX_HISTORY_PAGE_SIZE = 100;

    private final ChatService chatService;
    private final ChatMessageRepository chatMessageRepository;

    @PostMapping("/messages")
    @PreAuthorize("isAuthenticated()")
    public ChatMessageResponse send(
            @PathVariable UUID sessionId,
            @Valid @RequestBody SendMessageRequest request) {

        UUID userId = CurrentUser.profileId();
        String role = CurrentUser.role();

        var saved = chatService.sendMessage(new ChatService.SendMessageCommand(
                sessionId,
                userId,
                role,
                request.text(),
                request.replyToMessageId()
        ));
        return ChatMessageResponse.from(saved);
    }

    @GetMapping("/messages")
    @PreAuthorize("isAuthenticated()")
    public List<ChatMessageResponse> history(
            @PathVariable UUID sessionId,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {

        int capped = Math.min(Math.max(limit, 1), MAX_HISTORY_PAGE_SIZE);
        return chatMessageRepository.findLiveFeed(sessionId, PageRequest.of(0, capped))
                .stream()
                .map(ChatMessageResponse::from)
                .toList();
    }

    /**
     * Map policy violations onto RFC-9457 ProblemDetail so the front-end
     * sees a stable machine-readable {@code code} field in the response.
     * We return 422 rather than 400 to distinguish "your request was
     * valid JSON but a chat rule stopped it" from malformed input.
     */
    @ExceptionHandler(ChatPolicyViolation.class)
    public ProblemDetail onPolicyViolation(ChatPolicyViolation ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        pd.setType(URI.create("https://webizon.kz/problems/chat-policy"));
        pd.setTitle("Chat policy violation");
        pd.setProperty("code", ex.code());
        return pd;
    }
}
