package com.webizon.timeline.api;

import com.webizon.auth.CurrentUser;
import com.webizon.timeline.api.dto.TimelineActionActiveRequest;
import com.webizon.timeline.api.dto.TimelineActionCreateRequest;
import com.webizon.timeline.api.dto.TimelineActionResponse;
import com.webizon.timeline.api.dto.TimelineActionUpdateRequest;
import com.webizon.timeline.service.TimelineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Admin view of the timeline for a single LIVE (source) session.
 *
 * <p>Most reads and writes here target the source LIVE session's id,
 * because that's where timeline rows live in the database. AUTO
 * sessions never own timeline rows themselves — they replay the rows
 * of their {@code sourceLiveSessionId}.
 *
 * <p>The replay-queue endpoint is handy for the frontend during a
 * reconnect mid-AUTO: it can pass the current offset and only receive
 * rows that still need to fire.
 */
@RestController
@RequestMapping("/api/v1/events/{eventId}")
@RequiredArgsConstructor
public class TimelineController {

    private final TimelineService timelineService;

    /**
     * Every captured timeline row for a source LIVE session, active
     * and inactive. Used by the admin timeline editor.
     */
    @GetMapping("/sessions/{sourceSessionId}/timeline")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_MODERATOR','TENANT_PRESENTER','TENANT_ANALYST')")
    public List<TimelineActionResponse> list(@PathVariable UUID eventId,
                                               @PathVariable UUID sourceSessionId) {
        return timelineService.listForSourceSession(sourceSessionId).stream()
                .map(TimelineActionResponse::from)
                .toList();
    }

    /**
     * Ordered replay queue for a source LIVE session. Optional
     * {@code fromOffset} lets late-joining AUTO viewers skip already-fired
     * actions on reconnect.
     */
    @GetMapping("/sessions/{sourceSessionId}/timeline/replay-queue")
    @PreAuthorize("isAuthenticated()")
    public List<TimelineActionResponse> replayQueue(
            @PathVariable UUID eventId,
            @PathVariable UUID sourceSessionId,
            @RequestParam(name = "fromOffset", required = false) Integer fromOffset) {
        var rows = fromOffset == null
                ? timelineService.replayQueue(sourceSessionId)
                : timelineService.replayQueueFromOffset(sourceSessionId, fromOffset);
        return rows.stream().map(TimelineActionResponse::from).toList();
    }

    /**
     * Manual insert — typically used to pre-build a timeline for a
     * pre-recorded event that is going straight to AUTO without ever
     * having an interactive LIVE broadcast.
     */
    @PostMapping("/sessions/{sourceSessionId}/timeline")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_PRESENTER')")
    public TimelineActionResponse create(@PathVariable UUID eventId,
                                           @PathVariable UUID sourceSessionId,
                                           @Valid @RequestBody TimelineActionCreateRequest req) {
        return TimelineActionResponse.from(
                timelineService.manualInsert(
                        eventId,
                        sourceSessionId,
                        req.offsetSeconds(),
                        req.actionType(),
                        req.payload(),
                        CurrentUser.profileId()));
    }

    @PatchMapping("/timeline/{actionId}")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_PRESENTER')")
    public TimelineActionResponse update(@PathVariable UUID eventId,
                                           @PathVariable UUID actionId,
                                           @Valid @RequestBody TimelineActionUpdateRequest req) {
        return TimelineActionResponse.from(
                timelineService.updateAction(actionId, req.offsetSeconds(), req.payload()));
    }

    @PutMapping("/timeline/{actionId}/active")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_PRESENTER')")
    public TimelineActionResponse setActive(@PathVariable UUID eventId,
                                              @PathVariable UUID actionId,
                                              @Valid @RequestBody TimelineActionActiveRequest req) {
        return TimelineActionResponse.from(timelineService.setActive(actionId, req.active()));
    }

    @DeleteMapping("/timeline/{actionId}")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN')")
    public void delete(@PathVariable UUID eventId, @PathVariable UUID actionId) {
        timelineService.deleteAction(actionId);
    }
}
