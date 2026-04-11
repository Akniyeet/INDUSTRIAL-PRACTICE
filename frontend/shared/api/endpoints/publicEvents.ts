import type { ApiClient } from '../client'
import type { PublicEventResolution } from '../types'

/**
 * Public landing-page resolver at
 * {@code /api/v1/public/tenants/{tenantSlug}/events/{eventSlug}/resolve}.
 *
 * <p>This endpoint is reachable without a JWT so that unauthenticated visitors
 * can still see the event landing page. The response's {@code state} decides
 * whether to render the landing page, the waiting room, a slot picker, the
 * "unavailable" page, or to drop the user straight into the live room.
 */
export class PublicEventsApi {
  constructor(private readonly client: ApiClient) {}

  resolve(tenantSlug: string, eventSlug: string) {
    const path = `/v1/public/tenants/${encodeURIComponent(tenantSlug)}/events/${encodeURIComponent(eventSlug)}/resolve`
    return this.client.get<PublicEventResolution>(path)
  }
}
