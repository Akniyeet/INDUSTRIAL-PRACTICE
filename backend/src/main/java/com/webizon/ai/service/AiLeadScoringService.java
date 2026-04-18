package com.webizon.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webizon.ai.AnthropicClient;
import com.webizon.ai.AnthropicClient.AiResponse;
import com.webizon.ai.model.AiLeadScore;
import com.webizon.ai.model.LeadClassification;
import com.webizon.ai.repo.AiLeadScoreRepository;
import com.webizon.analytics.model.AnalyticsEvent;
import com.webizon.analytics.model.AnalyticsEventType;
import com.webizon.analytics.model.LeadSignal;
import com.webizon.analytics.model.SessionAttendance;
import com.webizon.analytics.repo.AnalyticsEventRepository;
import com.webizon.analytics.repo.LeadSignalRepository;
import com.webizon.analytics.repo.SessionAttendanceRepository;
import com.webizon.events.model.Event;
import com.webizon.events.model.Session;
import com.webizon.events.repo.EventRepository;
import com.webizon.events.repo.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AI-powered lead scoring engine.
 *
 * <p>For each attendee of a session, collects behavioral data (watch time,
 * CTA clicks, chat messages, return visits) and sends a structured prompt
 * to Claude for classification. Falls back to rule-based scoring when the
 * API key is not configured.
 *
 * <h2>Scoring flow</h2>
 * <ol>
 *   <li>Collect behavioral snapshot from analytics tables</li>
 *   <li>Compute rule-based score from lead_signals</li>
 *   <li>Build structured prompt with session context</li>
 *   <li>Call Claude API for classification</li>
 *   <li>Parse response and upsert into ai_lead_scores</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiLeadScoringService {

    private final AiLeadScoreRepository scoreRepository;
    private final SessionAttendanceRepository attendanceRepository;
    private final LeadSignalRepository leadSignalRepository;
    private final AnalyticsEventRepository analyticsEventRepository;
    private final SessionRepository sessionRepository;
    private final EventRepository eventRepository;
    private final AnthropicClient anthropicClient;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
            Ты — AI-аналитик лидов образовательной платформы Webizon. Твоя задача — \
            оценить вовлечённость зрителя вебинара и классифицировать его как лид.

            Правила классификации:
            - HOT (горячий): высокая вовлечённость, кликнул на курсовой CTA, смотрел долго, \
              активен в чате. Рекомендация: связаться в течение 24 часов.
            - WARM (тёплый): средняя вовлечённость, смотрел значительную часть, возможно \
              кликнул на информационный CTA. Рекомендация: отправить follow-up через 2-3 дня.
            - COLD (холодный): низкая вовлечённость, быстро ушёл или минимальная активность. \
              Рекомендация: добавить в рассылку для повторного приглашения.

            Учитывай контекст темы вебинара при оценке. Для образовательных тем \
            (программирование, английский) долгий просмотр = сильный сигнал. \
            Для маркетинговых тем клик на CTA = сильнее чем просто просмотр.

            ВАЖНО: Отвечай ТОЛЬКО валидным JSON без markdown-разметки. Никакого текста до или после JSON.
            """;

    /**
     * Score all attendees of a session. Called after session ends.
     */
    @Transactional
    public List<AiLeadScore> scoreSession(UUID sessionId) {
        Session session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            log.warn("Session {} not found for AI scoring", sessionId);
            return List.of();
        }

        Event event = eventRepository.findById(session.getEventId()).orElse(null);
        if (event == null) {
            log.warn("Event {} not found for session {}", session.getEventId(), sessionId);
            return List.of();
        }

        List<SessionAttendance> attendees =
                attendanceRepository.findAllBySessionIdOrderByFirstJoinedAtAsc(sessionId);

        if (attendees.isEmpty()) {
            log.info("No attendees for session {} — skipping AI scoring", sessionId);
            return List.of();
        }

        log.info("Starting AI lead scoring for session {} ({} attendees)", sessionId, attendees.size());

        int plannedDuration = session.getPlannedDurationSeconds() > 0
                ? session.getPlannedDurationSeconds()
                : 3600; // default 1h

        List<AiLeadScore> results = new ArrayList<>();
        for (SessionAttendance attendee : attendees) {
            try {
                AiLeadScore score = scoreAttendee(attendee, event, session, plannedDuration);
                results.add(score);
            } catch (Exception ex) {
                log.warn("Failed to score attendee {} in session {}: {}",
                        attendee.getProfileId(), sessionId, ex.getMessage());
            }
        }

        log.info("AI scoring complete for session {}: {} scored ({} hot, {} warm, {} cold)",
                sessionId, results.size(),
                results.stream().filter(s -> s.getClassification() == LeadClassification.HOT).count(),
                results.stream().filter(s -> s.getClassification() == LeadClassification.WARM).count(),
                results.stream().filter(s -> s.getClassification() == LeadClassification.COLD).count());

        return results;
    }

    /**
     * Score a single attendee. Can be called on-demand from admin panel.
     */
    @Transactional
    public AiLeadScore scoreAttendee(UUID sessionId, UUID profileId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        Event event = eventRepository.findById(session.getEventId())
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + session.getEventId()));
        SessionAttendance attendance = attendanceRepository.findBySessionIdAndProfileId(sessionId, profileId)
                .orElseThrow(() -> new IllegalArgumentException("Attendee not found"));

        int plannedDuration = session.getPlannedDurationSeconds() > 0
                ? session.getPlannedDurationSeconds() : 3600;

        return scoreAttendee(attendance, event, session, plannedDuration);
    }

    private AiLeadScore scoreAttendee(SessionAttendance attendance, Event event,
                                       Session session, int plannedDuration) {
        UUID profileId = attendance.getProfileId();
        UUID sessionId = attendance.getSessionId();

        // --- Collect behavioral data ---

        int watchSeconds = attendance.getTotalConnectedSeconds();
        float watchPercent = plannedDuration > 0
                ? Math.min(100f, (watchSeconds * 100f) / plannedDuration) : 0f;

        // Chat messages
        long chatCount = analyticsEventRepository.countBySessionIdAndProfileIdAndEventTypeIn(
                sessionId, profileId,
                List.of(AnalyticsEventType.CHAT_MESSAGE_SENT, AnalyticsEventType.CHAT_REPLY_SENT));

        // CTA clicks
        List<AnalyticsEvent> ctaEvents = analyticsEventRepository
                .findAllBySessionIdAndProfileIdAndEventTypeIn(
                        sessionId, profileId,
                        List.of(AnalyticsEventType.CTA_CLICK, AnalyticsEventType.CTA_DOWNLOAD));

        List<Map<String, Object>> ctaDetails = ctaEvents.stream()
                .map(e -> {
                    Map<String, Object> detail = new HashMap<>();
                    if (e.getMetadata() != null) {
                        detail.putAll(e.getMetadata());
                    }
                    detail.put("offsetMinutes", e.getOffsetSeconds() != null
                            ? e.getOffsetSeconds() / 60 : null);
                    detail.put("type", e.getEventType().name());
                    return detail;
                })
                .collect(Collectors.toList());

        // Return for auto
        List<SessionAttendance> history =
                attendanceRepository.findAllByProfileIdOrderByFirstJoinedAtDesc(profileId);
        boolean returnedForAuto = history.stream()
                .filter(a -> a.getEventId().equals(event.getId()))
                .count() >= 2;

        // Rule score
        int ruleScore = leadSignalRepository.findAllByProfileIdOrderByCreatedAtDesc(profileId).stream()
                .filter(s -> sessionId.equals(s.getSessionId()))
                .mapToInt(LeadSignal::getScore)
                .sum();

        // --- Build AI prompt ---

        String userMessage = buildUserPrompt(event, watchSeconds, plannedDuration,
                watchPercent, chatCount, ctaDetails, returnedForAuto, ruleScore);

        // --- Call Claude ---

        LeadClassification classification;
        float confidence;
        String reasoning;
        String recommendedAction;
        Integer followUpHours;
        String aiModel = null;
        Integer promptTokens = null;
        Integer completionTokens = null;

        Optional<AiResponse> aiResponse = anthropicClient.chat(SYSTEM_PROMPT, userMessage);

        if (aiResponse.isPresent()) {
            AiResponse resp = aiResponse.get();
            aiModel = resp.model();
            promptTokens = resp.usage().inputTokens();
            completionTokens = resp.usage().outputTokens();

            try {
                JsonNode json = objectMapper.readTree(resp.text());
                classification = parseClassification(json.path("classification").asText("cold"));
                confidence = (float) json.path("confidence").asDouble(0.5);
                reasoning = json.path("reasoning").asText(null);
                recommendedAction = json.path("recommended_action").asText(null);
                followUpHours = json.has("follow_up_hours")
                        ? json.path("follow_up_hours").asInt(72) : 72;
            } catch (Exception ex) {
                log.warn("Failed to parse AI response, falling back to rule-based: {}", ex.getMessage());
                classification = classifyByRuleScore(ruleScore);
                confidence = 0.3f;
                reasoning = "AI response parse error — rule-based fallback";
                recommendedAction = defaultAction(classification);
                followUpHours = defaultFollowUpHours(classification);
            }
        } else {
            // No AI — pure rule-based
            classification = classifyByRuleScore(ruleScore);
            confidence = 0.5f;
            reasoning = ruleBasedReasoning(ruleScore, watchPercent, chatCount, ctaDetails.size());
            recommendedAction = defaultAction(classification);
            followUpHours = defaultFollowUpHours(classification);
        }

        // --- Upsert ---

        AiLeadScore score = scoreRepository.findBySessionIdAndProfileId(sessionId, profileId)
                .orElseGet(AiLeadScore::new);

        score.setEventId(event.getId());
        score.setSessionId(sessionId);
        score.setProfileId(profileId);
        score.setRuleScore(ruleScore);
        score.setClassification(classification);
        score.setConfidence(confidence);
        score.setReasoning(reasoning);
        score.setRecommendedAction(recommendedAction);
        score.setFollowUpHours(followUpHours);
        score.setAiModel(aiModel);
        score.setPromptTokens(promptTokens);
        score.setCompletionTokens(completionTokens);
        score.setWatchDurationSeconds(watchSeconds);
        score.setWatchPercent(watchPercent);
        score.setChatMessagesCount((int) chatCount);
        score.setCtaClicksCount(ctaDetails.size());
        score.setCtaDetails(ctaDetails);
        score.setReturnedForAuto(returnedForAuto);

        return scoreRepository.save(score);
    }

    private String buildUserPrompt(Event event, int watchSeconds, int plannedDuration,
                                    float watchPercent, long chatCount,
                                    List<Map<String, Object>> ctaDetails,
                                    boolean returnedForAuto, int ruleScore) {
        StringBuilder sb = new StringBuilder();
        sb.append("Данные сессии:\n");
        sb.append("- Тема вебинара: ").append(event.getTitle()).append("\n");
        sb.append("- Длительность просмотра: ").append(watchSeconds / 60)
                .append(" мин (из ").append(plannedDuration / 60).append(")\n");
        sb.append("- Процент просмотра: ").append(String.format("%.0f%%", watchPercent)).append("\n");
        sb.append("- Сообщений в чате: ").append(chatCount).append("\n");

        if (!ctaDetails.isEmpty()) {
            sb.append("- CTA-клики:\n");
            for (Map<String, Object> cta : ctaDetails) {
                String ctaTitle = cta.getOrDefault("ctaTitle", "CTA").toString();
                Object offsetMin = cta.get("offsetMinutes");
                sb.append("  - \"").append(ctaTitle).append("\"");
                if (offsetMin != null) {
                    sb.append(" (на ").append(offsetMin).append(" мин)");
                }
                sb.append("\n");
            }
        } else {
            sb.append("- CTA-клики: нет\n");
        }

        sb.append("- Посещение авто-повтора: ").append(returnedForAuto ? "да" : "нет").append("\n");
        sb.append("- Rule-based score: ").append(ruleScore).append(" баллов\n");

        sb.append("\nВерни JSON:\n");
        sb.append("""
                {
                  "classification": "hot|warm|cold",
                  "confidence": 0.0-1.0,
                  "reasoning": "2-3 предложения на русском",
                  "recommended_action": "что должен сделать менеджер",
                  "follow_up_hours": 24
                }
                """);

        return sb.toString();
    }

    // --- Rule-based fallback ---

    private LeadClassification classifyByRuleScore(int score) {
        if (score >= 50) return LeadClassification.HOT;
        if (score >= 20) return LeadClassification.WARM;
        return LeadClassification.COLD;
    }

    private String ruleBasedReasoning(int score, float watchPercent, long chatCount, int ctaClicks) {
        StringBuilder sb = new StringBuilder();
        sb.append("Rule-based оценка: ").append(score).append(" баллов. ");
        if (watchPercent >= 70) sb.append("Просмотр >70%. ");
        if (chatCount > 3) sb.append("Активен в чате (").append(chatCount).append(" сообщений). ");
        if (ctaClicks > 0) sb.append("Кликнул на ").append(ctaClicks).append(" CTA. ");
        if (sb.toString().endsWith(". ")) return sb.toString().trim();
        return "Минимальная активность.";
    }

    private String defaultAction(LeadClassification c) {
        return switch (c) {
            case HOT -> "Связаться в течение 24 часов. Предложить запись на курс.";
            case WARM -> "Отправить follow-up email через 2-3 дня с материалами.";
            case COLD -> "Добавить в рассылку для повторного приглашения на следующий эфир.";
        };
    }

    private Integer defaultFollowUpHours(LeadClassification c) {
        return switch (c) {
            case HOT -> 24;
            case WARM -> 72;
            case COLD -> 168;
        };
    }

    private LeadClassification parseClassification(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "hot" -> LeadClassification.HOT;
            case "warm" -> LeadClassification.WARM;
            default -> LeadClassification.COLD;
        };
    }
}
