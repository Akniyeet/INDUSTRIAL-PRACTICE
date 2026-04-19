package com.webizon.tenancy;

import java.util.UUID;

/**
 * Marker interface for entities that belong to a tenant.
 *
 * <p>Every multi-tenant JPA entity MUST implement this interface. The tenant id
 * is set automatically by {@link TenantEntityListener} before insert, using
 * {@link TenantContext#getRequired()}.
 */
public interface TenantAware {
    UUID getTenantId();
    void setTenantId(UUID tenantId);
}
