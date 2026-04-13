<script setup lang="ts">
/**
 * Full Webizon logo: animated mark + wordmark.
 *
 * Props:
 *  - size: mark size in px (default 36)
 *  - animate: entrance animation (default true)
 *  - dark: use white text for dark backgrounds (default false)
 */
defineProps<{
  size?: number
  animate?: boolean
  dark?: boolean
}>()
</script>

<template>
  <NuxtLink to="/" class="logo-full group inline-flex items-center gap-2.5" :class="{ 'logo-full-animate': animate !== false }">
    <LogoMark :size="size ?? 36" :animate="animate" />
    <span
      class="logo-text text-lg font-bold tracking-tight transition-colors"
      :class="dark ? 'text-white' : 'text-slate-900'"
    >
      Webizon
    </span>
  </NuxtLink>
</template>

<style>
/* NOT scoped — needs to target child elements from LogoMark */
.logo-full-animate .logo-text {
  opacity: 0;
  transform: translateX(-8px);
  animation: logo-text-slide 0.5s cubic-bezier(0.16, 1, 0.3, 1) 0.4s forwards;
}

@keyframes logo-text-slide {
  to {
    opacity: 1;
    transform: translateX(0);
  }
}
</style>
