import { defineStore } from 'pinia'

/**
 * Transient notification store.
 *
 * <p>Every piece of UI that needs to surface a non-blocking message (success,
 * error, warning, info) pushes here. The {@code UiToastContainer} mounted in
 * the default layout reads this list and renders the stack. Toasts auto-
 * dismiss after a per-tone default but can be pinned by passing `timeout: 0`.
 *
 * <p>A toast id is required for programmatic dismissal (useful for a "saving…"
 * → "saved" pattern). If the caller doesn't care, we generate a random one.
 */

export type ToastTone = 'success' | 'error' | 'warning' | 'info'

export interface Toast {
  id: string
  tone: ToastTone
  title: string
  description?: string
  /** milliseconds; 0 = sticky */
  timeout: number
  createdAt: number
}

const DEFAULT_TIMEOUT: Record<ToastTone, number> = {
  success: 3000,
  error:   6000,
  warning: 5000,
  info:    4000,
}

export const useToastStore = defineStore('toast', {
  state: () => ({
    items: [] as Toast[],
  }),

  actions: {
    push(payload: {
      tone: ToastTone
      title: string
      description?: string
      timeout?: number
      id?: string
    }): string {
      const id = payload.id ?? Math.random().toString(36).slice(2, 10)
      const timeout = payload.timeout ?? DEFAULT_TIMEOUT[payload.tone]
      this.items.push({
        id,
        tone: payload.tone,
        title: payload.title,
        description: payload.description,
        timeout,
        createdAt: Date.now(),
      })
      if (timeout > 0 && import.meta.client) {
        setTimeout(() => this.dismiss(id), timeout)
      }
      return id
    },

    success(title: string, description?: string) {
      return this.push({ tone: 'success', title, description })
    },
    error(title: string, description?: string) {
      return this.push({ tone: 'error', title, description })
    },
    warning(title: string, description?: string) {
      return this.push({ tone: 'warning', title, description })
    },
    info(title: string, description?: string) {
      return this.push({ tone: 'info', title, description })
    },

    dismiss(id: string) {
      const i = this.items.findIndex(t => t.id === id)
      if (i >= 0) this.items.splice(i, 1)
    },

    clear() {
      this.items = []
    },
  },
})
