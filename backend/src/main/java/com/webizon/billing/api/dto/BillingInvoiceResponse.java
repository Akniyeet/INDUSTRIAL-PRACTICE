package com.webizon.billing.api.dto;

import com.webizon.billing.model.BillingInvoice;
import com.webizon.billing.model.BillingInvoiceLine;
import com.webizon.billing.model.InvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Wire shape for a billing invoice. The {@code lines} field is
 * optional: the list endpoint omits it (null) to keep the payload
 * compact, while the single-invoice GET populates it in display
 * order.
 *
 * <p>{@code pdfAssetId} lets the frontend call the storage module to
 * presign a download URL. The billing controller deliberately does
 * <em>not</em> proxy the PDF itself — the two-step download stays
 * consistent with every other file in the system.
 */
public record BillingInvoiceResponse(
        UUID id,
        String invoiceNumber,
        Instant periodStart,
        Instant periodEnd,
        InvoiceStatus status,
        String currency,
        long subtotalMillis,
        long vatMillis,
        long totalMillis,
        BigDecimal vatPercentSnapshot,
        UUID pdfAssetId,
        Instant issuedAt,
        Instant paidAt,
        Instant voidedAt,
        List<BillingInvoiceLineResponse> lines
) {

    /** Header-only projection for list endpoints. */
    public static BillingInvoiceResponse header(BillingInvoice invoice) {
        return new BillingInvoiceResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getPeriodStart(),
                invoice.getPeriodEnd(),
                invoice.getStatus(),
                invoice.getCurrency(),
                invoice.getSubtotalMillis(),
                invoice.getVatMillis(),
                invoice.getTotalMillis(),
                invoice.getVatPercentSnapshot(),
                invoice.getPdfAssetId(),
                invoice.getIssuedAt(),
                invoice.getPaidAt(),
                invoice.getVoidedAt(),
                null);
    }

    /** Header plus materialised lines for the detail endpoint. */
    public static BillingInvoiceResponse withLines(BillingInvoice invoice,
                                                    List<BillingInvoiceLine> lines) {
        return new BillingInvoiceResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getPeriodStart(),
                invoice.getPeriodEnd(),
                invoice.getStatus(),
                invoice.getCurrency(),
                invoice.getSubtotalMillis(),
                invoice.getVatMillis(),
                invoice.getTotalMillis(),
                invoice.getVatPercentSnapshot(),
                invoice.getPdfAssetId(),
                invoice.getIssuedAt(),
                invoice.getPaidAt(),
                invoice.getVoidedAt(),
                lines.stream().map(BillingInvoiceLineResponse::from).toList());
    }
}
