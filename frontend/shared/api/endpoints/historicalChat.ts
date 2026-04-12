import type { ApiClient } from '../client'
import type { HistoricalChatMessageView, UUID } from '../types'

/**
 * Admin endpoints for reviewing and curating the historical chat of a
 * LIVE session before it replays in AUTO sessions.
 *
 * <p>Backed by {@code HistoricalChatController} on the backend.
 */
export class HistoricalChatApi {
  constructor(private readonly client: ApiClient) {}

  transcript(sessionId: UUID) {
    return this.client.get<HistoricalChatMessageView[]>(
      `/v1/sessions/${sessionId}/historical-chat`,
    )
  }

  toggleReplay(sessionId: UUID, messageId: UUID) {
    return this.client.post<HistoricalChatMessageView>(
      `/v1/sessions/${sessionId}/historical-chat/${messageId}/toggle-replay`,
    )
  }

  bulkExclude(sessionId: UUID, messageIds: UUID[]) {
    return this.client.post<number>(
      `/v1/sessions/${sessionId}/historical-chat/bulk-exclude`,
      messageIds,
    )
  }

  bulkInclude(sessionId: UUID, messageIds: UUID[]) {
    return this.client.post<number>(
      `/v1/sessions/${sessionId}/historical-chat/bulk-include`,
      messageIds,
    )
  }
}
