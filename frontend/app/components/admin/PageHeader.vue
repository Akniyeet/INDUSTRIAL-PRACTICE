<script setup lang="ts">
/**
 * Standard page header used at the top of every admin view.
 *
 * <p>Accepts a title, an optional subtitle, and an `actions` slot for the
 * primary and secondary buttons on the right. The `breadcrumbs` prop drives
 * a ChevronRight-separated trail of navigable crumbs above the title.
 */
import { ChevronRight } from 'lucide-vue-next'

interface Crumb {
  label: string
  to?: string
}

defineProps<{
  title: string
  subtitle?: string
  breadcrumbs?: Crumb[]
}>()
</script>

<template>
  <header class="mb-6 flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
    <div class="min-w-0">
      <nav v-if="breadcrumbs?.length" class="mb-2 flex items-center gap-1 text-xs text-slate-500">
        <template v-for="(crumb, i) in breadcrumbs" :key="i">
          <NuxtLink
            v-if="crumb.to"
            :to="crumb.to"
            class="transition-colors hover:text-slate-900"
          >
            {{ crumb.label }}
          </NuxtLink>
          <span v-else class="text-slate-700">{{ crumb.label }}</span>
          <ChevronRight v-if="i < breadcrumbs.length - 1" class="h-3.5 w-3.5 text-slate-300" />
        </template>
      </nav>

      <h1 class="truncate text-2xl font-semibold text-slate-900">{{ title }}</h1>
      <p v-if="subtitle" class="mt-1 text-sm text-slate-500">{{ subtitle }}</p>
    </div>
    <div v-if="$slots.actions" class="flex shrink-0 items-center gap-2">
      <slot name="actions" />
    </div>
  </header>
</template>
