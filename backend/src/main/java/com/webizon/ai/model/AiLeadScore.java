package com.webizon.ai.model;

import com.webizon.tenancy.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI-powered lead classification for a user in a specific session.
 *
 * <p>Combines rule-based score from {@code lead_signals} with an AI
 * classification from Claude. One row per (session, profile) pair —
 * upserted after session ends or on-demand from the admin panel.
 */
@Entity
@Table(name = "ai_lead_scores")
@Getter
@Setter
public class AiLeadScore extends TenantAwareEntity {

    @Column(name = "event_id", nullable = false, columnDefinition = "UUID")
    private UUID eventId;

    @Column(name = "session_id", nullable = false, columnDefinition = "UUID")
    private UUID sessionId;

    @Column(name = "profile_id", nullable = false, columnDefinition = "UUID")
    private UUID profileId;

    // --- Rule-based score ---

    @Column(name = "rule_score", nullable = false)
    private int ruleScore;

    // --- AI classification ---

    @Enumerated(EnumType.STRING)
    @Column(name = "classification", nullable = false, length = 16)
    private LeadClassification classification = LeadClassification.COLD;

    @Column(name = "confidence", nullable = false)
    private float confidence;

    @Column(name = "reasoning", columnDefinition = "TEXT")
    private String reasoning;

    @Column(name = "recommended_action", columnDefinition = "TEXT")
    private String recommendedAction;

    @Column(name = "follow_up_hours")
    private Integer followUpHours;

    // --- Model metadata ---

    @Column(name = "ai_model", length = 64)
    private String aiModel;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    // --- Behavioral summary (snapshot at scoring time) ---

    @Column(name = "watch_duration_seconds", nullable = false)
    private int watchDurationSeconds;

    @Column(name = "watch_percent", nullable = false)
    private float watchPercent;

    @Column(name = "chat_messages_count", nullable = false)
    private int chatMessagesCount;

    @Column(name = "cta_clicks_count", nullable = false)
    private int ctaClicksCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cta_details", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> ctaDetails = new ArrayList<>();

    @Column(name = "returned_for_auto", nullable = false)
    private boolean returnedForAuto;
}
