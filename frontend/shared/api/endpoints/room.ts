import type { ApiClient } from '../client'
import type { RoomBootstrapResponse, UUID } from '../types'

/**
 * Single-call room bootstrap at
 * {@code GET /api/v1/sessions/{sessionId}/room}.
 *
 * <p>Everything the live/auto room needs on first paint arrives in one
 * response: event + session + chat history seed + active CTAs + Centrifugo
 * channel names + capability flags. Realtime tokens are issued via a
 * separate call so they can stay short-lived.
 */
export class RoomApi {
  constructor(private readonly client: ApiClient) {}

  bootstrap(sessionId: UUID) {
    return this.client.get<RoomBootstrapResponse>(`/v1/sessions/${sessionId}/room`)
  }
}
