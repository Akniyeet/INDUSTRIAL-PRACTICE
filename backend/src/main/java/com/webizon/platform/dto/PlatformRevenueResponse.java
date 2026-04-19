package com.webizon.platform.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Revenue analytics for the platform admin.
 */
public record PlatformRevenueResponse(
        // Totals
        BigDecimal allTimeSubtotalKzt,
        BigDecimal allTimeVatKzt,
        BigDecimal allTimeTotalKzt,
        BigDecimal thisMonthSubtotalKzt,
        BigDecimal thisMonthVatKzt,
        BigDecimal thisMonthTotalKzt,
        BigDecimal outstandingKzt,

        // Top tenants by revenue
        List<TenantRevenueRow> topTenants,

        // Top events by revenue
        List<EventRevenueRow> topEvents,

        // Monthly trend (last 12 months)
        List<MonthlyRow> monthlyTrend
) {
    public record TenantRevenueRow(
            UUID tenantId,
            String tenantSlug,
            String displayName,
            BigDecimal totalKzt,
            BigDecimal vatKzt,
            long invoiceCount
    ) {}

    public record EventRevenueRow(
            UUID eventId,
            UUID tenantId,
            String tenantSlug,
            String eventTitle,
            BigDecimal totalKzt,
            long sessionCount
    ) {}

    public record MonthlyRow(
            String month,
            BigDecimal subtotalKzt,
            BigDecimal vatKzt,
            BigDecimal totalKzt,
            long payingTenants
    ) {}
}
