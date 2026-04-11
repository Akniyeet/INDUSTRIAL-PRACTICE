package com.webizon.billing.repo;

import com.webizon.billing.model.BillingInvoice;
import com.webizon.billing.model.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link BillingInvoice}.
 */
public interface BillingInvoiceRepository extends JpaRepository<BillingInvoice, UUID> {

    Page<BillingInvoice> findAllByOrderByPeriodStartDesc(Pageable pageable);

    /** Used by the period-close job to avoid re-building an existing invoice. */
    Optional<BillingInvoice> findByPeriodStartAndPeriodEnd(Instant periodStart, Instant periodEnd);

    List<BillingInvoice> findAllByStatusOrderByPeriodStartDesc(InvoiceStatus status);
}
