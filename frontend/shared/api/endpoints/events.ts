import type { ApiClient } from '../client'
import type {
  EventCreateRequest,
  EventResponse,
  EventStatus,
  EventUpdateRequest,
  Page,
  UUID,
} from '../types'

/**
 * Endpoints backed by {@code EventController} at {@code /api/v1/events}.
 *
 * Admin-only; tenancy is resolved from the JWT `tenant_id` claim. All list
 * calls return a Spring {@link Page} — the frontend decides pagination UX.
 */
export class EventsApi {
  constructor(private readonly client: ApiClient) {}

  list(params: { page?: number; size?: number; status?: EventStatus } = {}) {
    return this.client.get<Page<EventResponse>>('/v1/events', {
      query: {
        page: params.page ?? 0,
        size: params.size ?? 20,
        status: params.status,
      },
    })
  }

  getById(id: UUID) {
    return this.client.get<EventResponse>(`/v1/events/${id}`)
  }

  create(body: EventCreateRequest) {
    return this.client.post<EventResponse>('/v1/events', body)
  }

  update(id: UUID, body: EventUpdateRequest) {
    return this.client.patch<EventResponse>(`/v1/events/${id}`, body)
  }

  publish(id: UUID) {
    return this.client.post<EventResponse>(`/v1/events/${id}/publish`)
  }

  unpublish(id: UUID) {
    return this.client.post<EventResponse>(`/v1/events/${id}/unpublish`)
  }

  remove(id: UUID) {
    return this.client.delete<void>(`/v1/events/${id}`)
  }
}
