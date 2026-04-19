package com.webizon.chat.model;

import com.webizon.tenancy.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Per-event chat configuration.
 *
 * <p>One row per event (enforced by the {@code event_chat_settings_event_uniq}
 * unique constraint). Sessions read these values at runtime through
 * {@code ChatSettingsService}; a future migration may add session-level
 * overrides, but for MVP the event-level row is the single source of
 * truth.
 *
 * <p>Defaults match the CLAUDE.md guidance:
 * <ul>
 *   <li>links disabled — reduces spam in educational sessions</li>
 *   <li>slow mode off — admins opt in when they see flooding</li>
 *   <li>profanity filter on — catches obvious violations without tuning</li>
 *   <li>anti-spam on — rate-limits repeat messages from the same user</li>
 * </ul>
 */
@Entity
@Table(
        name = "event_chat_settings",
        uniqueConstraints = @UniqueConstraint(
                name = "event_chat_settings_event_uniq",
                columnNames = {"event_id"}
        )
)
@Getter
@Setter
public class EventChatSettings extends TenantAwareEntity {

    @Column(name = "event_id", nullable = false, columnDefinition = "UUID")
    private UUID eventId;

    @Column(name = "allow_links", nullable = false)
    private boolean allowLinks;

    @Column(name = "slow_mode_seconds", nullable = false)
    private int slowModeSeconds;

    @Column(name = "show_participant_count", nullable = false)
    private boolean showParticipantCount = true;

    @Column(name = "show_participant_names", nullable = false)
    private boolean showParticipantNames = true;

    @Column(name = "welcome_message", columnDefinition = "TEXT")
    private String welcomeMessage;

    @Column(name = "premoderation_enabled", nullable = false)
    private boolean premoderationEnabled;

    @Column(name = "profanity_filter_enabled", nullable = false)
    private boolean profanityFilterEnabled = true;

    @Column(name = "anti_spam_enabled", nullable = false)
    private boolean antiSpamEnabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "chat_mode", nullable = false, length = 20)
    private ChatMode chatMode = ChatMode.EVERYONE;

    @Column(name = "chat_enabled", nullable = false)
    private boolean chatEnabled = true;

    @Column(name = "block_phone_numbers", nullable = false)
    private boolean blockPhoneNumbers = true;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.ARRAY)
    @Column(name = "forbidden_words", nullable = false, columnDefinition = "text[]")
    private String[] forbiddenWords = {};
}
