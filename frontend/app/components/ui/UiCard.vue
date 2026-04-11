<script setup lang="ts">
/**
 * Low-chrome surface for grouping content. The header slot is optional —
 * a card without a header is just a rounded panel. When header content is
 * provided, we add an inset padding and a bottom border so it reads as a
 * section heading.
 *
 * <p>`padded` controls whether the body slot inherits the default 20px padding
 * — useful to disable when the card hosts a table or a full-bleed image.
 */
withDefaults(
  defineProps<{
    title?: string
    subtitle?: string
    padded?: boolean
  }>(),
  { padded: true },
)
</script>

<template>
  <section class="card">
    <header
      v-if="title || $slots.header || $slots.actions"
      class="flex items-start justify-between gap-4 border-b border-slate-100 px-5 py-4"
    >
      <div class="min-w-0">
        <slot name="header">
          <h3 v-if="title" class="truncate text-base font-semibold text-slate-900">{{ title }}</h3>
          <p v-if="subtitle" class="mt-0.5 text-sm text-slate-500">{{ subtitle }}</p>
        </slot>
      </div>
      <div v-if="$slots.actions" class="flex shrink-0 items-center gap-2">
        <slot name="actions" />
      </div>
    </header>

    <div :class="padded && 'p-5'">
      <slot />
    </div>

    <footer
      v-if="$slots.footer"
      class="flex items-center justify-end gap-2 border-t border-slate-100 px-5 py-3"
    >
      <slot name="footer" />
    </footer>
  </section>
</template>
