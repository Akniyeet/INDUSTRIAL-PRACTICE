package com.webizon.billing.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

/**
 * Configuration for the billing module — rate card plus VAT.
 *
 * <p>All rates are sourced from {@code webizon.billing.*} in
 * {@code application.yml}. The YAML uses human-friendly whole KZT
 * numbers ({@code seat-live: 15}); this record stores them as KZT
 * and converts to the internal "KZT-millis" representation via
 * helper methods so the service layer never deals with floating
 * point.
 *
 * <p><b>Why millis?</b>
 * VAT at 12% on 15 KZT/min is 1.8 KZT/min. Multiplied over millions
 * of seat-minutes per invoice, rounding errors from {@code double}
 * accumulate into real money. Keeping everything in integer
 * KZT-millis (KZT * 1000) gives us four decimal digits of precision
 * below the minor unit while still fitting comfortably in a
 * {@code BIGINT} up to ~9.2 quadrillion KZT — which is more than
 * enough head-room even for a billion-KZT enterprise invoice.
 *
 * <p>Rate-card changes MUST go through a deploy (YAML edit) rather
 * than a runtime config. Usage rows snapshot the rate at meter time,
 * so historical invoices are immune to rate changes — but the
 * current-period estimate will reflect the new rate as soon as it is
 * applied.
 *
 * @param rates      per-unit rates in whole KZT
 * @param vatPercent VAT rate (12 for Kazakhstan, as whole percent)
 */
@ConfigurationProperties(prefix = "webizon.billing")
@Validated
public record BillingProperties(
        @Valid @NotNull Rates rates,
        @NotNull @DecimalMin("0.0") BigDecimal vatPercent
) {

    /**
     * Primary KZT-millis multiplier used by every conversion. Kept
     * as a named constant so a future switch to a currency with a
     * different minor-unit exponent (USD cents = 100) is a one-line
     * change here rather than a grep of the codebase.
     */
    public static final long MILLIS_PER_KZT = 1000L;

    public record Rates(
            /** Live seat cost per minute, in whole KZT. */
            @Min(0) int seatLive,
            /** Auto-replay seat cost per minute, in whole KZT. */
            @Min(0) int seatAuto,
            /** Storage cost per GB per month, in whole KZT. */
            @Min(0) int storageGbMonth,
            /** Payment processor fee, as a whole percent. */
            @Min(0) int paymentProcessingPercent
    ) {}

    // ------------------------------------------------------------------
    // Conversions — every rate lookup goes through one of these.
    // ------------------------------------------------------------------

    /**
     * Live seat rate in KZT-millis per seat-minute. This is the
     * number that gets snapshotted onto {@code billing_usage_records}
     * at meter time.
     */
    public long seatLiveRateMillisPerMinute() {
        return (long) rates.seatLive() * MILLIS_PER_KZT;
    }

    /** Auto seat rate in KZT-millis per seat-minute. */
    public long seatAutoRateMillisPerMinute() {
        return (long) rates.seatAuto() * MILLIS_PER_KZT;
    }

    /** Storage rate in KZT-millis per GB-month. */
    public long storageRateMillisPerGbMonth() {
        return (long) rates.storageGbMonth() * MILLIS_PER_KZT;
    }

    /**
     * Apply VAT to a KZT-millis subtotal and return the tax amount
     * as a separate KZT-millis value. Half-up rounding is used
     * because Kazakh tax rules round the final fils, not truncate.
     *
     * <p>Doing the math in {@link BigDecimal} once per invoice close
     * (not once per line) keeps us well clear of floating-point
     * drift without paying the BigDecimal cost on the hot sweeper
     * path. The caller feeds in the final subtotal and gets back
     * the VAT amount to be added as its own invoice line.
     */
    public long computeVatMillis(long subtotalMillis) {
        if (subtotalMillis <= 0) return 0L;
        return vatPercent
                .multiply(BigDecimal.valueOf(subtotalMillis))
                .movePointLeft(2) // divide by 100 for percent
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .longValueExact();
    }
}
