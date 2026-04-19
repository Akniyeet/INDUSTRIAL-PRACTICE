import type { ApiClient } from '../client'
import type {
  CtaCreateRequest,
  CtaResponse,
  CtaUpdateRequest,
  UUID,
} from '../types'

/**
 * CTA CRUD (admin) + show/hide commands (live control).
 *
 * <p>Admin-side CRUD lives under {@code /v1/events/{eventId}/ctas}. The
 * show/hide verbs are session-scoped because a CTA belongs to an event but is
 * activated per run; activating a CTA writes a timeline row linked to the
 * source live session, so auto replays can replay it later.
 */
export class CtaApi {
  constructor(private readonly client: ApiClient) {}

  list(eventId: UUID) {
    return this.client.get<CtaResponse[]>(`/v1/events/${eventId}/ctas`)
  }

  create(eventId: UUID, body: CtaCreateRequest) {
    return this.client.post<CtaResponse>(`/v1/events/${eventId}/ctas`, body)
  }

  update(eventId: UUID, ctaId: UUID, body: CtaUpdateRequest) {
    return this.client.patch<CtaResponse>(`/v1/events/${eventId}/ctas/${ctaId}`, body)
  }

  setActive(eventId: UUID, ctaId: UUID, active: boolean) {
    return this.client.put<CtaResponse>(`/v1/events/${eventId}/ctas/${ctaId}/active`, { active })
  }

  remove(eventId: UUID, ctaId: UUID) {
    return this.client.delete<void>(`/v1/events/${eventId}/ctas/${ctaId}`)
  }

  show(sessionId: UUID, ctaId: UUID) {
    return this.client.post<void>(`/v1/sessions/${sessionId}/ctas/${ctaId}/show`)
  }

  hide(sessionId: UUID, ctaId: UUID) {
    return this.client.post<void>(`/v1/sessions/${sessionId}/ctas/${ctaId}/hide`)
  }
}
