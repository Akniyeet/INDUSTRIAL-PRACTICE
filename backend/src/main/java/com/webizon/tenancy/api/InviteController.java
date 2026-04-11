package com.webizon.tenancy.api;

import com.webizon.tenancy.api.dto.InviteAcceptResponse;
import com.webizon.tenancy.api.dto.InviteCreateRequest;
import com.webizon.tenancy.api.dto.InviteCreateResponse;
import com.webizon.tenancy.api.dto.InviteResponse;
import com.webizon.tenancy.model.InviteStatus;
import com.webizon.tenancy.service.InviteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST surface for tenant invites.
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code POST   /api/v1/invites} — create a PENDING invite.
 *       Admin only. Returns the one-time accept URL once.</li>
 *   <li>{@code GET    /api/v1/invites} — paginated listing for the
 *       admin panel, with optional {@code ?status=PENDING} filter.</li>
 *   <li>{@code GET    /api/v1/invites/{id}} — single invite detail.</li>
 *   <li>{@code DELETE /api/v1/invites/{id}} — revoke a pending invite.
 *       Idempotent: revoking a terminal invite is a no-op.</li>
 *   <li>{@code POST   /api/v1/invites/accept/{token}} — the recipient
 *       claims the invite. Authenticated but crosses tenants — this
 *       is the ONLY endpoint whose caller does not yet have a
 *       {@code tenant_id} claim in their JWT.</li>
 * </ul>
 *
 * <h2>Who can call what</h2>
 * The create / list / revoke endpoints require a tenant staff role
 * that can manage members — {@code TENANT_OWNER} and
 * {@code TENANT_ADMIN}. Moderators, presenters, and analysts cannot
 * touch member lifecycle. The accept endpoint requires only
 * authentication — the recipient may hold any role (including none)
 * at the moment of acceptance.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/invites")
public class InviteController {

    private final InviteService inviteService;

    // ------------------------------------------------------------------
    // Admin endpoints (tenant scoped via JWT tenant_id claim)
    // ------------------------------------------------------------------

    @PostMapping
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public InviteCreateResponse create(@Valid @RequestBody InviteCreateRequest request) {
        return inviteService.create(request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN')")
    public Page<InviteResponse> list(
            @RequestParam(value = "status", required = false) InviteStatus status,
            Pageable pageable) {
        return inviteService.list(status, pageable).map(InviteResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN')")
    public InviteResponse getOne(@PathVariable UUID id) {
        return InviteResponse.from(inviteService.requireById(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN')")
    public InviteResponse revoke(@PathVariable UUID id) {
        return InviteResponse.from(inviteService.revoke(id));
    }

    // ------------------------------------------------------------------
    // Recipient-facing accept endpoint
    // ------------------------------------------------------------------

    /**
     * Consume a one-time invite token. The caller must be
     * authenticated (any Keycloak user works — this endpoint does
     * not require a {@code tenant_id} claim and is the bootstrap
     * point for brand-new staff members).
     *
     * <p>The token is passed as a path variable rather than a query
     * param so it does not leak into access logs / referrer headers
     * as readily. It is one-time: a successful accept marks the
     * invite ACCEPTED and any subsequent call returns 410 Gone.
     */
    @PostMapping("/accept/{token}")
    @PreAuthorize("isAuthenticated()")
    public InviteAcceptResponse accept(@PathVariable String token) {
        return inviteService.accept(token);
    }
}
