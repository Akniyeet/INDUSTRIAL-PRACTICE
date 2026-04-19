package com.webizon.tenancy.api;

import com.webizon.auth.CurrentUser;
import com.webizon.tenancy.TenantContext;
import com.webizon.tenancy.api.dto.TenantCreateRequest;
import com.webizon.tenancy.api.dto.TenantResponse;
import com.webizon.tenancy.model.Tenant;
import com.webizon.tenancy.model.User;
import com.webizon.tenancy.service.TenantService;
import com.webizon.tenancy.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * {@code /api/v1/tenants} — create a workspace and inspect the currently
 * selected one.
 *
 * <p>{@code POST} is available to any authenticated user; it bootstraps a new
 * tenant and makes the caller its {@code TENANT_OWNER}.
 *
 * <p>{@code GET /me} requires the JWT to already carry a {@code tenant_id}
 * claim — meaning the user has selected a workspace on the frontend.
 */
@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;
    private final UserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantResponse createTenant(@Valid @RequestBody TenantCreateRequest request) {
        // Make sure the caller exists in our mirror (they may be hitting us
        // directly after Keycloak registration without calling /bootstrap).
        User owner = userService.bootstrapFromJwt();
        Tenant tenant = tenantService.createTenant(owner, request.slug(), request.displayName());
        return TenantResponse.from(tenant);
    }

    @GetMapping("/me")
    public TenantResponse currentTenant() {
        UUID tenantId = TenantContext.getOptional().or(CurrentUser::tenantId)
                .orElseThrow(() -> new IllegalStateException(
                        "No tenant selected — JWT lacks tenant_id claim. Create or pick a workspace first."));
        return TenantResponse.from(tenantService.requireById(tenantId));
    }
}
