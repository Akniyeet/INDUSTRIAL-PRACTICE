package com.webizon.tenancy.model;

import com.webizon.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A rate card in the Webizon billing catalog.
 *
 * <p><strong>Global entity</strong> — plans are shared across all tenants.
 *
 * <p>All monetary values are stored as {@code BIGINT} in the smallest currency
 * unit (tiyn for KZT, cents for USD). Never use {@code double}/{@code float} for
 * money — see ADR-0004 §Monetary representation.
 */
@Entity
@Table(name = "plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Plan extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 32)
    private String code;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "CHAR(3)")
    @Builder.Default
    private String currency = "KZT";

    /** Minor units per live seat (e.g. 1500 = 15.00 KZT). */
    @Column(name = "live_seat_rate_minor", nullable = false)
    private long liveSeatRateMinor;

    /** Minor units per auto-replay seat. */
    @Column(name = "auto_seat_rate_minor", nullable = false)
    private long autoSeatRateMinor;

    /** Minor units per GB of recording storage per month. */
    @Column(name = "storage_gb_month_rate_minor", nullable = false)
    private long storageGbMonthRateMinor;

    /** Basis points. 300 bps = 3.00 %. */
    @Column(name = "payment_fee_bps", nullable = false)
    @Builder.Default
    private int paymentFeeBps = 300;

    /** Basis points. 1200 bps = 12 % (Kazakhstan VAT). */
    @Column(name = "vat_bps", nullable = false)
    @Builder.Default
    private int vatBps = 1200;

    @Column(name = "trial_days", nullable = false)
    @Builder.Default
    private int trialDays = 14;

    @Column(name = "is_public", nullable = false)
    @Builder.Default
    private boolean isPublic = true;
}
