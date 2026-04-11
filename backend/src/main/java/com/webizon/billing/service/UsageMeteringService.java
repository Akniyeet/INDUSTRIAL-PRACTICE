package com.webizon.billing.service;

import com.webizon.analytics.repo.SessionAttendanceRepository;
import com.webizon.billing.config.BillingProperties;
import com.webizon.billing.model.BillingUsageRecord;
import com.webizon.billing.model.UsageKind;
import com.webizon.billing.repo.BillingUsageRecordRepository;
import com.webizon.events.model.Session;
import com.webizon.events.model.SessionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Converts a single finalized session into a
 * {@link BillingUsageRecord} and stamps
 * {@link Session#setBillingMeteredAt(Instant)} in the same
 * transaction.
 *
 * <p>The metering logic is intentionally kept small and
 * side-effect-free beyond the DB writes: the caller ({@link
 * UsageMeteringScheduler}) owns the tenant iteration and the
 * transactional boundary, so this service only needs to answer
 * "given this session, what usage row should I produce?".
 *
 * <h2>Rate selection</h2>
 * A LIVE session is billed at {@code seat-live}, an AUTO session at
 * {@code seat-auto}. The rate is snapshotted onto the row at meter
 * time — a rate-card change later does not rewrite historical rows.
 *
 * <h2>Rounding</h2>
 * Seat-seconds are billed per MINUTE (the rate card is stated per
 * minute). Conversion uses {@link Math#floorDiv} so partial minutes
 * are rounded <em>down</em>: a viewer who was in the room for 59
 * seconds is not billed at all. This is intentional — it punishes
 * the billing module for refresh-storm flicker rather than the
 * tenant.
 *
 * <h2>Idempotency</h2>
 * The DB carries a unique constraint on {@code (tenant_id,
 * session_id, kind)}, so even a double-dispatched sweeper tick
 * cannot insert two rows for the same session. {@link
 * #meterSession(Session)} catches the violation and logs it as a
 * warning — the session still gets its {@code billing_metered_at}
 * stamp on the second pass.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UsageMeteringService {

    /**
     * Seat-seconds per seat-minute. Named constant rather than
     * {@code 60} literal so the unit conversion is self-documenting
     * at the call site.
     */
    private static final long SECONDS_PER_MINUTE = 60L;

    private final SessionAttendanceRepository attendanceRepository;
    private final BillingUsageRecordRepository usageRepository;
    private final BillingProperties billingProperties;

    /**
     * Build and persist the usage record for a single finalized
     * session, then stamp the session's {@code billingMeteredAt}.
     *
     * <p>Returns the saved row wrapped in {@link Optional}, or empty
     * if the session produced no billable seat-seconds (still
     * stamps the session so the sweeper skips it next tick).
     *
     * @throws IllegalArgumentException if the session is not yet
     *         finalized — the sweeper's query already filters these
     *         out, but the defensive check guards against misuse
     *         from future call sites
     */
    public Optional<BillingUsageRecord> meterSession(Session session) {
        if (session.getFinalizedAt() == null) {
            throw new IllegalArgumentException(
                    "Cannot meter a non-finalized session: " + session.getId());
        }
        if (session.getBillingMeteredAt() != null) {
            // Race with a sibling sweeper tick — the other tick won,
            // we fall through silently.
            return Optional.empty();
        }

        UsageKind kind = kindFor(session);
        long rateMillisPerMinute = rateFor(kind);

        long seatSeconds = attendanceRepository
                .sumTotalConnectedSecondsBySessionId(session.getId());
        long seatMinutes = Math.floorDiv(seatSeconds, SECONDS_PER_MINUTE);
        long amountMillis = Math.multiplyExact(seatMinutes, rateMillisPerMinute);

        // Stamp the session even when amount is zero — otherwise the
        // sweeper would re-queue this session forever on every tick.
        session.setBillingMeteredAt(Instant.now());

        if (seatSeconds <= 0) {
            log.debug("Session {} produced zero seat-seconds, no usage row written",
                    session.getId());
            return Optional.empty();
        }

        BillingUsageRecord row = new BillingUsageRecord();
        row.setSessionId(session.getId());
        row.setEventId(session.getEventId());
        row.setKind(kind);
        row.setQuantity(seatSeconds);
        row.setRateMillisPerUnit(rateMillisPerMinute);
        row.setAmountMillis(amountMillis);
        row.setBilledForInstant(
                session.getFinalizedAt() != null
                        ? session.getFinalizedAt()
                        : Instant.now());

        try {
            return Optional.of(usageRepository.save(row));
        } catch (DataIntegrityViolationException race) {
            // Unique (tenant_id, session_id, kind) already exists
            // — another sweeper raced us. Stamp the session anyway
            // and pretend success.
            log.warn("Usage record already exists for session {}; skipping: {}",
                    session.getId(), race.getMessage());
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static UsageKind kindFor(Session session) {
        return session.getType() == SessionType.LIVE
                ? UsageKind.SEAT_LIVE
                : UsageKind.SEAT_AUTO;
    }

    private long rateFor(UsageKind kind) {
        return switch (kind) {
            case SEAT_LIVE        -> billingProperties.seatLiveRateMillisPerMinute();
            case SEAT_AUTO        -> billingProperties.seatAutoRateMillisPerMinute();
            case STORAGE_GB_MONTH -> billingProperties.storageRateMillisPerGbMonth();
        };
    }
}
