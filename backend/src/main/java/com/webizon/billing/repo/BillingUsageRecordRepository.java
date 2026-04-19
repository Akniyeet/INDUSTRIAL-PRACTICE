package com.webizon.billing.repo;

import com.webizon.billing.model.BillingUsageRecord;
import com.webizon.billing.model.UsageKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link BillingUsageRecord}.
 *
 * <p>Every read runs under the tenant RLS policy. The only
 * cross-tenant writes go through {@link com.webizon.billing.service.UsageMeteringScheduler}
 * which pivots tenant context via {@code TenantContext.runWith}
 * before each batch.
 */
public interface BillingUsageRecordRepository extends JpaRepository<BillingUsageRecord, UUID> {

    /**
     * Idempotency check before inserting a new usage row. The DB also
     * carries a unique constraint on {@code (tenant_id, session_id,
     * kind)}, but checking here first keeps the error path simple in
     * the common case.
     */
    boolean existsBySessionIdAndKind(UUID sessionId, UsageKind kind);

    /**
     * All currently-open (not yet invoiced) usage records for the
     * tenant. Used by the "current period estimate" view on the
     * tenant dashboard.
     */
    @Query("""
           select u from BillingUsageRecord u
           where u.invoiceId is null
             and u.billedForInstant >= :from
             and u.billedForInstant <  :to
           """)
    List<BillingUsageRecord> findOpenInPeriod(
            @Param("from") Instant from,
            @Param("to")   Instant to);

    /**
     * All usage records ever issued under a given invoice. Used by
     * the admin drill-down and by re-render paths.
     */
    List<BillingUsageRecord> findAllByInvoiceId(UUID invoiceId);

    /**
     * Bulk mark a set of usage records as claimed by a freshly built
     * invoice. Done as a JPQL update so we do not load N rows into
     * the persistence context just to flip one field. The caller
     * must ensure the ids all belong to the current tenant (they
     * come from a previous tenant-scoped query, so this is safe by
     * construction).
     */
    @Modifying
    @Query("""
           update BillingUsageRecord u
              set u.invoiceId = :invoiceId
            where u.id in :ids
              and u.invoiceId is null
           """)
    int claimForInvoice(@Param("invoiceId") UUID invoiceId,
                        @Param("ids") List<UUID> ids);
}
