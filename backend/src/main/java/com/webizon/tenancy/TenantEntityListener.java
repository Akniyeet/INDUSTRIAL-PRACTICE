package com.webizon.tenancy;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.util.UUID;

/**
 * JPA listener that auto-populates and guards the {@code tenant_id} column on
 * every persist and update operation.
 *
 * <p>Applied globally to all {@link TenantAware} entities via
 * {@code orm.xml} (or via {@code @EntityListeners} per entity).
 */
public class TenantEntityListener {

    @PrePersist
    public void prePersist(TenantAware entity) {
        UUID current = TenantContext.getRequired();
        if (entity.getTenantId() == null) {
            entity.setTenantId(current);
        } else if (!entity.getTenantId().equals(current)) {
            throw new SecurityException(
                    "Attempted to insert an entity with tenant_id=" + entity.getTenantId()
                            + " while TenantContext is " + current
                            + ". Cross-tenant writes are forbidden.");
        }
    }

    @PreUpdate
    public void preUpdate(TenantAware entity) {
        UUID current = TenantContext.getRequired();
        if (entity.getTenantId() == null || !entity.getTenantId().equals(current)) {
            throw new SecurityException(
                    "Attempted to update an entity with tenant_id=" + entity.getTenantId()
                            + " while TenantContext is " + current
                            + ". Cross-tenant updates are forbidden.");
        }
    }
}
