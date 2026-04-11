import type { FetchOptions } from 'ofetch'

/**
 * Typed wrapper around `$fetch` that automatically attaches the Webizon API
 * base URL and the current JWT. All browser-side requests go through the
 * Nitro proxy configured in `nuxt.config.ts` so nothing leaks the internal
 * backend hostname.
 *
 * Usage:
 *   const api = useApi()
 *   const event = await api<EventDto>('/v1/events/{id}', { path: { id } })
 */
export function useApi() {
  const auth = useAuthStore()
  const { apiBase } = useRuntimeConfig().public

  return async <T = unknown>(
    path: string,
    options: FetchOptions<'json'> = {},
  ): Promise<T> => {
    const headers: Record<string, string> = {
      Accept: 'application/json',
      ...(options.headers as Record<string, string> | undefined),
    }
    if (auth.token) {
      headers.Authorization = `Bearer ${auth.token}`
    }

    return await $fetch<T>(path, {
      baseURL: apiBase,
      ...options,
      headers,
    })
  }
}
