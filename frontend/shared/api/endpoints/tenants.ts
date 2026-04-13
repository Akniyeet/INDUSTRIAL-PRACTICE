import type { ApiClient } from '../client'

export interface TenantResponse {
  id: string
  slug: string
  displayName: string
  status: string
  countryCode: string
  defaultCurrency: string
  defaultLocale: string
  defaultTimezone: string
  trialEndsAt: string | null
  createdAt: string
}

/**
 * Tenant management endpoints at {@code /api/v1/tenants}.
 */
export class TenantsApi {
  constructor(private readonly client: ApiClient) {}

  /** Fetch the tenant bound to the current JWT's {@code tenant_id} claim. */
  me() {
    return this.client.get<TenantResponse>('/v1/tenants/me')
  }
}
