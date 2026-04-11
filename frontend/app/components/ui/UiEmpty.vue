<script setup lang="ts">
/**
 * Generic empty-state panel. Pass an icon component via the default slot's
 * `icon` named slot, a short headline via `title`, and helper copy via
 * `description`. Actions (buttons) go in the `actions` slot.
 */
defineProps<{
  title: string
  description?: string
}>()
</script>

<template>
  <div
    class="flex flex-col items-center justify-center gap-3 rounded-2xl border border-dashed border-slate-200 bg-white/60 px-6 py-12 text-center"
  >
    <div
      v-if="$slots.icon"
      class="flex h-12 w-12 items-center justify-center rounded-full bg-slate-100 text-slate-500"
    >
      <slot name="icon" />
    </div>
    <h3 class="text-base font-semibold text-slate-900">{{ title }}</h3>
    <p v-if="description" class="max-w-md text-sm text-slate-500">{{ description }}</p>
    <div v-if="$slots.actions" class="mt-2 flex items-center gap-2">
      <slot name="actions" />
    </div>
  </div>
</template>
