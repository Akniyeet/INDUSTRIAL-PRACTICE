<script setup lang="ts">
/**
 * Retention curve chart — enhanced bar chart showing viewer drop-off.
 * Shows absolute viewers, % of peak, and drop-off from previous interval.
 */
import type { RetentionPoint } from '#shared/api/types'
import { TrendingDown } from 'lucide-vue-next'

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

function barColor(percentage: number): string {
  if (percentage >= 70) return 'bg-emerald-500'
  if (percentage >= 50) return 'bg-brand-500'
  if (percentage >= 30) return 'bg-amber-500'
  return 'bg-red-500'
}

function barBg(percentage: number): string {
  if (percentage >= 70) return 'bg-emerald-50'
  if (percentage >= 50) return 'bg-brand-50'
  if (percentage >= 30) return 'bg-amber-50'
  return 'bg-red-50'
}

function textColor(percentage: number): string {
  if (percentage >= 70) return 'text-emerald-700'
  if (percentage >= 50) return 'text-brand-700'
  if (percentage >= 30) return 'text-amber-700'
  return 'text-red-700'
}

function dropOff(index: number): number | null {
  if (index === 0 || !props.points[index - 1]) return null
  const prev = props.points[index - 1].viewers
  const curr = props.points[index].viewers
  if (prev === 0) return null
  return Math.round(((prev - curr) / prev) * 100)
}

function formatOffset(secs: number): string {
  if (secs === 0) return 'Старт'
  const m = Math.round(secs / 60)
  if (m < 60) return `${m} мин`
  const h = Math.floor(m / 60)
  const rem = m % 60
  return rem === 0 ? `${h} ч` : `${h}ч ${rem}м`
}

const totalDrop = computed(() => {
  if (props.points.length < 2) return null
  const first = props.points[0].viewers
  const last = props.points[props.points.length - 1].viewers
  if (first === 0) return null
  return Math.round(((first - last) / first) * 100)
})

const avgRetention = computed(() => {
  if (props.points.length === 0) return 0
  const avg = props.points.reduce((s, p) => s + pct(p.viewers), 0) / props.points.length
  return Math.round(avg)
})
</script>

<template>
  <div class="card overflow-hidden p-0">
    <!-- Header -->
    <div class="flex items-center justify-between border-b border-slate-100 px-5 py-4">
      <div>
        <h3 class="text-sm font-semibold text-slate-900">Retention кривой</h3>
        <p class="mt-0.5 text-xs text-slate-400">Зрители бойынша уақыт</p>
      </div>
      <div v-if="points.length > 0" class="flex items-center gap-4 text-right">
        <div>
          <p class="text-xs text-slate-400">Орт. retention</p>
          <p class="text-sm font-bold text-slate-700">{{ avgRetention }}%</p>
        </div>
        <div v-if="totalDrop !== null">
          <p class="text-xs text-slate-400">Жалпы кету</p>
          <p class="flex items-center gap-1 text-sm font-bold text-red-600">
            <TrendingDown class="h-3.5 w-3.5" />
            {{ totalDrop }}%
          </p>
        </div>
      </div>
    </div>

    <div class="p-5">
      <div v-if="points.length === 0" class="flex flex-col items-center justify-center py-12 text-center">
        <div class="mb-3 flex h-12 w-12 items-center justify-center rounded-xl bg-slate-100">
          <TrendingDown class="h-6 w-6 text-slate-400" />
        </div>
        <p class="text-sm font-medium text-slate-500">Мәлімет жоқ</p>
        <p class="mt-1 text-xs text-slate-400">Деректер сессия аяқталғаннан кейін пайда болады</p>
      </div>

      <div v-else class="space-y-2.5">
        <div
          v-for="(pt, i) in points"
          :key="pt.offsetSeconds"
          class="group flex items-center gap-3"
        >
          <!-- Time label -->
          <span class="w-14 flex-shrink-0 text-right text-xs font-medium text-slate-500">
            {{ formatOffset(pt.offsetSeconds) }}
          </span>

          <!-- Bar track -->
          <div
            class="relative h-7 flex-1 overflow-hidden rounded-lg"
            :class="barBg(pct(pt.viewers))"
          >
            <!-- Filled bar -->
            <div
              class="absolute inset-y-0 left-0 rounded-lg transition-all duration-700"
              :class="barColor(pct(pt.viewers))"
              :style="{ width: pct(pt.viewers) + '%', opacity: '0.85' }"
            />
            <!-- Viewers count -->
            <span
              class="relative z-10 flex h-full items-center px-3 text-xs font-semibold"
              :class="pct(pt.viewers) > 30 ? 'text-white' : textColor(pct(pt.viewers))"
            >
              {{ pt.viewers.toLocaleString() }}
            </span>
          </div>

          <!-- Percentage -->
          <span
            class="w-12 flex-shrink-0 text-right text-xs font-bold"
            :class="textColor(pct(pt.viewers))"
          >
            {{ pct(pt.viewers) }}%
          </span>

          <!-- Drop indicator -->
          <span
            v-if="dropOff(i) !== null && dropOff(i)! > 0"
            class="w-14 flex-shrink-0 text-right text-[11px] font-medium text-red-500"
          >
            −{{ dropOff(i) }}%
          </span>
          <span v-else class="w-14 flex-shrink-0" />
        </div>
      </div>
    </div>
  </div>
</template>
