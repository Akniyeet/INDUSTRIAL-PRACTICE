package com.webizon.cta.api;

import com.webizon.auth.CurrentUser;
import com.webizon.cta.service.CtaService;
import com.webizon.timeline.api.dto.TimelineActionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Live-room endpoints for triggering CTA pulses inside an airing
 * session. Every successful call both captures a timeline row (so
 * the AUTO replay will reproduce the same pulse) and publishes to
 * the session's CTA channel.
 *
 * <p>Deliberately scoped to the four moderator roles — the same
 * set used throughout {@code ModerationController} and Phase 3's
 * {@code ChannelAccessService}.
 */
@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/ctas")
@RequiredArgsConstructor
public class SessionCtaController {

    private final CtaService ctaService;

    @PostMapping("/{ctaId}/show")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_MODERATOR','TENANT_PRESENTER')")
    public TimelineActionResponse show(@PathVariable UUID sessionId,
                                         @PathVariable UUID ctaId) {
        return TimelineActionResponse.from(
                ctaService.showCta(sessionId, ctaId, CurrentUser.profileId()));
    }

    @PostMapping("/{ctaId}/hide")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN','TENANT_MODERATOR','TENANT_PRESENTER')")
    public TimelineActionResponse hide(@PathVariable UUID sessionId,
                                         @PathVariable UUID ctaId) {
        return TimelineActionResponse.from(
                ctaService.hideCta(sessionId, ctaId, CurrentUser.profileId()));
    }
}
