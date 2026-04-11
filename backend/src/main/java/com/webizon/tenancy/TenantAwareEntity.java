package com.webizon.tenancy;

import com.webizon.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

import java.util.UUID;

/**
 * Base class for every multi-tenant JPA entity.
 *
 * <p>The {@code tenant_id} column is automatically:
 * <ul>
 *   <li>populated on INSERT by Hibernate 6's {@link TenantId} feature, which
 *       calls the registered {@code CurrentTenantIdentifierResolver}
 *       (see {@link com.webizon.tenancy.hibernate.WebizonCurrentTenantIdentifierResolver})</li>
 *   <li>validated by {@link TenantEntityListener} as a defence-in-depth check —
 *       any cross-tenant write throws a {@link SecurityException}</li>
 *   <li>appended to every SELECT by Hibernate as an implicit filter</li>
 *   <li>enforced at the database level by PostgreSQL RLS policies that read
 *       {@code current_setting('app.current_tenant')}</li>
 * </ul>
 *
 * <p>This triple-layered enforcement means an accidental cross-tenant read or
 * write has to bypass Spring, Hibernate, AND PostgreSQL simultaneously.
 */
@MappedSuperclass
@EntityListeners(TenantEntityListener.class)
@Getter
@Setter
public abstract class TenantAwareEntity extends BaseEntity implements TenantAware {

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false, columnDefinition = "UUID")
    private UUID tenantId;
}
