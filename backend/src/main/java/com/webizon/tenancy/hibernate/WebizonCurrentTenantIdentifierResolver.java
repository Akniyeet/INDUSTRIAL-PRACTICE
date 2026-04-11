package com.webizon.tenancy.hibernate;

import com.webizon.tenancy.TenantContext;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Tells Hibernate which tenant "owns" the current session.
 *
 * <p>Hibernate calls this on every new session; we look at
 * {@link TenantContext} (populated by {@code TenantContextFilter} for HTTP
 * requests, or by Kafka listeners / scheduled jobs that intentionally enter
 * a tenant scope).
 *
 * <p>If no tenant is set we return the
 * {@link WebizonMultiTenantConnectionProvider#DEFAULT_TENANT} sentinel rather
 * than {@code null}. Hibernate requires a non-null identifier, and the
 * sentinel causes the connection provider to skip the {@code SET}, which in
 * turn causes PostgreSQL RLS to deny tenant-scoped reads (fail-closed).
 */
@Component
public class WebizonCurrentTenantIdentifierResolver implements CurrentTenantIdentifierResolver<Object> {

    @Override
    public Object resolveCurrentTenantIdentifier() {
        return TenantContext.getOptional()
                .map(UUID::toString)
                .map(s -> (Object) s)
                .orElse(WebizonMultiTenantConnectionProvider.DEFAULT_TENANT);
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        // If the tenant changes mid-session we want Hibernate to complain loudly
        // rather than silently cross streams.
        return true;
    }

    @Override
    public boolean isRoot(Object tenantId) {
        return WebizonMultiTenantConnectionProvider.DEFAULT_TENANT.equals(tenantId);
    }
}
