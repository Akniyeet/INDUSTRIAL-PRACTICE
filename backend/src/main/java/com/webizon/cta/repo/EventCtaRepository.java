package com.webizon.cta.repo;

import com.webizon.cta.model.EventCta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA repository for {@link EventCta}. Tenant-scoped via Hibernate
 * {@code @TenantId} and PostgreSQL RLS; every query here is filtered
 * by the caller's active tenant.
 */
public interface EventCtaRepository extends JpaRepository<EventCta, UUID> {

    /** Every CTA attached to an event, active and inactive, for the admin view. */
    List<EventCta> findAllByEventIdOrderByPriorityDesc(UUID eventId);

    /**
     * Only the CTAs that are currently toggled on — the hot read path
     * used by the resolver when the frontend backfills CTA state on
     * reconnect.
     */
    List<EventCta> findAllByEventIdAndActiveTrueOrderByPriorityDesc(UUID eventId);

    Optional<EventCta> findByIdAndEventId(UUID id, UUID eventId);
}
