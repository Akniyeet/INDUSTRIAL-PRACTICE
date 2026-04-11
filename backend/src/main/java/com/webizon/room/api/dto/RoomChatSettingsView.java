package com.webizon.room.api.dto;

import com.webizon.chat.model.EventChatSettings;

/**
 * Viewer-facing projection of {@link EventChatSettings}.
 *
 * <p>Moderation-only knobs ({@code premoderationEnabled},
 * {@code profanityFilterEnabled}, {@code antiSpamEnabled}) are
 * omitted on purpose — the client UI only needs the fields that
 * change <em>rendering or input behaviour</em>.
 */
public record RoomChatSettingsView(
        boolean allowLinks,
        int slowModeSeconds,
        boolean showParticipantCount,
        boolean showParticipantNames,
        String welcomeMessage
) {
    public static RoomChatSettingsView from(EventChatSettings s) {
        return new RoomChatSettingsView(
                s.isAllowLinks(),
                s.getSlowModeSeconds(),
                s.isShowParticipantCount(),
                s.isShowParticipantNames(),
                s.getWelcomeMessage()
        );
    }
}
