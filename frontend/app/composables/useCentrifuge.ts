import { Centrifuge, type Subscription } from 'centrifuge'

/**
 * Thin wrapper around centrifuge-js that:
 *   * authenticates via a JWT minted by the backend
 *   * lazily creates one Centrifuge client per browser tab
 *   * exposes an ergonomic API for chat/timeline/system channels
 *
 * The client is created on demand, NOT during SSR — WebSocket is
 * browser-only. Channel names must come from the server or be built
 * via the matching Java `ChannelNameFactory` so that the tenant prefix
 * is always consistent.
 */
let clientSingleton: Centrifuge | null = null

export function useCentrifuge() {
  const { centrifugoUrl } = useRuntimeConfig().public
  const auth = useAuthStore()

  function ensureClient(): Centrifuge {
    if (!import.meta.client) {
      throw new Error('Centrifuge client can only be created in the browser.')
    }
    if (clientSingleton) return clientSingleton

    clientSingleton = new Centrifuge(centrifugoUrl, {
      // Initial token; real-time token refresh is handled via the
      // `refreshToken` endpoint the backend exposes.
      token: auth.token ?? '',
      getToken: async () => {
        // Delegated to the backend /api/v1/realtime/connect-token endpoint.
        // See shared/api/endpoints/realtime.ts.
        const api = useApi()
        const resp = await api.realtime.mintConnectToken()
        return resp.token
      },
      debug: import.meta.dev,
    })

    clientSingleton.on('connecting', ctx => console.info('[centrifuge] connecting', ctx))
    clientSingleton.on('connected',  ctx => console.info('[centrifuge] connected',  ctx))
    clientSingleton.on('disconnected', ctx => console.warn('[centrifuge] disconnected', ctx))
    clientSingleton.on('error',     ctx => console.error('[centrifuge] error',     ctx))

    clientSingleton.connect()
    return clientSingleton
  }

  function subscribe<TData = unknown>(
    channel: string,
    handlers: {
      onPublication?: (data: TData) => void
      onJoin?: (user: { client: string; user: string }) => void
      onLeave?: (user: { client: string; user: string }) => void
      onError?: (err: unknown) => void
    } = {},
  ): Subscription {
    const client = ensureClient()
    const existing = client.getSubscription(channel)
    const sub = existing ?? client.newSubscription(channel)

    if (handlers.onPublication) sub.on('publication', ctx => handlers.onPublication!(ctx.data as TData))
    if (handlers.onJoin)        sub.on('join',        ctx => handlers.onJoin!(ctx.info))
    if (handlers.onLeave)       sub.on('leave',       ctx => handlers.onLeave!(ctx.info))
    if (handlers.onError)       sub.on('error',       handlers.onError)

    if (sub.state !== 'subscribed') sub.subscribe()
    return sub
  }

  function disconnect() {
    if (clientSingleton) {
      clientSingleton.disconnect()
      clientSingleton = null
    }
  }

  return { subscribe, disconnect, ensureClient }
}
