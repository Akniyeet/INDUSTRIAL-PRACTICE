package com.webizon.notifications.service;

import com.webizon.notifications.model.NotificationOutboxEntry;
import com.webizon.notifications.model.NotificationStatus;
import com.webizon.notifications.repo.NotificationOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Entry point for every business module that needs to send a
 * notification. Inserts a row on the outbox inside the caller's
 * transaction; delivery is handled asynchronously by
 * {@link NotificationDispatcher}.
 *
 * <h2>Transactional contract</h2>
 * {@link #enqueue(NotificationRequest)} runs with
 * {@link Propagation#MANDATORY} — it must be invoked from inside an
 * existing transaction so the outbox row commits atomically with
 * the business change. If no transaction is active, Spring throws
 * an {@code IllegalTransactionStateException}; that error is loud
 * on purpose, because calling this method outside a tx defeats the
 * whole point of the outbox pattern.
 *
 * <h2>Idempotency</h2>
 * Callers SHOULD supply an {@code idempotencyKey}. When they do,
 * this service first probes the outbox; if a row with the same
 * key already exists it is returned as-is without a second insert.
 * The database also enforces uniqueness via a partial index so a
 * race between two threads enqueueing the same key is still safe —
 * the second insert catches {@link DataIntegrityViolationException}
 * and re-fetches the existing row.
 *
 * <h2>What this service does NOT do</h2>
 * <ul>
 *   <li>Render templates. Callers pass already-rendered content.</li>
 *   <li>Look up recipient addresses. Callers pass {@code toAddress}
 *       explicitly so a later profile email change cannot silently
 *       misroute a retried message.</li>
 *   <li>Deliver. That is the dispatcher's job.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationOutboxRepository outboxRepository;

    /**
     * Insert (or short-circuit to) a pending outbox row. Must be
     * called from inside an active transaction.
     *
     * @return the persisted outbox entry — the ID is stable and can
     *         be embedded in audit logs or the admin UI.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public NotificationOutboxEntry enqueue(NotificationRequest request) {
        // Idempotency short-circuit. The DB unique index is still
        // the authoritative guard; this lookup just keeps the hot
        // path free of caught-and-rethrown exceptions.
        if (request.idempotencyKey() != null) {
            Optional<NotificationOutboxEntry> existing =
                    outboxRepository.findByIdempotencyKey(request.idempotencyKey());
            if (existing.isPresent()) {
                log.debug("Notification idempotency hit for key={}, returning existing id={}",
                        request.idempotencyKey(), existing.get().getId());
                return existing.get();
            }
        }

        NotificationOutboxEntry row = new NotificationOutboxEntry();
        row.setProfileId(request.profileId());
        row.setKind(request.kind());
        row.setChannel(request.channel());
        row.setStatus(NotificationStatus.PENDING);
        row.setToAddress(request.toAddress());
        row.setSubject(request.subject());
        row.setBodyText(request.bodyText());
        row.setBodyHtml(request.bodyHtml());
        row.setPayloadJson(request.payloadJson() == null ? "{}" : request.payloadJson());
        row.setIdempotencyKey(request.idempotencyKey());
        row.setMaxAttempts(request.kind().defaultMaxAttempts());
        row.setNextAttemptAt(Instant.now());

        try {
            return outboxRepository.save(row);
        } catch (DataIntegrityViolationException dupe) {
            // Lost an enqueue race. Re-fetch by idempotency key; if
            // the key was null (caller opted out of dedupe) the
            // violation was something else and we must propagate.
            if (request.idempotencyKey() == null) {
                throw dupe;
            }
            return outboxRepository.findByIdempotencyKey(request.idempotencyKey())
                    .orElseThrow(() -> dupe);
        }
    }

    /**
     * Admin replay: reset a DEAD (or any non-SENT) row back to
     * PENDING and make it due immediately. Returns the updated row
     * so the admin UI can re-render it without a second fetch.
     */
    @Transactional
    public NotificationOutboxEntry replay(UUID entryId) {
        NotificationOutboxEntry row = outboxRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Notification outbox entry not found: " + entryId));
        if (row.getStatus() == NotificationStatus.SENT) {
            throw new IllegalStateException(
                    "Cannot replay a SENT notification; re-enqueue a fresh one instead.");
        }
        row.resetForReplay(Instant.now());
        return outboxRepository.save(row);
    }
}
