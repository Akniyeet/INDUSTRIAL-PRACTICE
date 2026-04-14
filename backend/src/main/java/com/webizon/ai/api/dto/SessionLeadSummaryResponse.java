package com.webizon.ai.api.dto;

public record SessionLeadSummaryResponse(
        long total,
        long hot,
        long warm,
        long cold
) {
    public float hotPercent() {
        return total > 0 ? (hot * 100f) / total : 0f;
    }

    public float warmPercent() {
        return total > 0 ? (warm * 100f) / total : 0f;
    }

    public float coldPercent() {
        return total > 0 ? (cold * 100f) / total : 0f;
    }
}
