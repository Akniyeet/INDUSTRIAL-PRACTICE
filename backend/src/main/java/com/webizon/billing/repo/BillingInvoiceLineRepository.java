package com.webizon.billing.repo;

import com.webizon.billing.model.BillingInvoiceLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link BillingInvoiceLine}.
 */
public interface BillingInvoiceLineRepository extends JpaRepository<BillingInvoiceLine, UUID> {

    /** All lines for an invoice, ordered by display position. */
    List<BillingInvoiceLine> findAllByInvoiceIdOrderByLineNumberAsc(UUID invoiceId);
}
