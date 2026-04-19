package com.webizon.analytics.repo;

import com.webizon.analytics.model.LeadSignal;
import com.webizon.analytics.model.LeadSignalType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * JPA repository for {@link LeadSignal}.
 *
 * <p>Because signals are append-only, most reads are "every signal
 * for this profile" or "every signal of type X for this session".
 * More complex scoring queries (aggregate score by segment, ranked
 * lead lists) live in the report service and use JPQL/native SQL
 * directly there.
 */
public interface LeadSignalRepository extends JpaRepository<LeadSignal, UUID> {

    List<LeadSignal> findAllByProfileIdOrderByCreatedAtDesc(UUID profileId);

    List<LeadSignal> findAllBySessionIdAndSignalType(UUID sessionId, LeadSignalType type);

    /**
     * Idempotency check: has this profile already received a signal
     * of this type for this session? Used by the evaluator to avoid
     * emitting duplicate signals on repeated triggers (refresh,
     * heartbeat bursts).
     */
    boolean existsBySessionIdAndProfileIdAndSignalType(UUID sessionId, UUID profileId, LeadSignalType type);
}
