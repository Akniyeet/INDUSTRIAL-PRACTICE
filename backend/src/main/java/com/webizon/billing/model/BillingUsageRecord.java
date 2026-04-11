package com.webizon.billing.model;

import com.webizon.tenancy.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One metered usage row. Append-only ledger; see V008 for the full
 * rationale behind per-session (not per-profile) aggregation and
 * rate snapshotting.
 *
 * <p>This entity intentionally does NOT carry JPA relationships to
 * {@link com.webizon.events.model.Session} or
 * {@link com.webizon.events.model.Event} — those FKs are plain UUID
 * columns. The billing module is a read-mostly accounting layer and
 * hydrating event graphs on every aggregation is expensive, so
 * billing stays deliberately flat.
 */
@Entity
@Table(name = "billing_usage_records")
@Getter
@Setter
public class BillingUsageRecord extends TenantAwareEntity {

    @Column(name = "session_id", columnDefinition = "UUID")
    private UUID sessionId;

    @Column(name = "event_id", columnDefinition = "UUID")
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 24)
    private UsageKind kind;

    /**
     * Natural-unit quantity: seat-seconds for SEAT_LIVE / SEAT_AUTO,
     * GB-seconds for STORAGE_GB_MONTH. Kept in the smallest
     * reasonable unit so the aggregation math stays in integers.
     */
    @Column(name = "quantity", nullable = false)
    private long quantity;

    /**
     * Rate snapshot at meter time. Semantics depend on kind; see
     * {@link BillingUsageRecord}'s class-level comment and the
     * {@link com.webizon.billing.config.BillingProperties}.
     */
    @Column(name = "rate_millis_per_unit", nullable = false)
    private long rateMillisPerUnit;

    /** Precomputed line amount in KZT-millis. */
    @Column(name = "amount_millis", nullable = false)
    private long amountMillis;

    /** Business timestamp for the usage (session end, typically). */
    @Column(name = "billed_for_instant", nullable = false)
    private Instant billedForInstant;

    /**
     * Non-null once an {@link BillingInvoice} has claimed this row.
     * Used for "open" usage queries (the tenant dashboard's current
     * period estimate) and to prevent double-invoicing the same row.
     */
    @Column(name = "invoice_id", columnDefinition = "UUID")
    private UUID invoiceId;
}
