package com.webizon.events.service;

import com.webizon.events.api.dto.EventCreateRequest;
import com.webizon.events.api.dto.EventUpdateRequest;
import com.webizon.events.model.Event;
import com.webizon.events.model.EventStatus;
import com.webizon.events.repo.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.UUID;

/**
 * CRUD + lifecycle transitions for {@link Event}.
 *
 * <p>Every method here runs inside a tenant-scoped transaction. Hibernate 6's
 * {@code @TenantId} filter and Postgres RLS make sure this service can never
 * touch an event belonging to a different workspace, even if a bug were to
 * leak an untrusted id into the code path.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class EventService {

    private final EventRepository eventRepository;

    // ---------------------------------------------------------------
    // Queries
    // ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public Event requireById(UUID id) {
        return eventRepository.findById(id).orElseThrow(
                () -> new IllegalArgumentException("Event not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<Event> list(EventStatus status, Pageable pageable) {
        return status == null
                ? eventRepository.findAllByOrderByCreatedAtDesc(pageable)
                : eventRepository.findAllByStatusOrderByCreatedAtDesc(status, pageable);
    }

    // ---------------------------------------------------------------
    // Mutations
    // ---------------------------------------------------------------

    public Event create(EventCreateRequest request, UUID createdByUserId) {
        if (eventRepository.existsBySlug(request.slug())) {
            throw new IllegalStateException(
                    "Event slug already used in this workspace: " + request.slug());
        }

        Event event = new Event();
        event.setSlug(request.slug());
        event.setTitle(request.title());
        event.setDescription(request.description());
        event.setSpeakerName(request.speakerName());
        event.setSpeakerBio(request.speakerBio());
        event.setCoverImageUrl(request.coverImageUrl());
        if (request.timezone() != null && !request.timezone().isBlank()) {
            event.setTimezone(request.timezone());
        }
        if (request.language() != null && !request.language().isBlank()) {
            event.setLanguage(request.language());
        }
        // "Лендинг Builder" blob: admins can ship the create form with or
        // without it. Always persist a non-null map so the JSONB column is
        // happy even when the admin skipped step 5.
        event.setLandingConfig(
                request.landingConfig() != null ? request.landingConfig() : new HashMap<>()
        );
        event.setStatus(EventStatus.DRAFT);
        event.setCreatedByUserId(createdByUserId);
        return eventRepository.save(event);
    }

    public Event update(UUID id, EventUpdateRequest request) {
        Event event = requireById(id);
        if (event.getStatus() == EventStatus.ARCHIVED) {
            throw new IllegalStateException("Archived events are read-only");
        }
        if (request.title() != null)         event.setTitle(request.title());
        if (request.description() != null)   event.setDescription(request.description());
        if (request.speakerName() != null)   event.setSpeakerName(request.speakerName());
        if (request.speakerBio() != null)    event.setSpeakerBio(request.speakerBio());
        if (request.coverImageUrl() != null) event.setCoverImageUrl(request.coverImageUrl());
        if (request.timezone() != null && !request.timezone().isBlank()) {
            event.setTimezone(request.timezone());
        }
        if (request.language() != null && !request.language().isBlank()) {
            event.setLanguage(request.language());
        }
        // Null in PATCH = "no change"; an empty map explicitly clears the
        // landing customisation. We never overwrite with null so the JSONB
        // column never flips to a state the constraint forbids.
        if (request.landingConfig() != null) {
            event.setLandingConfig(request.landingConfig());
        }
        return event;
    }

    public Event publish(UUID id) {
        Event event = requireById(id);
        if (event.getStatus() == EventStatus.ARCHIVED) {
            throw new IllegalStateException("Cannot publish an archived event");
        }
        event.setStatus(EventStatus.PUBLISHED);
        return event;
    }

    public Event unpublish(UUID id) {
        Event event = requireById(id);
        if (event.getStatus() == EventStatus.ARCHIVED) {
            throw new IllegalStateException("Cannot modify an archived event");
        }
        event.setStatus(EventStatus.DRAFT);
        return event;
    }

    public Event archive(UUID id) {
        Event event = requireById(id);
        event.setStatus(EventStatus.ARCHIVED);
        return event;
    }
}
