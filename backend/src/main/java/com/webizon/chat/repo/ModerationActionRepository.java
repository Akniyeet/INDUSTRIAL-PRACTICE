package com.webizon.chat.repo;

import com.webizon.chat.model.ModerationAction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * JPA repository for {@link ModerationAction}. Append-only — the entity
 * has no setters for the id and the table has no updated_at trigger, so
 * save() is effectively insert.
 */
public interface ModerationActionRepository extends JpaRepository<ModerationAction, UUID> {

    List<ModerationAction> findAllBySessionIdOrderByCreatedAtDesc(UUID sessionId, Pageable pageable);

    long countBySessionIdAndTargetUserIdAndActionType(UUID sessionId, UUID targetUserId,
                                                      com.webizon.chat.model.ModerationActionType actionType);
}
