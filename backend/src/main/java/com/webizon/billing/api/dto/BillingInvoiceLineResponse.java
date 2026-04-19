package com.webizon.billing.api.dto;

import com.webizon.billing.model.BillingInvoiceLine;
import com.webizon.billing.model.InvoiceLineKind;

import java.util.UUID;

/**
 * Wire shape for a single invoice line. Mirrors the persistence
 * record field-for-field but strips the tenant id and row-audit
 * columns — the tenant dashboard only needs the numbers.
 *
 * <p>Amounts are serialised as whole KZT-millis so the frontend can
 * format them with the same {@code formatMoney} helper the invoice
 * PDF uses and there is no JSON parsing of decimals. The frontend
 * divides by {@code 1000} when rendering KZT and by
 * {@code 100000} when rendering minor-unit tiyin if ever needed.
 */
public record BillingInvoiceLineResponse(
        UUID id,
        int lineNumber,
        InvoiceLineKind kind,
        String description,
        long quantity,
        String unitLabel,
        long unitRateMillis,
        long amountMillis
) {
    public static BillingInvoiceLineResponse from(BillingInvoiceLine line) {
        return new BillingInvoiceLineResponse(
                line.getId(),
                line.getLineNumber(),
                line.getKind(),
                line.getDescription(),
                line.getQuantity(),
                line.getUnitLabel(),
                line.getUnitRateMillis(),
                line.getAmountMillis());
    }
}
