package com.webizon.tenancy.api;

import com.webizon.tenancy.api.dto.CodeExchangeRequest;
import com.webizon.tenancy.api.dto.LoginRequest;
import com.webizon.tenancy.api.dto.OtpRequest;
import com.webizon.tenancy.api.dto.OtpVerifyRequest;
import com.webizon.tenancy.api.dto.PasswordResetConfirmDto;
import com.webizon.tenancy.api.dto.PasswordResetRequestDto;
import com.webizon.tenancy.api.dto.RefreshRequest;
import com.webizon.tenancy.api.dto.RegisterRequest;
import com.webizon.tenancy.api.dto.TokenResponse;
import com.webizon.tenancy.service.KeycloakAuthService;
import com.webizon.tenancy.service.OtpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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
@Slf4j
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

    // -----------------------------------------------------------------------
    // Password reset (email OTP flow, no old-password required)
    // -----------------------------------------------------------------------

    /**
     * Step 1 — Request a password-reset OTP.
     *
     * <p>Always returns 200 to prevent email enumeration. If the email is
     * registered in Keycloak, a 6-digit OTP is sent. If not, the request
     * is silently ignored (no error surfaced to the client).
     */
    @PostMapping("/password-reset/request")
    public java.util.Map<String, String> passwordResetRequest(
            @Valid @RequestBody PasswordResetRequestDto request) {
        try {
            // Verify the email exists in Keycloak before sending an OTP —
            // we do this by triggering resetPassword with a dummy check,
            // but actually we just generate the OTP and Keycloak lookup
            // happens on confirm. So we send OTP unconditionally here and
            // fail gracefully on confirm if the user doesn't exist.
            otpService.generateAndSendPasswordReset(request.email());
        } catch (ResponseStatusException e) {
            if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) throw e;
            // All other errors (e.g., mail send failure) are swallowed to
            // prevent information leakage. The confirm step will fail properly.
            log.warn("Password reset OTP silently failed for {}: {}", request.email(), e.getReason());
        }
        return java.util.Map.of("status", "sent", "email", request.email());
    }

    /**
     * Step 2 — Verify the OTP and set the new password.
     *
     * <p>On success the password is changed in Keycloak and a fresh JWT is
     * returned so the frontend can log the user in immediately.
     */
    @PostMapping("/password-reset/confirm")
    public TokenResponse passwordResetConfirm(
            @Valid @RequestBody PasswordResetConfirmDto request) {
        // Verify OTP first (throws 400 if expired / wrong)
        otpService.verifyPasswordResetOtp(request.email(), request.code());

        // Reset password in Keycloak (throws 404 if user not found)
        keycloakAuthService.resetPassword(request.email(), request.newPassword());

        // Issue fresh tokens so the user is logged in immediately
        return keycloakAuthService.loginWithPassword(request.email(), request.newPassword());
    }
}
