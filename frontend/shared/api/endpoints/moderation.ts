import type { ApiClient } from '../client'
import type {
  BanRequest,
  DeleteMessageRequest,
  HideMessageRequest,
  ModerationActionResponse,
  MuteRequest,
  UUID,
  WarnRequest,
} from '../types'

/**
 * Moderator verbs for the admin live control room.
 *
 * <p>Every mutation goes through {@code /v1/sessions/{id}/moderation/*} and
 * requires a moderator-grade role on the backend (owner / admin / moderator /
 * presenter). On success the service publishes a matching event on the
 * session's CONTROL channel so affected clients can react without polling —
 * the participant room page flips its local {@code capabilities} when a
 * {@code ChatStatusChanged} system event arrives.
 *
 * <p>The read-side {@link #log} endpoint is also open to analysts so reports
 * can cite the same audit trail.
 */
export class ModerationApi {
  constructor(private readonly client: ApiClient) {}

  warn(sessionId: UUID, body: WarnRequest) {
    return this.client.post<ModerationActionResponse>(
      `/v1/sessions/${sessionId}/moderation/warn`,
      body,
    )
  }

  mute(sessionId: UUID, body: MuteRequest) {
    return this.client.post<ModerationActionResponse>(
      `/v1/sessions/${sessionId}/moderation/mute`,
      body,
    )
  }

  chatBan(sessionId: UUID, body: BanRequest) {
    return this.client.post<ModerationActionResponse>(
      `/v1/sessions/${sessionId}/moderation/chat-ban`,
      body,
    )
  }

  roomRemove(sessionId: UUID, body: BanRequest) {
    return this.client.post<ModerationActionResponse>(
      `/v1/sessions/${sessionId}/moderation/room-remove`,
      body,
    )
  }

  fullBan(sessionId: UUID, body: BanRequest) {
    return this.client.post<ModerationActionResponse>(
      `/v1/sessions/${sessionId}/moderation/full-ban`,
      body,
    )
  }

  deleteMessage(sessionId: UUID, body: DeleteMessageRequest) {
    return this.client.post<ModerationActionResponse>(
      `/v1/sessions/${sessionId}/moderation/delete-message`,
      body,
    )
  }

  hideMessage(sessionId: UUID, body: HideMessageRequest) {
    return this.client.post<ModerationActionResponse>(
      `/v1/sessions/${sessionId}/moderation/hide-message`,
      body,
    )
  }

  log(sessionId: UUID, limit = 100) {
    return this.client.get<ModerationActionResponse[]>(
      `/v1/sessions/${sessionId}/moderation/log`,
      { query: { limit } },
    )
  }
}
