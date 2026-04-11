<script setup lang="ts">
/**
 * Primary, secondary, ghost, and danger buttons.
 *
 * <p>We route every button — including ones used as `<NuxtLink>` — through
 * this component so the hover, focus, and loading states stay consistent.
 * The `to` prop is a pass-through: if present, we render a `<NuxtLink>`
 * instead of a `<button>` so admin navigation keeps SSR links.
 */
import { Loader2 } from 'lucide-vue-next'
import { computed } from 'vue'

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'outline'
type Size = 'sm' | 'md' | 'lg'

const props = withDefaults(
  defineProps<{
    variant?: Variant
    size?: Size
    type?: 'button' | 'submit' | 'reset'
    disabled?: boolean
    loading?: boolean
    block?: boolean
    to?: string
    href?: string
  }>(),
  {
    variant: 'primary',
    size: 'md',
    type: 'button',
    disabled: false,
    loading: false,
    block: false,
    to: undefined,
    href: undefined,
  },
)

defineEmits<{ (e: 'click', ev: MouseEvent): void }>()

const base = 'inline-flex items-center justify-center gap-2 rounded-lg font-medium transition-all duration-150 focus:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-brand-500 disabled:opacity-60 disabled:cursor-not-allowed'

const sizeClasses: Record<Size, string> = {
  sm: 'px-3 py-1.5 text-xs',
  md: 'px-4 py-2 text-sm',
  lg: 'px-5 py-2.5 text-base',
}

const variantClasses: Record<Variant, string> = {
  primary:   'bg-brand-600 text-white hover:bg-brand-700 shadow-sm',
  secondary: 'bg-slate-100 text-slate-900 hover:bg-slate-200',
  ghost:     'bg-transparent text-slate-700 hover:bg-slate-100',
  outline:   'border border-slate-300 bg-white text-slate-700 hover:bg-slate-50',
  danger:    'bg-danger-600 text-white hover:bg-danger-700 shadow-sm',
}

const classes = computed(() => [
  base,
  sizeClasses[props.size],
  variantClasses[props.variant],
  props.block && 'w-full',
])

const effectiveDisabled = computed(() => props.disabled || props.loading)
const element = computed<'button' | 'a'>(() => (props.to || props.href ? 'a' : 'button'))
</script>

<template>
  <NuxtLink v-if="to" :to="to" :class="classes">
    <Loader2 v-if="loading" class="h-4 w-4 animate-spin" />
    <slot />
  </NuxtLink>
  <a v-else-if="href" :href="href" :class="classes">
    <Loader2 v-if="loading" class="h-4 w-4 animate-spin" />
    <slot />
  </a>
  <button
    v-else
    :type="type"
    :disabled="effectiveDisabled"
    :class="classes"
    @click="(ev) => $emit('click', ev)"
  >
    <Loader2 v-if="loading" class="h-4 w-4 animate-spin" />
    <slot />
  </button>
</template>
