package com.webizon.tenancy.model;

import com.webizon.tenancy.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A tenant's subscription to a {@link Plan}.
 *
 * <p>Invariants (also enforced in the database; see {@code V001__core_tenancy.sql}):
 * <ul>
 *   <li>Exactly one non-terminal subscription per tenant (partial unique index).</li>
 *   <li>{@code current_period_end} is strictly after {@code current_period_start}.</li>
 *   <li>Status transitions are monotonic and only written by the billing pipeline.</li>
 * </ul>
 */
@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription extends TenantAwareEntity {

    @Column(name = "plan_id", nullable = false, columnDefinition = "UUID")
    private UUID planId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.TRIAL;

    @Column(name = "started_at", nullable = false)
    @Builder.Default
    private Instant startedAt = Instant.now();

    @Column(name = "current_period_start", nullable = false)
    @Builder.Default
    private Instant currentPeriodStart = Instant.now();

    @Column(name = "current_period_end", nullable = false)
    private Instant currentPeriodEnd;

    @Column(name = "trial_ends_at")
    private Instant trialEndsAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", length = 255)
    private String cancellationReason;

    /** Billing provider code, e.g. {@code "cloudpayments"}. */
    @Column(name = "external_provider", length = 32)
    private String externalProvider;

    /** Provider-side opaque subscription identifier. */
    @Column(name = "external_id", length = 128)
    private String externalId;
}
