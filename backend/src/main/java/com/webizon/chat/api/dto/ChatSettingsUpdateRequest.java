package com.webizon.chat.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Partial update for {@link com.webizon.chat.model.EventChatSettings}.
 *
 * <p>Every field is nullable — {@code null} means "leave this field
 * unchanged". The service layer reads this into a
 * {@code ChatSettingsPatch} record with identical semantics.
 */
public record ChatSettingsUpdateRequest(
        Boolean allowLinks,
        @Min(0) @Max(600) Integer slowModeSeconds,
        Boolean showParticipantCount,
        Boolean showParticipantNames,
        @Size(max = 500) String welcomeMessage,
        Boolean premoderationEnabled,
        Boolean profanityFilterEnabled,
        Boolean antiSpamEnabled
) {}
