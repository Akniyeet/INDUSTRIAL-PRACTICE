package com.webizon.tenancy.api;

import com.webizon.tenancy.api.dto.CodeExchangeRequest;
import com.webizon.tenancy.api.dto.LoginRequest;
import com.webizon.tenancy.api.dto.OtpRequest;
import com.webizon.tenancy.api.dto.OtpVerifyRequest;
import com.webizon.tenancy.api.dto.RefreshRequest;
import com.webizon.tenancy.api.dto.RegisterRequest;
import com.webizon.tenancy.api.dto.TokenResponse;
import com.webizon.tenancy.service.KeycloakAuthService;
import com.webizon.tenancy.service.OtpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public (unauthenticated) auth endpoints.
 *
 * <p>Mapped under {@code /api/v1/public/**} which is whitelisted in
 * {@code SecurityConfig} — no JWT required to call these.
 *
 * <p>All paths proxy to Keycloak, keeping client credentials server-side so
 * they are never exposed to the browser.
 */
@RestController
@RequestMapping("/api/v1/public/auth")
@RequiredArgsConstructor
public class AuthPublicController {

    private final KeycloakAuthService keycloakAuthService;
    private final OtpService otpService;

    @Value("${webizon.keycloak.server-url:http://keycloak:8180}")
    private String keycloakServerUrl;

    @Value("${webizon.keycloak.realm:webizon}")
    private String realm;

    @Value("${webizon.keycloak.client-id:webizon-frontend}")
    private String clientId;

    /**
     * Email + password login. Proxies to Keycloak Direct Grant.
     */
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return keycloakAuthService.loginWithPassword(request.email(), request.password());
    }

    /**
     * New user registration. Creates user via Keycloak Admin API then returns
     * tokens so the frontend proceeds directly to the app without a second login.
     */
    @PostMapping("/register")
    public TokenResponse register(@Valid @RequestBody RegisterRequest request) {
        return keycloakAuthService.register(request.fullName(), request.email(), request.password());
    }

    /**
     * PKCE Authorization Code exchange — called after Google OAuth redirect.
     * Frontend generates code_verifier/code_challenge, stores verifier in
     * sessionStorage, then sends it here after the callback.
     */
    @PostMapping("/callback")
    public TokenResponse callback(@Valid @RequestBody CodeExchangeRequest request) {
        return keycloakAuthService.exchangeCode(request.code(), request.redirectUri(), request.codeVerifier());
    }

    /**
     * Refresh an expired access token using a refresh token.
     */
    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return keycloakAuthService.refreshToken(request.refreshToken());
    }

    /**
     * OTP Step 1: Validate credentials and send 6-digit code to email.
     * Credentials are checked first via Keycloak Direct Grant — if invalid,
     * a 401 is returned immediately without sending any email.
     * The validated tokens are stored in Redis so they can be returned
     * after OTP verification without requiring the password again.
     */
    @PostMapping("/otp/request")
    public java.util.Map<String, String> otpRequest(@Valid @RequestBody OtpRequest request) {
        // Validate credentials (throws if invalid)
        TokenResponse tokens = keycloakAuthService.loginWithPassword(request.email(), request.password());
        // Credentials valid → generate OTP and store tokens alongside it
        otpService.generateAndSend(request.email(), tokens);
        return java.util.Map.of("status", "sent", "email", request.email());
    }

    /**
     * OTP Step 2: Verify the 6-digit code and return the JWT tokens
     * that were pre-generated during otp/request.
     */
    @PostMapping("/otp/verify")
    public TokenResponse otpVerify(@Valid @RequestBody OtpVerifyRequest request) {
        return otpService.verifyAndGetTokens(request.email(), request.code());
    }
}
