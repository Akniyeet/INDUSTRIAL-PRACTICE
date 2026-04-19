/**
 * Route guard for /platform/** pages.
 * Allows access only to authenticated platform admins.
 * In dev mode, skips the check (matching the pattern used by the auth middleware).
 */
export default defineNuxtRouteMiddleware(() => {
  if (import.meta.server) return
  if (import.meta.dev) return

  const auth = useAuthStore()
  auth.hydrate()

  if (!auth.isAuthenticated) {
    return navigateTo('/auth/sign-in')
  }

  if (!auth.isPlatformAdmin) {
    return navigateTo('/admin')
  }
})
