<script setup lang="ts">
/**
 * Admin page header — the standard top-of-page block used across every admin
 * route. Renders (in order):
 *
 * <ol>
 *   <li>An optional breadcrumb trail (last item is shown as plain text).</li>
 *   <li>The page title in the brand display face.</li>
 *   <li>An optional muted subtitle.</li>
 *   <li>An optional right-aligned slot for action buttons.</li>
 * </ol>
 *
 * <p>Originally this component silently swallowed title/subtitle and only
 * rendered the actions slot. Pages relied on those props but got blank
 * output, which made the admin shell feel half-finished. This implementation
 * wires them up with a tidy, restrained layout that matches the rest of the
 * admin UI.
 */
defineProps<{
  title: string
  subtitle?: string
  breadcrumbs?: { label: string; to?: string }[]
}>()
</script>

<template>
  <header class="mb-6 flex flex-col gap-3 border-b border-slate-200/70 pb-5 sm:flex-row sm:items-end sm:justify-between">
    <div class="min-w-0">
      <!-- Title -->
      <h1 class="truncate text-2xl font-bold tracking-tight text-slate-900 sm:text-[28px]">
        {{ title }}
      </h1>

      <!-- Subtitle -->
      <p v-if="subtitle" class="mt-1.5 max-w-2xl text-sm text-slate-500">
        {{ subtitle }}
      </p>
    </div>

    <div v-if="$slots.actions" class="flex shrink-0 items-center justify-end gap-2">
      <slot name="actions" />
    </div>
  </header>
</template>
