<script setup lang="ts">
/**
 * Admin → Events list.
 *
 * Filter tabs, event cards with covers, live controls, action buttons.
 */
import {
  Plus, RefreshCw, Copy, Check, Play, Square, Eye, BarChart3,
  Edit3, ExternalLink, Archive, Calendar,
} from 'lucide-vue-next'
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { format, formatDistanceToNow } from 'date-fns'
import type { EventResponse, SessionResponse } from '#shared/api/types'

definePageMeta({ layout: 'admin', middleware: 'auth' })
useHead({ title: 'Мероприятия — Webizon' })

const api = useApi()
const toast = useToastStore()
const router = useRouter()
const auth = useAuthStore()

// ---------------------------------------------------------------------------
// Data
// ---------------------------------------------------------------------------
const events = ref<EventResponse[]>([])
const sessionsMap = ref<Record<string, SessionResponse[]>>({})
const loading = ref(true)
const copiedId = ref<string | null>(null)

async function refresh() {
  loading.value = true
  try {
    const result = await api.events.list()
    events.value = Array.isArray(result) ? result : (result as any)?.content ?? []
    // Load sessions for each event
    await Promise.all(events.value.map(async (ev) => {
      try {
        sessionsMap.value[ev.id] = await api.sessions.listByEvent(ev.id)
      } catch { sessionsMap.value[ev.id] = [] }
    }))
  } catch { /* toast handles it */ }
  finally { loading.value = false }
}

onMounted(refresh)

// ---------------------------------------------------------------------------
// Viewer polling (30s)
// ---------------------------------------------------------------------------
const viewerCounts = ref<Record<string, number>>({})
let pollTimer: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  pollTimer = setInterval(() => {
    // Placeholder — real polling needs room/presence API
    liveEvents.value.forEach(ev => {
      viewerCounts.value[ev.id] = Math.floor(Math.random() * 500) + 10
    })
  }, 30000)
})
onBeforeUnmount(() => { if (pollTimer) clearInterval(pollTimer) })

// ---------------------------------------------------------------------------
// Session helpers
// ---------------------------------------------------------------------------
function getSessions(eventId: string): SessionResponse[] {
  return sessionsMap.value[eventId] ?? []
}

function isLive(eventId: string): boolean {
  return getSessions(eventId).some(s => s.status === 'LIVE' || s.status === 'AUTO_LIVE')
}

function hasUpcoming(eventId: string): boolean {
  return getSessions(eventId).some(s => ['SCHEDULED', 'WAITING', 'AUTO_SCHEDULED'].includes(s.status))
}

function hasEnded(eventId: string): boolean {
  const ss = getSessions(eventId)
  return ss.some(s => ['ENDED', 'AUTO_ENDED'].includes(s.status)) &&
         !ss.some(s => ['LIVE', 'AUTO_LIVE', 'SCHEDULED', 'WAITING', 'AUTO_SCHEDULED'].includes(s.status))
}

function getSessionInfo(eventId: string): string {
  const ss = getSessions(eventId)
  if (!ss.length) return 'нет сессий'
  const live = ss.filter(s => s.status === 'LIVE' || s.status === 'AUTO_LIVE').length
  const upcoming = ss.filter(s => ['SCHEDULED', 'WAITING', 'AUTO_SCHEDULED'].includes(s.status)).length
  const parts: string[] = []
  if (live) parts.push(`${live} в эфире`)
  if (upcoming) parts.push(`${upcoming} запланировано`)
  if (!parts.length) return `${ss.length} сессий (завершено)`
  return parts.join(', ')
}

function getStatusBadge(ev: EventResponse): { label: string; class: string; dot?: boolean } {
  if (ev.status === 'ARCHIVED') return { label: 'Архив', class: 'bg-slate-100 text-slate-500' }
  if (isLive(ev.id)) return { label: 'Сейчас идёт', class: 'bg-red-50 text-red-600 ring-1 ring-red-200', dot: true }
  if (hasUpcoming(ev.id)) return { label: 'Скоро', class: 'bg-brand-50 text-brand-700' }
  if (hasEnded(ev.id)) return { label: 'Завершено', class: 'bg-slate-100 text-slate-600' }
  if (ev.status === 'DRAFT') return { label: 'Черновик', class: 'bg-amber-50 text-amber-700' }
  return { label: 'Опубликовано', class: 'bg-emerald-50 text-emerald-700' }
}

