import type { ApiClient } from '../client'
import type {
  ChatMessageResponse,
  ChatSettingsResponse,
  SendMessageRequest,
  UUID,
} from '../types'

/**
 * Chat + chat settings endpoints.
 *
 * <p>Sending a message goes through {@link #send}, which returns 202 Accepted
 * on the backend — the message is queued to Kafka and delivered to every
 * room client via Centrifugo. Clients should NOT optimistically append;
 * instead they wait for the Centrifugo broadcast so the order is canonical.
 *
 * <p>{@link #history} returns the most recent N messages for the session;
 * the live stream arrives over Centrifugo.
 */
export class ChatApi {
  constructor(private readonly client: ApiClient) {}

  send(sessionId: UUID, body: SendMessageRequest) {
    return this.client.post<void>(`/v1/sessions/${sessionId}/chat/messages`, body)
  }

  history(sessionId: UUID, params: { limit?: number; before?: string } = {}) {
    return this.client.get<ChatMessageResponse[]>(`/v1/sessions/${sessionId}/chat/messages`, {
      query: { limit: params.limit ?? 50, before: params.before },
    })
  }

  getSettings(eventId: UUID) {
    return this.client.get<ChatSettingsResponse>(`/v1/events/${eventId}/chat-settings`)
  }

  updateSettings(eventId: UUID, body: Partial<ChatSettingsResponse>) {
    return this.client.patch<ChatSettingsResponse>(`/v1/events/${eventId}/chat-settings`, body)
  }
}
