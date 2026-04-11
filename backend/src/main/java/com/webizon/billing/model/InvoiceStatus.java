package com.webizon.billing.model;

/**
 * Lifecycle states of a {@link BillingInvoice}.
 *
 * <pre>
 *     DRAFT ---issue()---> ISSUED ---pay()----> PAID
 *       |                    |
 *       |                    +---markOverdue()---> OVERDUE ---pay()---> PAID
 *       |                    |
 *       +---void()---> VOID  +---void()---> VOID
 * </pre>
 *
 * <ul>
 *   <li><b>DRAFT</b> — period has just been closed, subtotal is
 *       computed but no customer-visible invoice number has been
 *       assigned. Admins can re-run line aggregation without
 *       touching customer state. A voided draft disappears silently
 *       and does not consume an invoice number.</li>
 *   <li><b>ISSUED</b> — invoice number has been minted and the PDF
 *       is available. Payment is due.</li>
 *   <li><b>PAID</b> — payment has been reconciled; terminal.</li>
 *   <li><b>OVERDUE</b> — payment due date passed without PAID; a
 *       scheduled sweep flips ISSUED → OVERDUE. Still recoverable
 *       back to PAID.</li>
 *   <li><b>VOID</b> — cancelled for any reason (billing error,
 *       tenant dispute, write-off). Terminal. An ISSUED invoice
 *       voided after issue keeps its number for audit.</li>
 * </ul>
 */
public enum InvoiceStatus {
    DRAFT,
    ISSUED,
    PAID,
    OVERDUE,
    VOID;

    public boolean isTerminal() {
        return this == PAID || this == VOID;
    }
}
