# ADR-0002: Multi-Tenancy Strategy

**Status**: Accepted
**Date**: 2026-04-10

## Context

Webizon is a multi-tenant SaaS from day one. Tenants must be strictly isolated: no tenant should ever see another tenant's data, even under a bug. At the same time, we need to operate cost-effectively at hundreds of tenants without managing hundreds of databases.

## Decision

**Shared database, shared schema, `tenant_id` discriminator column, enforced by three layers:**

1. **Application layer**: A `TenantContext` (ThreadLocal UUID) set by a Spring filter on every authenticated request from the JWT `tenant_id` claim. A `TenantAwareRepository<T, ID>` base class auto-injects `WHERE tenant_id = :currentTenant` into every query.

2. **ORM layer**: Hibernate `@Filter` enabled globally on every entity with a `tenant_id` column, driven by `TenantContext`.

3. **Database layer**: PostgreSQL Row-Level Security (RLS) policies on every multi-tenant table. A session variable `app.current_tenant` is set at the beginning of each transaction by a Hibernate `StatementInspector` or a `javax.sql.DataSource` wrapper. RLS enforces that no row outside the current tenant is ever returned, regardless of application bugs.

**Tenant ID is a UUID v7** (time-ordered, index-friendly).

## Consequences

**Positive**
- Operational simplicity — one PostgreSQL cluster to back up, patch, monitor.
- Cost-effective at small and medium scale (most tenants are small).
- Three independent enforcement layers mean a bug in the application cannot leak data across tenants (RLS will block it at the DB level).
- Cache keys, Kafka headers, Centrifugo channels, S3 prefixes, metrics labels, log MDC all consistently scoped by `tenant_id` — building on the same principle.

**Negative**
- Large tenants share resources with small tenants (noisy neighbor potential). Mitigated by per-tenant rate limits and quotas.
- Any query that forgets to filter by tenant is a **critical bug**. We protect against this with RLS + code review + integration tests that attempt cross-tenant reads.
- Schema migrations apply to all tenants at once; no tenant can delay an upgrade.
- Upgrading to per-tenant schemas or per-tenant databases later (for massive customers) would require a data migration.

## Enforcement Checklist (applies to every PR)

1. Does every new table include `tenant_id UUID NOT NULL`?
2. Does every index include `tenant_id` as the first column (when the query uses it)?
3. Is RLS enabled and tested for the new table?
4. Does every new query filter by `tenant_id` (verified by repository base class or explicit check)?
5. Does the integration test attempt a cross-tenant read and verify it returns nothing?

## Alternatives Considered

### Alternative 1: Database-per-tenant
- **Rejected for MVP**: operational burden is prohibitive at 100+ tenants. Revisit for enterprise-tier customers who require dedicated resources.

### Alternative 2: Schema-per-tenant (same DB, different schemas)
- **Rejected**: PostgreSQL schema count has practical limits (~5000), migrations become hard to coordinate, connection pooling is awkward.

### Alternative 3: Shared schema without RLS (application-only enforcement)
- **Rejected**: single point of failure. An application bug would leak data. RLS provides defense-in-depth at near-zero cost.

## References
- PostgreSQL RLS docs: https://www.postgresql.org/docs/current/ddl-rowsecurity.html
- Hibernate `@Filter`: https://docs.jboss.org/hibernate/orm/6.4/userguide/html_single/Hibernate_User_Guide.html#pc-filter
