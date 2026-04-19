package com.webizon.tenancy;

import com.webizon.tenancy.model.MembershipStatus;
import com.webizon.tenancy.model.TenantUser;
import com.webizon.tenancy.repo.TenantUserRepository;
import com.webizon.tenancy.repo.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Enriches Spring Security authorities with the caller's tenant-scoped membership role.
 *
 * <p><strong>Why this filter exists</strong>: The Keycloak JWT only carries realm-level
 * roles in {@code realm_access.roles}, not the application-level tenant membership roles
 * ({@code TENANT_OWNER}, {@code TENANT_ADMIN}, etc.). Without enrichment, every
 * {@code @PreAuthorize("hasAnyRole('TENANT_OWNER', ...)")} check fails with 403.
 *
 * <p><strong>Execution order</strong>: Runs at {@code @Order(20)}, which is after the
 * Spring Security filter chain ({@code FilterChainProxy} at order {@code -100}). At
 * this point:
 * <ul>
 *   <li>Spring Security has already authenticated the JWT and populated
 *       {@code SecurityContextHolder}.</li>
 *   <li>{@link TenantContextFilter} (inside the security chain) has already set
 *       {@link TenantContext} from the {@code X-Tenant-Id} header or JWT claim.</li>
 * </ul>
 *
 * <p>Hibernate's {@code @TenantId} discriminator filter is active because
 * {@link TenantContext} is set, so the JPA repository call is automatically
 * scoped to the correct tenant without any additional setup.
 *
 * <p>{@link OncePerRequestFilter} guarantees idempotency — if this filter is somehow
 * triggered twice (e.g., error-dispatch), the enrichment is skipped on the second pass.
 */
@Component
@Order(20)
@RequiredArgsConstructor
@Slf4j
public class RoleEnrichmentFilter extends OncePerRequestFilter {

    private final UserRepository      userRepository;
    private final TenantUserRepository tenantUserRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest  request,
            HttpServletResponse response,
            FilterChain         filterChain) throws ServletException, IOException {

        try {
            Authentication auth     = SecurityContextHolder.getContext().getAuthentication();
            boolean        hasTenant = TenantContext.isSet();

            if (auth instanceof JwtAuthenticationToken jwtAuth && hasTenant) {
                Jwt    jwt         = jwtAuth.getToken();
                String subjectStr  = jwt.getSubject();   // Keycloak UUID as String

                if (subjectStr != null) {
                    UUID keycloakId = UUID.fromString(subjectStr);
                    Optional<TenantUser> membership = userRepository.findByKeycloakId(keycloakId)
                            .flatMap(user -> tenantUserRepository.findByUserId(user.getId()))
                            .filter(tu -> tu.getStatus() == MembershipStatus.ACTIVE);

                    if (membership.isPresent()) {
                        String roleName = membership.get().getRole().name(); // e.g. "TENANT_OWNER"
                        Collection<GrantedAuthority> enriched = new ArrayList<>(jwtAuth.getAuthorities());
                        enriched.add(new SimpleGrantedAuthority("ROLE_" + roleName));

                        SecurityContextHolder.getContext().setAuthentication(
                                new JwtAuthenticationToken(jwt, enriched, jwtAuth.getName()));

                        log.debug("Enriched authorities: tenantId={} role={} keycloakId={}",
                                TenantContext.getOptional().orElse(null), roleName, keycloakId);
                    } else {
                        log.debug("No active membership for keycloakId={} in tenant={}",
                                keycloakId, TenantContext.getOptional().orElse(null));
                    }
                }
            }
        } catch (Exception ex) {
            // Never block the request — enrichment failure is non-fatal.
            // The @PreAuthorize check will then fail gracefully with 403.
            log.warn("RoleEnrichmentFilter: could not enrich authorities — {}", ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
