import { createApi, type Api } from '#shared/api'

/**
 * Nuxt-aware bridge to {@link createApi}.
 *
 * <p>We build the client lazily on first use and cache it on the Nuxt app
 * instance so every component inside a single request/page shares the same
 * {@link ApiClient}. That matters for request deduplication and for hooking
 * up a single `onError` sink (toast store).
 *
 * <p>The token is read through a getter callback rather than captured at
 * construction time so that a token refresh propagates to in-flight requests
 * without needing to rebuild the client.
 *
 * <h3>Usage</h3>
 * <pre>
 *   const api = useApi()
 *   const events = await api.events.list()
 *   await api.sessions.startLive(id)
 * </pre>
 */
export function useApi(): Api {
  const nuxt = useNuxtApp()
  const existing = nuxt.$api as Api | undefined
  if (existing) return existing

  const auth = useAuthStore()
  const { apiBase } = useRuntimeConfig().public
  const toast = useToastStore()

  const api = createApi({
    baseURL: apiBase,
    getToken: () => auth.token,
    onError: (err) => {
      // 401 is handled by the auth middleware — don't yell at the user for it.
      if (err.status === 401) return
      // Validation errors are usually surfaced inline by forms, so the global
      // toast would be noisy and unhelpful.
      if (err.status === 400 && err.errors) return
      toast.error(err.title ?? 'Қате', err.detail ?? 'Белгісіз қате')
    },
  })

  // Cache on the nuxt app instance (typed loosely; this is the idiomatic way
  // to add per-request singletons without a full plugin).
  ;(nuxt as unknown as { $api: Api }).$api = api
  return api
}
