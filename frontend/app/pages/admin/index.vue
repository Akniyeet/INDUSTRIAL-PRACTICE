<script setup lang="ts">
/**
 * Admin dashboard at {@code /admin}.
 *
 * <p>Aggregates live operational data: active sessions, upcoming schedule,
 * event counts, and quick-action shortcuts. Loads all events + sessions on
 * mount and computes summary stats.
 */
import type { EventResponse, SessionResponse } from '#shared/api/types'
import {
  Calendar,
  Radio,
  Users,
  ArrowRight,
  Settings,
  BarChart3,
  Clock,
  Eye,
  RefreshCw,
} from 'lucide-vue-next'
import { ref, computed } from 'vue'
import { format, formatDistanceToNow } from 'date-fns'

definePageMeta({
  layout: 'admin',
  middleware: 'auth',
})

useHead({ title: 'Главная — Webizon' })

const auth = useAuthStore()
const api = useApi()
const displayName = computed(() => auth.user?.fullName || auth.user?.email?.split('@')[0] || 'друг')

// ---------------------------------------------------------------------------
// Data
// ---------------------------------------------------------------------------

interface SessionWithEvent extends SessionResponse {
  eventTitle: string
}

const events = ref<EventResponse[]>([])
const sessions = ref<SessionWithEvent[]>([])
const loading = ref(true)

async function refresh() {
  loading.value = true
  try {
    const evts = await api.events.list()
    events.value = evts
    const allSessions: SessionWithEvent[] = []
    await Promise.all(
      evts.map(async (ev: EventResponse) => {
        try {
          const ss = await api.sessions.listByEvent(ev.id)
          for (const s of ss) {
            allSessions.push({ ...s, eventTitle: ev.title })
          }
        } catch { /* skip */ }
      }),
    )
    sessions.value = allSessions
  } catch { /* silently fail */ }
  finally { loading.value = false }
}

onMounted(refresh)

// ---------------------------------------------------------------------------
// Computed stats
// ---------------------------------------------------------------------------

const liveSessions = computed(() =>
  sessions.value.filter(s => s.status === 'LIVE' || s.status === 'AUTO_LIVE'),
)

const upcomingSessions = computed(() =>
  sessions.value
    .filter(s => s.status === 'SCHEDULED' || s.status === 'AUTO_SCHEDULED')
    .sort((a, b) => new Date(a.startTime).getTime() - new Date(b.startTime).getTime())
    .slice(0, 5),
)

const endedCount = computed(() =>
  sessions.value.filter(s => s.status === 'ENDED' || s.status === 'AUTO_ENDED').length,
)

const publishedEvents = computed(() =>
  events.value.filter(e => e.status === 'PUBLISHED').length,
)

const draftEvents = computed(() =>
  events.value.filter(e => e.status === 'DRAFT').length,
)

// ---------------------------------------------------------------------------
// Formatters
// ---------------------------------------------------------------------------

function formatDateTime(iso: string) {
  try { return format(new Date(iso), 'dd.MM.yyyy HH:mm') } catch { return iso }
}

function formatRelative(iso: string) {
  try { return formatDistanceToNow(new Date(iso), { addSuffix: true }) } catch { return '' }
}

// ---------------------------------------------------------------------------
// Shortcuts
// ---------------------------------------------------------------------------

const shortcuts = [
  {
    to: '/admin/events',
    title: 'Мероприятия',
    description: 'Создание и редактирование мероприятий.',
    icon: Calendar,
  },
  {
    to: '/admin/sessions',
    title: 'Сессии',
    description: 'Запланированные и текущие трансляции.',
    icon: Radio,
  },
  {
    to: '/admin/members',
    title: 'Команда',
    description: 'Пригласите коллег и управляйте ролями.',
    icon: Users,
  },
  {
    to: '/admin/settings',
    title: 'Настройки',
    description: 'Настройки рабочего пространства.',
    icon: Settings,
  },
]
</script>

