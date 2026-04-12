import { defineStore } from 'pinia'

/**
 * Authenticated session state.
 *
 * The JWT is issued by Keycloak and refreshed by the OIDC callback flow.
 * Everything tenant-scoped on the backend is driven by the `tenant_id`
 * claim inside this token — we mirror it here for convenience, but the
 * token remains the source of truth.
 */
export interface AuthUser {
  id: string
  email: string
  fullName: string | null
  role: string
  tenantId: string | null
  tenantSlug: string | null
  avatarUrl?: string | null
}

export interface AuthState {
  token: string | null
  expiresAt: number | null
  user: AuthUser | null
}

const STORAGE_KEY = 'webizon:auth'

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    token: null,
    expiresAt: null,
    user: null,
  }),

  getters: {
    isAuthenticated: (s): boolean => !!s.token && (s.expiresAt ?? 0) > Date.now(),
    tenantId:        (s): string | null => s.user?.tenantId ?? null,
    tenantSlug:      (s): string | null => s.user?.tenantSlug ?? null,
    hasRole:         (s) => (role: string): boolean => s.user?.role === role,
  },

  actions: {
    hydrate() {
      if (import.meta.server) return
      const raw = localStorage.getItem(STORAGE_KEY)
      if (!raw) return
      try {
        const parsed = JSON.parse(raw) as AuthState
        if (parsed.token && parsed.expiresAt && parsed.expiresAt > Date.now()) {
          this.$patch(parsed)
        } else {
          localStorage.removeItem(STORAGE_KEY)
        }
      } catch {
        localStorage.removeItem(STORAGE_KEY)
      }
    },

    setSession(payload: { token: string; expiresAt: number; user: AuthUser }) {
      this.token      = payload.token
      this.expiresAt  = payload.expiresAt
      this.user       = payload.user
      if (import.meta.client) {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(this.$state))
      }
    },

    logout() {
      this.token = null
      this.expiresAt = null
      this.user = null
      if (import.meta.client) {
        localStorage.removeItem(STORAGE_KEY)
      }
    },
  },
})
