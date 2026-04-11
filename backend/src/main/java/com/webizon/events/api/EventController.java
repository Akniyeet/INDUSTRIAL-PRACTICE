package com.webizon.events.api;

import com.webizon.auth.CurrentUser;
import com.webizon.events.api.dto.EventCreateRequest;
import com.webizon.events.api.dto.EventResponse;
import com.webizon.events.api.dto.EventUpdateRequest;
import com.webizon.events.model.Event;
import com.webizon.events.model.EventStatus;
import com.webizon.events.service.EventService;
import com.webizon.tenancy.model.User;
import com.webizon.tenancy.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Tenant-scoped admin CRUD for events.
 *
 * <p>Every endpoint runs under the authenticated user's active tenant —
 * the JWT carries a {@code tenant_id} claim which {@code TenantContextFilter}
 * has already propagated into {@code TenantContext}. Hibernate + RLS then
 * confine all reads and writes to that workspace.
 */
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;
    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_MODERATOR','TENANT_PRESENTER','TENANT_ANALYST')")
    public Page<EventResponse> list(@RequestParam(required = false) EventStatus status,
                                    Pageable pageable) {
        return eventService.list(status, pageable).map(EventResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_MODERATOR','TENANT_PRESENTER','TENANT_ANALYST')")
    public EventResponse get(@PathVariable UUID id) {
        return EventResponse.from(eventService.requireById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_PRESENTER')")
    public EventResponse create(@Valid @RequestBody EventCreateRequest request) {
        User creator = currentUser();
        Event saved = eventService.create(request, creator.getId());
        return EventResponse.from(saved);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_PRESENTER')")
    public EventResponse update(@PathVariable UUID id,
                                @Valid @RequestBody EventUpdateRequest request) {
        return EventResponse.from(eventService.update(id, request));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN')")
    public EventResponse publish(@PathVariable UUID id) {
        return EventResponse.from(eventService.publish(id));
    }

    @PostMapping("/{id}/unpublish")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN')")
    public EventResponse unpublish(@PathVariable UUID id) {
        return EventResponse.from(eventService.unpublish(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN')")
    public void archive(@PathVariable UUID id) {
        eventService.archive(id);
    }

    private User currentUser() {
        return userService.requireByKeycloakId(CurrentUser.keycloakId());
    }
}
