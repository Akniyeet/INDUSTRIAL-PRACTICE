<script setup lang="ts">
/**
 * Cross-event sessions overview at {@code /admin/sessions}.
 *
 * <p>Loads all events, then all sessions per event, and aggregates into
 * a single view grouped by lifecycle bucket (live / upcoming / ended).
 * Provides a quick operational view for admins who manage multiple events.
 */
import type { EventResponse, SessionResponse, SessionStatus, UUID } from '#shared/api/types'
import {
  Radio,
  History,
  RefreshCw,
  ExternalLink,
  BarChart3,
  Download,
  Clock,
  MessageSquareText,
} from 'lucide-vue-next'
import { ref, computed } from 'vue'
import { format, formatDistanceToNow } from 'date-fns'

definePageMeta({
  layout: 'admin',
  middleware: 'auth',
})

useHead({ title: 'Сессии — Webizon' })

const api = useApi()
const toast = useToastStore()

// ---------------------------------------------------------------------------
// Data
// ---------------------------------------------------------------------------

interface SessionWithEvent extends SessionResponse {
  eventTitle: string
  eventSlug: string
}

const sessions = ref<SessionWithEvent[]>([])
const loading = ref(false)

async function refresh() {
  loading.value = true
  try {
    const events = await api.events.list()
    const allSessions: SessionWithEvent[] = []
    await Promise.all(
      events.map(async (ev: EventResponse) => {
        try {
          const ss = await api.sessions.listByEvent(ev.id)
          for (const s of ss) {
            allSessions.push({ ...s, eventTitle: ev.title, eventSlug: ev.slug })
          }
        } catch { /* skip failed event */ }
      }),
    )
    sessions.value = allSessions
  } catch {
    toast.error('Ошибка загрузки сессий')
  } finally {
    loading.value = false
  }
}

onMounted(refresh)

// ---------------------------------------------------------------------------
// Grouping
// ---------------------------------------------------------------------------

type Bucket = 'live' | 'upcoming' | 'ended' | 'cancelled'
const LIVE_STATUSES: SessionStatus[] = ['LIVE', 'AUTO_LIVE']
const UPCOMING_STATUSES: SessionStatus[] = ['SCHEDULED', 'AUTO_SCHEDULED']
const ENDED_STATUSES: SessionStatus[] = ['ENDED', 'AUTO_ENDED']

function bucketOf(status: SessionStatus): Bucket {
  if (LIVE_STATUSES.includes(status)) return 'live'
  if (UPCOMING_STATUSES.includes(status)) return 'upcoming'
  if (ENDED_STATUSES.includes(status)) return 'ended'
  return 'cancelled'
}

const liveSessions = computed(() =>
  sessions.value.filter((s) => bucketOf(s.status) === 'live')
    .sort((a, b) => new Date(a.startTime).getTime() - new Date(b.startTime).getTime()),
)

const upcomingSessions = computed(() =>
  sessions.value.filter((s) => bucketOf(s.status) === 'upcoming')
    .sort((a, b) => new Date(a.startTime).getTime() - new Date(b.startTime).getTime()),
)

