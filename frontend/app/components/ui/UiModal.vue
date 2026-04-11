<script setup lang="ts">
/**
 * Accessible modal dialog on top of Headless UI.
 *
 * <p>We wrap Headless UI to inject our own chrome (card styling, title slot,
 * footer slot) so every modal in the app has identical padding, scrolling
 * behaviour, and close affordances. `v-model` controls visibility.
 */
import {
  Dialog,
  DialogPanel,
  DialogTitle,
  TransitionChild,
  TransitionRoot,
} from '@headlessui/vue'
import { X } from 'lucide-vue-next'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    title?: string
    size?: 'sm' | 'md' | 'lg' | 'xl'
    closable?: boolean
  }>(),
  { size: 'md', closable: true },
)

const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void }>()

function close() {
  if (props.closable) emit('update:modelValue', false)
}

const sizeClasses: Record<NonNullable<typeof props.size>, string> = {
  sm: 'max-w-md',
  md: 'max-w-lg',
  lg: 'max-w-2xl',
  xl: 'max-w-4xl',
}
</script>

<template>
  <TransitionRoot :show="modelValue" as="template">
    <Dialog class="relative z-50" @close="close">
      <TransitionChild
        as="template"
        enter="duration-200 ease-out"
        enter-from="opacity-0"
        enter-to="opacity-100"
        leave="duration-150 ease-in"
        leave-from="opacity-100"
        leave-to="opacity-0"
      >
        <div class="fixed inset-0 bg-slate-900/50 backdrop-blur-sm" aria-hidden="true" />
      </TransitionChild>

      <div class="fixed inset-0 overflow-y-auto">
        <div class="flex min-h-full items-center justify-center p-4">
          <TransitionChild
            as="template"
            enter="duration-200 ease-out-expo"
            enter-from="opacity-0 translate-y-2 scale-95"
            enter-to="opacity-100 translate-y-0 scale-100"
            leave="duration-150 ease-in"
            leave-from="opacity-100 translate-y-0 scale-100"
            leave-to="opacity-0 translate-y-2 scale-95"
          >
            <DialogPanel
              :class="[
                'relative w-full overflow-hidden rounded-2xl bg-white shadow-overlay',
                sizeClasses[size],
              ]"
            >
              <header
                v-if="title || $slots.header"
                class="flex items-start justify-between gap-4 border-b border-slate-100 px-6 py-4"
              >
                <DialogTitle v-if="title" class="text-lg font-semibold text-slate-900">
                  {{ title }}
                </DialogTitle>
                <slot v-else name="header" />
                <button
                  v-if="closable"
                  type="button"
                  class="rounded-md p-1 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-600"
                  aria-label="Close"
                  @click="close"
                >
                  <X class="h-5 w-5" />
                </button>
              </header>

              <div class="px-6 py-5">
                <slot />
              </div>

              <footer
                v-if="$slots.footer"
                class="flex items-center justify-end gap-2 border-t border-slate-100 bg-slate-50/50 px-6 py-3"
              >
                <slot name="footer" />
              </footer>
            </DialogPanel>
          </TransitionChild>
        </div>
      </div>
    </Dialog>
  </TransitionRoot>
</template>
