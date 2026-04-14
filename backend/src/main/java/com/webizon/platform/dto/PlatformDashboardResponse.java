package com.webizon.platform.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Top-level platform KPIs returned by {@code GET /api/v1/platform/dashboard}.
 */
public record PlatformDashboardResponse(
        long totalTenants,
        long activeTenants,
        long totalUsers,
        long totalEvents,
        long totalSessions,
        long currentlyLiveSessions,

        // Financial — all in KZT (full units, not millis)
        BigDecimal revenueThisMonthKzt,
        BigDecimal vatThisMonthKzt,
        BigDecimal revenueAllTimeKzt,

        List<MonthlyRevenue> monthlyRevenue
) {
    public record MonthlyRevenue(
            String month,         // "2025-03"
            BigDecimal totalKzt,
            BigDecimal vatKzt,
            long payingTenants
    ) {}
}
