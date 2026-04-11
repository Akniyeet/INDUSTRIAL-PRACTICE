import { ApiClient, type ApiClientOptions } from './client'
import { AuthApi } from './endpoints/auth'
import { ChatApi } from './endpoints/chat'
import { CtaApi } from './endpoints/cta'
import { EventsApi } from './endpoints/events'
import { PublicEventsApi } from './endpoints/publicEvents'
import { RealtimeApi } from './endpoints/realtime'
import { RoomApi } from './endpoints/room'
import { SessionsApi } from './endpoints/sessions'

export * from './types'
export { ApiClient, isApiError } from './client'

/**
 * Aggregated API facade that the rest of the app consumes.
 *
 * <pre>
 *   const api = createApi({ baseURL, getToken, onError })
 *   await api.events.list()
 *   await api.sessions.startLive(id)
 * </pre>
 *
 * Keeping every endpoint under one object means:
 *   1. Components never import `$fetch` directly.
 *   2. Adding a new endpoint is a one-line change here and in the facade.
 *   3. Mocking in tests is a single swap.
 */
export interface Api {
  auth:          AuthApi
  events:        EventsApi
  sessions:      SessionsApi
  publicEvents:  PublicEventsApi
  room:          RoomApi
  chat:          ChatApi
  cta:           CtaApi
  realtime:      RealtimeApi
}

export function createApi(opts: ApiClientOptions): Api {
  const client = new ApiClient(opts)
  return {
    auth:         new AuthApi(client),
    events:       new EventsApi(client),
    sessions:     new SessionsApi(client),
    publicEvents: new PublicEventsApi(client),
    room:         new RoomApi(client),
    chat:         new ChatApi(client),
    cta:          new CtaApi(client),
    realtime:     new RealtimeApi(client),
  }
}
