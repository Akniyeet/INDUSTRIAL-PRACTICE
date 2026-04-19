package com.webizon.realtime;

import com.webizon.events.model.Session;
import com.webizon.events.repo.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * Authorisation decisions for Centrifugo channel subscriptions.
 *
 * <p>Every subscribe-token request the {@code RealtimeController} receives
 * runs through {@link #authorize(UUID, UUID, String, String)}. The service
 * returns a {@link Decision} rather than throwing: callers translate
 * {@code DENY} into an HTTP 403 with a {@code ProblemDetail} and never
 * leak the reason back to the client.
 *
 * <p>The rules are intentionally conservative. It is cheaper to deny a
 * subscribe and have the front-end retry than to accidentally broadcast
 * another tenant's chat to a user.
 *
 * <h2>Rules (in order)</h2>
 * <ol>
 *   <li>The channel must be parseable by {@link ChannelNameFactory}.</li>
 *   <li>The parsed {@code tenantId} must match the caller's JWT tenant.
 *       We do not support cross-tenant subscribe: public landing traffic
 *       hits REST endpoints, not real-time channels.</li>
 *   <li>The referenced session must exist inside the caller's tenant
 *       (verified via the tenant-aware repository — RLS enforces this
 *       even if the application check is bypassed).</li>
 *   <li>For {@link ChannelKind#CONTROL}: the caller must hold a
 *       privileged role (owner / admin / moderator / presenter).
 *       Analysts and unknown roles are denied — CONTROL carries
 *       moderator actions that must never be visible to read-only
 *       or anonymous audiences.</li>
 *   <li>For any other kind: any authenticated user inside the tenant
 *       is allowed. Finer-grained per-user bans (e.g. room-ban) are
 *       enforced by the chat policy chain at publish time, not here.</li>
 * </ol>
 *
 * <h2>Why role comparison is string-based</h2>
 * <p>The {@code role} claim is minted by our Keycloak token customiser as
 * a single string ({@code "TENANT_OWNER"}, {@code "TENANT_MODERATOR"},
 * etc). We match against a {@link Set} of allowed values rather than
 * importing the {@code MembershipRole} enum so that token mismatch on
 * old/new deployments is a soft failure (unknown role → deny) instead of
 * an enum-parse exception that 500s the whole endpoint.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChannelAccessService {

    /**
     * Roles permitted to subscribe to the CONTROL channel. Mirrors the
     * @PreAuthorize check on the moderation REST endpoints — keep them
     * in sync when adding a new role.
     */
    private static final Set<String> CONTROL_ROLES = Set.of(
            "TENANT_OWNER",
            "TENANT_ADMIN",
            "TENANT_MODERATOR",
            "TENANT_PRESENTER"
    );

    private final ChannelNameFactory channelNameFactory;
    private final SessionRepository sessionRepository;

    /**
     * Decide whether {@code userId} (acting inside {@code callerTenantId}
     * with the single-role shortcut claim {@code callerRole}) may
     * subscribe to {@code channel}.
     *
     * <p>Read-only transactional boundary so the session lookup respects
     * {@code TenantContext} / RLS without accidentally participating in
     * an upstream writer transaction.
     */
    @Transactional(readOnly = true)
    public Decision authorize(UUID userId, UUID callerTenantId, String callerRole, String channel) {
        if (userId == null || callerTenantId == null) {
            return Decision.deny("unauthenticated");
        }

        ChannelNameFactory.ParsedChannel parsed = channelNameFactory.parse(channel);
        if (parsed == null) {
            return Decision.deny("malformed-channel");
        }

        if (!callerTenantId.equals(parsed.tenantId())) {
            log.debug("Cross-tenant subscribe denied: caller={} channel-tenant={}",
                    callerTenantId, parsed.tenantId());
            return Decision.deny("cross-tenant");
        }

        Session session = sessionRepository.findById(parsed.sessionId()).orElse(null);
        if (session == null) {
            return Decision.deny("session-not-found");
        }

        if (parsed.kind() == ChannelKind.CONTROL) {
            if (callerRole == null || !CONTROL_ROLES.contains(callerRole)) {
                return Decision.deny("control-requires-moderator");
            }
        }

        return Decision.allow(parsed);
    }

    /**
     * Immutable verdict: either allowed (carries the parsed channel for
     * the caller's convenience) or denied (carries an opaque reason tag
     * for logging, never surfaced to clients).
     */
    public record Decision(boolean allowed, ChannelNameFactory.ParsedChannel channel, String denyReason) {

        public static Decision allow(ChannelNameFactory.ParsedChannel channel) {
            return new Decision(true, channel, null);
        }

        public static Decision deny(String reason) {
            return new Decision(false, null, reason);
        }
    }
}
