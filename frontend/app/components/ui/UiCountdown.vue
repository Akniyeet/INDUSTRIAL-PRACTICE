<script setup lang="ts">
/**
 * Block-style countdown timer with two themes.
 *
 * <ul>
 *   <li>{@code light} (default) — white cards with a soft blue glow,
 *       used on EDUSER-style public landings over a pale background.</li>
 *   <li>{@code dark} — glassmorphic cards with a frosted-blue surface,
 *       used on the "Broadcast Universe" cinematic hero so the timer
 *       matches the orbital/dark aesthetic.</li>
 * </ul>
 *
 * <p>Four blocks (Days / Hours / Minutes / Seconds), tick once per
 * second, emit {@code finished} when the target passes so the parent
 * can swap views (landing → room, waiting → live).
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

const props = withDefaults(defineProps<{
  target: string // ISO-8601
  compact?: boolean
  theme?: 'light' | 'dark'
}>(), { theme: 'light' })

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

const blocks = computed(() => [
  { value: pad(remaining.value.days),    label: 'Күн'    },
  { value: pad(remaining.value.hours),   label: 'Сағат'  },
  { value: pad(remaining.value.minutes), label: 'Минут'  },
  { value: pad(remaining.value.seconds), label: 'Секунд' },
])
</script>

<template>
  <div
    class="eduser-countdown"
    :class="[
      { 'eduser-countdown--compact': compact },
      `eduser-countdown--${theme}`,
    ]"
  >
    <template v-for="(b, i) in blocks" :key="b.label">
      <div class="eduser-countdown__block">
        <div class="eduser-countdown__value">{{ b.value }}</div>
        <div class="eduser-countdown__label">{{ b.label }}</div>
      </div>
      <div v-if="i < blocks.length - 1" class="eduser-countdown__sep">:</div>
    </template>
  </div>
</template>

<style scoped>
/*
 * Shared structure for both themes. The block card itself and the
 * separator change via theme-scoped selectors below.
 */
.eduser-countdown {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-variant-numeric: tabular-nums;
}

.eduser-countdown__block {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-width: 72px;
  padding: 14px 12px;
  border-radius: 14px;
}

.eduser-countdown__value {
  font-size: 32px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: -0.02em;
}

.eduser-countdown__label {
  margin-top: 6px;
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.eduser-countdown__sep {
  font-size: 28px;
  font-weight: 700;
  line-height: 1;
  transform: translateY(-10px);
}

/* ───────────────────────── Light theme (EDUSER cards) ───────── */
.eduser-countdown--light .eduser-countdown__block {
  background: rgba(255, 255, 255, 0.95);
  border: 1px solid rgba(33, 92, 255, 0.14);
  box-shadow: 0 4px 18px rgba(102, 141, 255, 0.18);
}
.eduser-countdown--light .eduser-countdown__value {
  color: #1f64d3;
}
.eduser-countdown--light .eduser-countdown__label {
  color: #8e8fa3;
}
.eduser-countdown--light .eduser-countdown__sep {
  color: #c5cce0;
}

/* ───────────────────────── Dark theme (Broadcast Universe) ──── */
.eduser-countdown--dark .eduser-countdown__block {
  background: linear-gradient(
    180deg,
    rgba(255, 255, 255, 0.06) 0%,
    rgba(255, 255, 255, 0.02) 100%
  );
  border: 1px solid rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  box-shadow:
    0 10px 40px rgba(33, 92, 255, 0.18),
    inset 0 1px 0 rgba(255, 255, 255, 0.05);
  transition: border-color 0.3s ease, transform 0.3s ease;
}
.eduser-countdown--dark .eduser-countdown__block:hover {
  border-color: rgba(33, 92, 255, 0.35);
  transform: translateY(-2px);
}
.eduser-countdown--dark .eduser-countdown__value {
  background: linear-gradient(180deg, #ffffff 0%, #b8c5ff 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}
.eduser-countdown--dark .eduser-countdown__label {
  color: rgba(255, 255, 255, 0.4);
}
.eduser-countdown--dark .eduser-countdown__sep {
  color: rgba(255, 255, 255, 0.2);
}

/* ───────────────────────── Compact variant ──────────────────── */
.eduser-countdown--compact {
  gap: 6px;
}
.eduser-countdown--compact .eduser-countdown__block {
  min-width: 50px;
  padding: 8px 6px;
  border-radius: 10px;
}
.eduser-countdown--compact .eduser-countdown__value {
  font-size: 20px;
}
.eduser-countdown--compact .eduser-countdown__label {
  font-size: 9px;
}
.eduser-countdown--compact .eduser-countdown__sep {
  font-size: 18px;
  transform: translateY(-6px);
}

/* ───────────────────────── Desktop upscale ──────────────────── */
@media (min-width: 768px) {
  .eduser-countdown:not(.eduser-countdown--compact) {
    gap: 14px;
  }
  .eduser-countdown:not(.eduser-countdown--compact) .eduser-countdown__block {
    min-width: 96px;
    padding: 20px 16px;
    border-radius: 18px;
  }
  .eduser-countdown:not(.eduser-countdown--compact) .eduser-countdown__value {
    font-size: 44px;
  }
  .eduser-countdown:not(.eduser-countdown--compact) .eduser-countdown__label {
    font-size: 12px;
  }
}
</style>
