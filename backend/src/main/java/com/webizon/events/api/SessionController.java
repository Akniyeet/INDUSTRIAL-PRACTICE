package com.webizon.events.api;

import com.webizon.auth.CurrentUser;
import com.webizon.events.api.dto.SessionCreateRequest;
import com.webizon.events.api.dto.SessionResponse;
import com.webizon.events.api.dto.SessionUpdateRequest;
import com.webizon.events.service.SessionService;
import com.webizon.tenancy.model.User;
import com.webizon.tenancy.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Tenant-scoped admin CRUD + state transitions for sessions.
 *
 * <p>Listing and creation are nested under an event, matching how admins
 * think about scheduling: "give me all sessions of this event" or "add a
 * new slot to this event". State transitions are addressed by the session
 * id directly since a session's lifecycle is self-contained.
 */
@RestController
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;
    private final UserService userService;

    // ---------------------------------------------------------------
    // Event-scoped collection
    // ---------------------------------------------------------------

    @GetMapping("/api/v1/events/{eventId}/sessions")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_MODERATOR','TENANT_PRESENTER','TENANT_ANALYST')")
    public List<SessionResponse> listForEvent(@PathVariable UUID eventId) {
        return sessionService.listByEvent(eventId).stream()
                .map(SessionResponse::from)
                .toList();
    }

    @PostMapping("/api/v1/events/{eventId}/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_PRESENTER')")
    public SessionResponse create(@PathVariable UUID eventId,
                                  @Valid @RequestBody SessionCreateRequest request) {
        User creator = currentUser();
        return SessionResponse.from(sessionService.create(eventId, request, creator.getId()));
    }

    // ---------------------------------------------------------------
    // Single-session resource
    // ---------------------------------------------------------------

    @GetMapping("/api/v1/sessions/{id}")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_MODERATOR','TENANT_PRESENTER','TENANT_ANALYST')")
    public SessionResponse get(@PathVariable UUID id) {
        return SessionResponse.from(sessionService.requireById(id));
    }

    @PatchMapping("/api/v1/sessions/{id}")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_PRESENTER')")
    public SessionResponse update(@PathVariable UUID id,
                                  @Valid @RequestBody SessionUpdateRequest request) {
        return SessionResponse.from(sessionService.update(id, request));
    }

    @PostMapping("/api/v1/sessions/{id}/start-live")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_PRESENTER')")
    public SessionResponse startLive(@PathVariable UUID id) {
        return SessionResponse.from(sessionService.startLive(id));
    }

    @PostMapping("/api/v1/sessions/{id}/end-live")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_PRESENTER')")
    public SessionResponse endLive(@PathVariable UUID id) {
        return SessionResponse.from(sessionService.endLive(id));
    }

    @PostMapping("/api/v1/sessions/{id}/start-auto")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_PRESENTER')")
    public SessionResponse startAuto(@PathVariable UUID id) {
        return SessionResponse.from(sessionService.startAuto(id));
    }

    @PostMapping("/api/v1/sessions/{id}/end-auto")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_PRESENTER')")
    public SessionResponse endAuto(@PathVariable UUID id) {
        return SessionResponse.from(sessionService.endAuto(id));
    }

    @PostMapping("/api/v1/sessions/{id}/cancel")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN')")
    public SessionResponse cancel(@PathVariable UUID id) {
        return SessionResponse.from(sessionService.cancel(id));
    }

    private User currentUser() {
        return userService.requireByKeycloakId(CurrentUser.keycloakId());
    }
}
