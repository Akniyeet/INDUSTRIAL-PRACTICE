/**
 * Route guard for authenticated areas. Applied via `definePageMeta({
 * middleware: 'auth' })` on pages that require a logged-in user.
 *
 * SSR is permissive (the check re-runs on the client after hydration)
 * because the token lives in localStorage.
 */
export default defineNuxtRouteMiddleware((to) => {
  if (import.meta.server) return
  const auth = useAuthStore()
  if (!auth.isAuthenticated) {
    return navigateTo({
      path: '/auth/sign-in',
      query: { redirect: to.fullPath },
    })
  }
})
