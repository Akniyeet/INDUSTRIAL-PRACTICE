package com.webizon.analytics.api.dto;

import com.webizon.analytics.service.AnalyticsReportService;

import java.util.List;
import java.util.UUID;

/**
 * Aggregate report view for one session — the shape the admin
 * dashboard renders in a single page. A single endpoint returns the
 * whole bundle to keep the round-trip count predictable on a slow
 * connection.
 */
public record SessionReportResponse(
        UUID sessionId,
        AnalyticsReportService.SessionSummary summary,
        List<AnalyticsReportService.RetentionPoint> retentionCurve,
        List<AnalyticsReportService.CtaCtrRow> ctaCtr
) {}
