package com.webizon.events.service;

import com.webizon.events.api.dto.SessionRegistrationRequest;
import com.webizon.events.model.Session;
import com.webizon.events.model.SessionRegistration;
import com.webizon.events.model.SessionStatus;
import com.webizon.events.repo.SessionRegistrationRepository;
import com.webizon.events.repo.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Viewer-facing CRUD for {@link SessionRegistration}.
 *
 * <p>Business rules enforced here (not in the controller):
 * <ol>
 *   <li>You cannot register for a session that has already finished.
 *       Finalized sessions are immutable and reminders would be
 *       meaningless — the UI should already hide the "Remind me"
 *       button, but defence in depth is cheap.</li>
 *   <li>You cannot register for a cancelled session. Same reason.</li>
 *   <li>Re-registering after an opt-out creates a NEW row. We never
 *       un-opt-out the old row because {@code reminder_sent_at}
 *       carries the scheduler's cursor — flipping a previously-sent
 *       row back to active would silently re-send the old reminder
 *       on the next tick.</li>
 *   <li>The partial unique index {@code session_registrations_active_uniq}
 *       is the authoritative guard against a (session, profile) being
 *       active twice in parallel. We pre-check for idempotency and
 *       catch the constraint on the losing side of a race.</li>
 * </ol>
 *
 * <p>All methods run inside a tenant-scoped tx. Callers pass the
 * {@code profileId} resolved from the current JWT — this service
 * never touches {@code CurrentUser} directly so it stays unit
 * testable without a security context.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class SessionRegistrationService {

    private final SessionRegistrationRepository registrationRepository;
    private final SessionRepository sessionRepository;

    // ------------------------------------------------------------------
    // Register
    // ------------------------------------------------------------------

    /**
     * Idempotently register the caller for a session. If an active
     * registration already exists, returns it unchanged — supplied
     * email/reminder-flag overrides update the existing row in that
     * case rather than failing with a unique-constraint error.
     *
     * @param sessionId         target session
     * @param profileId         the caller's canonical Webizon profile id
     * @param fallbackEmail     email to freeze if {@link SessionRegistrationRequest#notifyEmail()}
     *                          is blank; typically the caller's JWT email claim
     * @param request           optional overrides from the request body
     */
    public SessionRegistration register(UUID sessionId,
                                        UUID profileId,
                                        String fallbackEmail,
                                        SessionRegistrationRequest request) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        assertSessionAcceptsRegistrations(session);

        String notifyEmail = pickNotifyEmail(request, fallbackEmail);
        boolean remindersEnabled = request == null || request.emailRemindersEnabled() == null
                ? true
                : request.emailRemindersEnabled();

        // Idempotency short-circuit. If the caller already holds an
        // active row, update the mutable fields (email target,
        // reminder flag) instead of inserting a second one.
        var existing = registrationRepository.findActive(sessionId, profileId);
        if (existing.isPresent()) {
            SessionRegistration row = existing.get();
            if (!row.getNotifyEmail().equalsIgnoreCase(notifyEmail)) {
                row.setNotifyEmail(notifyEmail);
            }
            if (row.isEmailRemindersEnabled() != remindersEnabled) {
                row.setEmailRemindersEnabled(remindersEnabled);
            }
            return row;
        }

        SessionRegistration row = new SessionRegistration();
        row.setSessionId(session.getId());
        row.setEventId(session.getEventId());
        row.setProfileId(profileId);
        row.setNotifyEmail(notifyEmail);
        row.setEmailRemindersEnabled(remindersEnabled);
        row.setRegisteredAt(Instant.now());

        try {
            return registrationRepository.save(row);
        } catch (DataIntegrityViolationException dupe) {
            // Lost the race — re-read the winning row.
            log.debug("Concurrent registration race for session={} profile={}, re-reading winner",
                    sessionId, profileId);
            return registrationRepository.findActive(sessionId, profileId)
                    .orElseThrow(() -> dupe);
        }
    }

    // ------------------------------------------------------------------
    // Unregister
    // ------------------------------------------------------------------

    /**
     * Opt the caller out of reminders for the given session. Safe
     * to call when no active row exists — in that case the method
     * returns {@code false} so the controller can respond with a
     * 404 rather than pretending an opt-out happened.
     *
     * @return {@code true} if an active row was flipped, {@code false} otherwise
     */
    public boolean unregister(UUID sessionId, UUID profileId) {
        var existing = registrationRepository.findActive(sessionId, profileId);
        if (existing.isEmpty()) {
            return false;
        }
        existing.get().markUnregistered(Instant.now());
        return true;
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public SessionRegistration requireActive(UUID sessionId, UUID profileId) {
        return registrationRepository.findActive(sessionId, profileId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No active registration for session " + sessionId + " and profile " + profileId));
    }

    @Transactional(readOnly = true)
    public Page<SessionRegistration> listMyRegistrations(UUID profileId, Pageable pageable) {
        return registrationRepository.findActiveByProfile(profileId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<SessionRegistration> listForSession(UUID sessionId, Pageable pageable) {
        return registrationRepository.findAllBySessionIdOrderByRegisteredAtDesc(sessionId, pageable);
    }

    @Transactional(readOnly = true)
    public long countActiveForEvent(UUID eventId) {
        return registrationRepository.countByEventIdAndUnregisteredAtIsNull(eventId);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void assertSessionAcceptsRegistrations(Session session) {
        if (session.isFinalized() || session.getStatus().isFinal()) {
            throw new IllegalStateException(
                    "Session " + session.getId() + " is finalized and cannot accept new registrations");
        }
        if (session.getStatus() == SessionStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Session " + session.getId() + " was cancelled");
        }
        // Registering for a session that is currently airing is
        // allowed — a late arrival should still be able to opt in
        // to "notify me when the next session of this event starts"
        // semantics and the admin may replay the landing.
    }

    private static String pickNotifyEmail(SessionRegistrationRequest request, String fallbackEmail) {
        if (request != null && request.notifyEmail() != null && !request.notifyEmail().isBlank()) {
            return request.notifyEmail().trim();
        }
        if (fallbackEmail == null || fallbackEmail.isBlank()) {
            throw new IllegalArgumentException(
                    "No notification email supplied and the caller has no email on their JWT");
        }
        return fallbackEmail.trim();
    }
}
