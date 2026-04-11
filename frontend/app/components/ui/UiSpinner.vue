<script setup lang="ts">
/**
 * Inline spinner built on Lucide's `Loader2`. Default size is 20px to match
 * default body text, but it's overridable for page-level loaders.
 */
import { Loader2 } from 'lucide-vue-next'
import { computed } from 'vue'

type Size = 'xs' | 'sm' | 'md' | 'lg'
const props = withDefaults(defineProps<{ size?: Size }>(), { size: 'md' })

const sizeClasses: Record<Size, string> = {
  xs: 'h-3 w-3',
  sm: 'h-4 w-4',
  md: 'h-5 w-5',
  lg: 'h-8 w-8',
}

const cls = computed(() => ['animate-spin text-slate-400', sizeClasses[props.size]])
</script>

<template>
  <Loader2 :class="cls" />
</template>
