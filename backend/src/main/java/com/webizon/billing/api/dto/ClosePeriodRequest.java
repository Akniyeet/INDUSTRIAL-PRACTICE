package com.webizon.billing.api.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Admin request body for a manual period close. The scheduled close
 * job will eventually drive this end-to-end, but the manual path is
 * retained for three reasons:
 * <ul>
 *   <li>Re-closing a previously-voided draft for the same period
 *       (the unique constraint is only on non-voided invoices in the
 *       intended final design).</li>
 *   <li>Closing a short ad-hoc period for enterprise tenants that
 *       opt into weekly billing.</li>
 *   <li>Integration tests can drive the flow deterministically
 *       without waiting for the real-clock scheduler tick.</li>
 * </ul>
 *
 * <p>Both bounds are required. The service layer enforces
 * {@code periodStart < periodEnd}; allowing a zero-width or inverted
 * range here would only produce a less-informative error later.
 */
public record ClosePeriodRequest(
        @NotNull Instant periodStart,
        @NotNull Instant periodEnd
) {}
