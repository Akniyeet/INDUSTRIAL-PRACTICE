package com.webizon.tenancy.service;

import com.webizon.auth.CurrentUser;
import com.webizon.notifications.model.NotificationKind;
import com.webizon.notifications.service.NotificationRequest;
import com.webizon.notifications.service.NotificationService;
import com.webizon.tenancy.AllowCrossTenant;
import com.webizon.tenancy.TenantContext;
import com.webizon.tenancy.api.dto.InviteAcceptResponse;
import com.webizon.tenancy.api.dto.InviteCreateRequest;
import com.webizon.tenancy.api.dto.InviteCreateResponse;
import com.webizon.tenancy.api.dto.InviteResponse;
import com.webizon.tenancy.model.InviteStatus;
import com.webizon.tenancy.model.MembershipRole;
import com.webizon.tenancy.model.MembershipStatus;
import com.webizon.tenancy.model.Tenant;
import com.webizon.tenancy.model.TenantInvite;
import com.webizon.tenancy.model.TenantUser;
import com.webizon.tenancy.model.User;
import com.webizon.tenancy.repo.TenantInviteRepository;
import com.webizon.tenancy.repo.TenantRepository;
import com.webizon.tenancy.repo.TenantUserRepository;
import com.webizon.tenancy.repo.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Business logic for tenant invites.
 *
 * <h2>Three verbs</h2>
 * <ul>
 *   <li>{@link #create(InviteCreateRequest)} — an existing tenant
 *       admin creates a PENDING invite for a future staff member. A
 *       fresh bearer token is generated, hashed, and persisted; the
 *       plaintext is returned to the admin exactly once, wrapped in
 *       an {@link InviteCreateResponse}, and enqueued for email
 *       delivery as a {@link NotificationKind#TENANT_INVITE}. Tenant
 *       scope: the current tenant, enforced by {@code TenantContext}
 *       + RLS.</li>
 *   <li>{@link #revoke(UUID)} — an admin cancels a still-PENDING
 *       invite before acceptance. Revoking a terminal invite is a
 *       no-op (we return the existing row unchanged) because the
 *       admin UI is racy by nature and the idempotent behaviour is
 *       the right UX.</li>
 *   <li>{@link #accept(String)} — the recipient clicks their one-time
 *       link. This is the ONLY cross-tenant path: the recipient
 *       carries no {@code tenant_id} claim yet, so we look the invite
 *       up via the dedicated {@code app.invite_lookup = 'on'} RLS
 *       escape policy, verify the JWT email matches the frozen
 *       target email, verify the link is still actionable (PENDING
 *       and not past {@code expires_at}), pivot into the invite's
 *       tenant scope, create the matching {@link TenantUser} row in
 *       ACTIVE state, stamp the invite, and enqueue a
 *       {@link NotificationKind#WELCOME} email. The whole flow runs
 *       in a single outer transaction so a mid-way crash rolls
 *       everything back cleanly.</li>
 * </ul>
 *
 * <h2>Why invites live in their own table</h2>
 * See the design comment at the top of {@code V012__tenant_invites.sql}
 * — in short, {@code tenant_users.user_id} is {@code NOT NULL} FK to
 * {@code users(id)}, so a placeholder row for an email that is not
 * yet a Webizon user would violate referential integrity. Keeping
 * invites separate also lets admins revoke and reissue cleanly
 * without polluting the membership table with cancelled rows.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InviteService {

    private final TenantInviteRepository inviteRepository;
    private final TenantUserRepository tenantUserRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final InviteTokenGenerator tokenGenerator;
    private final NotificationService notificationService;

    @PersistenceContext
    private EntityManager entityManager;

    /** How long a new invite stays clickable before expiring. */
    @Value("${webizon.invites.ttl:7d}")
    private Duration inviteTtl;

    /** Public base URL of the frontend, used to render the accept link. */
    @Value("${webizon.frontend.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    // ==================================================================
    // CREATE
    // ==================================================================

    /**
     * Create a new PENDING invite for the current tenant. Idempotent
     * per (tenant, email) while the previous invite is still pending —
     * a second call within the TTL returns the SAME row, so double-click
     * on the admin UI does not pile up duplicate tokens.
     */
    @Transactional
    public InviteCreateResponse create(InviteCreateRequest request) {
        UUID tenantId = TenantContext.getRequired();
        UUID inviterUserId = requireLocalUserId();

        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);

        // Refuse to invite someone who is already a member of this
        // tenant. We look the target email up in the global users
        // table (if it exists) and, if we find a Webizon user, check
        // their membership. If the email is not yet in `users` they
        // obviously cannot be a member yet.
        Optional<User> targetUser = userRepository.findByEmailIgnoreCase(normalizedEmail);
        if (targetUser.isPresent()) {
            Optional<TenantUser> existingMembership =
                    tenantUserRepository.findByUserId(targetUser.get().getId());
            if (existingMembership.isPresent()
                    && existingMembership.get().getStatus() == MembershipStatus.ACTIVE) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "User " + normalizedEmail + " is already a member of this tenant");
            }
        }

        // Idempotency short-circuit: if an actionable invite already
        // exists for this email, return it instead of minting a
        // duplicate. The partial unique index on
        // (tenant_id, email) WHERE status='PENDING' is the
        // authoritative guard; this check keeps the hot path free of
        // caught exceptions.
        Optional<TenantInvite> alreadyPending =
                inviteRepository.findByEmailAndStatus(normalizedEmail, InviteStatus.PENDING);
        if (alreadyPending.isPresent()) {
            TenantInvite existing = alreadyPending.get();
            if (existing.isActionable(Instant.now())) {
                // Return the existing row with NO token — the admin
                // already has the original email in flight, and we
                // cannot regenerate the plaintext from the hash.
                // They can revoke + re-create if they want a new
                // bearer.
                log.debug("Tenant {}: re-using existing pending invite {} for {}",
                        tenantId, existing.getId(), normalizedEmail);
                return new InviteCreateResponse(InviteResponse.from(existing), null);
            }
            // Pending but expired — let the sweeper flip it to
            // EXPIRED on its next tick, and issue a fresh invite now
            // alongside it. The partial unique index only covers
            // PENDING rows so this does not conflict.
        }

        InviteTokenGenerator.Token token = tokenGenerator.generate();

        TenantInvite invite = new TenantInvite();
        invite.setTokenHash(token.hash());
        invite.setEmail(normalizedEmail);
        invite.setRole(request.role());
        invite.setStatus(InviteStatus.PENDING);
        invite.setInvitedByUserId(inviterUserId);
        invite.setExpiresAt(Instant.now().plus(inviteTtl));
        invite.setMessage(request.message());

        TenantInvite saved;
        try {
            saved = inviteRepository.save(invite);
        } catch (DataIntegrityViolationException dupe) {
            // Lost the race against another admin creating the same
            // invite. Re-read the pending row and return it with no
            // token (see the comment above).
            TenantInvite existing = inviteRepository.findByEmailAndStatus(normalizedEmail, InviteStatus.PENDING)
                    .orElseThrow(() -> dupe);
            log.info("Tenant {}: invite race for {} lost; returning existing row {}",
                    tenantId, normalizedEmail, existing.getId());
            return new InviteCreateResponse(InviteResponse.from(existing), null);
        }

        // Resolve tenant display name for the email body — we do
        // this with the tenant already in scope, so the read goes
        // through RLS normally.
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalStateException(
                        "Current tenant " + tenantId + " vanished between context set and invite create"));

        String acceptUrl = buildAcceptUrl(token.plaintext());

        // Enqueue the invite email. The outbox insert runs inside
        // the same transaction as the invite insert, so a crash
        // before commit rolls back both atomically.
        notificationService.enqueue(NotificationRequest.email(
                /* profileId */ null,
                NotificationKind.TENANT_INVITE,
                normalizedEmail,
                renderInviteSubject(tenant),
                renderInviteBody(tenant, request.role(), request.message(), acceptUrl),
                "tenant-invite:" + saved.getId()));

        log.info("Tenant {}: created invite {} for {} (role={})",
                tenantId, saved.getId(), normalizedEmail, request.role());

        return new InviteCreateResponse(InviteResponse.from(saved), acceptUrl);
    }

    // ==================================================================
    // LIST / GET
    // ==================================================================

    @Transactional(readOnly = true)
    public Page<TenantInvite> list(InviteStatus filter, Pageable pageable) {
        TenantContext.getRequired();
        if (filter == null) {
            return inviteRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return inviteRepository.findAllByStatusOrderByCreatedAtDesc(filter, pageable);
    }

    @Transactional(readOnly = true)
    public TenantInvite requireById(UUID inviteId) {
        TenantContext.getRequired();
        return inviteRepository.findById(inviteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Invite not found: " + inviteId));
    }

    // ==================================================================
    // REVOKE
    // ==================================================================

    /**
     * Cancel a still-PENDING invite. Idempotent: revoking an already
     * terminal row returns it unchanged.
     */
    @Transactional
    public TenantInvite revoke(UUID inviteId) {
        UUID tenantId = TenantContext.getRequired();
        UUID actorUserId = requireLocalUserId();

        TenantInvite invite = inviteRepository.findById(inviteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Invite not found: " + inviteId));

        if (invite.getStatus() != InviteStatus.PENDING) {
            // Idempotent no-op — a double-click in the admin UI
            // hits this path and should not error out.
            return invite;
        }

        invite.markRevoked(actorUserId, Instant.now());
        TenantInvite saved = inviteRepository.save(invite);
        log.info("Tenant {}: revoked invite {} (by user {})", tenantId, inviteId, actorUserId);
        return saved;
    }

    // ==================================================================
    // ACCEPT — the cross-tenant path
    // ==================================================================

    /**
     * Claim an invite via its one-time plaintext bearer token. The
     * caller is the already-authenticated JWT subject (Keycloak
     * refresh has minted them a basic user token with no
     * {@code tenant_id} claim yet). The flow is:
     *
     * <ol>
     *   <li>Hash the token and look the invite up via the SELECT-only
     *       escape policy ({@code app.invite_lookup = 'on'}).</li>
     *   <li>Verify PENDING and not expired.</li>
     *   <li>Verify the JWT email matches the frozen target email
     *       (case-insensitive). This is the token-replay guard — a
     *       leaked URL opened by a different user is rejected.</li>
     *   <li>Look up the local {@link User} row (it must already
     *       exist because {@code /auth/bootstrap} is called on every
     *       login and we refuse to accept invites for users who have
     *       never hit the bootstrap endpoint).</li>
     *   <li>Pivot into the invite's tenant scope via
     *       {@code TenantContext.runWith} + {@code SET LOCAL
     *       app.current_tenant} so Hibernate {@code @TenantId} and
     *       RLS see the correct identifier.</li>
     *   <li>Create the {@link TenantUser} membership row in ACTIVE
     *       state. If a suspended/removed row already exists we
     *       re-activate it instead of inserting a duplicate.</li>
     *   <li>Stamp the invite as ACCEPTED with the new membership id.</li>
     *   <li>Enqueue a {@link NotificationKind#WELCOME} email.</li>
     * </ol>
     */
    @Transactional
    @AllowCrossTenant(reason = "Accept flow: recipient carries no tenant_id claim yet; service pivots to the invite's tenant after verifying the token.")
    public InviteAcceptResponse accept(String plaintextToken) {
        if (plaintextToken == null || plaintextToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing invite token");
        }

        String tokenHash = tokenGenerator.hash(plaintextToken);

        // Step 1: escape-policy lookup. Flip the flag on the tx
        // connection, read the row, flip the flag off — any
        // subsequent query on this connection is back on the strict
        // tenant_isolation policy.
        TenantInvite invite = findInviteInLookupMode(tokenHash);
        if (invite == null) {
            // Don't leak whether a hash ever existed — an attacker
            // grinding random tokens gets the same answer as
            // someone with a revoked link.
            throw new ResponseStatusException(HttpStatus.GONE, "Invite link is invalid or has already been used");
        }

        Instant now = Instant.now();

        if (invite.getStatus() != InviteStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.GONE,
                    "Invite is " + invite.getStatus().name().toLowerCase(Locale.ROOT));
        }
        if (invite.isExpired(now)) {
            // Stamp the row expired before we bail so the admin UI
            // reflects reality on the next refresh. Needs the
            // invite's tenant in scope for the UPDATE to pass RLS.
            markExpiredInTenantScope(invite);
            throw new ResponseStatusException(HttpStatus.GONE, "Invite link has expired");
        }

        // Step 2: token-replay check. The accepter's JWT email must
        // match the frozen target email. Case-insensitive because
        // Keycloak normalizes emails lower-case but some IdPs
        // upstream may not.
        String jwtEmail;
        try {
            jwtEmail = CurrentUser.email();
        } catch (IllegalStateException noEmail) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Accepting an invite requires an authenticated user with an email claim");
        }
        if (!invite.getEmail().equalsIgnoreCase(jwtEmail)) {
            log.warn("Invite {}: token replay attempt — frozen email={} accepter email={}",
                    invite.getId(), invite.getEmail(), jwtEmail);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This invite was addressed to a different email");
        }

        // Step 3: the accepter must already exist as a Webizon user.
        // /auth/bootstrap is called on every login and creates the
        // mirror row; if it is missing, something is wrong with the
        // frontend flow and we bail loudly instead of silently
        // auto-creating it here.
        UUID keycloakId = CurrentUser.keycloakId();
        User accepter = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "User must complete /auth/bootstrap before accepting an invite"));

        // Step 4: pivot into the invite's tenant. From this point on
        // every JPA operation is tenant-scoped as if the accepter
        // had a fresh JWT with the right tenant_id claim.
        UUID targetTenantId = invite.getTenantId();
        UUID[] createdMembershipId = new UUID[1];

        TenantContext.runWith(targetTenantId, () -> {
            entityManager.createNativeQuery(
                    "SET LOCAL app.current_tenant = '" + targetTenantId + "'")
                    .executeUpdate();

            // Step 5: create-or-reactivate the membership row.
            Optional<TenantUser> existingMembership =
                    tenantUserRepository.findByUserId(accepter.getId());

            TenantUser membership;
            if (existingMembership.isPresent()) {
                membership = existingMembership.get();
                if (membership.getStatus() == MembershipStatus.ACTIVE) {
                    // Already a member — treat as idempotent accept.
                    // We still mark the invite ACCEPTED so the admin
                    // sees the closed loop, but do not create a
                    // duplicate row.
                    log.info("Tenant {}: invite {} accepted by existing ACTIVE member {}",
                            targetTenantId, invite.getId(), accepter.getId());
                } else {
                    membership.setStatus(MembershipStatus.ACTIVE);
                    membership.setRole(invite.getRole());
                    membership.setJoinedAt(Instant.now());
                    membership.setInvitedByUserId(invite.getInvitedByUserId());
                    membership = tenantUserRepository.save(membership);
                    log.info("Tenant {}: reactivated membership {} via invite {}",
                            targetTenantId, membership.getId(), invite.getId());
                }
            } else {
                membership = TenantUser.builder()
                        .userId(accepter.getId())
                        .role(invite.getRole())
                        .status(MembershipStatus.ACTIVE)
                        .invitedByUserId(invite.getInvitedByUserId())
                        .joinedAt(Instant.now())
                        .build();
                membership = tenantUserRepository.save(membership);
                log.info("Tenant {}: created membership {} via invite {}",
                        targetTenantId, membership.getId(), invite.getId());
            }
            createdMembershipId[0] = membership.getId();

            // Step 6: stamp the invite.
            invite.markAccepted(accepter.getId(), membership.getId(), Instant.now());
            inviteRepository.save(invite);

            // Step 7: WELCOME email. Enqueued inside the same outer
            // tx so a rollback scrubs it atomically with the
            // membership insert.
            Tenant tenant = tenantRepository.findById(targetTenantId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Target tenant " + targetTenantId + " vanished during accept"));
            notificationService.enqueue(NotificationRequest.email(
                    /* profileId */ null,
                    NotificationKind.WELCOME,
                    accepter.getEmail(),
                    renderWelcomeSubject(tenant),
                    renderWelcomeBody(tenant, accepter, invite.getRole()),
                    "welcome:" + invite.getId()));
        });

        Tenant tenant = lookupTenantGlobal(targetTenantId);
        return new InviteAcceptResponse(
                targetTenantId,
                tenant.getSlug(),
                tenant.getDisplayName(),
                invite.getRole(),
                createdMembershipId[0]);
    }

    // ==================================================================
    // Helpers — cross-tenant bits of the accept flow
    // ==================================================================

    /**
     * Read a single invite by its hashed token using the SELECT-only
     * escape policy. Flips {@code app.invite_lookup = 'on'} for the
     * duration of the lookup and unconditionally flips it back off in
     * the {@code finally} block so no subsequent query on this
     * connection accidentally rides the escape hatch.
     */
    private TenantInvite findInviteInLookupMode(String tokenHash) {
        entityManager.createNativeQuery("SET LOCAL app.invite_lookup = 'on'").executeUpdate();
        try {
            return inviteRepository.findByTokenHashInLookupMode(tokenHash).orElse(null);
        } finally {
            // Belt-and-braces clear. `SET LOCAL` is scoped to the
            // current transaction and resets automatically at
            // commit/rollback, so this is defence in depth rather
            // than a correctness requirement. We swallow any
            // failure here so it cannot mask a real exception from
            // the try body.
            try {
                entityManager.createNativeQuery("SET LOCAL app.invite_lookup = 'off'").executeUpdate();
            } catch (Exception suppressed) {
                log.debug("Clearing app.invite_lookup failed (tx likely already aborted): {}",
                        suppressed.getMessage());
            }
        }
    }

    /**
     * Stamp an already-found invite as EXPIRED. Requires the
     * invite's tenant in scope because the UPDATE path is still
     * governed by the strict tenant-isolation policy — the escape
     * hatch is SELECT-only by design.
     */
    private void markExpiredInTenantScope(TenantInvite invite) {
        UUID targetTenantId = invite.getTenantId();
        TenantContext.runWith(targetTenantId, () -> {
            entityManager.createNativeQuery(
                    "SET LOCAL app.current_tenant = '" + targetTenantId + "'")
                    .executeUpdate();
            invite.markExpired();
            inviteRepository.save(invite);
        });
    }

    /**
     * Read the tenant display metadata after the accept has
     * committed. Tenants live in the GLOBAL {@code tenants} table so
     * RLS does not fire — no pivot needed.
     */
    @AllowCrossTenant(reason = "Tenants are a global table; reading the accepted tenant's display metadata is safe.")
    private Tenant lookupTenantGlobal(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalStateException(
                        "Accepted tenant " + tenantId + " vanished before response render"));
    }

    /**
     * Resolve the caller's local {@link User#getId()} for audit
     * columns. Callers of the admin-facing methods are always
     * tenant-staff so the bootstrap mirror is guaranteed to exist.
     */
    private UUID requireLocalUserId() {
        UUID keycloakId = CurrentUser.keycloakId();
        return userRepository.findByKeycloakId(keycloakId)
                .map(User::getId)
                .orElseThrow(() -> new IllegalStateException(
                        "No local user mirror for Keycloak subject " + keycloakId
                                + "; /auth/bootstrap must be called before invite operations."));
    }

    // ==================================================================
    // Rendering — kept small and inline; promotion to a template
    // engine is a later concern.
    // ==================================================================

    private String buildAcceptUrl(String plaintextToken) {
        String base = frontendBaseUrl;
        if (base == null || base.isBlank()) {
            base = "http://localhost:3000";
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/invite/accept/" + plaintextToken;
    }

    private String renderInviteSubject(Tenant tenant) {
        return "Вас пригласили в команду «" + tenant.getDisplayName() + "» на Webizon";
    }

    private String renderInviteBody(Tenant tenant,
                                    MembershipRole role,
                                    String adminMessage,
                                    String acceptUrl) {
        StringBuilder body = new StringBuilder(768);
        body.append("Здравствуйте!\n\n")
            .append("Администратор рабочего пространства «").append(tenant.getDisplayName())
            .append("» пригласил вас присоединиться к команде Webizon в роли ")
            .append(humanizeRole(role)).append(".\n\n");

        if (adminMessage != null && !adminMessage.isBlank()) {
            body.append("Сообщение от администратора:\n")
                .append("  ").append(adminMessage.trim()).append("\n\n");
        }

        body.append("Чтобы принять приглашение, откройте ссылку ниже и войдите в свой аккаунт\n")
            .append("Webizon. Ссылка одноразовая и привязана к вашему e-mail — её нельзя\n")
            .append("переслать другому получателю.\n\n")
            .append("  ").append(acceptUrl).append("\n\n")
            .append("Ссылка действительна ").append(humanizeTtl(inviteTtl)).append(".\n")
            .append("Если вы не ожидали это приглашение — просто проигнорируйте письмо.\n\n")
            .append("— Webizon");
        return body.toString();
    }

    private String renderWelcomeSubject(Tenant tenant) {
        return "Добро пожаловать в «" + tenant.getDisplayName() + "»";
    }

    private String renderWelcomeBody(Tenant tenant, User user, MembershipRole role) {
        String greeting = user.getFullName() != null && !user.getFullName().isBlank()
                ? user.getFullName().trim()
                : "коллега";
        StringBuilder body = new StringBuilder(512);
        body.append("Здравствуйте, ").append(greeting).append("!\n\n")
            .append("Вы теперь участник рабочего пространства «").append(tenant.getDisplayName())
            .append("» на Webizon в роли ").append(humanizeRole(role)).append(".\n\n")
            .append("Откройте приложение, чтобы настроить первое событие, пригласить\n")
            .append("зрителей и проверить расписание эфиров. Если есть вопросы по\n")
            .append("работе платформы — напишите в поддержку прямо из интерфейса.\n\n")
            .append("— Команда Webizon");
        return body.toString();
    }

    private static String humanizeRole(MembershipRole role) {
        return switch (role) {
            case TENANT_OWNER     -> "владельца";
            case TENANT_ADMIN     -> "администратора";
            case TENANT_MODERATOR -> "модератора";
            case TENANT_PRESENTER -> "ведущего";
            case TENANT_ANALYST   -> "аналитика";
        };
    }

    private static String humanizeTtl(Duration ttl) {
        long days = ttl.toDays();
        if (days >= 1) {
            // Russian noun case agreement: 1 день, 2-4 дня, 5+ дней.
            long tail = days % 10;
            long teen = (days % 100) / 10;
            String noun;
            if (teen == 1) {
                noun = " дней";
            } else if (tail == 1) {
                noun = " день";
            } else if (tail >= 2 && tail <= 4) {
                noun = " дня";
            } else {
                noun = " дней";
            }
            return days + noun;
        }
        long hours = Math.max(1, ttl.toHours());
        return hours + " ч";
    }
}
