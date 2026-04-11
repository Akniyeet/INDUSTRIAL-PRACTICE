package com.webizon.config;

import com.webizon.tenancy.TenantContextFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Security configuration for the Webizon API.
 *
 * <p>The API is stateless and backed by Keycloak-issued JWTs. Every protected
 * request must carry a valid {@code Authorization: Bearer <token>} header. The
 * {@link TenantContextFilter} then extracts the {@code tenant_id} claim and
 * propagates it through {@code TenantContext} + MDC for the duration of the
 * request.
 *
 * <p><strong>Public endpoints</strong> — intentionally unauthenticated:
 * <ul>
 *   <li>{@code /actuator/health}, {@code /actuator/info} — liveness probes</li>
 *   <li>{@code /api/v1/public/**} — landing pages, magic-link signup</li>
 *   <li>{@code /swagger-ui/**}, {@code /v3/api-docs/**} — API documentation</li>
 *   <li>{@code /api/v1/webhooks/**} — provider webhooks (CloudPayments, Centrifugo proxy)</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final TenantContextFilter tenantContextFilter;

    public SecurityConfig(TenantContextFilter tenantContextFilter) {
        this.tenantContextFilter = tenantContextFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info",
                                "/actuator/prometheus"
                        ).permitAll()
                        .requestMatchers(
                                "/api/v1/public/**",
                                "/api/v1/webhooks/**"
                        ).permitAll()
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                )
                // TenantContextFilter runs AFTER authentication so the JWT is already available.
                .addFilterAfter(tenantContextFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Convert JWT claims into Spring Security authorities.
     *
     * <p>We merge two sources of roles:
     * <ul>
     *   <li>{@code realm_access.roles} — Keycloak realm roles</li>
     *   <li>{@code role} — single-role shortcut claim minted by our token customizer</li>
     * </ul>
     * All roles are prefixed with {@code ROLE_} so that {@code @PreAuthorize("hasRole('...')")}
     * works without additional configuration.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();
        scopeConverter.setAuthorityPrefix("SCOPE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new WebizonAuthoritiesConverter(scopeConverter));
        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(List.of(
                "http://localhost:3000",
                "http://localhost:8080",
                "https://*.webizon.kz",
                "https://webizon.kz"
        ));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setExposedHeaders(List.of("Location", "X-Request-Id", "X-Tenant-Id"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    /** Extracts realm roles + single-role claim and merges them with OAuth scopes. */
    private static final class WebizonAuthoritiesConverter
            implements Converter<Jwt, Collection<GrantedAuthority>> {

        private final JwtGrantedAuthoritiesConverter scopeConverter;

        private WebizonAuthoritiesConverter(JwtGrantedAuthoritiesConverter scopeConverter) {
            this.scopeConverter = scopeConverter;
        }

        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            Collection<GrantedAuthority> authorities = new ArrayList<>(scopeConverter.convert(jwt));

            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess != null && realmAccess.get("roles") instanceof Collection<?> roles) {
                roles.stream()
                        .map(Object::toString)
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r.toUpperCase()))
                        .forEach(authorities::add);
            }

            String singleRole = jwt.getClaimAsString("role");
            if (singleRole != null && !singleRole.isBlank()) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + singleRole.toUpperCase()));
            }

            return authorities;
        }
    }
}
