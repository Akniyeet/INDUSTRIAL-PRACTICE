package com.webizon.chat.repo;

import com.webizon.chat.model.ChatUserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA repository for {@link ChatUserStatus}.
 *
 * <p>Lookups are always by the composite (session_id, user_id) pair
 * that mirrors the unique constraint — no point in querying by user_id
 * alone because moderation state is session-local.
 */
public interface ChatUserStatusRepository extends JpaRepository<ChatUserStatus, UUID> {

    Optional<ChatUserStatus> findBySessionIdAndUserId(UUID sessionId, UUID userId);
}
