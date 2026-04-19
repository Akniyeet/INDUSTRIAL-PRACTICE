package com.webizon.tenancy.api;

import com.webizon.tenancy.api.dto.BootstrapResponse;
import com.webizon.tenancy.api.dto.MembershipResponse;
import com.webizon.tenancy.api.dto.UserResponse;
import com.webizon.tenancy.model.Tenant;
import com.webizon.tenancy.model.TenantUser;
import com.webizon.tenancy.model.User;
import com.webizon.tenancy.repo.TenantRepository;
import com.webizon.tenancy.service.MembershipService;
import com.webizon.tenancy.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@code POST /api/v1/auth/bootstrap} — called by the frontend immediately
 * after Keycloak sign-in.
 *
 * <p>Idempotent: create-or-update the local mirror of the authenticated user
 * and return the user plus all their active memberships. The frontend then
 * either drops the user straight into a single workspace or shows a picker.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthBootstrapController {

    private final UserService userService;
    private final MembershipService membershipService;
    private final TenantRepository tenantRepository;

    @PostMapping("/bootstrap")
    public BootstrapResponse bootstrap() {
        User user = userService.bootstrapFromJwt();
        List<TenantUser> tenantUsers = membershipService.listMembershipsForUser(user.getId());
        Map<UUID, Tenant> tenantsById = tenantRepository.findAllById(
                tenantUsers.stream().map(TenantUser::getTenantId).toList()
        ).stream().collect(Collectors.toMap(Tenant::getId, t -> t));
        List<MembershipResponse> memberships = tenantUsers.stream()
                .map(tu -> {
                    Tenant t = tenantsById.get(tu.getTenantId());
                    return MembershipResponse.from(tu, t == null ? null : t.getSlug(), t == null ? null : t.getDisplayName());
                })
                .toList();
        return new BootstrapResponse(UserResponse.from(user), memberships);
    }
}
