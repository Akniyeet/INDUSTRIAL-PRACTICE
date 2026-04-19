package com.webizon.analytics.api;

import com.webizon.analytics.api.dto.AttendanceResponse;
import com.webizon.analytics.api.dto.HeartbeatRequest;
import com.webizon.analytics.service.AttendanceTracker;
import com.webizon.auth.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Per-session presence endpoints: join, heartbeat, leave.
 *
 * <p>These three actions map onto {@code AttendanceTracker} one-to-one.
 * The frontend calls {@link #join} when it opens the room, then
 * {@link #heartbeat} every ~20 seconds while the player is active,
 * then {@link #leave} on tab close (the leave ping uses
 * {@code navigator.sendBeacon} so it survives a page unload).
 *
 * <p>All three endpoints use the caller's {@code profileId} — a
 * client can't falsify attendance for another user. Returning the
 * full attendance row from every call lets the UI render "you've
 * watched N minutes" without extra reads.
 */
@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceTracker attendanceTracker;

    @PostMapping("/join")
    @PreAuthorize("isAuthenticated()")
    public AttendanceResponse join(@PathVariable UUID sessionId) {
        return AttendanceResponse.from(
                attendanceTracker.join(sessionId, CurrentUser.profileId()));
    }

    @PostMapping("/heartbeat")
    @PreAuthorize("isAuthenticated()")
    public AttendanceResponse heartbeat(@PathVariable UUID sessionId,
                                         @RequestBody(required = false) HeartbeatRequest req) {
        Integer offset = req == null ? null : req.offsetSeconds();
        return AttendanceResponse.from(
                attendanceTracker.heartbeat(sessionId, CurrentUser.profileId(), offset));
    }

    @PostMapping("/leave")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AttendanceResponse> leave(@PathVariable UUID sessionId) {
        return attendanceTracker.leave(sessionId, CurrentUser.profileId())
                .map(AttendanceResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
