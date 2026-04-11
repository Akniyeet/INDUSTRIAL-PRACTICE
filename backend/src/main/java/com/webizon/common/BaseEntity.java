package com.webizon.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Common shape for all Webizon JPA entities:
 * <ul>
 *   <li>{@code id} — UUID v4, generated application-side for safety in idempotent writes</li>
 *   <li>{@code createdAt}, {@code updatedAt} — audited automatically</li>
 *   <li>{@code version} — optimistic locking to protect hot rows (tenants, sessions, subscriptions)</li>
 *   <li>{@code equals/hashCode} — based on id only, never on business fields</li>
 * </ul>
 *
 * <p>Subclasses should not redefine any of these fields. Tenant-scoped entities
 * should extend {@link com.webizon.tenancy.TenantAwareEntity} instead of this class.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class BaseEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "UUID")
    private UUID id = UUID.randomUUID();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BaseEntity other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public final int hashCode() {
        // Stable across the lifetime of the entity: use the class, not the mutable id.
        return Objects.hash(getClass().getName());
    }
}
