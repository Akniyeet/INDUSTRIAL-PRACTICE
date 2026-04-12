<script setup lang="ts">
/**
 * Top-line session counters — the horizontal strip of big numbers at
 * the top of the analytics dashboard. Each card is a single KPI.
 */
import type { SessionSummary } from '#shared/api/types'
import { Users, Eye, MessageSquare, MousePointerClick, ShieldAlert } from 'lucide-vue-next'

const props = defineProps<{
  summary: SessionSummary
}>()

const cards = computed(() => [
  {
    icon: Users,
    label: 'Жалпы көрермен',
    value: props.summary.totalAttendees,
    color: 'text-brand-600 bg-brand-50',
  },
  {
    icon: Eye,
    label: '> 30 мин көрген',
    value: props.summary.watchedLongCount,
    color: 'text-success-600 bg-success-50',
  },
  {
    icon: MessageSquare,
    label: 'Чат хабарламалар',
    value: props.summary.chatMessages,
    color: 'text-brand-600 bg-brand-50',
  },
  {
    icon: MousePointerClick,
    label: 'CTA кликтер',
    value: props.summary.ctaClicks,
    color: 'text-warning-600 bg-warning-50',
  },
  {
    icon: ShieldAlert,
    label: 'Модерация',
    value: props.summary.moderationEventCount,
    color: 'text-danger-600 bg-danger-50',
  },
])
</script>

<template>
  <div class="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
    <div
      v-for="c in cards"
      :key="c.label"
      class="card p-4"
    >
      <div class="flex items-center gap-3">
        <div
          class="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-lg"
          :class="c.color"
        >
          <component :is="c.icon" class="h-4 w-4" />
        </div>
        <div class="min-w-0">
          <p class="text-2xl font-bold text-slate-900">{{ c.value.toLocaleString() }}</p>
          <p class="truncate text-xs text-slate-500">{{ c.label }}</p>
        </div>
      </div>
    </div>
  </div>
</template>
