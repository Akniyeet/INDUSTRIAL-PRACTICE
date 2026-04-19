package com.webizon.ai.repo;

import com.webizon.ai.model.AiLeadScore;
import com.webizon.ai.model.LeadClassification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiLeadScoreRepository extends JpaRepository<AiLeadScore, UUID> {

    Optional<AiLeadScore> findBySessionIdAndProfileId(UUID sessionId, UUID profileId);

    List<AiLeadScore> findAllBySessionIdOrderByConfidenceDesc(UUID sessionId);

    List<AiLeadScore> findAllBySessionIdAndClassificationOrderByConfidenceDesc(
            UUID sessionId, LeadClassification classification);

    List<AiLeadScore> findAllByProfileIdOrderByCreatedAtDesc(UUID profileId);

    @Query("""
           SELECT COUNT(a) FROM AiLeadScore a
           WHERE a.sessionId = :sessionId AND a.classification = :classification
           """)
    long countBySessionIdAndClassification(
            @Param("sessionId") UUID sessionId,
            @Param("classification") LeadClassification classification);

    @Query("""
           SELECT COUNT(a) FROM AiLeadScore a
           WHERE a.sessionId = :sessionId
           """)
    long countBySessionId(@Param("sessionId") UUID sessionId);
}
