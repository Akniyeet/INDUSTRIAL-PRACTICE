package com.webizon.room.api;

import com.webizon.auth.CurrentUser;
import com.webizon.room.api.dto.RoomBootstrapResponse;
import com.webizon.room.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Authenticated room bootstrap.
 *
 * <p>The public landing resolver ({@link com.webizon.events.api.PublicEventController})
 * tells an unauthenticated visitor <em>which</em> session they should
 * enter. Once they authenticate and click through, the frontend hits
 * this endpoint to get the complete room state in a single call —
 * event, session, chat settings, recent chat seed, currently visible
 * CTAs, attendance count, current offset, Centrifugo channel names,
 * and role-based capability flags.
 *
 * <p>The endpoint also records attendance (via
 * {@code AttendanceTracker.join}) as a side effect, matching the
 * user intent "I'm in the room". That is idempotent across reloads.
 *
 * <p>Short-lived Centrifugo connect and subscribe tokens are
 * deliberately <em>not</em> included in this payload — they come
 * from {@code /api/v1/realtime/*} and are refreshed on reconnect.
 * Bundling them here would either tie their lifetime to the
 * bootstrap cache window or force the bootstrap to be uncacheable;
 * keeping them separate leaves both responses clean.
 */
@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/room")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public RoomBootstrapResponse bootstrap(@PathVariable UUID sessionId) {
        return roomService.bootstrap(
                sessionId,
                CurrentUser.profileId(),
                CurrentUser.role());
    }
}
