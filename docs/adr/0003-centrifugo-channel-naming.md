# ADR-0003: Centrifugo Channel Naming Convention

**Status**: Accepted
**Date**: 2026-04-10

## Context

Centrifugo channels are the address space for our real-time layer. Bad naming invites accidental cross-tenant leakage, makes subscriptions ambiguous, and complicates operational debugging.

## Decision

All channels follow this pattern:

```
tenant.{tenantId}.session.{sessionId}.{purpose}
```

**Rules:**
1. **Tenant prefix is mandatory.** Every channel starts with `tenant.{tenantId}`. Centrifugo config only permits channels in the `tenant.*` namespace.
2. **Numeric or UUID IDs only.** No human-readable slugs in channel names (they can change).
3. **Purpose suffixes** are enumerated:
   - `chat` — user chat messages + system chat events
   - `system` — session lifecycle (started, ended, viewer count, admin messages)
   - `timeline` — CTA show/hide and timeline actions
   - `presence` — presence count updates
4. **Private user channels** use Centrifugo's `$` prefix: `$tenant.{tenantId}.user.{userId}`. Only the owning user may subscribe.
5. **Admin-only channels** use suffix `.admin`: `tenant.{tenantId}.session.{sessionId}.admin` — subscribe permission requires `role in (tenant_moderator, tenant_admin, tenant_owner)`.

**Subscription permissions** are enforced by the Centrifugo connection token (JWT signed by the API). The token's `channels` claim lists the exact channels the user is allowed to subscribe to for this session.

## Consequences

**Positive**
- Impossible to accidentally subscribe to another tenant's channel (token claim scopes it).
- Operational tools (Centrifugo dashboard, logs) show tenant and session at a glance.
- Adding new purposes is a matter of adding a constant, not redesigning the scheme.

**Negative**
- Channel names are long (but Centrifugo handles long names efficiently).
- Regenerating a token on role change (promoting a user to moderator mid-session) requires the client to reconnect or request a new token.

## Code

```java
public final class ChannelNameFactory {
    private ChannelNameFactory() {}

    public static String chat(UUID tenantId, long sessionId) {
        return "tenant." + tenantId + ".session." + sessionId + ".chat";
    }

    public static String system(UUID tenantId, long sessionId) {
        return "tenant." + tenantId + ".session." + sessionId + ".system";
    }

    public static String timeline(UUID tenantId, long sessionId) {
        return "tenant." + tenantId + ".session." + sessionId + ".timeline";
    }

    public static String presence(UUID tenantId, long sessionId) {
        return "tenant." + tenantId + ".session." + sessionId + ".presence";
    }

    public static String adminOnly(UUID tenantId, long sessionId) {
        return "tenant." + tenantId + ".session." + sessionId + ".admin";
    }

    public static String privateUser(UUID tenantId, UUID userId) {
        return "$tenant." + tenantId + ".user." + userId;
    }
}
```

No channel name may be constructed outside `ChannelNameFactory`. This is enforced by a Checkstyle rule and a code review checklist.

## References
- Centrifugo channels: https://centrifugal.dev/docs/server/channels
- Private channels: https://centrifugal.dev/docs/server/channels#private-channel-prefix
