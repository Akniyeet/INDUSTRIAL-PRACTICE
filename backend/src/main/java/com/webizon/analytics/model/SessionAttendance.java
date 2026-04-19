package com.webizon.analytics.model;

import com.webizon.tenancy.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Aggregate attendance row: one per (session, profile).
 *
 * <p>The analytics log can answer "every join/leave event" exactly,
 * but that's expensive to aggregate on every dashboard refresh. This
 * table is the running total that the hot read paths use — "who's
 * still in the room", "total watch time per user", "highest offset
 * reached".
 *
 * <p>Upsert semantics: the {@link com.webizon.analytics.service.AttendanceTracker}
 * maintains this row via {@code INSERT … ON CONFLICT} style logic. A
 * user who refreshes the browser is the same session row with
 * {@code entry_count++}; leaving bumps
 * {@code total_connected_seconds} by the session delta and stamps
 * {@code left_at} so the dashboard knows they're gone.
 */
@Entity
@Table(name = "session_attendance")
@Getter
@Setter
public class SessionAttendance extends TenantAwareEntity {

    @Column(name = "event_id", nullable = false, columnDefinition = "UUID")
    private UUID eventId;

    @Column(name = "session_id", nullable = false, columnDefinition = "UUID")
    private UUID sessionId;

    @Column(name = "profile_id", nullable = false, columnDefinition = "UUID")
    private UUID profileId;

    @Column(name = "first_joined_at", nullable = false)
    private Instant firstJoinedAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "left_at")
    private Instant leftAt;

    @Column(name = "total_connected_seconds", nullable = false)
    private int totalConnectedSeconds;

    @Column(name = "entry_count", nullable = false)
    private int entryCount = 1;

    @Column(name = "max_offset_seconds")
    private Integer maxOffsetSeconds;

    public boolean isCurrentlyPresent() {
        return leftAt == null;
    }
}
