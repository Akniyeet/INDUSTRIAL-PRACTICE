<script setup lang="ts">
/**
 * Waiting room at {@code /e/{tenant}/{slug}/waiting}.
 *
 * <p>Immersive pre-session experience that retains viewers before the
 * broadcast starts. Auto-redirects to the room when the session goes live.
 * Polls the resolution endpoint every 15 seconds to detect state changes.
 *
 * <p>Features:
 * - Cover image with gradient overlay
 * - Large countdown timer
 * - Event title and speaker info
 * - "Preparing your questions" prompt
 * - Auto-redirect when session starts
 */
import type { PublicEventView, PublicSessionView } from '#shared/api/types'
import { Users, MessageSquare, Clock, Radio } from 'lucide-vue-next'
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'

definePageMeta({
  layout: 'event',
  middleware: 'auth',
})

const route = useRoute()
const router = useRouter()
const api = useApi()

const tenantSlug = computed(() => String(route.params.tenant))
const eventSlug = computed(() => String(route.params.slug))

const event = ref<PublicEventView | null>(null)
const nextSession = ref<PublicSessionView | null>(null)
const loading = ref(true)

async function checkState() {
  try {
    const data = await api.publicEvents.resolve(tenantSlug.value, eventSlug.value)

    if (data.state === 'LIVE_NOW') {
      // Session started — redirect to room
      router.replace(`/e/${tenantSlug.value}/${eventSlug.value}/room`)
      return
    }

    if (data.state === 'UNAVAILABLE') {
      router.replace(`/e/${tenantSlug.value}/${eventSlug.value}`)
      return
    }

    event.value = data.event
    nextSession.value = data.nextSession ?? data.activeSession
  } catch {
    // Silently retry on next tick
  } finally {
    loading.value = false
  }
}

// Poll every 15 seconds
let pollInterval: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  checkState()
  pollInterval = setInterval(checkState, 15000)
})

onBeforeUnmount(() => {
  if (pollInterval) clearInterval(pollInterval)
})

// Countdown finished handler — check immediately
function onCountdownFinished() {
  checkState()
}

// Formatters
const dateFmt = new Intl.DateTimeFormat('kk-KZ', {
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  hour: '2-digit',
  minute: '2-digit',
})

function formatStart(iso: string): string {
  try { return dateFmt.format(new Date(iso)) } catch { return iso }
}

function formatDuration(seconds: number): string {
  const m = Math.round(seconds / 60)
  if (m < 60) return `${m} минут`
  const h = Math.floor(m / 60)
  const rem = m % 60
  return rem === 0 ? `${h} сағат` : `${h} сағ ${rem} мин`
}

useHead(() => ({
  title: event.value
    ? `Күту бөлмесі — ${event.value.title}`
    : 'Күту бөлмесі',
}))
</script>

<template>
  <div class="relative min-h-screen">
    <!-- Background -->
    <div class="absolute inset-0 -z-10">
      <div
        v-if="event?.coverImageUrl"
        class="absolute inset-0 bg-cover bg-center blur-2xl brightness-[0.3]"
        :style="{ backgroundImage: `url(${event.coverImageUrl})` }"
      />
      <div v-else class="absolute inset-0 bg-gradient-to-br from-slate-900 via-brand-950 to-slate-900" />
      <div class="absolute inset-0 bg-black/30" />
    </div>

    <!-- Loading -->
    <div v-if="loading" class="flex min-h-screen items-center justify-center">
      <div class="text-center">
        <div class="mx-auto h-10 w-10 animate-spin rounded-full border-4 border-white/20 border-t-white" />
        <p class="mt-4 text-sm text-white/60">Жүктелуде...</p>
      </div>
    </div>

    <!-- Content -->
    <div v-else-if="event" class="flex min-h-screen flex-col items-center justify-center px-4 py-12">
      <div class="w-full max-w-2xl text-center">
        <!-- Cover image -->
        <div
          v-if="event.coverImageUrl"
          class="mx-auto mb-8 w-full max-w-md overflow-hidden rounded-2xl shadow-2xl"
        >
          <img
            :src="event.coverImageUrl"
            :alt="event.title"
            class="aspect-video w-full object-cover"
          />
        </div>

        <!-- Event title -->
        <h1 class="text-3xl font-bold text-white md:text-4xl">
          {{ event.title }}
        </h1>

        <!-- Speaker -->
        <div v-if="event.speakerName" class="mt-3 flex items-center justify-center gap-2">
          <div class="flex h-8 w-8 items-center justify-center rounded-full bg-white/20 text-sm font-semibold text-white">
            {{ event.speakerName.slice(0, 1).toUpperCase() }}
          </div>
          <span class="text-sm font-medium text-white/80">{{ event.speakerName }}</span>
        </div>

        <!-- Session info -->
        <div v-if="nextSession" class="mt-6 space-y-6">
          <!-- Start time -->
          <p class="text-lg text-white/70">
            {{ formatStart(nextSession.startTime) }}
          </p>

          <!-- Countdown -->
          <div class="mx-auto inline-block rounded-2xl bg-white/10 px-8 py-6 backdrop-blur-sm">
            <p class="mb-3 text-xs font-medium uppercase tracking-widest text-white/50">
              Эфір басталуына
            </p>
            <UiCountdown
              :target="nextSession.startTime"
              class="text-5xl font-bold text-white md:text-6xl"
              @finished="onCountdownFinished"
            />
          </div>

          <!-- Duration hint -->
          <p class="text-sm text-white/50">
            <Clock class="mr-1 inline h-3.5 w-3.5" />
            ~{{ formatDuration(nextSession.plannedDurationSeconds) }}
          </p>
        </div>

        <!-- Tips -->
        <div class="mt-10 grid gap-3 sm:grid-cols-3">
          <div class="rounded-xl bg-white/5 px-4 py-3 backdrop-blur-sm">
            <MessageSquare class="mx-auto h-5 w-5 text-white/40" />
            <p class="mt-2 text-xs text-white/60">Сұрақтарыңызды дайындаңыз</p>
          </div>
          <div class="rounded-xl bg-white/5 px-4 py-3 backdrop-blur-sm">
            <Users class="mx-auto h-5 w-5 text-white/40" />
            <p class="mt-2 text-xs text-white/60">Достарыңызды шақырыңыз</p>
          </div>
          <div class="rounded-xl bg-white/5 px-4 py-3 backdrop-blur-sm">
            <Radio class="mx-auto h-5 w-5 text-white/40" />
            <p class="mt-2 text-xs text-white/60">Эфірде сізді күтеміз</p>
          </div>
        </div>

        <!-- Back link -->
        <NuxtLink
          :to="`/e/${tenantSlug}/${eventSlug}`"
          class="mt-8 inline-flex text-sm text-white/40 transition-colors hover:text-white/70"
        >
          ← Ивент бетіне оралу
        </NuxtLink>
      </div>
    </div>

    <!-- No session -->
    <div v-else class="flex min-h-screen items-center justify-center px-4">
      <div class="text-center">
        <h2 class="text-xl font-semibold text-white">Эфір жоспарланбаған</h2>
        <p class="mt-2 text-sm text-white/60">Қазіргі уақытта белсенді сессия жоқ.</p>
        <NuxtLink
          :to="`/e/${tenantSlug}/${eventSlug}`"
          class="mt-4 inline-flex text-sm text-white/50 hover:text-white"
        >
          ← Ивент бетіне оралу
        </NuxtLink>
      </div>
    </div>
  </div>
</template>
