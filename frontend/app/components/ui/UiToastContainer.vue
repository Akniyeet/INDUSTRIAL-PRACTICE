<script setup lang="ts">
/**
 * Floating stack that renders every {@link Toast} from the toast store.
 *
 * <p>Mounted once near the top of the default layout so it survives page
 * transitions. Positioned with a fixed container and a pointer-events-none
 * outer so clicks pass through the gaps between toasts.
 */
import { CheckCircle2, AlertTriangle, Info, XCircle, X } from 'lucide-vue-next'
import { storeToRefs } from 'pinia'

const toastStore = useToastStore()
const { items } = storeToRefs(toastStore)

const toneIcon = {
  success: CheckCircle2,
  error:   XCircle,
  warning: AlertTriangle,
  info:    Info,
}
const toneClasses = {
  success: 'border-success-500/30 bg-success-50 text-success-700',
  error:   'border-danger-500/30 bg-danger-50 text-danger-700',
  warning: 'border-warning-500/30 bg-warning-50 text-warning-700',
  info:    'border-brand-200 bg-brand-50 text-brand-700',
}
</script>

<template>
  <div
    class="pointer-events-none fixed right-4 top-[4.5rem] z-[100] flex flex-col items-end gap-2 sm:right-6"
    aria-live="polite"
    aria-atomic="true"
  >
    <TransitionGroup
      enter-active-class="transition duration-200 ease-out"
      enter-from-class="opacity-0 translate-x-4 scale-95"
      enter-to-class="opacity-100 translate-x-0 scale-100"
      leave-active-class="transition duration-150 ease-in"
      leave-from-class="opacity-100 translate-x-0"
      leave-to-class="opacity-0 translate-x-4"
      tag="div"
      class="flex w-80 flex-col gap-2"
    >
      <div
        v-for="t in items"
        :key="t.id"
        class="pointer-events-auto flex items-start gap-3 rounded-xl border bg-white px-4 py-3 shadow-overlay"
        :class="toneClasses[t.tone]"
        role="status"
      >
        <component :is="toneIcon[t.tone]" class="mt-0.5 h-5 w-5 shrink-0" />
        <div class="min-w-0 flex-1">
          <p class="text-sm font-semibold">{{ t.title }}</p>
          <p v-if="t.description" class="mt-0.5 break-words text-xs opacity-80">
            {{ t.description }}
          </p>
        </div>
        <button
          type="button"
          class="rounded-md p-1 opacity-60 transition-opacity hover:opacity-100"
          aria-label="Dismiss"
          @click="toastStore.dismiss(t.id)"
        >
          <X class="h-4 w-4" />
        </button>
      </div>
    </TransitionGroup>
  </div>
</template>
