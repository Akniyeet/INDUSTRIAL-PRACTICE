import type { ApiClient } from '../client'
import type {
  InviteCreateRequest,
  InviteCreateResponse,
  InviteResponse,
  InviteStatus,
  Page,
  UUID,
} from '../types'

/**
 * Admin invite management endpoints.
 *
 * <p>Backed by {@code InviteController} on the backend. Create/list/revoke
 * are gated to TENANT_OWNER / TENANT_ADMIN. The accept endpoint lives on
 * {@link AuthApi} because the recipient may not yet have tenant context.
 */
export class InvitesApi {
  constructor(private readonly client: ApiClient) {}

  create(body: InviteCreateRequest) {
    return this.client.post<InviteCreateResponse>('/v1/invites', body)
  }

  list(params: { status?: InviteStatus; page?: number; size?: number } = {}) {
    return this.client.get<Page<InviteResponse>>('/v1/invites', {
      query: {
        status: params.status,
        page: params.page ?? 0,
        size: params.size ?? 20,
      },
    })
  }

  getById(id: UUID) {
    return this.client.get<InviteResponse>(`/v1/invites/${id}`)
  }

  revoke(id: UUID) {
    return this.client.delete<InviteResponse>(`/v1/invites/${id}`)
  }
}
