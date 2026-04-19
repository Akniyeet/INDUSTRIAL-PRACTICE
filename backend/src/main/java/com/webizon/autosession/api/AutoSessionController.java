package com.webizon.autosession.api;

import com.webizon.auth.CurrentUser;
import com.webizon.autosession.api.dto.CreateAutoSlotBatchRequest;
import com.webizon.autosession.api.dto.CreateAutoSlotRequest;
import com.webizon.autosession.service.AutoSessionService;
import com.webizon.events.api.dto.SessionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Admin surface for scheduling replay slots of a finished LIVE
 * session.
 *
 * <p>Endpoints deliberately reuse {@link SessionResponse} so the
 * admin UI can render AUTO slots with the same component as live
 * sessions — the only difference is the {@code type} / {@code status}
 * fields and the populated {@code sourceLiveSessionId} pointer.
 *
 * <p>Read access is granted to every authenticated caller because the
 * landing-page slot selector is a public room feature; writes are
 * restricted to owner / admin / presenter. Cancelling a slot is kept
 * to owner / admin because cancelling an already-published schedule
 * is mildly destructive and should require elevated trust.
 */
@RestController
@RequestMapping("/api/v1/events/{eventId}/auto-sessions")
@RequiredArgsConstructor
public class AutoSessionController {

    private final AutoSessionService autoSessionService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<SessionResponse> listUpcoming(@PathVariable UUID eventId) {
        return autoSessionService.listUpcomingSlots(eventId).stream()
                .map(SessionResponse::from)
                .toList();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_PRESENTER')")
    public SessionResponse create(@PathVariable UUID eventId,
                                   @Valid @RequestBody CreateAutoSlotRequest req) {
        var slot = autoSessionService.createSlot(
                eventId,
                req.sourceLiveSessionId(),
                req.startTime(),
                CurrentUser.profileId());
        return SessionResponse.from(slot);
    }

    @PostMapping("/batch")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_PRESENTER')")
    public List<SessionResponse> createBatch(@PathVariable UUID eventId,
                                              @Valid @RequestBody CreateAutoSlotBatchRequest req) {
        return autoSessionService.createSlots(
                        eventId,
                        req.sourceLiveSessionId(),
                        req.startTimes(),
                        CurrentUser.profileId())
                .stream()
                .map(SessionResponse::from)
                .toList();
    }

    @DeleteMapping("/{sessionId}")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN')")
    public SessionResponse cancel(@PathVariable UUID eventId,
                                   @PathVariable UUID sessionId) {
        // eventId is a path guard for URL consistency; the service
        // layer re-validates ownership via the Session row itself.
        return SessionResponse.from(autoSessionService.cancelSlot(sessionId));
    }
}