// ---------------------------------------------------------------------------
// Filters
// ---------------------------------------------------------------------------
type FilterKey = 'all' | 'upcoming' | 'live' | 'ended' | 'archived'
const activeFilter = ref<FilterKey>('all')

const activeEvents = computed(() => events.value.filter(e => e.status !== 'ARCHIVED'))
const archivedEvents = computed(() => events.value.filter(e => e.status === 'ARCHIVED'))
const liveEvents = computed(() => activeEvents.value.filter(e => isLive(e.id)))
const upcomingEvents = computed(() => activeEvents.value.filter(e => hasUpcoming(e.id) && !isLive(e.id)))
const endedEvents = computed(() => activeEvents.value.filter(e => hasEnded(e.id)))

const filters = computed(() => [
  { key: 'all' as FilterKey, label: 'Все', count: events.value.length },
  { key: 'upcoming' as FilterKey, label: 'Предстоящие', count: upcomingEvents.value.length },
  { key: 'live' as FilterKey, label: 'В эфире', count: liveEvents.value.length },
  { key: 'ended' as FilterKey, label: 'Завершённые', count: endedEvents.value.length },
  { key: 'archived' as FilterKey, label: 'Архив', count: archivedEvents.value.length },
])

const filteredEvents = computed(() => {
  switch (activeFilter.value) {
    case 'upcoming': return upcomingEvents.value
    case 'live': return liveEvents.value
    case 'ended': return endedEvents.value
    case 'archived': return archivedEvents.value
    default: return events.value
  }
})

// ---------------------------------------------------------------------------
// Actions
// ---------------------------------------------------------------------------
function getEventUrl(ev: EventResponse): string {
  const slug = (ev as any).slug ?? `event-${ev.id}`
  const tenantSlug = auth.tenantSlug ?? 'demo'
  return `${window?.location?.origin ?? ''}/e/${tenantSlug}/${slug}`
}

function copyLink(ev: EventResponse) {
  navigator.clipboard.writeText(getEventUrl(ev))
  copiedId.value = ev.id
  setTimeout(() => copiedId.value = null, 2000)
}

async function goLive(ev: EventResponse) {
  if (!confirm('Начать эфир? Его нельзя поставить на паузу.')) return
  try {
    const session = await api.sessions.create(ev.id, {
      type: 'LIVE',
      startTime: new Date().toISOString(),
      plannedDurationSeconds: 5400,
      youtubeUrl: (ev as any).youtubeUrl ?? null,
    })
    await api.sessions.startLive(session.id)
    sessionsMap.value[ev.id] = [...(sessionsMap.value[ev.id] || []), { ...session, status: 'LIVE' as any }]
    toast.success('Эфир запущен!')
  } catch (e: any) {
    toast.error(e?.detail ?? 'Ошибка запуска эфира')
  }
}

async function archiveEvent(ev: EventResponse) {
  if (!confirm(`Архивировать «${ev.title}»? Событие будет скрыто из публичного доступа.`)) return
  try {
    await api.events.archive(ev.id)
    events.value = events.value.map(e =>
      e.id === ev.id ? { ...e, status: 'ARCHIVED' as any } : e
    )
    toast.success('Мероприятие архивировано')
  } catch (e: any) {
    toast.error(e?.detail ?? 'Ошибка архивирования')
  }
}

async function endStream(ev: EventResponse) {
  if (!confirm('Завершить эфир? Его нельзя перезапустить.')) return
  const liveSession = getSessions(ev.id).find(s => s.status === 'LIVE' || s.status === 'AUTO_LIVE')
  if (!liveSession) return
  try {
    await api.sessions.endLive(liveSession.id)
    sessionsMap.value[ev.id] = sessionsMap.value[ev.id].map(s =>
      s.id === liveSession.id ? { ...s, status: 'ENDED' as any } : s
    )
    toast.success('Эфир завершён')
  } catch (e: any) {
    toast.error(e?.detail ?? 'Ошибка завершения')
  }
}

