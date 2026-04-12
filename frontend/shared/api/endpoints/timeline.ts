import type { ApiClient } from '../client'
import type {
  TimelineActionCreateRequest,
  TimelineActionResponse,
  TimelineActionUpdateRequest,
  UUID,
} from '../types'

/**
 * Timeline action endpoints — admin CRUD + replay queue reads.
 *
 * <p>Backed by {@code TimelineController} on the backend. All mutation
 * endpoints require TENANT_OWNER / TENANT_ADMIN / TENANT_PRESENTER.
 */
export class TimelineApi {
  constructor(private readonly client: ApiClient) {}

  list(eventId: UUID, sourceSessionId: UUID) {
    return this.client.get<TimelineActionResponse[]>(
      `/v1/events/${eventId}/sessions/${sourceSessionId}/timeline`,
    )
  }

  create(eventId: UUID, sourceSessionId: UUID, body: TimelineActionCreateRequest) {
    return this.client.post<TimelineActionResponse>(
      `/v1/events/${eventId}/sessions/${sourceSessionId}/timeline`,
      body,
    )
  }

  update(eventId: UUID, actionId: UUID, body: TimelineActionUpdateRequest) {
    return this.client.patch<TimelineActionResponse>(
      `/v1/events/${eventId}/timeline/${actionId}`,
      body,
    )
  }

  setActive(eventId: UUID, actionId: UUID, active: boolean) {
    return this.client.put<TimelineActionResponse>(
      `/v1/events/${eventId}/timeline/${actionId}/active`,
      { active },
    )
  }

  remove(eventId: UUID, actionId: UUID) {
    return this.client.delete<void>(`/v1/events/${eventId}/timeline/${actionId}`)
  }
}
