<script setup lang="ts">
/**
 * Full-screen or inline loading indicator using the animated Webizon logo.
 * Replaces generic spinners with branded loading experience.
 *
 * Props:
 *  - size: logo size in px (default 64)
 *  - fullscreen: fill the entire viewport (default false)
 *  - text: optional loading message below the logo
 */
defineProps<{
  size?: number
  fullscreen?: boolean
  text?: string
}>()
</script>

<template>
  <div
    class="flex flex-col items-center justify-center gap-4"
    :class="fullscreen ? 'fixed inset-0 z-[9999] bg-white/90 backdrop-blur-sm' : ''"
  >
    <LogoMark :size="size ?? 64" :animate="true" :pulse="true" />
    <p v-if="text" class="text-sm font-medium text-slate-500 animate-pulse">
      {{ text }}
    </p>
  </div>
</template>
