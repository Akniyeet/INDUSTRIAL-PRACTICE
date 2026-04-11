import type { ApiError } from './types'

/**
 * Minimal request options we surface to endpoint modules. We deliberately do
 * NOT re-export {@code FetchOptions<'json'>} from ofetch because Nuxt's global
 * `$fetch` is typed with a different generic that tries to resolve every
 * Nitro route at compile time — feeding arbitrary paths through it explodes
 * TypeScript's type instantiation depth. A narrow wrapper keeps inference
 * fast and the public surface honest.
 */
export interface RequestOptions {
  query?: Record<string, unknown>
  headers?: Record<string, string>
  body?: unknown
  signal?: AbortSignal
}

type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

/**
 * Low-level HTTP client used by every endpoint module in `shared/api/endpoints/*`.
 *
 * <h2>Why a dedicated client and not raw `$fetch`?</h2>
 * <ul>
 *   <li>Every outgoing request needs the JWT header; forgetting it is a very
 *       common source of "why am I getting 401" bugs. Centralising the header
 *       here eliminates that class of mistake.</li>
 *   <li>Backend errors arrive as Spring ProblemDetail — we normalise them into
 *       {@link ApiError} so call sites can rely on a stable shape regardless
 *       of which HTTP layer is actually talking.</li>
 *   <li>We want one place to log, metric, and retry. That place is here.</li>
 * </ul>
 *
 * This module is intentionally framework-agnostic (does not import anything
 * from `#imports`). The Nuxt-aware wrapper lives in `composables/useApi.ts`.
 */

export interface ApiClientOptions {
  baseURL: string
  /** Returns the current access token, or null if unauthenticated. */
  getToken: () => string | null
  /** Invoked with every normalised error. Used for toast + Sentry wiring. */
  onError?: (err: ApiError) => void
}

export class ApiClient {
  constructor(private readonly opts: ApiClientOptions) {}

  async request<T>(method: HttpMethod, path: string, options: RequestOptions = {}): Promise<T> {
    const token = this.opts.getToken()
    const headers: Record<string, string> = {
      Accept: 'application/json',
      ...(options.headers ?? {}),
    }
    if (token) {
      headers.Authorization = `Bearer ${token}`
    }
    if (options.body !== undefined && !headers['Content-Type']) {
      headers['Content-Type'] = 'application/json'
    }

    // Cast $fetch to a plain signature — Nuxt's auto-typed Nitro route map
    // is not useful for arbitrary backend paths and tanks inference.
    const fetch = $fetch as unknown as <R>(
      req: string,
      init: {
        method: HttpMethod
        baseURL: string
        headers: Record<string, string>
        query?: Record<string, unknown>
        body?: unknown
        signal?: AbortSignal
      },
    ) => Promise<R>

    try {
      return await fetch<T>(path, {
        method,
        baseURL: this.opts.baseURL,
        headers,
        query: options.query,
        body: options.body,
        signal: options.signal,
      })
    } catch (raw: unknown) {
      const err = normaliseError(raw)
      this.opts.onError?.(err)
      throw err
    }
  }

  get<T>(path: string, options: RequestOptions = {}) {
    return this.request<T>('GET', path, options)
  }
  post<T>(path: string, body?: unknown, options: RequestOptions = {}) {
    return this.request<T>('POST', path, { ...options, body })
  }
  put<T>(path: string, body?: unknown, options: RequestOptions = {}) {
    return this.request<T>('PUT', path, { ...options, body })
  }
  patch<T>(path: string, body?: unknown, options: RequestOptions = {}) {
    return this.request<T>('PATCH', path, { ...options, body })
  }
  delete<T>(path: string, options: RequestOptions = {}) {
    return this.request<T>('DELETE', path, options)
  }
}

/**
 * ofetch throws an `FetchError` with `data` already parsed. Normalise it to
 * our {@link ApiError} shape so downstream code only has to care about one
 * type. If the response is not a ProblemDetail (network error, CORS, etc.),
 * we synthesise a best-effort envelope with status 0.
 */
export function normaliseError(raw: unknown): ApiError {
  // ofetch FetchError has { response, status, data, message }
  const e = raw as {
    response?: { status?: number }
    status?: number
    data?: Partial<ApiError> & { message?: string }
    message?: string
  }
  const status = e?.response?.status ?? e?.status ?? 0
  const payload = e?.data ?? {}
  return {
    status,
    type: payload.type,
    title: payload.title ?? (status === 0 ? 'Network error' : `HTTP ${status}`),
    detail: payload.detail ?? payload.message ?? e?.message,
    instance: payload.instance,
    errors: payload.errors,
    timestamp: payload.timestamp,
  }
}

export function isApiError(value: unknown): value is ApiError {
  if (!value || typeof value !== 'object') return false
  return typeof (value as ApiError).status === 'number'
}
