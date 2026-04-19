package com.webizon.events.repo;

import com.webizon.events.model.SessionRegistration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA repository for {@link SessionRegistration}. Tenant-scoped via
 * Hibernate's {@code @TenantId} filter plus PostgreSQL RLS — all
 * methods on this interface run inside the caller's tenant context
 * unless explicitly noted.
 *
 * <p>The reminder scheduler uses a narrow read path that filters on
 * {@code unregistered_at IS NULL} and {@code reminder_sent_at IS NULL}
 * to match the partial index
 * {@code session_registrations_pending_reminder_idx}, keeping the
 * tick O(pending) rather than O(total).
 */
@Repository
public interface SessionRegistrationRepository extends JpaRepository<SessionRegistration, UUID> {

    // ------------------------------------------------------------------
    // Viewer-facing queries
    // ------------------------------------------------------------------

    /**
     * Active registration (if any) for a specific viewer on a
     * specific session. Drives the "am I registered?" GET endpoint
     * and the opt-out path.
     */
    @Query("""
           select r from SessionRegistration r
           where r.sessionId = :sessionId
             and r.profileId = :profileId
             and r.unregisteredAt is null
           """)
    Optional<SessionRegistration> findActive(@Param("sessionId") UUID sessionId,
                                             @Param("profileId") UUID profileId);

    /**
     * List every active registration the given profile holds,
     * ordered by newest first. Powers the "my upcoming" drawer on
     * the viewer dashboard.
     */
    @Query("""
           select r from SessionRegistration r
           where r.profileId = :profileId
             and r.unregisteredAt is null
           order by r.registeredAt desc
           """)
    Page<SessionRegistration> findActiveByProfile(@Param("profileId") UUID profileId, Pageable pageable);

    /**
     * Active registrations for a session whose reminder has not
     * yet been enqueued. Matches the partial index
     * {@code session_registrations_pending_reminder_idx} — the
     * reminder scheduler uses this list as its fan-out source.
     */
    @Query("""
           select r from SessionRegistration r
           where r.sessionId = :sessionId
             and r.unregisteredAt is null
             and r.reminderSentAt is null
             and r.emailRemindersEnabled = true
           """)
    List<SessionRegistration> findPendingReminders(@Param("sessionId") UUID sessionId);

    /** Admin-facing "how many people registered for this event". */
    long countByEventIdAndUnregisteredAtIsNull(UUID eventId);

    /** Admin-facing paginated list of registrations for a session. */
    Page<SessionRegistration> findAllBySessionIdOrderByRegisteredAtDesc(UUID sessionId, Pageable pageable);
}
