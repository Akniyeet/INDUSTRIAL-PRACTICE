package com.webizon.notifications.repo;

import com.webizon.notifications.model.NotificationKind;
import com.webizon.notifications.model.NotificationOutboxEntry;
import com.webizon.notifications.model.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link NotificationOutboxEntry}.
 *
 * <p>Every read runs under the tenant RLS policy. The dispatcher
 * pivots {@code TenantContext.runWith} before each batch, so even
 * the cross-tenant sweep goes through the same RLS-protected path
 * as a tenant-originated enqueue.
 */
public interface NotificationOutboxRepository extends JpaRepository<NotificationOutboxEntry, UUID> {

    /**
     * Dispatchable rows whose backoff window has expired. Ordered by
     * {@code nextAttemptAt} so the oldest-due row goes first — a
     * FIFO within the dispatchable set prevents a constantly-retrying
     * row from starving new enqueues.
     *
     * <p>Bounded by {@code pageable} so a single tick never commits
     * a million rows to memory; the dispatcher calls this once per
     * tenant per tick with a small page size.
     */
    @Query("""
           select n from NotificationOutboxEntry n
           where n.status in (com.webizon.notifications.model.NotificationStatus.PENDING,
                              com.webizon.notifications.model.NotificationStatus.FAILED)
             and n.nextAttemptAt <= :now
           order by n.nextAttemptAt asc
           """)
    List<NotificationOutboxEntry> findDispatchable(@Param("now") Instant now, Pageable pageable);

    /**
     * Idempotency probe used by {@code NotificationService} before
     * inserting. The DB also carries a partial unique index on
     * {@code (tenant_id, idempotency_key)} so a race between two
     * parallel enqueues is still safe — but checking here first
     * keeps the happy path free of caught exceptions.
     */
    Optional<NotificationOutboxEntry> findByIdempotencyKey(String idempotencyKey);

    /**
     * Stale-SENDING healer: claw back rows that were claimed by a
     * previous tick but never transitioned to SENT / FAILED (process
     * crashed mid-send).
     */
    @Query("""
           select n from NotificationOutboxEntry n
           where n.status = com.webizon.notifications.model.NotificationStatus.SENDING
             and n.updatedAt <= :threshold
           """)
    List<NotificationOutboxEntry> findStaleSending(@Param("threshold") Instant threshold);

    /** Admin outbox UI: paginated list, newest first. */
    Page<NotificationOutboxEntry> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Admin outbox filtered by status — used by the dead-letter view. */
    Page<NotificationOutboxEntry> findAllByStatusOrderByCreatedAtDesc(
            NotificationStatus status, Pageable pageable);

    /** Admin outbox filtered by kind. */
    Page<NotificationOutboxEntry> findAllByKindOrderByCreatedAtDesc(
            NotificationKind kind, Pageable pageable);
}
