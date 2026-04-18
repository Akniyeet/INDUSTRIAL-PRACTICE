package com.webizon.autosession.api;

import com.webizon.autosession.api.dto.HistoricalChatMessageResponse;
import com.webizon.autosession.api.dto.ReplayExclusionRequest;
import com.webizon.autosession.service.HistoricalChatCurationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Admin endpoints for cleaning the chat transcript of a finished
 * LIVE session before it is replayed in future AUTO sessions.
 *
 * <p>The single mutation endpoint is a PUT with a boolean body so
 * the same URL handles both exclude and restore — idempotent and
 * trivially cache-busted by just resending the desired state.
 */
@RestController
@RequestMapping("/api/v1/sessions/{sourceSessionId}/historical-chat")
@RequiredArgsConstructor
public class HistoricalChatController {

    private final HistoricalChatCurationService curationService;

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_MODERATOR','TENANT_PRESENTER')")
    public List<HistoricalChatMessageResponse> listTranscript(@PathVariable UUID sourceSessionId) {
        return curationService.listTranscript(sourceSessionId).stream()
                .map(HistoricalChatMessageResponse::from)
                .toList();
    }

    @PutMapping("/{messageId}/replay-exclusion")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_MODERATOR')")
    public HistoricalChatMessageResponse setExclusion(@PathVariable UUID sourceSessionId,
                                                       @PathVariable UUID messageId,
                                                       @Valid @RequestBody ReplayExclusionRequest req) {
        var updated = Boolean.TRUE.equals(req.excluded())
                ? curationService.excludeFromReplay(sourceSessionId, messageId)
                : curationService.restoreToReplay(sourceSessionId, messageId);
        return HistoricalChatMessageResponse.from(updated);
    }

    /**
     * Toggle a single message's replay-exclusion state.
     * Used by the frontend single-click action on the review table.
     */
    @PostMapping("/{messageId}/toggle-replay")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_MODERATOR')")
    public HistoricalChatMessageResponse toggleReplay(@PathVariable UUID sourceSessionId,
                                                       @PathVariable UUID messageId) {
        return HistoricalChatMessageResponse.from(
                curationService.toggleReplay(sourceSessionId, messageId));
    }

    /**
     * Bulk-exclude a set of messages from replay.
     *
     * @param messageIds list of message UUIDs to exclude
     * @return count of messages actually changed
     */
    @PostMapping("/bulk-exclude")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_MODERATOR')")
    public int bulkExclude(@PathVariable UUID sourceSessionId,
                           @RequestBody List<UUID> messageIds) {
        return curationService.bulkExclude(sourceSessionId, messageIds);
    }

    /**
     * Bulk-restore a set of messages to the replay stream.
     *
     * @param messageIds list of message UUIDs to restore
     * @return count of messages actually changed
     */
    @PostMapping("/bulk-include")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_MODERATOR')")
    public int bulkInclude(@PathVariable UUID sourceSessionId,
                           @RequestBody List<UUID> messageIds) {
        return curationService.bulkInclude(sourceSessionId, messageIds);
    }
}
