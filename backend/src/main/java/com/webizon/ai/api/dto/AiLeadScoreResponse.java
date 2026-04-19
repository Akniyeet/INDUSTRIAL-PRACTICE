package com.webizon.ai.api.dto;

import com.webizon.ai.model.AiLeadScore;
import com.webizon.ai.model.LeadClassification;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AiLeadScoreResponse(
        UUID id,
        UUID eventId,
        UUID sessionId,
        UUID profileId,
        int ruleScore,
        LeadClassification classification,
        float confidence,
        String reasoning,
        String recommendedAction,
        Integer followUpHours,
        String aiModel,
        int watchDurationSeconds,
        float watchPercent,
        int chatMessagesCount,
        int ctaClicksCount,
        List<Map<String, Object>> ctaDetails,
        boolean returnedForAuto,
        Instant createdAt
) {
    public static AiLeadScoreResponse from(AiLeadScore s) {
        return new AiLeadScoreResponse(
                s.getId(),
                s.getEventId(),
                s.getSessionId(),
                s.getProfileId(),
                s.getRuleScore(),
                s.getClassification(),
                s.getConfidence(),
                s.getReasoning(),
                s.getRecommendedAction(),
                s.getFollowUpHours(),
                s.getAiModel(),
                s.getWatchDurationSeconds(),
                s.getWatchPercent(),
                s.getChatMessagesCount(),
                s.getCtaClicksCount(),
                s.getCtaDetails(),
                s.isReturnedForAuto(),
                s.getCreatedAt()
        );
    }
}
