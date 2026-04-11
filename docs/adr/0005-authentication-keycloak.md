# ADR-0005: Authentication via Self-Hosted Keycloak

**Status**: Accepted
**Date**: 2026-04-10

## Context

Webizon needs enterprise-grade authentication: OIDC, multi-tenant scoping, MFA, social login, account recovery, password policies, and an admin UI — for tenants of all sizes. Building this in-house is expensive and error-prone.

## Decision

Self-host **Keycloak 24** as the identity provider. Webizon API validates JWTs issued by Keycloak; the frontend uses the standard OIDC Authorization Code flow with PKCE.

**Realm strategy**: one shared realm `webizon`, with `tenant_id` as a user attribute and JWT claim. Tenants share the realm; isolation is enforced by the `tenant_id` claim in every token.

**Why not a realm per tenant?** Realm count has practical limits, and realm management becomes a dedicated operational role at scale. For enterprise customers who demand realm isolation, we will offer it as a paid add-on later.

**Token lifetimes**:
- Access token: 15 minutes
- Refresh token: 30 days, rotating (a new refresh token is issued on every refresh)
- ID token: 15 minutes

**Token storage in the frontend**: HttpOnly + Secure + SameSite=Lax cookies. Never in `localStorage`. The callback runs server-side in Nuxt's Nitro to protect the client secret.

## JWT Claims

```json
{
  "sub": "user-uuid",
  "profile_id": "user-uuid",
  "tenant_id": "tenant-uuid",
  "role": "tenant_admin",
  "email": "alice@example.com",
  "email_verified": true,
  "name": "Alice",
  "iat": 1712000000,
  "exp": 1712000900,
  "iss": "https://auth.webizon.kz/realms/webizon",
  "aud": "webizon-frontend"
}
```

The `tenant_id` and `role` claims are set via a Keycloak **custom protocol mapper** that reads the user's attributes.

## Participant Signup (Magic Link)

Participants joining a public webinar should not be forced through a full password signup. We use **email magic-link signup**:

1. Participant enters email on the landing page
2. Webizon API generates a one-time token, stores it in Redis (TTL 15 min), and emails a link
3. Clicking the link hits `GET /api/v1/auth/magic-link?token=...`
4. API validates the token, creates a user in Keycloak (or logs in existing), issues session cookies
5. Participant is redirected into the event room

The user can later set a password to upgrade to a full account.

## Keycloak Admin UI Access

- Only `platform_admin` users (Webizon staff) have Keycloak admin access
- `tenant_owner` users manage their own tenant's users via the Webizon admin UI, which calls Keycloak Admin REST API behind the scenes
- Tenant users are scoped by a Keycloak **group** `/tenants/{tenant_id}`

## Consequences

**Positive**
- Mature, battle-tested auth. We do not roll our own crypto.
- Built-in MFA, social login, password reset flows.
- OIDC is a standard; clients and libraries exist everywhere.
- Keycloak admin UI gives us a debugging and operational surface for free.

**Negative**
- Keycloak is a heavy service (JVM, requires its own DB).
- Operational burden: backups, upgrades, clustering for HA.
- Custom protocol mapper for tenant_id must be maintained as a Keycloak provider or script.
- Migration to another IdP later is non-trivial.

## Alternatives Considered

### Alternative 1: Auth0 / Okta / WorkOS (managed)
- **Rejected for MVP**: per-user pricing becomes expensive at 100k+ participants. Data residency may be a concern for KZ customers.

### Alternative 2: Rolling our own with Spring Security + PostgreSQL
- **Rejected**: re-implementing MFA, social login, account recovery, audit logging is months of work and a security liability.

### Alternative 3: AWS Cognito
- **Rejected**: vendor lock-in, weaker multi-tenant story, less flexible UI customization.

## References
- Keycloak docs: https://www.keycloak.org/documentation
- OIDC spec: https://openid.net/specs/openid-connect-core-1_0.html
- OAuth 2.0 for browser-based apps: https://datatracker.ietf.org/doc/html/draft-ietf-oauth-browser-based-apps
