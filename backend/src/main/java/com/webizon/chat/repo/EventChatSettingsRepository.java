package com.webizon.chat.repo;

import com.webizon.chat.model.EventChatSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA repository for {@link EventChatSettings}. One row per event,
 * enforced by the {@code event_chat_settings_event_uniq} unique
 * constraint.
 */
public interface EventChatSettingsRepository extends JpaRepository<EventChatSettings, UUID> {

    Optional<EventChatSettings> findByEventId(UUID eventId);
}
