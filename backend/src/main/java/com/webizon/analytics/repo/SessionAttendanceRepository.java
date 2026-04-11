package com.webizon.analytics.repo;

import com.webizon.analytics.model.SessionAttendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA repository for {@link SessionAttendance}.
 *
 * <p>The upsert path uses {@link #findBySessionIdAndProfileId} to
 * locate an existing row and falls back to {@link JpaRepository#save}
 * for new inserts. Under heavy concurrency a unique constraint on
 * {@code (session_id, profile_id)} plus optimistic locking
 * {@code @Version} inherited from {@code BaseEntity} keeps the row
 * consistent without DB-level locks.
 */
public interface SessionAttendanceRepository extends JpaRepository<SessionAttendance, UUID> {

    Optional<SessionAttendance> findBySessionIdAndProfileId(UUID sessionId, UUID profileId);

    /** Currently-present users in a session (left_at is null). */
    @Query("""
           select a from SessionAttendance a
           where a.sessionId = :sessionId
             and a.leftAt is null
           """)
    List<SessionAttendance> findPresentBySessionId(@Param("sessionId") UUID sessionId);

    long countBySessionId(UUID sessionId);

    /**
     * Users whose total watch time crossed a threshold — feeds the
     * {@code WATCHED_LONG} lead signal and the retention dashboard.
     */
    @Query("""
           select count(a) from SessionAttendance a
           where a.sessionId = :sessionId
             and a.totalConnectedSeconds >= :minSeconds
           """)
    long countBySessionIdWithMinWatchTime(@Param("sessionId") UUID sessionId,
                                           @Param("minSeconds") int minSeconds);

    /** Cross-session history: every session this profile ever attended. */
    List<SessionAttendance> findAllByProfileIdOrderByFirstJoinedAtDesc(UUID profileId);
}
