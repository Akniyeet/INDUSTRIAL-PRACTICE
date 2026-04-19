package com.webizon.tenancy.repo;

import com.webizon.tenancy.model.InviteStatus;
import com.webizon.tenancy.model.TenantInvite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA repository for {@link TenantInvite}. Mostly tenant-scoped (admin
 * listing / revoke), with a single deliberately cross-tenant lookup used by
 * the accept endpoint — see {@link #findByTokenHashGlobal}.
 */
@Repository
public interface TenantInviteRepository extends JpaRepository<TenantInvite, UUID> {

    /**
     * Tenant-scoped paginated listing for the admin panel. Matches the
     * {@code tenant_invites_tenant_status_idx} index — the trailing
     * {@code created_at DESC} sort is served directly by the index.
     */
    Page<TenantInvite> findAllByStatusOrderByCreatedAtDesc(InviteStatus status, Pageable pageable);

    /**
     * Tenant-scoped listing of all invites (any status), newest first.
     * Used by the admin overview page.
     */
    Page<TenantInvite> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Tenant-scoped lookup of an existing pending invite for the given
     * email. Matches the partial unique index
     * {@code tenant_invites_pending_email_uniq} — at most one row can
     * satisfy this predicate per tenant, so we short-circuit re-invites
     * instead of piling up duplicate rows.
     */
    Optional<TenantInvite> findByEmailAndStatus(String email, InviteStatus status);

    /**
     * Pending invites for the current tenant whose {@code expires_at} has
     * passed. The cleanup sweeper uses this to flip them into EXPIRED so
     * the accept endpoint returns 410 cleanly.
     *
     * <p>Tenant-scoped on purpose — the sweeper runs once per tenant via
     * {@code TenantContext.runWith}.
     */
    @Query("""
           select i from TenantInvite i
           where i.status = com.webizon.tenancy.model.InviteStatus.PENDING
             and i.expiresAt <= :cutoff
           """)
    List<TenantInvite> findExpiredPending(@Param("cutoff") Instant cutoff);

    /**
     * <strong>Cross-tenant lookup.</strong> The accept endpoint has no
     * tenant context at the moment the recipient clicks the link — the
     * recipient may not even be a member of any Webizon tenant yet — so
     * we address the invite directly by its globally-unique token hash
     * and let the service layer pivot to the invite's tenant via
     * {@code TenantContext.runWith} afterwards.
     *
     * <p>The table has a dedicated SELECT-only RLS escape policy
     * {@code tenant_invites_lookup_mode} that fires only when the
     * connection variable {@code app.invite_lookup} is set to
     * {@code 'on'}. {@code InviteService#accept} is the sole caller and
     * is responsible for:
     * <ol>
     *   <li>issuing {@code SET LOCAL app.invite_lookup = 'on'} on the
     *       transactional connection</li>
     *   <li>invoking this method</li>
     *   <li>issuing {@code SET LOCAL app.invite_lookup = 'off'}
     *       immediately after the lookup and before any further work</li>
     *   <li>pivoting {@code TenantContext} + {@code app.current_tenant}
     *       to the invite's own tenant for the accept work</li>
     * </ol>
     * The escape policy is SELECT-only, so a rogue lookup-mode
     * connection cannot mutate anything even if the flag leaks.
     */
    @Query(value = """
           SELECT * FROM tenant_invites
           WHERE token_hash = :tokenHash
           """, nativeQuery = true)
    Optional<TenantInvite> findByTokenHashInLookupMode(@Param("tokenHash") String tokenHash);
}
