package com.webizon.chat.api;

import com.webizon.chat.api.dto.ChatSettingsResponse;
import com.webizon.chat.api.dto.ChatSettingsUpdateRequest;
import com.webizon.chat.service.ChatSettingsService;
import com.webizon.chat.service.ChatSettingsService.ChatSettingsPatch;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Admin surface for {@link com.webizon.chat.model.EventChatSettings}.
 *
 * <p>Reads are allowed for all tenant roles (analysts need to see the
 * configuration in reports); writes are restricted to owner / admin /
 * presenter. Moderators intentionally do NOT edit settings — they
 * moderate users, not the event configuration.
 */
@RestController
@RequestMapping("/api/v1/events/{eventId}/chat-settings")
@RequiredArgsConstructor
public class ChatSettingsController {

    private final ChatSettingsService chatSettingsService;

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_PRESENTER','TENANT_MODERATOR','TENANT_ANALYST')")
    public ChatSettingsResponse get(@PathVariable UUID eventId) {
        return ChatSettingsResponse.from(chatSettingsService.findOrCreate(eventId));
    }

    @PatchMapping
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_PRESENTER')")
    public ChatSettingsResponse update(
            @PathVariable UUID eventId,
            @Valid @RequestBody ChatSettingsUpdateRequest request) {

        var patch = new ChatSettingsPatch(
                request.allowLinks(),
                request.slowModeSeconds(),
                request.showParticipantCount(),
                request.showParticipantNames(),
                request.welcomeMessage(),
                request.premoderationEnabled(),
                request.profanityFilterEnabled(),
                request.antiSpamEnabled()
        );
        return ChatSettingsResponse.from(chatSettingsService.update(eventId, patch));
    }
}
