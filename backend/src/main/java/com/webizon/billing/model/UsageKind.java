package com.webizon.billing.model;

/**
 * Taxonomy of metered usage captured by the billing module.
 *
 * <p>Every {@link BillingUsageRecord} belongs to exactly one kind.
 * The invoice builder groups usage records by {@code (tenant_id,
 * kind)} to produce line items, so adding a new kind means
 * extending this enum, {@link InvoiceLineKind}, the database CHECK
 * constraint on {@code billing_usage_records.kind}, and the
 * switch in {@code InvoiceService.lineKindFor}.
 *
 * <ul>
 *   <li><b>SEAT_LIVE</b> — one viewer-second of attendance on a
 *       LIVE session. Rate is charged at
 *       {@code rates.seat-live} KZT per seat-minute.</li>
 *   <li><b>SEAT_AUTO</b> — same measurement applied to an AUTO
 *       replay session. Charged at the cheaper
 *       {@code rates.seat-auto} rate because auto sessions have
 *       lower operational cost (no live-capture pipeline).</li>
 *   <li><b>STORAGE_GB_MONTH</b> — aggregate storage footprint of
 *       UPLOADED {@code file_assets} rows belonging to the tenant,
 *       accrued daily as GB-seconds and converted at invoice time.
 *       Reserved for a future storage-sweep job; kept in the enum
 *       now so V008 does not need a follow-up migration later.</li>
 * </ul>
 */
public enum UsageKind {
    SEAT_LIVE,
    SEAT_AUTO,
    STORAGE_GB_MONTH
}
