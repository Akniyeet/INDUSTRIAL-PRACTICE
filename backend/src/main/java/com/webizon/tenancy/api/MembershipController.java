package com.webizon.tenancy.api;

import com.webizon.tenancy.api.dto.MembershipResponse;
import com.webizon.tenancy.model.Tenant;
import com.webizon.tenancy.model.TenantUser;
import com.webizon.tenancy.model.User;
import com.webizon.tenancy.repo.TenantRepository;
import com.webizon.tenancy.service.MembershipService;
import com.webizon.tenancy.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@code /api/v1/memberships} — list the caller's memberships across all
 * tenants. Used by the workspace switcher in the frontend header.
 */
@RestController
@RequestMapping("/api/v1/memberships")
@RequiredArgsConstructor
public class MembershipController {

    private final UserService userService;
    private final MembershipService membershipService;
    private final TenantRepository tenantRepository;

    @GetMapping("/me")
    public List<MembershipResponse> myMemberships() {
        User me = userService.bootstrapFromJwt();
        List<TenantUser> tenantUsers = membershipService.listMembershipsForUser(me.getId());
        Map<UUID, Tenant> tenantsById = tenantRepository.findAllById(
                tenantUsers.stream().map(TenantUser::getTenantId).toList()
        ).stream().collect(Collectors.toMap(Tenant::getId, t -> t));
        return tenantUsers.stream()
                .map(tu -> {
                    Tenant t = tenantsById.get(tu.getTenantId());
                    return MembershipResponse.from(tu, t == null ? null : t.getSlug(), t == null ? null : t.getDisplayName());
                })
                .toList();
    }
}
