package com.webizon.tenancy.repo;

import com.webizon.tenancy.model.MembershipStatus;
import com.webizon.tenancy.model.TenantUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantUserRepository extends JpaRepository<TenantUser, UUID> {

    /**
     * Lookup a membership by {@code userId} inside the currently-resolved tenant.
     * The {@code tenant_id} predicate is added automatically by the Hibernate
     * discriminator filter.
     */
    Optional<TenantUser> findByUserId(UUID userId);

    List<TenantUser> findAllByStatus(MembershipStatus status);

    /**
     * List every membership a given user has across ALL tenants. This crosses
     * the tenant boundary on purpose (login bootstrap / tenant switcher),
     * so we issue a native query with an {@code ALLOW_CROSS_TENANT} comment
     * and RLS is bypassed because the query runs via the bootstrap path.
     *
     * <p>Callers MUST be annotated with {@code @AllowCrossTenant}.
     */
    @Query(value = """
            SELECT * FROM tenant_users
            WHERE user_id = :userId
              AND status = 'ACTIVE'
            """, nativeQuery = true)
    List<TenantUser> findAllActiveByUserIdGlobal(@Param("userId") UUID userId);
}
