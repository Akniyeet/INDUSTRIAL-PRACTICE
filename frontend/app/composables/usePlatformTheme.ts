/**
 * Manages light/dark theme for the Platform Admin layout.
 * Persists preference to localStorage and applies `.dark` class
 * on the layout root element.
 */
const STORAGE_KEY = 'webizon:platform-theme'

const isDark = ref(false)

export function usePlatformTheme() {
  function hydrate() {
    if (import.meta.server) return
    const saved = localStorage.getItem(STORAGE_KEY)
    isDark.value = saved === 'dark'
  }

  function toggle() {
    isDark.value = !isDark.value
    if (import.meta.client) {
      localStorage.setItem(STORAGE_KEY, isDark.value ? 'dark' : 'light')
    }
  }

  return { isDark, toggle, hydrate }
}
