import type { ApiClient } from '../client'
import type { SessionReportResponse, UUID } from '../types'

/**
 * Admin analytics report endpoint.
 *
 * <p>Maps to {@code AnalyticsReportController} on the backend which returns
 * the full session report bundle: summary counters, retention curve, and
 * per-CTA click-through rates.
 */
export class AnalyticsApi {
  constructor(private readonly client: ApiClient) {}

  /** Full session report — summary + retention + CTA CTR. */
  report(eventId: UUID, sessionId: UUID): Promise<SessionReportResponse> {
    return this.client.get(`/v1/events/${eventId}/sessions/${sessionId}/report`)
  }
}