const endedSessions = computed(() =>
  sessions.value.filter((s) => bucketOf(s.status) === 'ended')
    .sort((a, b) => new Date(b.startTime).getTime() - new Date(a.startTime).getTime()),
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

function formatDuration(seconds: number) {
  const m = Math.round(seconds / 60)
  if (m < 60) return `${m} мин`
  const h = Math.floor(m / 60)
  const rem = m % 60
  return rem === 0 ? `${h} ч` : `${h} ч ${rem} мин`
}

function downloadExport(sessionId: UUID) {
  const config = useRuntimeConfig()
  const base = config.public.apiBase || '/api/backend'
  window.open(`${base}/v1/sessions/${sessionId}/export`, '_blank')
}
</script>

<template>
  <div>
    <PageHeader
      title="Все сессии"
      subtitle="Обзор сессий по всем мероприятиям"
      :breadcrumbs="[
        { label: 'Главная', to: '/admin' },
        { label: 'Сессии' },
      ]"
    >
      <template #actions>
        <UiButton variant="outline" size="md" :disabled="loading" @click="refresh">
          <RefreshCw class="h-4 w-4" :class="loading && 'animate-spin'" />
          Обновить
        </UiButton>
      </template>
    </PageHeader>

    <!-- Loading -->
    <div v-if="loading && sessions.length === 0" class="mt-5 space-y-3">
      <UiSkeleton v-for="i in 4" :key="i" h="h-[72px]" rounded="rounded-xl" />
    </div>

    <!-- Empty -->
    <UiEmpty
      v-else-if="sessions.length === 0 && !loading"
      title="Сессий нет"
      description="Сначала создайте мероприятие и добавьте к нему сессию."
      class="mt-5"
    >
      <template #icon><Radio class="h-5 w-5" /></template>
      <template #actions>
        <UiButton variant="primary" to="/admin/events">Перейти к мероприятиям</UiButton>
      </template>
    </UiEmpty>

    <template v-else>
      <!-- LIVE -->
      <section v-if="liveSessions.length > 0" class="mt-5">
        <div class="mb-2 flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-danger-600">
          <span class="inline-flex h-2 w-2 animate-pulse rounded-full bg-danger-500" />
          В эфире ({{ liveSessions.length }})
        </div>
        <div class="space-y-2">
          <div
            v-for="s in liveSessions"
            :key="s.id"
            class="rounded-xl border border-danger-200 bg-danger-50/40 p-4 shadow-sm"
          >
            <div class="flex items-center justify-between gap-3">
              <div>
                <div class="flex items-center gap-2">
                  <component :is="s.type === 'LIVE' ? Radio : History" class="h-4 w-4 text-danger-600" />
                  <span class="font-medium text-slate-900">{{ s.eventTitle }}</span>
                  <SessionStatusBadge :status="s.status" />
                </div>
                <p class="mt-1 text-xs text-slate-600">
                  {{ s.type }} · {{ formatRelative(s.actualStartedAt || s.startTime) }} · {{ formatDuration(s.plannedDurationSeconds) }}
                </p>
              </div>
              <UiButton variant="primary" size="sm" :to="`/admin/sessions/${s.id}/live`">
                <Radio class="h-3.5 w-3.5" />
                Управление
              </UiButton>
            </div>
          </div>
        </div>
      </section>

      <!-- Upcoming -->
      <section v-if="upcomingSessions.length > 0" class="mt-6">
        <div class="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">
          Предстоящие ({{ upcomingSessions.length }})
        </div>
        <UiCard :padded="false">
          <ul class="divide-y divide-slate-100">
            <li
              v-for="s in upcomingSessions"
              :key="s.id"
              class="flex items-center justify-between px-5 py-3 text-sm"
            >
              <div class="min-w-0">
                <div class="flex items-center gap-2">
                  <component :is="s.type === 'LIVE' ? Radio : History" class="h-4 w-4 text-slate-400" />
                  <span class="font-medium text-slate-900">{{ s.eventTitle }}</span>
                  <SessionStatusBadge :status="s.status" />
                </div>
                <p class="mt-0.5 text-xs text-slate-500">
                  {{ formatDateTime(s.startTime) }} · {{ s.type }} · {{ formatDuration(s.plannedDurationSeconds) }} · {{ formatRelative(s.startTime) }}
                </p>
              </div>
              <UiButton variant="ghost" size="sm" :to="`/admin/events/${s.eventId}?tab=sessions`">
                <ExternalLink class="h-3.5 w-3.5" />
              </UiButton>
            </li>
          </ul>
        </UiCard>
      </section>

      <!-- Ended -->
      <section v-if="endedSessions.length > 0" class="mt-6">
        <div class="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">
          Завершённые ({{ endedSessions.length }})
        </div>
        <UiCard :padded="false">
          <ul class="divide-y divide-slate-100">
            <li
              v-for="s in endedSessions"
              :key="s.id"
              class="flex items-center justify-between px-5 py-3 text-sm"
            >
              <div class="flex min-w-0 items-center gap-3">
                <component :is="s.type === 'LIVE' ? Radio : History" class="h-4 w-4 text-slate-300" />
                <div class="min-w-0">
                  <span class="font-medium text-slate-700">{{ s.eventTitle }}</span>
                  <p class="text-xs text-slate-400">
                    {{ formatDateTime(s.startTime) }} · {{ s.type }} · {{ formatDuration(s.plannedDurationSeconds) }}
                  </p>
                </div>
              </div>
              <div class="flex items-center gap-1">
                <UiButton variant="ghost" size="sm" :to="`/admin/sessions/${s.id}/analytics`">
                  <BarChart3 class="h-3.5 w-3.5" />
                </UiButton>
                <UiButton v-if="s.type === 'LIVE'" variant="ghost" size="sm" :to="`/admin/sessions/${s.id}/timeline`">
                  <Clock class="h-3.5 w-3.5" />
                </UiButton>
                <UiButton v-if="s.type === 'LIVE'" variant="ghost" size="sm" :to="`/admin/sessions/${s.id}/chat-review`">
                  <MessageSquareText class="h-3.5 w-3.5" />
                </UiButton>
                <UiButton variant="ghost" size="sm" @click="downloadExport(s.id)">
                  <Download class="h-3.5 w-3.5" />
                </UiButton>
              </div>
            </li>
          </ul>
        </UiCard>
      </section>
    </template>
  </div>
</template>
