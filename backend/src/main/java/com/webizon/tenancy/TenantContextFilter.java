package com.webizon.tenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Extracts the {@code tenant_id} claim from the authenticated JWT and pushes it
 * into {@link TenantContext} and {@link MDC} for the duration of the request.
 *
 * <p>Runs after Spring Security's filter chain so that {@link SecurityContextHolder}
 * is already populated.
 *
 * <p>Public endpoints (no JWT) simply skip tenant context setup — repository
 * queries on public tables must not use {@link TenantContext}.
 */
@Component
@Order(10)
@Slf4j
public class TenantContextFilter extends OncePerRequestFilter {

    public static final String TENANT_CLAIM = "tenant_id";
    public static final String MDC_TENANT_KEY = "tenant_id";
    public static final String MDC_PROFILE_KEY = "profile_id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken jwtAuth) {
                Jwt jwt = jwtAuth.getToken();
                String tenantIdRaw = jwt.getClaimAsString(TENANT_CLAIM);
                if (tenantIdRaw != null && !tenantIdRaw.isBlank()) {
                    try {
                        UUID tenantId = UUID.fromString(tenantIdRaw);
                        TenantContext.set(tenantId);
                        MDC.put(MDC_TENANT_KEY, tenantId.toString());
                    } catch (IllegalArgumentException ex) {
                        log.warn("Invalid tenant_id claim in JWT: '{}'", tenantIdRaw);
                    }
                }

                String profileId = jwt.getClaimAsString("profile_id");
                if (profileId != null) {
                    MDC.put(MDC_PROFILE_KEY, profileId);
                }
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.remove(MDC_TENANT_KEY);
            MDC.remove(MDC_PROFILE_KEY);
        }
    }
}
