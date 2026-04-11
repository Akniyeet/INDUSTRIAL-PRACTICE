package com.webizon.tenancy.service;

import com.webizon.tenancy.AllowCrossTenant;
import com.webizon.tenancy.TenantContext;
import com.webizon.tenancy.model.TenantUser;
import com.webizon.tenancy.repo.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and manipulates {@link TenantUser} memberships.
 *
 * <p>Most methods are tenant-scoped (use {@link TenantContext}). The one
 * exception is {@link #listMembershipsForUser} which is explicitly cross-tenant
 * because it feeds the "pick your workspace" screen right after login.
 */
@Service
@RequiredArgsConstructor
public class MembershipService {

    private final TenantUserRepository tenantUserRepository;

    @Transactional(readOnly = true)
    public Optional<TenantUser> findByUserIdInCurrentTenant(UUID userId) {
        TenantContext.getRequired(); // asserts scope
        return tenantUserRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public TenantUser requireByUserIdInCurrentTenant(UUID userId) {
        return findByUserIdInCurrentTenant(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "User " + userId + " is not a member of tenant " + TenantContext.getRequired()));
    }

    @Transactional(readOnly = true)
    @AllowCrossTenant(reason = "Post-login 'pick workspace' needs memberships across all tenants the user belongs to.")
    public List<TenantUser> listMembershipsForUser(UUID userId) {
        return tenantUserRepository.findAllActiveByUserIdGlobal(userId);
    }
}
