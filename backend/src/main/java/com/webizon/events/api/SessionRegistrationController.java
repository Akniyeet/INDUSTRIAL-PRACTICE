package com.webizon.events.api;

import com.webizon.auth.CurrentUser;
import com.webizon.events.api.dto.SessionRegistrationRequest;
import com.webizon.events.api.dto.SessionRegistrationResponse;
import com.webizon.events.model.SessionRegistration;
import com.webizon.events.service.SessionRegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Viewer-facing registration endpoints for the public event
 * landing page plus a handful of admin-only read paths.
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code POST   /api/v1/sessions/{id}/registration} — opt in</li>
 *   <li>{@code DELETE /api/v1/sessions/{id}/registration} — opt out</li>
 *   <li>{@code GET    /api/v1/sessions/{id}/registration} — current
 *       caller's own row, 404 if none</li>
 *   <li>{@code GET    /api/v1/me/registrations} — the caller's
 *       personal upcoming list</li>
 *   <li>{@code GET    /api/v1/admin/sessions/{id}/registrations} —
 *       admin paginated roster</li>
 * </ul>
 *
 * <p>The viewer endpoints are open to every authenticated role —
 * even {@code VIEWER} can opt in to reminders on their own behalf.
 * The admin roster is restricted to tenant staff because it
 * exposes other users' email addresses.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class SessionRegistrationController {

    private final SessionRegistrationService registrationService;

    // ------------------------------------------------------------------
    // Viewer self-service
    // ------------------------------------------------------------------

    @PostMapping("/sessions/{sessionId}/registration")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionRegistrationResponse register(
            @PathVariable UUID sessionId,
            @Valid @RequestBody(required = false) SessionRegistrationRequest request) {

        UUID profileId = CurrentUser.profileId();
        String fallbackEmail = currentEmailOrNull();

        SessionRegistration row = registrationService.register(
                sessionId, profileId, fallbackEmail, request);
        return SessionRegistrationResponse.from(row);
    }

    @DeleteMapping("/sessions/{sessionId}/registration")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> unregister(@PathVariable UUID sessionId) {
        UUID profileId = CurrentUser.profileId();
        boolean flipped = registrationService.unregister(sessionId, profileId);
        return flipped
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @GetMapping("/sessions/{sessionId}/registration")
    @PreAuthorize("isAuthenticated()")
    public SessionRegistrationResponse getMyRegistration(@PathVariable UUID sessionId) {
        UUID profileId = CurrentUser.profileId();
        try {
            return SessionRegistrationResponse.from(
                    registrationService.requireActive(sessionId, profileId));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    @GetMapping("/me/registrations")
    @PreAuthorize("isAuthenticated()")
    public Page<SessionRegistrationResponse> listMine(Pageable pageable) {
        UUID profileId = CurrentUser.profileId();
        return registrationService.listMyRegistrations(profileId, pageable)
                .map(SessionRegistrationResponse::from);
    }

    // ------------------------------------------------------------------
    // Admin roster
    // ------------------------------------------------------------------

    @GetMapping("/admin/sessions/{sessionId}/registrations")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_PRESENTER','TENANT_ANALYST')")
    public Page<SessionRegistrationResponse> listForSession(
            @PathVariable UUID sessionId, Pageable pageable) {
        return registrationService.listForSession(sessionId, pageable)
                .map(SessionRegistrationResponse::from);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Fish the email claim off the JWT if present. Returns null (not
     * throws) if absent — some M2M tokens intentionally omit the
     * email claim, and in that case the service requires the caller
     * to supply the address explicitly in the request body.
     */
    private static String currentEmailOrNull() {
        try {
            return CurrentUser.email();
        } catch (IllegalStateException notPresent) {
            return null;
        }
    }
}
