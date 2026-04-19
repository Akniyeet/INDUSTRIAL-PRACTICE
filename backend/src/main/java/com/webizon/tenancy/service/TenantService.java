package com.webizon.tenancy.service;

import com.webizon.tenancy.AllowCrossTenant;
import com.webizon.tenancy.TenantContext;
import com.webizon.tenancy.model.MembershipRole;
import com.webizon.tenancy.model.MembershipStatus;
import com.webizon.tenancy.model.Plan;
import com.webizon.tenancy.model.Subscription;
import com.webizon.tenancy.model.SubscriptionStatus;
import com.webizon.tenancy.model.Tenant;
import com.webizon.tenancy.model.TenantStatus;
import com.webizon.tenancy.model.TenantUser;
import com.webizon.tenancy.model.User;
import com.webizon.tenancy.repo.PlanRepository;
import com.webizon.tenancy.repo.SubscriptionRepository;
import com.webizon.tenancy.repo.TenantRepository;
import com.webizon.tenancy.repo.TenantUserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Creates and reads tenants.
 *
 * <p>The tricky operation here is {@link #createTenant} — it runs as a
 * <em>single transaction</em> that starts with no tenant in scope (the caller
 * has just registered and has no tenant_id claim yet), creates the global
 * {@code Tenant} row, then pivots into the new tenant's scope to insert the
 * tenant-scoped rows ({@code Subscription}, {@code TenantUser}).
 *
 * <p>The pivot happens via:
 * <ol>
 *   <li>{@link TenantContext#set} — so the Hibernate resolver returns the new
 *       tenant id for subsequent flushes</li>
 *   <li>A native {@code SET app.current_tenant = ?} executed on the same
 *       connection — so PostgreSQL RLS accepts the new tenant's writes</li>
 * </ol>
 *
 * <p>The previous tenant context (if any) is restored in a {@code finally}
 * block so that the filter's end-of-request clear never leaves stale state.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantService {

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{1,62}[a-z0-9]$");
    private static final String DEFAULT_PLAN_CODE = "webizon-kz-default";

    private final TenantRepository tenantRepository;
    private final TenantUserRepository tenantUserRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Create a new tenant, start a trial subscription, and add the caller as
     * the first {@code TENANT_OWNER}.
     *
     * @param owner       the authenticated user who will own the new tenant
     * @param slug        URL-safe identifier; validated against the slug regex
     * @param displayName human-readable name
     * @return the persisted {@link Tenant}
     */
    @Transactional
    @AllowCrossTenant(reason = "Bootstraps a NEW tenant; no tenant context exists yet when called.")
    public Tenant createTenant(User owner, String slug, String displayName) {
        String normalizedSlug = slug == null ? null : slug.trim().toLowerCase(Locale.ROOT);
        if (normalizedSlug == null || !SLUG_PATTERN.matcher(normalizedSlug).matches()) {
            throw new IllegalArgumentException(
                    "Slug must match " + SLUG_PATTERN.pattern() + " (was: '" + slug + "')");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        if (tenantRepository.existsBySlug(normalizedSlug)) {
            throw new IllegalStateException("Tenant slug already taken: " + normalizedSlug);
        }

        Plan plan = planRepository.findByCode(DEFAULT_PLAN_CODE)
                .orElseThrow(() -> new IllegalStateException(
                        "Default plan '" + DEFAULT_PLAN_CODE + "' is missing — check V001 seed."));

        Instant now = Instant.now();
        Instant trialEndsAt = now.plus(Duration.ofDays(plan.getTrialDays()));

        Tenant tenant = Tenant.builder()
                .slug(normalizedSlug)
                .displayName(displayName.trim())
                .status(TenantStatus.TRIAL)
                .trialEndsAt(trialEndsAt)
                .build();
        tenant = tenantRepository.saveAndFlush(tenant);
        log.info("Created tenant id={} slug={} owner={}", tenant.getId(), tenant.getSlug(), owner.getId());

        // Pivot into the new tenant's scope for the rest of the transaction so
        // that Hibernate @TenantId, the JPA listener, and PostgreSQL RLS all
        // see the correct identifier.
        UUID previousTenant = TenantContext.copy();
        try {
            TenantContext.set(tenant.getId());
            entityManager.createNativeQuery(
                    "SET LOCAL app.current_tenant = '" + tenant.getId() + "'")
                    .executeUpdate();

            Subscription subscription = Subscription.builder()
                    .planId(plan.getId())
                    .status(SubscriptionStatus.TRIAL)
                    .startedAt(now)
                    .currentPeriodStart(now)
                    .currentPeriodEnd(trialEndsAt)
                    .trialEndsAt(trialEndsAt)
                    .build();
            subscriptionRepository.save(subscription);

            TenantUser ownership = TenantUser.builder()
                    .userId(owner.getId())
                    .role(MembershipRole.TENANT_OWNER)
                    .status(MembershipStatus.ACTIVE)
                    .joinedAt(now)
                    .build();
            tenantUserRepository.save(ownership);
        } finally {
            if (previousTenant != null) {
                TenantContext.set(previousTenant);
                entityManager.createNativeQuery("SET LOCAL app.current_tenant = :tid")
                        .setParameter("tid", previousTenant.toString())
                        .executeUpdate();
            } else {
                TenantContext.clear();
                entityManager.createNativeQuery("RESET app.current_tenant").executeUpdate();
            }
        }

        return tenant;
    }

    @Transactional(readOnly = true)
    public Tenant requireById(UUID id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Tenant not found: " + id));
    }
}
