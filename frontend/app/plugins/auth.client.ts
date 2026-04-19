/**
 * Hydrate auth store from localStorage on boot and start a background
 * token-refresh loop. SSR cannot access localStorage so this runs client-only.
 *
 * Refresh strategy: check every 60 s; if the token expires within 2 minutes,
 * call the refresh endpoint proactively so the user never hits a 401.
 */
export default defineNuxtPlugin(() => {
  const auth = useAuthStore()
  auth.hydrate()

  // Dev mode: synthetic session so admin layout works without Keycloak.
  if (import.meta.dev && !auth.isAuthenticated) {
    auth.setSession({
      token:     'dev-local-token',
      expiresAt: Date.now() + 24 * 60 * 60 * 1000,
      user: {
        id:         '00000000-0000-0000-0000-000000000001',
        email:      'dev@webizon.local',
        fullName:   'Dev Admin',
        role:       'TENANT_OWNER',
        tenantId:   '00000000-0000-0000-0000-00000000aaaa',
        tenantSlug: 'dev-workspace',
      },
    })
    return
  }

  // Proactive refresh when < 2 minutes remain on the access token.
  const TWO_MINUTES = 2 * 60 * 1000
  const interval = setInterval(async () => {
    if (!auth.isAuthenticated) return
    const remaining = (auth.expiresAt ?? 0) - Date.now()
    if (remaining < TWO_MINUTES) {
      await auth.tryRefresh()
    }
  }, 60_000)

  if (import.meta.hot) {
    import.meta.hot.dispose(() => clearInterval(interval))
  }
})
