import { ApiClient, type ApiClientOptions } from './client'
import { AnalyticsApi } from './endpoints/analytics'
import { AuthApi } from './endpoints/auth'
import { InfrastructureApi } from './endpoints/infrastructure'
import { ChatApi } from './endpoints/chat'
import { CtaApi } from './endpoints/cta'
import { EventsApi } from './endpoints/events'
import { HistoricalChatApi } from './endpoints/historicalChat'
import { InvitesApi } from './endpoints/invites'
import { ModerationApi } from './endpoints/moderation'
import { PlatformApi } from './endpoints/platform'
import { PublicEventsApi } from './endpoints/publicEvents'
import { RealtimeApi } from './endpoints/realtime'
import { RoomApi } from './endpoints/room'
import { SessionsApi } from './endpoints/sessions'
import { StorageApi } from './endpoints/storage'
import { TenantsApi } from './endpoints/tenants'
import { TimelineApi } from './endpoints/timeline'
import { AiLeadScoresApi } from './endpoints/aiLeadScores'

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
  analytics:       AnalyticsApi
  infrastructure:  InfrastructureApi
  auth:            AuthApi
  events:          EventsApi
  sessions:        SessionsApi
  publicEvents:    PublicEventsApi
  platform:        PlatformApi
  room:            RoomApi
  chat:            ChatApi
  cta:             CtaApi
  historicalChat:  HistoricalChatApi
  invites:         InvitesApi
  moderation:      ModerationApi
  realtime:        RealtimeApi
  storage:         StorageApi
  tenants:         TenantsApi
  timeline:        TimelineApi
  aiLeadScores:    AiLeadScoresApi
}

export function createApi(opts: ApiClientOptions): Api {
  const client = new ApiClient(opts)
  return {
    analytics:      new AnalyticsApi(client),
    infrastructure: new InfrastructureApi(client),
    auth:           new AuthApi(client),
    events:         new EventsApi(client),
    sessions:       new SessionsApi(client),
    publicEvents:   new PublicEventsApi(client),
    platform:       new PlatformApi(client),
    room:           new RoomApi(client),
    chat:           new ChatApi(client),
    cta:            new CtaApi(client),
    historicalChat: new HistoricalChatApi(client),
    invites:        new InvitesApi(client),
    moderation:     new ModerationApi(client),
    realtime:       new RealtimeApi(client),
    storage:        new StorageApi(client),
    tenants:        new TenantsApi(client),
    timeline:       new TimelineApi(client),
    aiLeadScores:   new AiLeadScoresApi(client),
  }
}
