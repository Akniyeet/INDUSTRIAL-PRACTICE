# ADR-0004: Billing Pipeline — Pay-Per-Use Metering

**Status**: Accepted
**Date**: 2026-04-10

## Context

Webizon is a pay-per-use SaaS billed in KZT. Billing correctness is non-negotiable: undercounting loses revenue, overcounting loses customers. Both must be provably correct.

## Decision

### Pipeline shape
```
Domain action (e.g. participant joined)
    ↓
API emits BillingEvent → Kafka topic `webizon.billing.events`
    ↓
BillingEventConsumer (idempotent by event_id)
    ↓
Atomic transaction:
    • INSERT INTO billing_ledger (immutable append-only)
    • UPDATE usage_meter SET counter = counter + 1
    ↓
Monthly scheduler: aggregate meter → generate invoice → charge card
```

### Core invariants (enforced by code, tests, and reconciliation)

1. **Immutable ledger.** `billing_ledger` is insert-only. Corrections are new entries with a `reversal_of_id` reference. No `UPDATE` or `DELETE` ever.

2. **Idempotency keys.** Every BillingEvent carries a deterministic `event_id` derived from `(tenant_id, session_id, profile_id, event_type, bucket_timestamp)`. Duplicates at the consumer are no-ops. Kafka at-least-once delivery is safe because consumers are idempotent.

3. **Atomicity.** Ledger insert and meter update happen in the **same database transaction**. If either fails, both roll back and the Kafka offset is not committed.

4. **Ordering per tenant.** Kafka topic is partitioned by `tenant_id`, guaranteeing in-order processing within a tenant.

5. **Reconciliation.** A nightly job computes `SUM(amount) FROM billing_ledger WHERE period = X GROUP BY tenant_id` and compares to `usage_meter WHERE period = X`. Any drift triggers a critical alert and blocks invoice generation until resolved.

### Metrics billed
| Metric | Event source | Rate (default, KZT) |
|--------|--------------|---------------------|
| `seat.live` | Participant joined a LIVE session (first join per session) | 15 |
| `seat.auto` | Participant joined an AUTO session (first join per session) | 10 |
| `storage.gb_month` | Monthly snapshot of storage usage over free quota | 200 per GB |
| `payment_processing` | Payment collected through Webizon checkout | 3% of amount |

### What is NOT billed
- Chat messages (unmetered)
- API calls (unmetered)
- Admin actions (unmetered)
- Infrastructure failures (if a session is terminated by our error, affected seats are **not billed** — tracked via a `failure_waiver` flag on the session)

### Invoicing
- Billing period: calendar month (UTC+5, Almaty time)
- Invoice generated on the 1st of the following month at 00:30 local
- VAT: 12% (Kazakhstan) added on top, shown as a separate line
- PDF stored in S3 `tenants/{tid}/invoices/{period}.pdf`, pre-signed URL (7-day expiry) emailed to Tenant Owner
- Automatic charge attempt against the default PaymentMethod
- Dunning schedule: see CLAUDE.md §11

### Payment provider abstraction
A `PaymentProvider` interface is defined so that CloudPayments, PayBox, and Stripe are swappable. The default for KZ tenants is CloudPayments Kazakhstan.

```java
public interface PaymentProvider {
    TokenizedMethod tokenizeCard(String customerId, String cardToken);
    PaymentResult charge(TokenizedMethod method, Money amount, String idempotencyKey);
    WebhookPayload verifyWebhook(String body, String signature);
}
```

## Consequences

**Positive**
- Billing is correct even under Kafka retries, network partitions, or consumer crashes.
- Audit trail is complete: every KZT charged traces back to a specific ledger row and a domain event.
- Reconciliation catches any drift proactively before customers see incorrect invoices.
- Adding a new billable metric is a localized change (new event type + new ledger rate).

**Negative**
- Three components (Kafka, consumer, DB transaction) must all work for billing to advance. Mitigated by DLQ and monitoring.
- Reconciliation job is additional operational complexity.
- Idempotency key design must be carefully chosen — a bad key can cause both undercounting (hash collisions) and overcounting (missing uniqueness).

## Testing Requirements (mandatory before merge of any billing change)
1. **Duplicate event test**: emit the same event twice, verify ledger has one row and meter increments once.
2. **Concurrent emission test**: emit 1000 events in parallel for the same session, verify meter = 1000.
3. **Consumer crash test**: crash the consumer mid-batch, restart, verify no double-counting.
4. **Reconciliation drift test**: intentionally corrupt the meter, verify reconciliation detects and alerts.
5. **Idempotency key collision test**: verify keys from similar-but-distinct events do not collide.

## References
- Stripe's billing engine design notes (public): https://stripe.com/docs/billing/subscriptions/overview
- Idempotent consumers pattern (Vaughn Vernon / Kafka docs)
