<script setup lang="ts">
/**
 * Translates {@link EventStatus} into a coloured pill. Single source of truth
 * for the Kazakh labels and tones so that a rename in the backend never
 * leaves half the frontend showing the old value.
 */
import type { EventStatus } from '~/shared/api'
import { computed } from 'vue'

const props = defineProps<{ status: EventStatus }>()

const config = computed(() => {
  switch (props.status) {
    case 'DRAFT':     return { tone: 'neutral' as const, label: 'Жоба' }
    case 'PUBLISHED': return { tone: 'success' as const, label: 'Жарияланған' }
    case 'ARCHIVED':  return { tone: 'warning' as const, label: 'Мұрағатта' }
  }
})
</script>

<template>
  <UiBadge :tone="config.tone" dot>{{ config.label }}</UiBadge>
</template>
