import { defineStore } from 'pinia'

export interface AuthUser {
  id: string
  email: string
  fullName: string | null
  role: string
  tenantId: string | null
  tenantSlug: string | null
  avatarUrl?: string | null
  isPlatformAdmin?: boolean
}

export interface AuthState {
  token: string | null
  refreshToken: string | null
  expiresAt: number | null
  user: AuthUser | null
}

interface KeycloakTokenResponse {
  access_token: string
  refresh_token: string
  expires_in: number
}

interface BootstrapResponse {
  user: { id: string; email: string; fullName: string | null; avatarUrl: string | null }
  memberships: Array<{ tenantId: string; tenantSlug: string; tenantDisplayName: string; role: string; status: string }>
}

const STORAGE_KEY = 'webizon:auth'

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    token: null,
    refreshToken: null,
    expiresAt: null,
    user: null,
  }),

  getters: {
    isAuthenticated:  (s): boolean => !!s.token && (s.expiresAt ?? 0) > Date.now(),
    tenantId:         (s): string | null => s.user?.tenantId ?? null,
    tenantSlug:       (s): string | null => s.user?.tenantSlug ?? null,
    hasRole:          (s) => (role: string): boolean => s.user?.role === role,
    isPlatformAdmin:  (s): boolean => s.user?.isPlatformAdmin === true,
  },

  actions: {
    // -----------------------------------------------------------------------
    // Persistence
    // -----------------------------------------------------------------------

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

    setSession(payload: { token: string; refreshToken?: string; expiresAt: number; user: AuthUser }) {
      this.token        = payload.token
      this.refreshToken = payload.refreshToken ?? null
      this.expiresAt    = payload.expiresAt
      this.user         = payload.user
      if (import.meta.client) {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(this.$state))
      }
    },

    logout() {
      this.token        = null
      this.refreshToken = null
      this.expiresAt    = null
      this.user         = null
      if (import.meta.client) {
        localStorage.removeItem(STORAGE_KEY)
      }
    },

    // -----------------------------------------------------------------------
    // Auth actions
    // -----------------------------------------------------------------------

    async login(email: string, password: string) {
      const tokens = await $fetch<KeycloakTokenResponse>('/api/backend/v1/public/auth/login', {
        method: 'POST',
        body: { email, password },
      })
      await this._applyTokens(tokens)
    },

    async register(fullName: string, email: string, password: string) {
      const tokens = await $fetch<KeycloakTokenResponse>('/api/backend/v1/public/auth/register', {
        method: 'POST',
        body: { fullName, email, password },
      })
      await this._applyTokens(tokens)
    },

    async handleOAuthCallback(code: string, redirectUri: string, codeVerifier: string) {
      const tokens = await $fetch<KeycloakTokenResponse>('/api/backend/v1/public/auth/callback', {
        method: 'POST',
        body: { code, redirectUri, codeVerifier },
      })
      await this._applyTokens(tokens)
    },

    async tryRefresh(): Promise<boolean> {
      if (!this.refreshToken) return false
      try {
        const tokens = await $fetch<KeycloakTokenResponse>('/api/backend/v1/public/auth/refresh', {
          method: 'POST',
          body: { refreshToken: this.refreshToken },
        })
        await this._applyTokens(tokens)
        return true
      } catch {
        this.logout()
        return false
      }
    },

    /**
     * Initiate Google OAuth via PKCE (S256).
     * Stores verifier + destination in sessionStorage, then redirects.
     */
    async startGoogleLogin(callbackUrl: string, destination = '/admin') {
      const verifier  = _generateVerifier()
      const challenge = await _computeS256(verifier)

      sessionStorage.setItem('webizon:pkce_verifier',     verifier)
      sessionStorage.setItem('webizon:pkce_redirect_uri', callbackUrl)
      sessionStorage.setItem('webizon:auth_destination',  destination)

      const config = useRuntimeConfig().public
      const params = new URLSearchParams({
        client_id:             config.keycloakClientId as string,
        redirect_uri:          callbackUrl,
        response_type:         'code',
        scope:                 'openid profile email',
        kc_idp_hint:           'google',
        code_challenge:        challenge,
        code_challenge_method: 'S256',
      })

      window.location.href =
        `${config.keycloakUrl}/realms/${config.keycloakRealm}/protocol/openid-connect/auth?${params}`
    },

    // -----------------------------------------------------------------------
    // Internal
    // -----------------------------------------------------------------------

    async _applyTokens(tokens: KeycloakTokenResponse) {
      const bootstrap = await $fetch<BootstrapResponse>('/api/backend/v1/auth/bootstrap', {
        method: 'POST',
        headers: { Authorization: `Bearer ${tokens.access_token}` },
      })

      const membership = bootstrap.memberships.find(m => m.status === 'ACTIVE')

      this.setSession({
        token:        tokens.access_token,
        refreshToken: tokens.refresh_token,
        expiresAt:    Date.now() + tokens.expires_in * 1000,
        user: {
          id:             bootstrap.user.id,
          email:          bootstrap.user.email,
          fullName:       bootstrap.user.fullName,
          avatarUrl:      bootstrap.user.avatarUrl,
          role:           membership?.role ?? 'participant',
          tenantId:       membership?.tenantId ?? null,
          tenantSlug:     membership?.tenantSlug ?? null,
          isPlatformAdmin: (bootstrap.user as any).platformAdmin === true,
        },
      })
    },
  },
})

// ---------------------------------------------------------------------------
// PKCE helpers (RFC 7636 S256)
// ---------------------------------------------------------------------------

function _generateVerifier(): string {
  const arr = new Uint8Array(32)
  crypto.getRandomValues(arr)
  return btoa(String.fromCharCode(...arr))
    .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

async function _computeS256(verifier: string): Promise<string> {
  const data   = new TextEncoder().encode(verifier)
  const digest = await crypto.subtle.digest('SHA-256', data)
  return btoa(String.fromCharCode(...new Uint8Array(digest)))
    .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}
