package com.webizon.events.api;

import com.webizon.events.api.dto.PublicEventResolution;
import com.webizon.events.service.PublicEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unauthenticated landing page resolver.
 *
 * <p>Every other API endpoint under {@code /api/v1/} requires a JWT. This
 * one intentionally does not — visitors clicking a webinar share link
 * must be able to see the landing page before they decide to sign up.
 * Actual participation still requires authentication (enforced by the
 * room endpoints in later phases).
 *
 * <p>Mapped under {@code /api/v1/public/**}, which the security chain
 * permits for anonymous access.
 */
@RestController
@RequestMapping("/api/v1/public/tenants/{tenantSlug}/events/{eventSlug}")
@RequiredArgsConstructor
public class PublicEventController {

    private final PublicEventService publicEventService;

    @GetMapping("/resolve")
    public PublicEventResolution resolve(@PathVariable String tenantSlug,
                                         @PathVariable String eventSlug) {
        return publicEventService.resolve(tenantSlug, eventSlug);
    }
}
