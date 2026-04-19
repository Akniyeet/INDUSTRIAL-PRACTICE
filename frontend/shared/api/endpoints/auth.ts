import type { ApiClient } from '../client'
import type { BootstrapResponse, MembershipResponse, UUID } from '../types'

/**
 * Auth bootstrap + membership endpoints.
 *
 * <p>{@link #bootstrap} is called right after Keycloak issues a JWT; the
 * backend mirrors the user into its local {@code users} table (idempotent)
 * and returns the user's tenant memberships so the frontend can route.
 */
export class AuthApi {
  constructor(private readonly client: ApiClient) {}

  bootstrap() {
    return this.client.post<BootstrapResponse>('/v1/auth/bootstrap')
  }

  myMemberships() {
    return this.client.get<MembershipResponse[]>('/v1/memberships/me')
  }

  acceptInvite(token: string) {
    return this.client.post<{
      tenantId: UUID
      tenantSlug: string
      tenantDisplayName: string
      role: string
      membershipId: UUID
    }>(`/v1/invites/accept/${encodeURIComponent(token)}`)
  }
}
