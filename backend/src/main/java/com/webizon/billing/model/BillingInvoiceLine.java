package com.webizon.billing.model;

import com.webizon.tenancy.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Itemised row of a {@link BillingInvoice}. The header references
 * lines indirectly through {@code invoice_id}; the reverse
 * relationship is not materialised as a JPA association because the
 * invoice builder batches bulk writes with {@link
 * org.springframework.data.jpa.repository.JpaRepository#saveAll(Iterable)}
 * and a {@code @OneToMany} on the header would force a per-line
 * orphan-removal scan every save.
 *
 * <p>{@code lineNumber} is the 1-indexed display order inside the
 * invoice. It is unique per invoice (enforced by DB constraint) so
 * a reordering edit must rewrite the line numbers explicitly.
 *
 * <p>{@code unitLabel} is frozen text at invoice time. It is NOT
 * derived from the enum so the historical invoice rendering keeps
 * its wording even if we rename "seat-minutes" to "participant
 * minutes" in a future release.
 */
@Entity
@Table(name = "billing_invoice_lines")
@Getter
@Setter
public class BillingInvoiceLine extends TenantAwareEntity {

    @Column(name = "invoice_id", nullable = false, columnDefinition = "UUID")
    private UUID invoiceId;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 24)
    private InvoiceLineKind kind;

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    @Column(name = "quantity", nullable = false)
    private long quantity;

    @Column(name = "unit_label", nullable = false, length = 32)
    private String unitLabel;

    @Column(name = "unit_rate_millis", nullable = false)
    private long unitRateMillis;

    @Column(name = "amount_millis", nullable = false)
    private long amountMillis;
}
