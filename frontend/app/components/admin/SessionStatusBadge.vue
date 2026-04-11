<script setup lang="ts">
/**
 * Translates {@link SessionStatus} into a coloured pill. LIVE states use the
 * brand tone to draw attention; terminal states use neutral so they visually
 * recede on a crowded session list.
 */
import type { SessionStatus } from '#shared/api'
import { computed } from 'vue'

const props = defineProps<{ status: SessionStatus }>()

const config = computed(() => {
  switch (props.status) {
    case 'SCHEDULED':      return { tone: 'brand'   as const, label: 'Жоспарланған' }
    case 'LIVE':           return { tone: 'danger'  as const, label: 'Эфирде' }
    case 'ENDED':          return { tone: 'neutral' as const, label: 'Аяқталды' }
    case 'CANCELLED':      return { tone: 'neutral' as const, label: 'Бас тартылды' }
    case 'AUTO_SCHEDULED': return { tone: 'brand'   as const, label: 'Авто-жоспар' }
    case 'AUTO_LIVE':      return { tone: 'danger'  as const, label: 'Авто-эфир' }
    case 'AUTO_ENDED':     return { tone: 'neutral' as const, label: 'Авто аяқталды' }
    default:               return { tone: 'neutral' as const, label: String(props.status) }
  }
})
</script>

<template>
  <UiBadge :tone="config.tone" dot>{{ config.label }}</UiBadge>
</template>
