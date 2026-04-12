<script setup lang="ts">
/**
 * Retention bar chart — shows how many viewers were still watching at
 * each sample point. Pure CSS/HTML bars, no charting library needed.
 *
 * <p>The backend returns a list of {@code RetentionPoint} with
 * {@code offsetSeconds} and {@code viewers}. We normalise against the
 * first point (the "start" anchor) so bars are relative percentages.
 */
import type { RetentionPoint } from '#shared/api/types'

const props = defineProps<{
  points: RetentionPoint[]
}>()

const maxViewers = computed(() => {
  if (props.points.length === 0) return 1
  return Math.max(...props.points.map((p) => p.viewers), 1)
})

function pct(viewers: number): number {
  return Math.round((viewers / maxViewers.value) * 100)
}

function formatOffset(secs: number): string {
  if (secs === 0) return 'Старт'
  const m = Math.round(secs / 60)
  if (m < 60) return `${m} мин`
  const h = Math.floor(m / 60)
  const rem = m % 60
  return rem === 0 ? `${h} сағ` : `${h}с ${rem}м`
}
</script>

<template>
  <div class="card p-5">
    <h3 class="mb-4 text-sm font-semibold text-slate-900">Retention</h3>

    <div v-if="points.length === 0" class="py-8 text-center text-sm text-slate-400">
      Деректер жоқ
    </div>

    <div v-else class="space-y-2">
      <div
        v-for="pt in points"
        :key="pt.offsetSeconds"
        class="flex items-center gap-3"
      >
        <span class="w-14 flex-shrink-0 text-right text-xs text-slate-500">
          {{ formatOffset(pt.offsetSeconds) }}
        </span>
        <div class="relative h-6 flex-1 overflow-hidden rounded-md bg-slate-100">
          <div
            class="absolute inset-y-0 left-0 rounded-md transition-all duration-500"
            :class="pct(pt.viewers) > 50 ? 'bg-brand-500' : pct(pt.viewers) > 25 ? 'bg-warning-500' : 'bg-danger-500'"
            :style="{ width: pct(pt.viewers) + '%' }"
          />
          <span
            class="relative z-10 flex h-full items-center px-2 text-xs font-medium"
            :class="pct(pt.viewers) > 30 ? 'text-white' : 'text-slate-700'"
          >
            {{ pt.viewers.toLocaleString() }}
          </span>
        </div>
        <span class="w-10 flex-shrink-0 text-right text-xs font-medium text-slate-600">
          {{ pct(pt.viewers) }}%
        </span>
      </div>
    </div>
  </div>
</template>
