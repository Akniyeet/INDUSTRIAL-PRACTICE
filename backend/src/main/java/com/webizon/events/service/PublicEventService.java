package com.webizon.events.service;

import com.webizon.events.api.dto.PublicEventResolution;
import com.webizon.events.api.dto.PublicEventView;
import com.webizon.events.api.dto.PublicSessionView;
import com.webizon.events.model.Event;
import com.webizon.events.model.EventStatus;
import com.webizon.events.model.Session;
import com.webizon.events.repo.EventRepository;
import com.webizon.events.repo.SessionRepository;
import com.webizon.tenancy.AllowCrossTenant;
import com.webizon.tenancy.TenantContext;
import com.webizon.tenancy.model.Tenant;
import com.webizon.tenancy.model.TenantStatus;
import com.webizon.tenancy.repo.TenantRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the {@code /event/{slug}} landing page for unauthenticated users.
 *
 * <p>Public landing traffic is not yet inside any tenant scope, so this
 * service is the single place where we perform a tenant pivot for read-only
 * workloads:
 *
 * <ol>
 *   <li>Look up the tenant by slug — {@code tenants} is a <em>global</em>
 *       table that does not use RLS, so this read is safe without a context.</li>
 *   <li>Set {@link TenantContext} and {@code app.current_tenant} for the
 *       remainder of the transaction so Hibernate and RLS confine the rest
 *       of the queries to the right workspace.</li>
 *   <li>Restore the previous context on exit, matching the pattern in
 *       {@code TenantService.createTenant}.</li>
 * </ol>
 *
 * <p>If the tenant is not in an operable status (draft, suspended, archived)
 * the resolver returns {@link PublicEventResolution#unavailable()} without
 * revealing whether the slug exists — we do not want to leak tenant state
 * to random visitors.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@AllowCrossTenant(reason = "Public landing resolver: pivots into the target tenant's scope before querying.")
public class PublicEventService {

    /** How far ahead of start_time the waiting room is shown. */
    private static final Duration WAITING_WINDOW = Duration.ofMinutes(30);

    private final TenantRepository tenantRepository;
    private final EventRepository eventRepository;
    private final SessionRepository sessionRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public PublicEventResolution resolve(String tenantSlug, String eventSlug) {
        if (tenantSlug == null || tenantSlug.isBlank() || eventSlug == null || eventSlug.isBlank()) {
            return PublicEventResolution.unavailable();
        }

        Optional<Tenant> tenantOpt = tenantRepository.findBySlug(tenantSlug.trim().toLowerCase());
        if (tenantOpt.isEmpty() || !isPublicFacing(tenantOpt.get())) {
            return PublicEventResolution.unavailable();
        }
        Tenant tenant = tenantOpt.get();

        UUID previous = TenantContext.copy();
        try {
            TenantContext.set(tenant.getId());
            // PostgreSQL does not accept bind parameters in SET LOCAL — use string formatting
            // (safe: tenant.getId() is a validated UUID)
            entityManager.createNativeQuery(
                    "SET LOCAL app.current_tenant = '" + tenant.getId() + "'")
                    .executeUpdate();

            return resolveInsideTenant(eventSlug.trim().toLowerCase());
        } finally {
            try {
                if (previous != null) {
                    TenantContext.set(previous);
                    entityManager.createNativeQuery(
                            "SET LOCAL app.current_tenant = '" + previous + "'")
                            .executeUpdate();
                } else {
                    TenantContext.clear();
                    entityManager.createNativeQuery("RESET app.current_tenant").executeUpdate();
                }
            } catch (Exception ignored) {
                // best-effort cleanup — transaction may already be aborted
            }
        }
    }

    private PublicEventResolution resolveInsideTenant(String eventSlug) {
        Optional<Event> eventOpt = eventRepository.findBySlug(eventSlug);
        if (eventOpt.isEmpty()) {
            return PublicEventResolution.unavailable();
        }
        Event event = eventOpt.get();
        if (event.getStatus() != EventStatus.PUBLISHED) {
            // DRAFT and ARCHIVED events are never exposed publicly. We do
            // return UNAVAILABLE here rather than pretending the slug does
            // not exist — admins need a way to distinguish "invalid slug"
            // from "not published" in their logs, and the DTO is identical.
            return PublicEventResolution.unavailable();
        }

        PublicEventView eventView = PublicEventView.from(event);
        Instant now = Instant.now();

        // 1. Any airing session wins — drop the user straight into the room.
        Optional<Session> airing = sessionRepository.findAiringByEventId(event.getId());
        if (airing.isPresent()) {
            return PublicEventResolution.liveNow(eventView, PublicSessionView.from(airing.get()));
        }

        // 2. Nearest upcoming session — waiting room if close enough.
        List<Session> upcoming = sessionRepository.findUpcomingByEventId(event.getId(), now);
        if (!upcoming.isEmpty()) {
            Session next = upcoming.get(0);
            if (next.getStartTime().isBefore(now.plus(WAITING_WINDOW))) {
                return PublicEventResolution.waiting(eventView, PublicSessionView.from(next));
            }
        }

        // 3. Slot selector — multiple future AUTO replays, no LIVE imminent.
        List<Session> autoSlots = sessionRepository.findUpcomingAutoSlotsByEventId(event.getId(), now);
        if (autoSlots.size() >= 2) {
            List<PublicSessionView> slotViews = autoSlots.stream()
                    .map(PublicSessionView::from)
                    .toList();
            return PublicEventResolution.slotSelection(eventView, slotViews);
        }
        // Single future auto slot with room to spare — treat it like "next upcoming".
        if (autoSlots.size() == 1) {
            Session single = autoSlots.get(0);
            if (single.getStartTime().isBefore(now.plus(WAITING_WINDOW))) {
                return PublicEventResolution.waiting(eventView, PublicSessionView.from(single));
            }
        }

        // 4. Nothing imminent — show the informational landing page.
        return PublicEventResolution.landing(eventView);
    }

    private static boolean isPublicFacing(Tenant tenant) {
        TenantStatus status = tenant.getStatus();
        return status == TenantStatus.TRIAL || status == TenantStatus.ACTIVE || status == TenantStatus.PAST_DUE;
    }
}
