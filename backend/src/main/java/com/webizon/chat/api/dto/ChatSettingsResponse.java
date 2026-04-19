package com.webizon.chat.api.dto;

import com.webizon.chat.model.EventChatSettings;

import java.util.UUID;

public record ChatSettingsResponse(
        UUID eventId,
        boolean allowLinks,
        int slowModeSeconds,
        boolean showParticipantCount,
        boolean showParticipantNames,
        String welcomeMessage,
        boolean premoderationEnabled,
        boolean profanityFilterEnabled,
        boolean antiSpamEnabled,
        String chatMode
) {

    public static ChatSettingsResponse from(EventChatSettings s) {
        return new ChatSettingsResponse(
                s.getEventId(),
                s.isAllowLinks(),
                s.getSlowModeSeconds(),
                s.isShowParticipantCount(),
                s.isShowParticipantNames(),
                s.getWelcomeMessage(),
                s.isPremoderationEnabled(),
                s.isProfanityFilterEnabled(),
                s.isAntiSpamEnabled(),
                s.getChatMode().name()
        );
    }
}
