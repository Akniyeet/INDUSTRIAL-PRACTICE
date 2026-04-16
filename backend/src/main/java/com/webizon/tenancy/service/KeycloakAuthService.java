package com.webizon.tenancy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.webizon.tenancy.api.dto.TokenResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Proxy layer that calls Keycloak's token and Admin REST endpoints on behalf
 * of the Nuxt frontend. All Keycloak client credentials stay here — they are
 * never exposed to the browser.
 *
 * <p>Three external Keycloak surfaces are used:
 * <ul>
 *   <li>{@code /realms/{realm}/protocol/openid-connect/token} — Direct Grant,
 *       Authorization Code exchange, token refresh.</li>
 *   <li>{@code /admin/realms/{realm}/users} — user creation during registration.</li>
 *   <li>{@code /realms/master/protocol/openid-connect/token} — short-lived
 *       admin token for the Admin API (master realm, admin-cli client).</li>
 * </ul>
 */
@Slf4j
@Service
public class KeycloakAuthService {

    @Value("${webizon.keycloak.server-url:http://keycloak:8180}")
    private String serverUrl;

    @Value("${webizon.keycloak.realm:webizon}")
    private String realm;

    @Value("${webizon.keycloak.client-id:webizon-frontend}")
    private String clientId;

    @Value("${webizon.keycloak.client-secret:dev_client_secret}")
    private String clientSecret;

    @Value("${webizon.keycloak.admin-username:admin}")
    private String adminUsername;

    @Value("${webizon.keycloak.admin-password:admin}")
    private String adminPassword;

    private final RestClient http = RestClient.create();

    // -----------------------------------------------------------------------
    // Public auth actions
    // -----------------------------------------------------------------------

    /** Direct Grant: exchange email + password for tokens. */
    public TokenResponse loginWithPassword(String email, String password) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("username", email);
        form.add("password", password);
        form.add("scope", "openid profile email");
        return callTokenEndpoint(form, realm);
    }

    /** Authorization Code (PKCE): exchange code for tokens. */
    public TokenResponse exchangeCode(String code, String redirectUri, String codeVerifier) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        form.add("code_verifier", codeVerifier);
        return callTokenEndpoint(form, realm);
    }

    /** Refresh: obtain a new access token using a refresh token. */
    public TokenResponse refreshToken(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("refresh_token", refreshToken);
        return callTokenEndpoint(form, realm);
    }

    /**
     * Register a new user via the Keycloak Admin REST API, then immediately
     * issue tokens via Direct Grant so the frontend can proceed without an
     * extra login step.
     */
    public TokenResponse register(String fullName, String email, String password) {
        String adminToken = getAdminToken();
        createKeycloakUser(adminToken, fullName, email, password);
        return loginWithPassword(email, password);
    }

    /**
     * Reset a user's password via the Keycloak Admin API.
     * Looks up the user by email and sets a new non-temporary credential.
     *
     * @throws ResponseStatusException 404 if no user exists with the given email
     */
    public void resetPassword(String email, String newPassword) {
        String adminToken = getAdminToken();
        String userId = getUserIdByEmail(adminToken, email);

        Map<String, Object> credential = Map.of(
                "type", "password",
                "value", newPassword,
                "temporary", false
        );

        try {
            http.put()
                    .uri(serverUrl + "/admin/realms/" + realm + "/users/" + userId + "/reset-password")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(credential)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Password reset via Admin API for user {}", userId);
        } catch (HttpClientErrorException e) {
            log.error("Keycloak password reset error: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Ошибка при изменении пароля");
        }
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private TokenResponse callTokenEndpoint(MultiValueMap<String, String> form, String targetRealm) {
        String url = serverUrl + "/realms/" + targetRealm + "/protocol/openid-connect/token";
        try {
            JsonNode body = http.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);

            if (body == null) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Empty response from Keycloak");

            return new TokenResponse(
                    body.path("access_token").asText(),
                    body.path("refresh_token").asText(null),
                    body.path("expires_in").asInt(900),
                    body.path("token_type").asText("Bearer")
            );
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Неверный email или пароль");
            }
            log.error("Keycloak token endpoint error: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Ошибка авторизации");
        }
    }

    private String getAdminToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", "admin-cli");
        form.add("username", adminUsername);
        form.add("password", adminPassword);

        JsonNode body = http.post()
                .uri(serverUrl + "/realms/master/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);

        if (body == null) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Cannot get admin token");
        return body.path("access_token").asText();
    }

    /**
     * Check whether a user with the given email address exists in Keycloak.
     * Does not throw — returns {@code false} for any lookup failure.
     */
    public boolean userExistsByEmail(String email) {
        try {
            String adminToken = getAdminToken();
            // Use URI.create() so RestClient doesn't re-encode the already-encoded query string.
            java.net.URI uri = java.net.URI.create(
                    serverUrl + "/admin/realms/" + realm
                    + "/users?email=" + java.net.URLEncoder.encode(email, java.nio.charset.StandardCharsets.UTF_8)
                    + "&exact=true");
            com.fasterxml.jackson.databind.JsonNode users = http.get()
                    .uri(uri)
                    .header("Authorization", "Bearer " + adminToken)
                    .retrieve()
                    .body(com.fasterxml.jackson.databind.JsonNode.class);
            return users != null && users.isArray() && !users.isEmpty();
        } catch (Exception e) {
            log.warn("Could not verify email existence in Keycloak for {}: {}", email, e.getMessage());
            return false;
        }
    }

    /**
     * Look up a Keycloak user by exact email address and return their Keycloak UUID.
     *
     * @throws ResponseStatusException 404 if no matching user found
     */
    private String getUserIdByEmail(String adminToken, String email) {
        try {
            java.net.URI uri = java.net.URI.create(
                    serverUrl + "/admin/realms/" + realm
                    + "/users?email=" + java.net.URLEncoder.encode(email, java.nio.charset.StandardCharsets.UTF_8)
                    + "&exact=true");
            com.fasterxml.jackson.databind.JsonNode users = http.get()
                    .uri(uri)
                    .header("Authorization", "Bearer " + adminToken)
                    .retrieve()
                    .body(com.fasterxml.jackson.databind.JsonNode.class);

            if (users == null || !users.isArray() || users.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Пользователь с таким email не найден");
            }
            return users.get(0).path("id").asText();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (HttpClientErrorException e) {
            log.error("Keycloak user lookup error: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Ошибка при поиске пользователя");
        }
    }

    private void createKeycloakUser(String adminToken, String fullName, String email, String password) {
        String[] nameParts = splitName(fullName);
        Map<String, Object> user = Map.of(
                "username", email,
                "email", email,
                "firstName", nameParts[0],
                "lastName", nameParts[1],
                "enabled", true,
                "emailVerified", true,
                "credentials", new Object[]{
                        Map.of("type", "password", "value", password, "temporary", false)
                }
        );

        try {
            http.post()
                    .uri(serverUrl + "/admin/realms/" + realm + "/users")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(user)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Пользователь с таким email уже существует");
            }
            log.error("Keycloak user creation error: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Ошибка при создании пользователя");
        }
    }

    private String[] splitName(String fullName) {
        String trimmed = fullName.trim();
        int space = trimmed.indexOf(' ');
        if (space < 0) return new String[]{trimmed, ""};
        return new String[]{trimmed.substring(0, space), trimmed.substring(space + 1)};
    }
}
