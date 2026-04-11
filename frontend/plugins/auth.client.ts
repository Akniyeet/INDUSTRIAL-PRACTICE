/**
 * Hydrate the auth store from localStorage as soon as the app boots in
 * the browser. SSR cannot access localStorage, so this runs client-only.
 */
export default defineNuxtPlugin(() => {
  const auth = useAuthStore()
  auth.hydrate()
})
