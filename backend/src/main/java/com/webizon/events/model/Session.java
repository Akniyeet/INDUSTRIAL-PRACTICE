package com.webizon.events.model;

import com.webizon.tenancy.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A single scheduled run of an {@link Event}.
 *
 * <p>Sessions are immutable after they finish — the {@code finalized_at}
 * column is the marker, and the application layer refuses writes to any
 * row whose {@link SessionStatus#isFinal()} is {@code true}. Re-airings of
 * the same event must be created as new {@code AUTO} sessions pointing at
 * the original LIVE run via {@link #sourceLiveSessionId}.
 *
 * <p>All time fields are stored in UTC ({@link Instant}); wall-clock
 * formatting is done on the frontend using {@link Event#getTimezone()}.
 */
@Entity
@Table(name = "sessions")
@Getter
@Setter
public class Session extends TenantAwareEntity {

    @Column(name = "event_id", nullable = false, columnDefinition = "UUID")
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 8, updatable = false)
    private SessionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SessionStatus status;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "planned_duration_seconds", nullable = false)
    private int plannedDurationSeconds;

    @Column(name = "youtube_url", length = 500)
    private String youtubeUrl;

    @Column(name = "youtube_video_id", length = 32)
    private String youtubeVideoId;

    @Column(name = "youtube_embed_url", length = 500)
    private String youtubeEmbedUrl;

    /** For AUTO sessions: the LIVE session whose recording is being replayed. */
    @Column(name = "source_live_session_id", columnDefinition = "UUID")
    private UUID sourceLiveSessionId;

    @Column(name = "actual_started_at")
    private Instant actualStartedAt;

    @Column(name = "actual_ended_at")
    private Instant actualEndedAt;

    /** Non-null once the session has ended. Finalized rows are immutable. */
    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @Column(name = "created_by_user_id", nullable = false, updatable = false, columnDefinition = "UUID")
    private UUID createdByUserId;

    /**
     * Resumable replay cursor for AUTO sessions — the highest offset
     * (seconds) the {@code TimelineReplayEngine} has already dispatched
     * for this session. On every tick the engine fetches the window
     * {@code (lastReplayOffsetSeconds, currentOffsetSeconds]} of
     * timeline actions and historical chat messages and advances the
     * cursor atomically.
     *
     * <p>Unused for LIVE sessions; they produce the timeline rows that
     * AUTO sessions later replay. Kept {@code NOT NULL} with a default
     * of zero so the replay window is always well-defined.
     */
    @Column(name = "last_replay_offset_seconds", nullable = false)
    private int lastReplayOffsetSeconds;

    public boolean isFinalized() {
        return finalizedAt != null;
    }
}