<template>
  <div class="space-y-6">
    <!-- Header -->
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-semibold text-slate-900">Привет, {{ displayName }}</h1>
        <p class="mt-1 text-sm text-slate-500">Панель управления Webizon</p>
      </div>
      <UiButton variant="ghost" size="sm" :disabled="loading" @click="refresh">
        <RefreshCw class="h-4 w-4" :class="loading && 'animate-spin'" />
      </UiButton>
    </div>

    <!-- Stats strip -->
    <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
      <UiCard>
        <div class="flex items-center gap-3">
          <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-danger-50 text-danger-600">
            <Radio class="h-5 w-5" />
          </div>
          <div>
            <p class="text-2xl font-bold text-slate-900">{{ liveSessions.length }}</p>
            <p class="text-xs text-slate-500">В эфире</p>
          </div>
        </div>
      </UiCard>
      <UiCard>
        <div class="flex items-center gap-3">
          <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-brand-50 text-brand-600">
            <Clock class="h-5 w-5" />
          </div>
          <div>
            <p class="text-2xl font-bold text-slate-900">{{ upcomingSessions.length }}</p>
            <p class="text-xs text-slate-500">Предстоящие сессии</p>
          </div>
        </div>
      </UiCard>
      <UiCard>
        <div class="flex items-center gap-3">
          <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-success-50 text-success-600">
            <Eye class="h-5 w-5" />
          </div>
          <div>
            <p class="text-2xl font-bold text-slate-900">{{ publishedEvents }}</p>
            <p class="text-xs text-slate-500">Опубликованных</p>
          </div>
        </div>
      </UiCard>
      <UiCard>
        <div class="flex items-center gap-3">
          <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-slate-100 text-slate-600">
            <BarChart3 class="h-5 w-5" />
          </div>
          <div>
            <p class="text-2xl font-bold text-slate-900">{{ endedCount }}</p>
            <p class="text-xs text-slate-500">Завершённых сессий</p>
          </div>
        </div>
      </UiCard>
    </div>

    <!-- Live sessions alert -->
    <div v-if="liveSessions.length > 0" class="space-y-2">
      <div class="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-danger-600">
        <span class="inline-flex h-2 w-2 animate-pulse rounded-full bg-danger-500" />
        Сессии в эфире
      </div>
      <div
        v-for="s in liveSessions"
        :key="s.id"
        class="flex items-center justify-between rounded-xl border border-danger-200 bg-danger-50/40 p-4"
      >
        <div>
          <p class="font-medium text-slate-900">{{ s.eventTitle }}</p>
          <p class="text-xs text-slate-500">{{ s.type }} · {{ formatRelative(s.actualStartedAt || s.startTime) }}</p>
        </div>
        <UiButton variant="primary" size="sm" :to="`/admin/sessions/${s.id}/live`">
          <Radio class="h-3.5 w-3.5" /> Управление
        </UiButton>
      </div>
    </div>

    <!-- Upcoming sessions -->
    <UiCard v-if="upcomingSessions.length > 0" title="Предстоящие сессии" :padded="false">
      <ul class="divide-y divide-slate-100">
        <li
          v-for="s in upcomingSessions"
          :key="s.id"
          class="flex items-center justify-between px-5 py-3 text-sm"
        >
          <div class="min-w-0">
            <p class="font-medium text-slate-900">{{ s.eventTitle }}</p>
            <p class="text-xs text-slate-500">
              {{ formatDateTime(s.startTime) }} · {{ formatRelative(s.startTime) }}
            </p>
          </div>
          <UiButton variant="ghost" size="sm" :to="`/admin/events/${s.eventId}?tab=sessions`">
            <ArrowRight class="h-3.5 w-3.5" />
          </UiButton>
        </li>
      </ul>
    </UiCard>

    <!-- Loading skeleton for stats area -->
    <div v-if="loading && sessions.length === 0" class="space-y-3">
      <UiSkeleton v-for="i in 3" :key="i" h="h-16" rounded="rounded-xl" />
    </div>

    <!-- Quick shortcuts -->
    <div>
      <h2 class="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-500">Быстрые действия</h2>
      <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <NuxtLink
          v-for="s in shortcuts"
          :key="s.to"
          :to="s.to"
          class="card card-hover p-4 transition-transform hover:-translate-y-0.5"
        >
          <div class="flex h-9 w-9 items-center justify-center rounded-lg bg-brand-50 text-brand-700">
            <component :is="s.icon" class="h-4 w-4" />
          </div>
          <h3 class="mt-3 text-sm font-semibold text-slate-900">{{ s.title }}</h3>
          <p class="mt-0.5 text-xs text-slate-500">{{ s.description }}</p>
        </NuxtLink>
      </div>
    </div>

    <!-- Draft events hint -->
    <UiCard v-if="draftEvents > 0" class="border-amber-200 bg-amber-50/50">
      <div class="flex items-center gap-3">
        <Calendar class="h-5 w-5 text-amber-600" />
        <div>
          <p class="text-sm font-medium text-slate-900">{{ draftEvents }} черновиков мероприятий</p>
          <p class="text-xs text-slate-500">Перейдите в мероприятия для публикации.</p>
        </div>
        <UiButton variant="outline" size="sm" to="/admin/events" class="ml-auto">
          Открыть
        </UiButton>
      </div>
    </UiCard>
  </div>
</template>
