package com.webizon.analytics.service;

import com.webizon.analytics.model.AnalyticsEventType;
import com.webizon.analytics.repo.AnalyticsEventRepository;
import com.webizon.analytics.repo.SessionAttendanceRepository;
import com.webizon.cta.model.EventCta;
import com.webizon.cta.repo.EventCtaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Read-side aggregates for the admin analytics dashboard.
 *
 * <p>Everything here is JPA-level aggregation that runs straight
 * against the hot tables. None of it writes, none of it owns a
 * transaction boundary beyond {@code readOnly = true}. For a
 * self-serve BI layer we would mirror events into a columnar store
 * through the Kafka topic instead — this service is what the
 * in-product "Session report" screen uses for up-to-the-second
 * numbers.
 *
 * <h2>Retention curve</h2>
 * The retention chart shows "how many distinct viewers made it to
 * minute N". It reads {@code analytics_events} rows where
 * {@code event_type = WATCH_MILESTONE} and counts distinct profiles
 * whose recorded offset passed each sample point. A fixed set of
 * sample points keeps the chart consistent across sessions of
 * wildly different length; a longer session is still sampled every
 * few minutes but not every single second.
 *
 * <h2>CTA CTR</h2>
 * Click-through rate per CTA is
 * {@code clicks / max(1, impressions)} where both numerators are
 * counted from {@code analytics_events} via a native JSONB filter
 * on {@code metadata ->> 'ctaId'}. Zero impressions give a defined
 * zero CTR rather than a division-by-zero — an unseen CTA is a
 * legitimate reportable state.
 *
 * <h2>Top-line counters</h2>
 * Unique viewers, total viewers reaching each milestone, chat
 * engagement, and moderation volume. All of these feed the summary
 * strip above the retention chart.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsReportService {

    /**
     * Retention sample points in seconds. Covers the typical 2-hour
     * webinar envelope with denser sampling in the first 30 minutes
     * where most drop-off happens.
     */
    private static final int[] RETENTION_SAMPLE_POINTS_SECONDS = {
            0, 60, 5 * 60, 10 * 60, 15 * 60, 20 * 60, 30 * 60,
            45 * 60, 60 * 60, 75 * 60, 90 * 60, 105 * 60, 120 * 60
    };

    private final AnalyticsEventRepository analyticsEventRepository;
    private final SessionAttendanceRepository attendanceRepository;
    private final EventCtaRepository ctaRepository;

    /**
     * Top-line counters for a session. Single-query-per-metric for
     * now; when the dashboard starts loading this on every page
     * refresh we'll move it to a materialised view updated by the
     * Kafka consumer.
     */
    @Transactional(readOnly = true)
    public SessionSummary summarise(UUID sessionId) {
        long attendees = attendanceRepository.countBySessionId(sessionId);
        long presentNow = attendanceRepository.findPresentBySessionId(sessionId).size();
        long longWatchers = attendanceRepository.countBySessionIdWithMinWatchTime(
                sessionId, 30 * 60);
        long chatMessages = analyticsEventRepository
                .countBySessionIdAndEventType(sessionId, AnalyticsEventType.CHAT_MESSAGE_SENT)
                + analyticsEventRepository
                .countBySessionIdAndEventType(sessionId, AnalyticsEventType.CHAT_REPLY_SENT);
        long ctaClicks = analyticsEventRepository
                .countBySessionIdAndEventType(sessionId, AnalyticsEventType.CTA_CLICK);
        long warningsIssued = analyticsEventRepository
                .countBySessionIdAndEventType(sessionId, AnalyticsEventType.WARNING_RECEIVED);
        long mutes = analyticsEventRepository
                .countBySessionIdAndEventType(sessionId, AnalyticsEventType.MUTED);
        long bans = analyticsEventRepository
                .countBySessionIdAndEventType(sessionId, AnalyticsEventType.CHAT_BANNED)
                + analyticsEventRepository
                .countBySessionIdAndEventType(sessionId, AnalyticsEventType.FULL_BANNED);

        return new SessionSummary(
                attendees,
                presentNow,
                longWatchers,
                chatMessages,
                ctaClicks,
                warningsIssued + mutes + bans
        );
    }

    /**
     * Produces the retention curve as a list of sample points. Each
     * point is "at least N seconds of playback reached by M
     * viewers". The zero-offset point is not literally queried —
     * it's the session attendee count, which is what the dashboard
     * anchors the curve at.
     */
    @Transactional(readOnly = true)
    public List<RetentionPoint> retentionCurve(UUID sessionId) {
        long attendees = attendanceRepository.countBySessionId(sessionId);
        List<RetentionPoint> points = new ArrayList<>(RETENTION_SAMPLE_POINTS_SECONDS.length);
        for (int offset : RETENTION_SAMPLE_POINTS_SECONDS) {
            long viewers = offset == 0
                    ? attendees
                    : analyticsEventRepository.countViewersReachingOffset(sessionId, offset);
            points.add(new RetentionPoint(offset, viewers));
        }
        return points;
    }

    /**
     * Per-CTA click-through rate. Impressions and clicks are both
     * counted from {@code analytics_events}, so a CTA that was
     * never shown cleanly reports zero rather than erroring.
     */
    @Transactional(readOnly = true)
    public List<CtaCtrRow> ctaCtr(UUID eventId, UUID sessionId) {
        List<EventCta> ctas = ctaRepository.findAllByEventIdOrderByPriorityDesc(eventId);
        List<CtaCtrRow> rows = new ArrayList<>(ctas.size());
        for (EventCta cta : ctas) {
            String ctaIdString = cta.getId().toString();
            long impressions = analyticsEventRepository.countByCtaAndEventType(
                    sessionId, AnalyticsEventType.CTA_IMPRESSION.name(), ctaIdString);
            long clicks = analyticsEventRepository.countByCtaAndEventType(
                    sessionId, AnalyticsEventType.CTA_CLICK.name(), ctaIdString);
            long downloads = analyticsEventRepository.countByCtaAndEventType(
                    sessionId, AnalyticsEventType.CTA_DOWNLOAD.name(), ctaIdString);
            double ctr = impressions == 0 ? 0.0 : (double) clicks / (double) impressions;
            rows.add(new CtaCtrRow(
                    cta.getId(),
                    cta.getTitle(),
                    cta.getType().name(),
                    impressions,
                    clicks,
                    downloads,
                    ctr
            ));
        }
        return rows;
    }

    /**
     * Top-line session counters — the strip of big numbers at the top
     * of the report. Every value is cheap to compute on the hot path.
     */
    public record SessionSummary(
            long totalAttendees,
            long presentNow,
            long watchedLongCount,
            long chatMessages,
            long ctaClicks,
            long moderationEventCount
    ) {}

    /** One point on the retention curve. */
    public record RetentionPoint(int offsetSeconds, long viewers) {}

    /** Per-CTA click-through rate row. */
    public record CtaCtrRow(
            UUID ctaId,
            String title,
            String type,
            long impressions,
            long clicks,
            long downloads,
            double ctr
    ) {}
}
