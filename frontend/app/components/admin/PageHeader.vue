<script setup lang="ts">
defineProps<{
  title: string
  subtitle?: string
  breadcrumbs?: { label: string; to?: string }[]
}>()
</script>

<template>
  <div v-if="$slots.actions" class="mb-6 flex shrink-0 items-center justify-end gap-2">
    <slot name="actions" />
  </div>
</template>
