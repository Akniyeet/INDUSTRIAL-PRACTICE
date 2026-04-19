import type { ApiClient } from '../client'
import type {
  SessionCreateRequest,
  SessionResponse,
  SessionUpdateRequest,
  UUID,
} from '../types'

/**
 * Endpoints backed by {@code SessionController}.
 *
 * <p>Session lifecycle verbs ({@link #startLive}, {@link #endLive}, etc.) are
 * non-idempotent on purpose: the backend enforces state machine transitions
 * and rejects invalid ones with 409. The frontend should disable action
 * buttons optimistically on the correct status rather than trying to guess.
 */
export class SessionsApi {
  constructor(private readonly client: ApiClient) {}

  listByEvent(eventId: UUID) {
    return this.client.get<SessionResponse[]>(`/v1/events/${eventId}/sessions`)
  }

  create(eventId: UUID, body: SessionCreateRequest) {
    return this.client.post<SessionResponse>(`/v1/events/${eventId}/sessions`, body)
  }

  getById(sessionId: UUID) {
    return this.client.get<SessionResponse>(`/v1/sessions/${sessionId}`)
  }

  update(sessionId: UUID, body: SessionUpdateRequest) {
    return this.client.patch<SessionResponse>(`/v1/sessions/${sessionId}`, body)
  }

  startLive(sessionId: UUID) {
    return this.client.post<SessionResponse>(`/v1/sessions/${sessionId}/start-live`)
  }

  endLive(sessionId: UUID) {
    return this.client.post<SessionResponse>(`/v1/sessions/${sessionId}/end-live`)
  }

  startAuto(sessionId: UUID) {
    return this.client.post<SessionResponse>(`/v1/sessions/${sessionId}/start-auto`)
  }

  endAuto(sessionId: UUID) {
    return this.client.post<SessionResponse>(`/v1/sessions/${sessionId}/end-auto`)
  }

  cancel(sessionId: UUID) {
    return this.client.post<SessionResponse>(`/v1/sessions/${sessionId}/cancel`)
  }
}
