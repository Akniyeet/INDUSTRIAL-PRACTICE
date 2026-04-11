package com.webizon.billing.api.dto;

import com.webizon.billing.model.BillingUsageRecord;
import com.webizon.billing.model.UsageKind;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Tenant dashboard "what will my next invoice look like" projection.
 *
 * <p>Collapses every open {@link BillingUsageRecord} in the current
 * billing period into one aggregate per {@link UsageKind} so the
 * frontend can render three cards (seat-live, seat-auto, storage)
 * without iterating over thousands of session rows.
 *
 * <p>{@code estimatedVatMillis} and {@code estimatedTotalMillis}
 * give the tenant the same running total they would see on an
 * invoice once the period closes — computed via the same
 * {@code BillingProperties#computeVatMillis} path the invoice close
 * uses, so the estimate and the final invoice cannot drift.
 *
 * <p>This is deliberately a snapshot, not a subscription: the
 * dashboard polls on refresh. A realtime stream would be cheaper on
 * the server but the sweeper only writes every 30 seconds anyway, so
 * the marginal accuracy gain is noise.
 */
public record CurrentUsageResponse(
        Instant periodStart,
        Instant periodEnd,
        List<KindAggregate> breakdown,
        long subtotalMillis,
        long estimatedVatMillis,
        long estimatedTotalMillis
) {

    public record KindAggregate(
            UsageKind kind,
            /** Natural-unit quantity (seat-seconds or GB-seconds). */
            long quantity,
            /** Display-unit quantity already converted: seat-minutes or GB-months. */
            long displayQuantity,
            String unitLabel,
            long rateMillisPerUnit,
            long amountMillis
    ) {}

    /**
     * Builder used by the controller: folds raw records into
     * per-kind aggregates. Kept here (not in the controller) so the
     * wire shape and the folding logic stay close enough that a
     * future schema change can't miss one of them.
     */
    public static Map<UsageKind, long[]> foldQuantity(List<BillingUsageRecord> rows) {
        Map<UsageKind, long[]> acc = new EnumMap<>(UsageKind.class);
        for (BillingUsageRecord row : rows) {
            long[] slot = acc.computeIfAbsent(row.getKind(), k -> new long[]{0L, 0L, 0L});
            slot[0] = Math.addExact(slot[0], row.getQuantity());
            slot[1] = Math.addExact(slot[1], row.getAmountMillis());
            slot[2] = row.getRateMillisPerUnit();
        }
        return acc;
    }
}
