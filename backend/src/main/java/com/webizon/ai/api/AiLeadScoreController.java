package com.webizon.ai.api;

import com.webizon.ai.api.dto.AiLeadScoreResponse;
import com.webizon.ai.api.dto.SessionLeadSummaryResponse;
import com.webizon.ai.model.AiLeadScore;
import com.webizon.ai.model.LeadClassification;
import com.webizon.ai.repo.AiLeadScoreRepository;
import com.webizon.ai.service.AiLeadScoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Admin endpoints for AI lead scoring.
 *
 * <p>Provides session-level lead analysis, on-demand re-scoring,
 * and per-profile lead history. Gated to OWNER/ADMIN/ANALYST roles.
 */
@RestController
@RequestMapping("/api/v1/ai/lead-scores")
@RequiredArgsConstructor
public class AiLeadScoreController {

    private final AiLeadScoringService scoringService;
    private final AiLeadScoreRepository scoreRepository;

    /**
     * Get all lead scores for a session.
     * Optionally filter by classification (hot/warm/cold).
     */
    @GetMapping("/sessions/{sessionId}")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_ANALYST')")
    public List<AiLeadScoreResponse> getSessionScores(
            @PathVariable UUID sessionId,
            @RequestParam(required = false) LeadClassification classification) {
        List<AiLeadScore> scores = classification != null
                ? scoreRepository.findAllBySessionIdAndClassificationOrderByConfidenceDesc(sessionId, classification)
                : scoreRepository.findAllBySessionIdOrderByConfidenceDesc(sessionId);
        return scores.stream().map(AiLeadScoreResponse::from).toList();
    }

    /**
     * Get lead summary stats for a session.
     */
    @GetMapping("/sessions/{sessionId}/summary")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_ANALYST')")
    public SessionLeadSummaryResponse getSessionSummary(@PathVariable UUID sessionId) {
        long total = scoreRepository.countBySessionId(sessionId);
        long hot = scoreRepository.countBySessionIdAndClassification(sessionId, LeadClassification.HOT);
        long warm = scoreRepository.countBySessionIdAndClassification(sessionId, LeadClassification.WARM);
        long cold = scoreRepository.countBySessionIdAndClassification(sessionId, LeadClassification.COLD);
        return new SessionLeadSummaryResponse(total, hot, warm, cold);
    }

    /**
     * Score all attendees of a session (on-demand trigger).
     * Useful for re-scoring after API key is configured or model is changed.
     */
    @PostMapping("/sessions/{sessionId}/score")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN')")
    public List<AiLeadScoreResponse> scoreSession(@PathVariable UUID sessionId) {
        List<AiLeadScore> results = scoringService.scoreSession(sessionId);
        return results.stream().map(AiLeadScoreResponse::from).toList();
    }

    /**
     * Score a single attendee on-demand.
     */
    @PostMapping("/sessions/{sessionId}/profiles/{profileId}/score")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN')")
    public AiLeadScoreResponse scoreAttendee(
            @PathVariable UUID sessionId,
            @PathVariable UUID profileId) {
        AiLeadScore score = scoringService.scoreAttendee(sessionId, profileId);
        return AiLeadScoreResponse.from(score);
    }

    /**
     * Lead score history for a specific user across all sessions.
     */
    @GetMapping("/profiles/{profileId}")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_ANALYST')")
    public List<AiLeadScoreResponse> getProfileHistory(@PathVariable UUID profileId) {
        return scoreRepository.findAllByProfileIdOrderByCreatedAtDesc(profileId)
                .stream().map(AiLeadScoreResponse::from).toList();
    }
}