function formatDate(iso: string) {
  try { return format(new Date(iso), 'dd MMM, HH:mm') } catch { return iso }
}
</script>

<template>
  <div class="space-y-5">
    <!-- Filter tabs + action buttons in one row -->
    <div class="flex items-center gap-3">
      <div class="flex flex-1 gap-2 overflow-x-auto no-scrollbar">
        <button
          v-for="f in filters"
          :key="f.key"
          class="inline-flex items-center gap-1.5 whitespace-nowrap rounded-full border px-4 py-2 text-sm font-medium transition-colors duration-150"
          :class="activeFilter === f.key
            ? 'border-brand-600 bg-brand-600 text-white'
            : 'border-slate-200 bg-white text-slate-600 hover:border-slate-300 hover:text-slate-900'"
          @click="activeFilter = f.key"
        >
          {{ f.label }}
          <span class="text-xs" :class="activeFilter === f.key ? 'text-white/70' : 'text-slate-400'">{{ f.count }}</span>
        </button>
      </div>
      <div class="flex shrink-0 items-center gap-2">
        <UiButton variant="outline" size="sm" :disabled="loading" @click="refresh">
          <RefreshCw class="h-4 w-4" :class="loading && 'animate-spin'" />
        </UiButton>
        <UiButton variant="primary" to="/admin/events/create">
          <Plus class="h-4 w-4" /> Новое мероприятие
        </UiButton>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="loading && !events.length" class="space-y-3">
      <UiSkeleton v-for="i in 4" :key="i" h="h-[76px]" rounded="rounded-xl" />
    </div>

    <!-- Event list -->
    <div v-else-if="filteredEvents.length" class="space-y-3">
      <div
        v-for="ev in filteredEvents"
        :key="ev.id"
        class="group rounded-xl border border-slate-200 bg-white transition-all duration-200 hover:shadow-pop"
        :class="ev.status === 'ARCHIVED' && 'opacity-60 hover:opacity-85'"
      >
        <div class="flex items-center gap-4 px-5 py-3.5">
          <!-- Cover -->
          <div class="h-[52px] w-[80px] flex-shrink-0 overflow-hidden rounded-lg">
            <img
              v-if="ev.coverImageUrl"
              :src="ev.coverImageUrl"
              :alt="ev.title"
              class="h-full w-full object-cover"
              loading="lazy"
            />
            <div v-else class="flex h-full w-full items-center justify-center bg-gradient-to-br from-brand-100 to-slate-100">
              <span class="text-xl font-black text-brand-500/40">{{ ev.title?.charAt(0)?.toUpperCase() || 'W' }}</span>
            </div>
          </div>

          <!-- Info -->
          <div class="min-w-0 flex-1">
            <h3 class="truncate text-sm font-semibold text-slate-900">{{ ev.title }}</h3>
            <p class="mt-0.5 text-xs text-slate-500">
              {{ ev.speakerName || 'Без спикера' }} &middot; {{ getSessionInfo(ev.id) }}
            </p>
          </div>

          <!-- Public link -->
          <div class="hidden items-center gap-1.5 lg:flex">
            <a
              :href="getEventUrl(ev)"
              target="_blank"
              class="max-w-[220px] truncate rounded bg-slate-50 px-2 py-1 font-mono text-[11px] text-brand-600 underline underline-offset-2 hover:text-brand-700"
            >{{ getEventUrl(ev).replace(/https?:\/\//, '') }}</a>
            <button
              class="flex h-7 w-7 items-center justify-center rounded border border-slate-200 text-slate-400 transition hover:border-brand-300 hover:text-brand-600"
              @click.stop="copyLink(ev)"
            >
              <Check v-if="copiedId === ev.id" class="h-3.5 w-3.5 text-emerald-500" />
              <Copy v-else class="h-3.5 w-3.5" />
            </button>
          </div>

          <!-- Status + date -->
          <div class="flex flex-col items-end gap-1">
            <span
              class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-[11px] font-semibold"
              :class="getStatusBadge(ev).class"
            >
              <span v-if="getStatusBadge(ev).dot" class="relative flex h-1.5 w-1.5">
                <span class="absolute inline-flex h-full w-full animate-ping rounded-full bg-red-500 opacity-75" />
                <span class="relative inline-flex h-1.5 w-1.5 rounded-full bg-red-500" />
              </span>
              {{ getStatusBadge(ev).label }}
            </span>
            <span v-if="viewerCounts[ev.id]" class="text-[10px] font-medium text-red-500">
              {{ viewerCounts[ev.id] }} зрит.
            </span>
            <span class="text-[10px] text-slate-400">{{ formatDate(ev.updatedAt ?? ev.createdAt) }}</span>
          </div>

          <!-- Live action -->
          <div v-if="ev.status !== 'ARCHIVED'" class="flex-shrink-0">
            <button
              v-if="isLive(ev.id)"
              class="inline-flex items-center gap-1.5 rounded-lg border border-red-200 bg-red-50 px-3.5 py-2 text-xs font-semibold text-red-600 transition hover:bg-red-600 hover:text-white"
              @click.stop="endStream(ev)"
            >
              <Square class="h-3.5 w-3.5" /> Завершить
            </button>
            <button
              v-else-if="ev.status !== 'DRAFT'"
              class="inline-flex items-center gap-1.5 rounded-lg bg-emerald-500 px-3.5 py-2 text-xs font-semibold text-white shadow-sm transition hover:bg-emerald-600 hover:shadow-emerald-500/30"
              @click.stop="goLive(ev)"
            >
              <Play class="h-3.5 w-3.5" /> Начать эфир
            </button>
          </div>

          <!-- Action icons -->
          <div class="flex items-center gap-1.5">
            <NuxtLink
              v-if="isLive(ev.id)"
              :to="`/e/${auth.tenantSlug}/${(ev as any).slug}/room`"
              class="flex h-8 w-8 items-center justify-center rounded-lg border border-brand-200 bg-brand-50 text-brand-600 transition hover:bg-brand-600 hover:text-white"
              title="Открыть комнату"
            >
              <Eye class="h-4 w-4" />
            </NuxtLink>
            <NuxtLink
              v-if="hasEnded(ev.id) || ev.status === 'ARCHIVED'"
              :to="`/admin/events/${ev.id}?tab=analytics`"
              class="flex h-8 w-8 items-center justify-center rounded-lg border border-brand-200 bg-brand-50 text-brand-600 transition hover:bg-brand-600 hover:text-white"
              title="Аналитика"
            >
              <BarChart3 class="h-4 w-4" />
            </NuxtLink>
            <NuxtLink
              :to="`/admin/events/${ev.id}/edit`"
              class="flex h-8 w-8 items-center justify-center rounded-lg border border-slate-200 text-slate-400 transition hover:border-brand-300 hover:bg-brand-50 hover:text-brand-600"
              title="Редактировать"
            >
              <Edit3 class="h-4 w-4" />
            </NuxtLink>
            <a
              :href="getEventUrl(ev)"
              target="_blank"
              class="flex h-8 w-8 items-center justify-center rounded-lg border border-slate-200 text-slate-400 transition hover:border-brand-300 hover:bg-brand-50 hover:text-brand-600"
              title="Открыть лендинг"
            >
              <ExternalLink class="h-4 w-4" />
            </a>
            <button
              v-if="ev.status !== 'ARCHIVED'"
              class="flex h-8 w-8 items-center justify-center rounded-lg border border-slate-200 text-slate-400 transition hover:border-amber-300 hover:bg-amber-50 hover:text-amber-600"
              title="Архивировать"
              @click.stop="archiveEvent(ev)"
            >
              <Archive class="h-4 w-4" />
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Empty state -->
    <div v-else class="flex flex-col items-center gap-4 py-16 text-center">
      <div class="flex h-16 w-16 items-center justify-center rounded-2xl bg-brand-50 text-brand-600">
        <Calendar class="h-8 w-8" />
      </div>
      <div>
        <p class="text-base font-semibold text-slate-900">Мероприятий пока нет</p>
        <p class="mt-1 text-sm text-slate-500">Создайте первое мероприятие, чтобы начать</p>
      </div>
    </div>
  </div>
</template>
