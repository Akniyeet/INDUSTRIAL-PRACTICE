package com.webizon.analytics.api;

import com.webizon.analytics.api.dto.SessionReportResponse;
import com.webizon.analytics.service.AnalyticsReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Admin read-side for the session report screen. Every number here
 * comes from {@link AnalyticsReportService} and is recomputed on
 * demand — there is no cache layer yet because the aggregates are
 * fast (single-session scope, indexed columns) and the admin surface
 * is low-traffic.
 *
 * <p>Gated to owner / admin / presenter — moderators don't get the
 * CTA CTR split by design: conversion numbers live with the people
 * running the sales funnel.
 */
@RestController
@RequestMapping("/api/v1/events/{eventId}/sessions/{sessionId}/report")
@RequiredArgsConstructor
public class AnalyticsReportController {

    private final AnalyticsReportService analyticsReportService;

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_PRESENTER')")
    public SessionReportResponse report(@PathVariable UUID eventId,
                                         @PathVariable UUID sessionId) {
        return new SessionReportResponse(
                sessionId,
                analyticsReportService.summarise(sessionId),
                analyticsReportService.retentionCurve(sessionId),
                analyticsReportService.ctaCtr(eventId, sessionId)
        );
    }
}
