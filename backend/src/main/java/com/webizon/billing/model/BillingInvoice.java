package com.webizon.billing.model;

import com.webizon.tenancy.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Header row of a billing invoice. Lines hang off this via
 * {@link BillingInvoiceLine#getInvoiceId()}.
 *
 * <p>Totals are denormalised onto the header so tenant-facing
 * queries can answer "what do I owe?" without joining the lines
 * table. The invariant {@code total_millis = subtotal_millis +
 * vat_millis} is enforced by {@code InvoiceService} when writing and
 * is safe to re-verify on any read.
 *
 * <p>{@code vatPercentSnapshot} freezes the VAT rate at issue time
 * so re-rendering an historical PDF produces the same numbers even
 * if VAT changes in a future tax year.
 */
@Entity
@Table(name = "billing_invoices")
@Getter
@Setter
public class BillingInvoice extends TenantAwareEntity {

    /**
     * Human-readable sequential number assigned at issue time. Null
     * while DRAFT so a voided draft never consumes a number from
     * the sequence.
     */
    @Column(name = "invoice_number", length = 32)
    private String invoiceNumber;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private InvoiceStatus status = InvoiceStatus.DRAFT;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "KZT";

    @Column(name = "subtotal_millis", nullable = false)
    private long subtotalMillis;

    @Column(name = "vat_millis", nullable = false)
    private long vatMillis;

    @Column(name = "total_millis", nullable = false)
    private long totalMillis;

    @Column(name = "vat_percent_snapshot", nullable = false, precision = 5, scale = 2)
    private BigDecimal vatPercentSnapshot;

    /** Link to the generated PDF (null while DRAFT). */
    @Column(name = "pdf_asset_id", columnDefinition = "UUID")
    private UUID pdfAssetId;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "voided_at")
    private Instant voidedAt;
}
