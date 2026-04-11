package com.webizon.tenancy.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generates one-time bearer tokens for tenant invites and hashes
 * them for at-rest storage.
 *
 * <p>Design:
 * <ul>
 *   <li><strong>Plaintext only ever exists in memory once.</strong>
 *       {@link #generate()} returns a {@link Token} whose
 *       {@code plaintext} is handed back to the admin exactly once
 *       (wrapped into the create-invite response / email body) and
 *       then forgotten. The DB only ever stores the hash.</li>
 *   <li><strong>256-bit entropy.</strong> The default 32-byte random
 *       payload is base64url-encoded into a 43-char ASCII string,
 *       which fits comfortably in a URL without percent-escaping and
 *       is infeasible to guess.</li>
 *   <li><strong>SHA-256 at rest.</strong> An attacker with a DB dump
 *       cannot replay live invite links — the bearer never touches
 *       disk. SHA-256 is safe here because the preimage space (32
 *       random bytes) is itself cryptographically strong; a slow
 *       password-style hash (bcrypt/argon2) would buy nothing and
 *       would cost noticeable CPU on every accept call.</li>
 * </ul>
 *
 * <p>Hash encoding is lowercase hex (64 chars) to match the
 * {@code tenant_invites.token_hash CHAR(64)} column definition.
 */
@Component
public class InviteTokenGenerator {

    private final SecureRandom random = new SecureRandom();
    private final int tokenBytes;

    public InviteTokenGenerator(
            @Value("${webizon.invites.token-bytes:32}") int tokenBytes) {
        if (tokenBytes < 16) {
            throw new IllegalArgumentException(
                    "webizon.invites.token-bytes must be at least 16 (128 bits); was: " + tokenBytes);
        }
        this.tokenBytes = tokenBytes;
    }

    /**
     * Produce a fresh token and its hash in a single call. The caller
     * MUST keep {@link Token#plaintext} off the DB and out of any
     * persistent log — the hash is the only durable artifact.
     */
    public Token generate() {
        byte[] raw = new byte[tokenBytes];
        random.nextBytes(raw);
        String plaintext = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        String hash = sha256Hex(plaintext);
        return new Token(plaintext, hash);
    }

    /**
     * Deterministic hash used by the accept path to look an invite up
     * by the token a recipient presents.
     */
    public String hash(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException("plaintext token must not be blank");
        }
        return sha256Hex(plaintext);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is mandated by every JVM shipped in the last
            // two decades — if this fires the JRE is broken beyond
            // any meaningful recovery.
            throw new IllegalStateException("SHA-256 not available on this JVM", ex);
        }
    }

    /**
     * Pair of plaintext + hash. The plaintext is only exposed at
     * create time and must never be persisted.
     */
    public record Token(String plaintext, String hash) {}
}
