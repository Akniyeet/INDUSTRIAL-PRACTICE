<script setup lang="ts">
/**
 * Top-line session KPI cards — redesigned dashboard strip.
 * Shows 6 metrics: attendance, long watch, engagement rate,
 * chat messages, CTA clicks, and moderation events.
 */
import type { SessionSummary } from '#shared/api/types'
import { Users, Clock, TrendingUp, MessageSquare, MousePointerClick, ShieldAlert } from 'lucide-vue-next'

const props = defineProps<{
  summary: SessionSummary
}>()

const engagementRate = computed(() => {
  if (!props.summary.totalAttendees) return 0
  return Math.round((props.summary.chatMessages / props.summary.totalAttendees) * 100)
})

const retentionRate = computed(() => {
  if (!props.summary.totalAttendees) return 0
  return Math.round((props.summary.watchedLongCount / props.summary.totalAttendees) * 100)
})

const cards = computed(() => [
  {
    icon: Users,
    label: 'Жалпы қатысушы',
    value: props.summary.totalAttendees.toLocaleString(),
    sub: `${props.summary.presentNow} онлайн`,
    gradient: 'from-brand-500 to-brand-600',
    bg: 'bg-brand-50',
    text: 'text-brand-600',
    ring: 'ring-brand-100',
  },
  {
    icon: Clock,
    label: '>30 мин көрген',
    value: props.summary.watchedLongCount.toLocaleString(),
    sub: `Retention: ${retentionRate.value}%`,
    gradient: 'from-emerald-500 to-teal-600',
    bg: 'bg-emerald-50',
    text: 'text-emerald-600',
    ring: 'ring-emerald-100',
  },
  {
    icon: TrendingUp,
    label: 'Белсенділік',
    value: `${engagementRate.value}%`,
    sub: 'хабарлама / қатысушы',
    gradient: 'from-violet-500 to-purple-600',
    bg: 'bg-violet-50',
    text: 'text-violet-600',
    ring: 'ring-violet-100',
  },
  {
    icon: MessageSquare,
    label: 'Чат хабарламалары',
    value: props.summary.chatMessages.toLocaleString(),
    sub: 'барлық хабарламалар',
    gradient: 'from-sky-500 to-blue-600',
    bg: 'bg-sky-50',
    text: 'text-sky-600',
    ring: 'ring-sky-100',
  },
  {
    icon: MousePointerClick,
    label: 'CTA кликтер',
    value: props.summary.ctaClicks.toLocaleString(),
    sub: 'барлық кликтер',
    gradient: 'from-orange-500 to-amber-600',
    bg: 'bg-orange-50',
    text: 'text-orange-600',
    ring: 'ring-orange-100',
  },
  {
    icon: ShieldAlert,
    label: 'Модерация',
    value: props.summary.moderationEventCount.toLocaleString(),
    sub: 'модерация оқиғалары',
    gradient: 'from-red-500 to-rose-600',
    bg: 'bg-red-50',
    text: 'text-red-600',
    ring: 'ring-red-100',
  },
])
</script>

<template>
  <div class="grid grid-cols-2 gap-3 sm:grid-cols-3 xl:grid-cols-6">
    <div
      v-for="c in cards"
      :key="c.label"
      class="group relative overflow-hidden rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm ring-1 transition-shadow hover:shadow-md"
      :class="c.ring"
    >
      <!-- Gradient accent bar -->
      <div
        class="absolute inset-x-0 top-0 h-0.5 rounded-t-2xl bg-gradient-to-r"
        :class="c.gradient"
      />

      <!-- Icon -->
      <div
        class="mb-3 inline-flex h-10 w-10 items-center justify-center rounded-xl"
        :class="c.bg"
      >
        <component :is="c.icon" class="h-5 w-5" :class="c.text" />
      </div>

      <!-- Value -->
      <p class="text-2xl font-bold tracking-tight text-slate-900">{{ c.value }}</p>

      <!-- Label -->
      <p class="mt-0.5 text-xs font-medium text-slate-600">{{ c.label }}</p>

      <!-- Sub-label -->
      <p class="mt-1 text-[11px] text-slate-400">{{ c.sub }}</p>
    </div>
  </div>
</template>
