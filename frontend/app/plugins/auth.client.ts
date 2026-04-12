/**
 * Hydrate the auth store from localStorage as soon as the app boots in the
 * browser. SSR cannot access localStorage, so this runs client-only.
 *
 * <p>In dev mode (no Keycloak running), we install a synthetic session so the
 * admin layout has a name to display and the auth middleware stays green.
 * The token is a deterministic fake string — the dev backend's local security
 * config accepts anything and stamps a fixed profile id, so API calls still
 * pass through the Nitro proxy without a 401 loop.
 */
export default defineNuxtPlugin(() => {
  const auth = useAuthStore()
  auth.hydrate()

  if (import.meta.dev && !auth.isAuthenticated) {
    auth.setSession({
      token: 'dev-local-token',
      expiresAt: Date.now() + 24 * 60 * 60 * 1000,
      user: {
        id: '00000000-0000-0000-0000-000000000001',
        email: 'dev@webizon.local',
        fullName: 'Dev Admin',
        role: 'TENANT_OWNER',
        tenantId: '00000000-0000-0000-0000-00000000aaaa',
        tenantSlug: 'dev-workspace',
      },
    })
  }
})
