import type { ApiClient } from '../client'
import type { RealtimeSubscribeTokenResponse, RealtimeTokenResponse, UUID } from '../types'

/**
 * Short-lived Centrifugo token minter at {@code /api/v1/realtime/*}.
 *
 * <p>The connect token is issued per-user and carries tenant + user claims.
 * The subscribe token is issued per-channel and is the only thing that grants
 * the client the right to consume a namespaced channel. Tokens are refreshed
 * on demand by {@code useCentrifuge()} when Centrifugo returns
 * {@code unauthorized}.
 */
export class RealtimeApi {
  constructor(private readonly client: ApiClient) {}

  mintConnectToken() {
    return this.client.post<RealtimeTokenResponse>('/v1/realtime/connect-token')
  }

  mintSubscribeToken(channel: string) {
    return this.client.post<RealtimeSubscribeTokenResponse>(
      '/v1/realtime/subscribe-token',
      { channel },
    )
  }

  /** Convenience — mint a subscribe token for every channel of a session. */
  mintRoomBundle(sessionId: UUID) {
    return this.client.post<RealtimeSubscribeTokenResponse[]>(
      `/v1/realtime/sessions/${sessionId}/subscribe-tokens`,
    )
  }
}
