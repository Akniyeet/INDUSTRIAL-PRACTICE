package com.webizon.config;

import com.webizon.tenancy.TenantContextFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
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
@EnableMethodSecurity
public class SecurityConfig {

    private final TenantContextFilter tenantContextFilter;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    /**
     * External issuer — the hostname the browser uses for Keycloak.
     * In Docker, the backend sees {@code http://keycloak:8180} but the browser
     * uses {@code http://localhost:8180}. Google OAuth tokens carry the browser's
     * issuer because Keycloak sets {@code iss} from the original browser session.
     */
    @Value("${webizon.keycloak.external-issuer-uri:http://localhost:8180/realms/webizon}")
    private String externalIssuerUri;

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
                        .jwt(jwt -> jwt
                                .decoder(multiIssuerJwtDecoder())
                                .jwtAuthenticationConverter(jwtAuthenticationConverter()))
                )
                // TenantContextFilter runs AFTER BearerTokenAuthenticationFilter so the JWT
                // is already validated and available in SecurityContextHolder. The previous
                // position (after UsernamePasswordAuthenticationFilter) was too early —
                // BearerTokenAuthenticationFilter runs at BasicAuthenticationFilter's position,
                // which comes AFTER UsernamePasswordAuthenticationFilter in the chain.
                .addFilterAfter(tenantContextFilter, BearerTokenAuthenticationFilter.class);

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

    /**
     * Custom JWT decoder that accepts tokens from both internal ({@code keycloak:8180})
     * and external ({@code localhost:8180}) issuers.
     *
     * <p>In Docker, Direct-Grant tokens have {@code iss=http://keycloak:8180/realms/webizon}
     * because the backend calls Keycloak over the internal network. Google OAuth tokens
     * have {@code iss=http://localhost:8180/realms/webizon} because Keycloak remembers
     * the browser's original hostname from the authorization request.
     */
    @Bean
    public JwtDecoder multiIssuerJwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();

        // Accept either the internal or external issuer
        decoder.setJwtValidator(
                new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
                        new org.springframework.security.oauth2.jwt.JwtTimestampValidator(),
                        new JwtClaimValidator<String>("iss", iss ->
                                issuerUri.equals(iss) || externalIssuerUri.equals(iss))
                )
        );
        return decoder;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(List.of(
                "http://localhost:3000",
                "http://localhost:3001",
                "http://localhost:3003",
                "http://localhost:8080",
                "http://localhost:8081",
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
