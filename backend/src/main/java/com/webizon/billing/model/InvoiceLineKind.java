package com.webizon.billing.model;

/**
 * Line-item taxonomy for {@link BillingInvoiceLine}.
 *
 * <p>Every usage kind from {@link UsageKind} maps one-to-one to a
 * line kind (so the invoice carries the same SEAT_LIVE / SEAT_AUTO /
 * STORAGE_GB_MONTH vocabulary the ledger uses), plus three
 * invoice-only synthetic kinds:
 *
 * <ul>
 *   <li><b>VAT</b> — 12% Kazakh VAT applied once per invoice on
 *       the sum of the non-synthetic lines. Stored as its own line
 *       so the customer sees the break-out explicitly.</li>
 *   <li><b>DISCOUNT</b> — negative-amount adjustment applied before
 *       VAT. Reserved for promo codes / hand-entered credits by an
 *       admin.</li>
 *   <li><b>ADJUSTMENT</b> — positive- or negative-amount correction
 *       after issue for a dispute resolution or bookkeeping fix. A
 *       non-null ADJUSTMENT line is the audit trail for any
 *       post-issue modification.</li>
 * </ul>
 */
public enum InvoiceLineKind {
    SEAT_LIVE,
    SEAT_AUTO,
    STORAGE_GB_MONTH,
    VAT,
    DISCOUNT,
    ADJUSTMENT;

    /** Which line kind corresponds to a given metered usage kind. */
    public static InvoiceLineKind forUsage(UsageKind kind) {
        return switch (kind) {
            case SEAT_LIVE        -> SEAT_LIVE;
            case SEAT_AUTO        -> SEAT_AUTO;
            case STORAGE_GB_MONTH -> STORAGE_GB_MONTH;
        };
    }
}
