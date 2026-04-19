<script setup lang="ts">
/**
 * Shimmering placeholder used during data fetches. Pass explicit width/height
 * via the `w` and `h` props (Tailwind classes) so consumers can build
 * skeletons that match real content dimensions.
 */
withDefaults(
  defineProps<{
    w?: string
    h?: string
    rounded?: string
  }>(),
  { w: 'w-full', h: 'h-4', rounded: 'rounded' },
)
</script>

<template>
  <div
    class="animate-pulse bg-slate-200/80"
    :class="[w, h, rounded]"
  />
</template>
