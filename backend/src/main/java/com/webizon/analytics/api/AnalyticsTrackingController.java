package com.webizon.analytics.api;

import com.webizon.analytics.api.dto.TrackEventRequest;
import com.webizon.analytics.api.dto.TrackEventResponse;
import com.webizon.analytics.service.AnalyticsRecorder;
import com.webizon.auth.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Generic behavioural-event ingestion endpoint.
 *
 * <p>Any authenticated client can post events here; the controller
 * stamps the caller's {@code profileId} so the client can't claim
 * activity on behalf of another user. Tenant isolation flows through
 * the normal filter chain — the RLS layer would reject a cross-tenant
 * write anyway.
 *
 * <p>The vast majority of analytics rows are produced server-side
 * (chat, moderation, attendance) through the application event
 * pipeline, so this endpoint exists for a narrow set of client-only
 * actions: CTA impressions and clicks, UI-initiated dismissals, and
 * "the user scrolled past the CTA". Anything the server can observe
 * directly should not come through here.
 */
@RestController
@RequestMapping("/api/v1/analytics/events")
@RequiredArgsConstructor
public class AnalyticsTrackingController {

    private final AnalyticsRecorder analyticsRecorder;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public TrackEventResponse track(@Valid @RequestBody TrackEventRequest req) {
        var saved = analyticsRecorder.record(new AnalyticsRecorder.RecordCommand(
                req.eventId(),
                req.sessionId(),
                CurrentUser.profileId(),
                req.clientKey(),
                req.type(),
                req.offsetSeconds(),
                req.metadata()
        ));
        return TrackEventResponse.from(saved);
    }
}
