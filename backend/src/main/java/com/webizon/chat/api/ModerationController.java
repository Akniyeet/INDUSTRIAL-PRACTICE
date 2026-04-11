package com.webizon.chat.api;

import com.webizon.auth.CurrentUser;
import com.webizon.chat.api.dto.ModerationActionResponse;
import com.webizon.chat.api.dto.ModerationRequests.BanRequest;
import com.webizon.chat.api.dto.ModerationRequests.DeleteMessageRequest;
import com.webizon.chat.api.dto.ModerationRequests.HideMessageRequest;
import com.webizon.chat.api.dto.ModerationRequests.MuteRequest;
import com.webizon.chat.api.dto.ModerationRequests.WarnRequest;
import com.webizon.chat.repo.ModerationActionRepository;
import com.webizon.chat.service.ModerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST surface for moderator actions.
 *
 * <p>All mutating endpoints are restricted to moderator-grade roles
 * (owner / admin / moderator / presenter). The audit log read endpoint
 * is additionally available to analysts so reports can cite it.
 *
 * <p>Every successful action also broadcasts an event on the
 * {@link com.webizon.realtime.ChannelKind#CONTROL} channel so the
 * affected user's client can react without waiting for a poll.
 */
@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/moderation")
@RequiredArgsConstructor
public class ModerationController {

    /** Maximum page size for the audit log view. */
    private static final int MAX_LOG_PAGE_SIZE = 200;

    private final ModerationService moderationService;
    private final ModerationActionRepository moderationActionRepository;

    @PostMapping("/warn")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_MODERATOR','TENANT_PRESENTER')")
    public ModerationActionResponse warn(@PathVariable UUID sessionId,
                                          @Valid @RequestBody WarnRequest req) {
        return ModerationActionResponse.from(
                moderationService.warn(sessionId, req.targetUserId(), CurrentUser.profileId(), req.reason()));
    }

    @PostMapping("/mute")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_MODERATOR','TENANT_PRESENTER')")
    public ModerationActionResponse mute(@PathVariable UUID sessionId,
                                          @Valid @RequestBody MuteRequest req) {
        return ModerationActionResponse.from(
                moderationService.mute(sessionId, req.targetUserId(), CurrentUser.profileId(),
                        req.durationSeconds(), req.reason()));
    }

    @PostMapping("/chat-ban")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_MODERATOR','TENANT_PRESENTER')")
    public ModerationActionResponse chatBan(@PathVariable UUID sessionId,
                                             @Valid @RequestBody BanRequest req) {
        return ModerationActionResponse.from(
                moderationService.chatBan(sessionId, req.targetUserId(), CurrentUser.profileId(), req.reason()));
    }

    @PostMapping("/room-remove")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_MODERATOR','TENANT_PRESENTER')")
    public ModerationActionResponse roomRemove(@PathVariable UUID sessionId,
                                                @Valid @RequestBody BanRequest req) {
        return ModerationActionResponse.from(
                moderationService.roomRemove(sessionId, req.targetUserId(), CurrentUser.profileId(), req.reason()));
    }

    @PostMapping("/full-ban")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_MODERATOR','TENANT_PRESENTER')")
    public ModerationActionResponse fullBan(@PathVariable UUID sessionId,
                                             @Valid @RequestBody BanRequest req) {
        return ModerationActionResponse.from(
                moderationService.fullBan(sessionId, req.targetUserId(), CurrentUser.profileId(), req.reason()));
    }

    @PostMapping("/delete-message")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_MODERATOR','TENANT_PRESENTER')")
    public ModerationActionResponse deleteMessage(@PathVariable UUID sessionId,
                                                   @Valid @RequestBody DeleteMessageRequest req) {
        return ModerationActionResponse.from(
                moderationService.deleteMessage(sessionId, req.messageId(), CurrentUser.profileId(), req.reason()));
    }

    @PostMapping("/hide-message")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_MODERATOR','TENANT_PRESENTER')")
    public ModerationActionResponse hideMessage(@PathVariable UUID sessionId,
                                                 @Valid @RequestBody HideMessageRequest req) {
        return ModerationActionResponse.from(
                moderationService.hideMessage(sessionId, req.messageId(), CurrentUser.profileId(), req.reason()));
    }

    @GetMapping("/log")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_MODERATOR','TENANT_PRESENTER','TENANT_ANALYST')")
    public List<ModerationActionResponse> log(@PathVariable UUID sessionId,
                                                @RequestParam(name = "limit", defaultValue = "100") int limit) {
        int capped = Math.min(Math.max(limit, 1), MAX_LOG_PAGE_SIZE);
        return moderationActionRepository
                .findAllBySessionIdOrderByCreatedAtDesc(sessionId, PageRequest.of(0, capped))
                .stream()
                .map(ModerationActionResponse::from)
                .toList();
    }
}
