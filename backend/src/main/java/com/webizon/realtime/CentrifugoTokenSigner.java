package com.webizon.realtime;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Issues Centrifugo v5 connect and subscribe JWTs.
 *
 * <p>Centrifugo accepts plain HS256 JWTs for both client authentication
 * ({@code connect} tokens) and channel authorization ({@code subscribe}
 * tokens). The shared secret lives in {@link CentrifugoProperties} and
 * must match Centrifugo's {@code token_hmac_secret_key} config.
 *
 * <p><strong>Connect token</strong> — minted once per websocket connection.
 * Claims we set:
 * <ul>
 *   <li>{@code sub} — the user's {@code profile_id} (UUID as string); this
 *       is what locks private {@code '#'}-channels to the correct client</li>
 *   <li>{@code exp} — short TTL (see {@link CentrifugoProperties#tokenTtlSeconds});
 *       clients refresh on reconnect via {@code /api/v1/realtime/connect-token}</li>
 *   <li>{@code iat} — issued-at, for debugging and clock-skew diagnostics</li>
 *   <li>{@code info} — an opaque JSON object Centrifugo echoes back in
 *       presence data; we put {@code tenantId} and {@code role} so the
 *       front-end can render "Moderator" badges without an extra fetch</li>
 * </ul>
 *
 * <p><strong>Subscribe token</strong> — minted per (user, channel) pair
 * when {@link ChannelAccessService} has approved the request. Claims:
 * <ul>
 *   <li>{@code sub} — same {@code profile_id} as the connect token; the
 *       Centrifugo server rejects mismatches</li>
 *   <li>{@code channel} — exact channel name (required by Centrifugo)</li>
 *   <li>{@code exp} — same short TTL; expired subscribe tokens force the
 *       client to re-request, which re-runs the authorization check</li>
 * </ul>
 *
 * <p>This component is stateless and thread-safe; the {@link SecretKey}
 * is built once in the constructor so every call skips the HMAC key
 * derivation overhead.
 */
@Component
public class CentrifugoTokenSigner {

    private final SecretKey signingKey;
    private final long tokenTtlSeconds;

    public CentrifugoTokenSigner(CentrifugoProperties properties) {
        byte[] keyBytes = properties.tokenHmacSecret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            // HS256 requires ≥ 256-bit keys. Fail fast at startup rather
            // than producing tokens Centrifugo silently rejects at runtime.
            throw new IllegalStateException(
                    "webizon.centrifugo.token-hmac-secret must be at least 32 bytes (256 bits) for HS256;"
                            + " got " + keyBytes.length + " bytes"
            );
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.tokenTtlSeconds = properties.tokenTtlSeconds();
    }

    /**
     * Mint a connect token for a just-authenticated websocket client.
     *
     * @param userId   the authenticated user's profile id
     * @param tenantId the tenant the user is currently acting inside
     * @param role     the user's role string (e.g. {@code "MODERATOR"})
     * @return a signed JWT ready to be handed to the front-end
     */
    public SignedToken connectToken(UUID userId, UUID tenantId, String role) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(tokenTtlSeconds);

        Map<String, Object> info = new HashMap<>();
        info.put("tenantId", tenantId.toString());
        info.put("role", role);

        String jwt = Jwts.builder()
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim("info", info)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();

        return new SignedToken(jwt, expiresAt);
    }

    /**
     * Mint a subscribe token for a specific channel.
     *
     * <p>Callers MUST have already verified via {@link ChannelAccessService}
     * that this user is allowed on this channel — this method only signs,
     * it does not authorize.
     */
    public SignedToken subscribeToken(UUID userId, String channel) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(tokenTtlSeconds);

        String jwt = Jwts.builder()
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim("channel", channel)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();

        return new SignedToken(jwt, expiresAt);
    }

    /**
     * A signed JWT plus its absolute expiration instant.
     *
     * <p>We hand the expiration back to the caller so the front-end can
     * schedule a refresh a few seconds before the token would be rejected
     * by Centrifugo — no need to decode the JWT twice.
     */
    public record SignedToken(String token, Instant expiresAt) {}
}
