import type { ApiClient } from '../client'

export interface ServiceHealth {
  name: string
  /** 'healthy' | 'degraded' | 'down' */
  status: 'healthy' | 'degraded' | 'down'
  details: Record<string, unknown>
}

export interface InfrastructureHealthResponse {
  overall: 'healthy' | 'degraded' | 'down'
  services: ServiceHealth[]
}

/**
 * Admin infrastructure health endpoint at {@code /api/v1/infrastructure/health}.
 * Wraps Spring Actuator data into a stable contract for the monitoring page.
 */
export class InfrastructureApi {
  constructor(private readonly client: ApiClient) {}

  health() {
    return this.client.get<InfrastructureHealthResponse>('/v1/infrastructure/health')
  }
}
