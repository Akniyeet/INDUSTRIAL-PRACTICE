<script setup lang="ts">
/**
 * Compact status pill. Tone matches semantic colours from the Tailwind config;
 * "neutral" is the default so a badge without any tone still looks correct on
 * white surfaces.
 */
import { computed } from 'vue'

type Tone = 'neutral' | 'brand' | 'success' | 'warning' | 'danger'

const props = withDefaults(
  defineProps<{
    tone?: Tone
    dot?: boolean
  }>(),
  { tone: 'neutral', dot: false },
)

const toneClasses: Record<Tone, string> = {
  neutral: 'bg-slate-50 text-slate-700 border-slate-200',
  brand:   'bg-brand-50 text-brand-700 border-brand-200',
  success: 'bg-success-50 text-success-700 border-success-500/20',
  warning: 'bg-warning-50 text-warning-700 border-warning-500/20',
  danger:  'bg-danger-50 text-danger-700 border-danger-500/20',
}

const dotClass: Record<Tone, string> = {
  neutral: 'bg-slate-400',
  brand:   'bg-brand-500',
  success: 'bg-success-500',
  warning: 'bg-warning-500',
  danger:  'bg-danger-500',
}

const classes = computed(() => ['badge', toneClasses[props.tone]])
const dotCls = computed(() => ['h-1.5 w-1.5 rounded-full', dotClass[props.tone]])
</script>

<template>
  <span :class="classes">
    <span v-if="dot" :class="dotCls" />
    <slot />
  </span>
</template>
