package com.webizon.cta.api;

import com.webizon.auth.CurrentUser;
import com.webizon.cta.api.dto.CtaActiveRequest;
import com.webizon.cta.api.dto.CtaCreateRequest;
import com.webizon.cta.api.dto.CtaResponse;
import com.webizon.cta.api.dto.CtaUpdateRequest;
import com.webizon.cta.service.CtaService;
import com.webizon.cta.service.CtaService.CtaCreateCommand;
import com.webizon.cta.service.CtaService.CtaUpdateCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Admin CRUD for {@link com.webizon.cta.model.EventCta}.
 *
 * <p>Writes are gated to owner / admin / presenter — moderators do not
 * edit the CTA set, only trigger show/hide pulses via
 * {@link SessionCtaController}.
 *
 * <p>Reads are open to every authenticated user so the room can
 * backfill CTA state after a reconnect; the RLS + tenant filter still
 * prevents cross-tenant reads.
 */
@RestController
@RequestMapping("/api/v1/events/{eventId}/ctas")
@RequiredArgsConstructor
public class CtaController {

    private final CtaService ctaService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<CtaResponse> list(@PathVariable UUID eventId,
                                   @RequestParam(name = "activeOnly", defaultValue = "false") boolean activeOnly) {
        var rows = activeOnly ? ctaService.listActive(eventId) : ctaService.list(eventId);
        return rows.stream().map(CtaResponse::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_PRESENTER')")
    public CtaResponse create(@PathVariable UUID eventId,
                               @Valid @RequestBody CtaCreateRequest req) {
        var cmd = new CtaCreateCommand(
                req.title(),
                req.description(),
                req.type(),
                req.buttonText(),
                req.actionUrl(),
                req.fileUrl(),
                req.placement(),
                req.priority(),
                req.allowStack()
        );
        return CtaResponse.from(ctaService.create(eventId, CurrentUser.profileId(), cmd));
    }

    @PatchMapping("/{ctaId}")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_PRESENTER')")
    public CtaResponse update(@PathVariable UUID eventId,
                               @PathVariable UUID ctaId,
                               @Valid @RequestBody CtaUpdateRequest req) {
        var cmd = new CtaUpdateCommand(
                req.title(),
                req.description(),
                req.buttonText(),
                req.actionUrl(),
                req.fileUrl(),
                req.placement(),
                req.priority(),
                req.allowStack()
        );
        return CtaResponse.from(ctaService.update(eventId, ctaId, cmd));
    }

    @PutMapping("/{ctaId}/active")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_PRESENTER')")
    public CtaResponse setActive(@PathVariable UUID eventId,
                                  @PathVariable UUID ctaId,
                                  @Valid @RequestBody CtaActiveRequest req) {
        return CtaResponse.from(ctaService.toggleActive(eventId, ctaId, req.active()));
    }

    @DeleteMapping("/{ctaId}")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN')")
    public void delete(@PathVariable UUID eventId, @PathVariable UUID ctaId) {
        ctaService.delete(eventId, ctaId);
    }
}
