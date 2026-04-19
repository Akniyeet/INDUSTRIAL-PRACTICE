import type { ApiClient } from '../client'

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export interface PlatformDashboardResponse {
  totalTenants: number
  activeTenants: number
  totalUsers: number
  totalEvents: number
  totalSessions: number
  currentlyLiveSessions: number
  revenueThisMonthKzt: number
  vatThisMonthKzt: number
  revenueAllTimeKzt: number
  monthlyRevenue: Array<{
    month: string       // "2025-03"
    totalKzt: number
    vatKzt: number
    payingTenants: number
  }>
}

export interface PlatformTenantResponse {
  id: string
  slug: string
  displayName: string
  status: string
  trialEndsAt: string | null
  createdAt: string
  ownerEmail: string | null
  ownerFullName: string | null
  memberCount: number
  eventCount: number
  sessionCount: number
  currentlyLive: number
  totalPaidKzt: number
  totalOutstandingKzt: number
  lastPaymentAt: string | null
}

export interface PlatformUserResponse {
  id: string
  email: string
  fullName: string | null
  emailVerified: boolean
  platformAdmin: boolean
  lastLoginAt: string | null
  createdAt: string
  tenantSlugs: string[]
  tenantCount: number
}

export interface PlatformRevenueResponse {
  allTimeSubtotalKzt: number
  allTimeVatKzt: number
  allTimeTotalKzt: number
  thisMonthSubtotalKzt: number
  thisMonthVatKzt: number
  thisMonthTotalKzt: number
  outstandingKzt: number
  topTenants: Array<{
    tenantId: string
    tenantSlug: string
    displayName: string
    totalKzt: number
    vatKzt: number
    invoiceCount: number
  }>
  topEvents: Array<{
    eventId: string
    tenantId: string
    tenantSlug: string
    eventTitle: string
    totalKzt: number
    sessionCount: number
  }>
  monthlyTrend: Array<{
    month: string
    subtotalKzt: number
    vatKzt: number
    totalKzt: number
    payingTenants: number
  }>
}

export interface ImpersonateResponse {
  tenantId: string
  tenantSlug: string
  displayName: string
}

// ---------------------------------------------------------------------------
// API class
// ---------------------------------------------------------------------------

export class PlatformApi {
  constructor(private readonly client: ApiClient) {}

  dashboard(): Promise<PlatformDashboardResponse> {
    return this.client.get('/v1/platform/dashboard')
  }

  listTenants(params?: { page?: number; size?: number }): Promise<{ content: PlatformTenantResponse[]; totalElements: number }> {
    const qp = new URLSearchParams()
    if (params?.page !== undefined) qp.set('page', String(params.page))
    if (params?.size !== undefined) qp.set('size', String(params.size))
    const qs = qp.toString()
    return this.client.get(`/v1/platform/tenants${qs ? `?${qs}` : ''}`)
  }

  getTenant(tenantId: string): Promise<PlatformTenantResponse> {
    return this.client.get(`/v1/platform/tenants/${tenantId}`)
  }

  listUsers(params?: { search?: string; page?: number; size?: number }): Promise<{ content: PlatformUserResponse[]; totalElements: number }> {
    const qp = new URLSearchParams()
    if (params?.search) qp.set('search', params.search)
    if (params?.page !== undefined) qp.set('page', String(params.page))
    if (params?.size !== undefined) qp.set('size', String(params.size))
    const qs = qp.toString()
    return this.client.get(`/v1/platform/users${qs ? `?${qs}` : ''}`)
  }

  revenue(): Promise<PlatformRevenueResponse> {
    return this.client.get('/v1/platform/revenue')
  }

  impersonate(tenantId: string): Promise<ImpersonateResponse> {
    return this.client.post(`/v1/platform/tenants/${tenantId}/impersonate`, {})
  }
}
