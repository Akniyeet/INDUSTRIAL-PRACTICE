<script setup lang="ts">
/**
 * Countdown timer towards a target ISO date. Updates once per second, cleans
 * up its interval on unmount, and emits a `finished` event when the target
 * passes so callers can swap the view (landing → room, waiting → live, etc.).
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

const props = defineProps<{
  target: string // ISO-8601
  compact?: boolean
}>()

const emit = defineEmits<{ (e: 'finished'): void }>()

const now = ref(Date.now())
let timer: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  timer = setInterval(() => {
    now.value = Date.now()
    if (remaining.value.totalMs <= 0 && timer) {
      clearInterval(timer)
      timer = null
      emit('finished')
    }
  }, 1000)
})
onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})

const remaining = computed(() => {
  const target = new Date(props.target).getTime()
  const diff = Math.max(0, target - now.value)
  const days    = Math.floor(diff / (1000 * 60 * 60 * 24))
  const hours   = Math.floor((diff / (1000 * 60 * 60)) % 24)
  const minutes = Math.floor((diff / (1000 * 60)) % 60)
  const seconds = Math.floor((diff / 1000) % 60)
  return { days, hours, minutes, seconds, totalMs: diff }
})

const pad = (n: number) => String(n).padStart(2, '0')
</script>

<template>
  <div
    class="inline-flex items-center gap-2 font-mono tabular-nums"
    :class="compact ? 'text-sm' : 'text-2xl md:text-3xl'"
  >
    <template v-if="remaining.days > 0">
      <span>{{ remaining.days }}<span class="text-slate-400">д</span></span>
      <span class="text-slate-300">:</span>
    </template>
    <span>{{ pad(remaining.hours) }}</span>
    <span class="text-slate-300">:</span>
    <span>{{ pad(remaining.minutes) }}</span>
    <span class="text-slate-300">:</span>
    <span>{{ pad(remaining.seconds) }}</span>
  </div>
</template>
