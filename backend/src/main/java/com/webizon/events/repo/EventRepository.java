package com.webizon.events.repo;

import com.webizon.events.model.Event;
import com.webizon.events.model.EventStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA repository for {@link Event}. All finder methods are implicitly
 * tenant-scoped: Hibernate 6 injects the {@code tenant_id} filter on every
 * select, and PostgreSQL RLS enforces it again at the driver level.
 *
 * <p>Public landing pages reach this repository through the
 * {@code PublicEventService}, which first resolves the tenant from the URL
 * slug and pivots {@code TenantContext} + {@code app.current_tenant} into
 * the correct scope before calling {@link #findBySlug(String)}.
 */
public interface EventRepository extends JpaRepository<Event, UUID> {

    Optional<Event> findBySlug(String slug);

    boolean existsBySlug(String slug);

    Page<Event> findAllByStatusOrderByCreatedAtDesc(EventStatus status, Pageable pageable);

    Page<Event> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
