package com.webizon.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.CompositeHealth;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregated infrastructure health endpoint consumed by the admin UI.
 *
 * <p>Wraps Spring Actuator's {@link HealthEndpoint} (which already knows
 * about PostgreSQL, Redis, Kafka, etc. via registered {@link org.springframework.boot.actuate.health.HealthIndicator}
 * beans) into a stable JSON contract that the frontend can render without
 * parsing Actuator's raw format.
 *
 * <p>Accessible only to tenant owners and admins — not public.
 */
@RestController
@RequestMapping("/api/v1/infrastructure")
@RequiredArgsConstructor
@Slf4j
public class InfrastructureHealthController {

    private final HealthEndpoint healthEndpoint;

    public record ServiceHealth(
            String name,
            String status,   // "healthy" | "degraded" | "down"
            Map<String, Object> details
    ) {}

    public record InfrastructureHealthResponse(
            String overall,
            List<ServiceHealth> services
    ) {}

    @GetMapping("/health")
    @PreAuthorize("hasAnyRole('TENANT_OWNER','TENANT_ADMIN')")
    public InfrastructureHealthResponse health() {
        HealthComponent root = healthEndpoint.health();

        String overall = toFrontendStatus(root.getStatus());
        List<ServiceHealth> services;

        if (root instanceof CompositeHealth composite) {
            services = composite.getComponents().entrySet().stream()
                    .map(e -> toServiceHealth(e.getKey(), e.getValue()))
                    .toList();
        } else {
            // Single-component fallback (unlikely in production)
            services = List.of(toServiceHealth("api", root));
        }

        return new InfrastructureHealthResponse(overall, services);
    }

    /**
     * Restart request — triggers a graceful Spring context shutdown + JVM exit.
     * The Docker container's restart policy ({@code unless-stopped}) will bring
     * the service back up. Only TENANT_OWNER can invoke this.
     */
    @PostMapping("/restart")
    @PreAuthorize("hasAnyRole('TENANT_OWNER')")
    public Map<String, String> restart() {
        log.info("Restart requested by admin — scheduling graceful shutdown in 2 seconds");
        new Thread(() -> {
            try {
                Thread.sleep(2000); // Allow the HTTP response to be sent
                System.exit(0);     // Docker restart policy restarts the container
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
        return Map.of("message", "Restart scheduled. Services will be back in ~30 seconds.");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private ServiceHealth toServiceHealth(String name, HealthComponent component) {
        String status = toFrontendStatus(component.getStatus());
        Map<String, Object> details = new LinkedHashMap<>();
        if (component instanceof Health h && h.getDetails() != null) {
            details.putAll(h.getDetails());
        }
        return new ServiceHealth(name, status, details);
    }

    private static String toFrontendStatus(Status status) {
        if (Status.UP.equals(status)) return "healthy";
        if (Status.DOWN.equals(status)) return "down";
        return "degraded"; // OUT_OF_SERVICE, UNKNOWN, custom
    }
}
