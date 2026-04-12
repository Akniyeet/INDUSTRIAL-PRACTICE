package com.webizon.chat.service;

import com.webizon.chat.model.ChatMode;
import com.webizon.chat.model.EventChatSettings;
import com.webizon.chat.repo.EventChatSettingsRepository;
import com.webizon.events.repo.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Owns {@link EventChatSettings} lookup and creation.
 *
 * <p>Every other chat component needs the settings row to make
 * decisions (slow mode, link policy, profanity filter) — so this
 * service lazy-creates the default row on first access. That keeps
 * event creation simple (no "also create chat settings" step in the
 * event service) while making reads always return a non-null row the
 * policy chain can depend on.
 *
 * <p>Writes from the admin panel flow through {@link #update} which
 * reuses {@link #findOrCreate} and mutates the returned row — one
 * round-trip, no race on double-creation thanks to the
 * {@code event_chat_settings_event_uniq} constraint (a concurrent
 * create would fail the constraint and be retried).
 */
@Service
@RequiredArgsConstructor
public class ChatSettingsService {

    private final EventChatSettingsRepository settingsRepository;
    private final EventRepository eventRepository;

    /**
     * Fetch the settings for an event, creating a default row if none
     * exists yet. Idempotent.
     */
    @Transactional
    public EventChatSettings findOrCreate(UUID eventId) {
        return settingsRepository.findByEventId(eventId)
                .orElseGet(() -> createDefaults(eventId));
    }

    private EventChatSettings createDefaults(UUID eventId) {
        // Assert the event exists inside the current tenant before we
        // materialise a row — we do not want orphaned settings pointing
        // at a sibling tenant's event id (RLS would block the insert
        // anyway, but failing here gives a nicer error).
        eventRepository.findById(eventId).orElseThrow(
                () -> new IllegalArgumentException("Event not found: " + eventId));

        EventChatSettings settings = new EventChatSettings();
        settings.setEventId(eventId);
        // Defaults are provided by field initialisers on the entity.
        return settingsRepository.save(settings);
    }

    @Transactional
    public EventChatSettings update(UUID eventId, ChatSettingsPatch patch) {
        EventChatSettings settings = findOrCreate(eventId);
        if (patch.allowLinks() != null) settings.setAllowLinks(patch.allowLinks());
        if (patch.slowModeSeconds() != null) settings.setSlowModeSeconds(patch.slowModeSeconds());
        if (patch.showParticipantCount() != null) settings.setShowParticipantCount(patch.showParticipantCount());
        if (patch.showParticipantNames() != null) settings.setShowParticipantNames(patch.showParticipantNames());
        if (patch.welcomeMessage() != null) settings.setWelcomeMessage(patch.welcomeMessage());
        if (patch.premoderationEnabled() != null) settings.setPremoderationEnabled(patch.premoderationEnabled());
        if (patch.profanityFilterEnabled() != null) settings.setProfanityFilterEnabled(patch.profanityFilterEnabled());
        if (patch.antiSpamEnabled() != null) settings.setAntiSpamEnabled(patch.antiSpamEnabled());
        if (patch.chatMode() != null) {
            try {
                settings.setChatMode(ChatMode.valueOf(patch.chatMode()));
            } catch (IllegalArgumentException ignored) {
                // invalid value — ignore
            }
        }
        return settings;
    }

    /**
     * Partial update payload. Null fields mean "leave unchanged" — that
     * is the same semantics the REST layer uses for its PATCH endpoint,
     * so we expose it here to avoid a second DTO type.
     */
    public record ChatSettingsPatch(
            Boolean allowLinks,
            Integer slowModeSeconds,
            Boolean showParticipantCount,
            Boolean showParticipantNames,
            String welcomeMessage,
            Boolean premoderationEnabled,
            Boolean profanityFilterEnabled,
            Boolean antiSpamEnabled,
            String chatMode
    ) {}
}
