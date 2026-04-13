<script setup lang="ts">
/**
 * Admin → Session detail at {@code /admin/sessions/{id}}.
 *
 * <p>Shows session metadata (type, status, times, YouTube link) and provides
 * navigation to sub-pages: live control, analytics, timeline, chat review.
 * Also exposes lifecycle actions (start/end/cancel) appropriate to the
 * current session status.
 */
import type { SessionResponse, EventResponse } from '#shared/api/types'
import {
  Radio,
  History,
  Play,
  Square,
  XCircle,
  BarChart3,
  Clock,
  MessageSquareText,
  ExternalLink,
  RefreshCw,
  AlertTriangle,
  Download,
  ArrowLeft,
} from 'lucide-vue-next'
import { ref, computed } from 'vue'
import { format, formatDistanceToNow } from 'date-fns'

definePageMeta({
  layout: 'admin',
  middleware: 'auth',
})

const route = useRoute()
const router = useRouter()
const api = useApi()
const toast = useToastStore()

const sessionId = computed(() => route.params.id as string)

// ---------------------------------------------------------------------------
// Data
// ---------------------------------------------------------------------------

const session = ref<SessionResponse | null>(null)
const event = ref<EventResponse | null>(null)
const loading = ref(true)
const error = ref(false)

async function refresh() {
  loading.value = true
  error.value = false
  try {
    const s = await api.sessions.getById(sessionId.value)
    session.value = s
    try {
      event.value = await api.events.getById(s.eventId)
    } catch { /* event fetch optional */ }
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
}

onMounted(refresh)

useHead({
  title: () => event.value
    ? `Сессия — ${event.value.title} — Webizon`
    : 'Сессия — Webizon',
})

// ---------------------------------------------------------------------------
// Lifecycle actions
// ---------------------------------------------------------------------------

const actionLoading = ref(false)
const confirmModal = ref<'start-live' | 'end-live' | 'start-auto' | 'end-auto' | 'cancel' | null>(null)

async function doAction(action: string) {
  if (!session.value) return
  actionLoading.value = true
  try {
    let updated: SessionResponse
    switch (action) {
      case 'start-live':
        updated = await api.sessions.startLive(session.value.id)
        toast.success('Эфир начался')
        break
      case 'end-live':
        updated = await api.sessions.endLive(session.value.id)
        toast.success('Эфир завершён')
        break
      case 'start-auto':
        updated = await api.sessions.startAuto(session.value.id)
        toast.success('Авто-сессия началась')
        break
      case 'end-auto':
        updated = await api.sessions.endAuto(session.value.id)
        toast.success('Авто-сессия завершена')
        break
      case 'cancel':
        updated = await api.sessions.cancel(session.value.id)
        toast.success('Сессия отменена')
        break
      default:
        return
    }
    session.value = updated
    confirmModal.value = null
  } catch {
    toast.error('Действие не выполнено')
  } finally {
    actionLoading.value = false
  }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const isLive = computed(() =>
  session.value?.status === 'LIVE' || session.value?.status === 'AUTO_LIVE',
)
const isEnded = computed(() =>
  session.value?.status === 'ENDED' || session.value?.status === 'AUTO_ENDED',
)
const isScheduled = computed(() =>
  session.value?.status === 'SCHEDULED' || session.value?.status === 'AUTO_SCHEDULED',
)

function formatDateTime(iso: string | null) {
  if (!iso) return '—'
  try { return format(new Date(iso), 'dd.MM.yyyy HH:mm') } catch { return iso }
}

function formatRelative(iso: string | null) {
  if (!iso) return ''
  try { return formatDistanceToNow(new Date(iso), { addSuffix: true }) } catch { return '' }
}

function formatDuration(seconds: number) {
  const m = Math.round(seconds / 60)
  if (m < 60) return `${m} мин`
  const h = Math.floor(m / 60)
  const rem = m % 60
  return rem === 0 ? `${h} ч` : `${h} ч ${rem} мин`
}

function downloadExport() {
  if (!session.value) return
  const config = useRuntimeConfig()
  const base = config.public.apiBase || '/api/backend'
  window.open(`${base}/v1/sessions/${session.value.id}/export`, '_blank')
}

const STATUS_LABELS: Record<string, string> = {
  SCHEDULED: 'Запланирована',
  LIVE: 'В эфире',
  ENDED: 'Завершена',
  CANCELLED: 'Отменена',
  AUTO_SCHEDULED: 'Авто запланирована',
  AUTO_LIVE: 'Авто в эфире',
  AUTO_ENDED: 'Авто завершена',
}
</script>

<template>
  <div>
    <!-- Loading -->
    <div v-if="loading && !session" class="space-y-4">
      <UiSkeleton h="h-6" w="w-1/3" rounded="rounded-md" />
      <UiSkeleton h="h-[200px]" rounded="rounded-xl" />
    </div>

    <!-- Error -->
    <UiCard v-else-if="error || !session" class="border-danger-200 bg-danger-50/60">
      <div class="flex items-start gap-3">
        <AlertTriangle class="mt-0.5 h-5 w-5 text-danger-500" />
        <div>
          <h3 class="text-sm font-semibold text-danger-800">Сессия не найдена</h3>
          <p class="mt-1 text-sm text-danger-700">
            Эта сессия удалена или у вас нет к ней доступа.
          </p>
        </div>
      </div>
      <template #footer>
        <UiButton variant="outline" size="sm" to="/admin/sessions">Вернуться к списку</UiButton>
      </template>
    </UiCard>

    <!-- Content -->
    <template v-else>
      <PageHeader
        :title="event?.title ?? 'Сессия'"
        :subtitle="`${session.type} · ${STATUS_LABELS[session.status] || session.status}`"
        :breadcrumbs="[
          { label: 'Главная', to: '/admin' },
          { label: 'Сессии', to: '/admin/sessions' },
          { label: event?.title ?? 'Сессия' },
        ]"
      >
        <template #actions>
          <UiButton variant="ghost" size="md" :disabled="loading" @click="refresh">
            <RefreshCw class="h-4 w-4" :class="loading && 'animate-spin'" />
          </UiButton>
          <UiButton v-if="event" variant="outline" size="md" :to="`/admin/events/${event.id}?tab=sessions`">
            <ArrowLeft class="h-4 w-4" /> К мероприятию
          </UiButton>
        </template>
      </PageHeader>

      <!-- Status + quick actions -->
      <div class="mb-5 flex flex-wrap items-center gap-3 rounded-xl border border-slate-200 bg-white px-4 py-3">
        <SessionStatusBadge :status="session.status" />
        <span class="text-sm text-slate-500">
          <component :is="session.type === 'LIVE' ? Radio : History" class="mr-1 inline h-3.5 w-3.5" />
          {{ session.type }}
        </span>
        <span class="text-slate-300">·</span>
        <span class="text-sm text-slate-500">{{ formatDuration(session.plannedDurationSeconds) }}</span>

        <div class="ml-auto flex items-center gap-2">
          <!-- Start Live -->
          <UiButton
            v-if="session.status === 'SCHEDULED'"
            variant="primary"
            size="sm"
            @click="confirmModal = 'start-live'"
          >
            <Play class="h-3.5 w-3.5" /> Начать эфир
          </UiButton>

          <!-- End Live -->
          <UiButton
            v-if="session.status === 'LIVE'"
            variant="danger"
            size="sm"
            @click="confirmModal = 'end-live'"
          >
            <Square class="h-3.5 w-3.5" /> Завершить эфир
          </UiButton>

          <!-- Start Auto -->
          <UiButton
            v-if="session.status === 'AUTO_SCHEDULED'"
            variant="primary"
            size="sm"
            @click="confirmModal = 'start-auto'"
          >
            <Play class="h-3.5 w-3.5" /> Начать авто
          </UiButton>

          <!-- End Auto -->
          <UiButton
            v-if="session.status === 'AUTO_LIVE'"
            variant="danger"
            size="sm"
            @click="confirmModal = 'end-auto'"
          >
            <Square class="h-3.5 w-3.5" /> Завершить авто
          </UiButton>

          <!-- Cancel -->
          <UiButton
            v-if="isScheduled"
            variant="outline"
            size="sm"
            @click="confirmModal = 'cancel'"
          >
            <XCircle class="h-3.5 w-3.5" /> Отменить
          </UiButton>
        </div>
      </div>

      <!-- Info grid -->
      <div class="grid gap-5 lg:grid-cols-2">
        <UiCard title="Данные сессии">
          <dl class="space-y-3 text-sm">
            <div class="flex items-center justify-between">
              <dt class="text-slate-500">Время начала</dt>
              <dd class="font-medium text-slate-900">
                {{ formatDateTime(session.startTime) }}
                <span v-if="isScheduled" class="ml-1 text-xs text-slate-400">{{ formatRelative(session.startTime) }}</span>
              </dd>
            </div>
            <div class="flex items-center justify-between">
              <dt class="text-slate-500">Длительность</dt>
              <dd class="font-medium text-slate-900">{{ formatDuration(session.plannedDurationSeconds) }}</dd>
            </div>
            <div v-if="session.actualStartedAt" class="flex items-center justify-between">
              <dt class="text-slate-500">Фактическое начало</dt>
              <dd class="font-medium text-slate-900">{{ formatDateTime(session.actualStartedAt) }}</dd>
            </div>
            <div v-if="session.actualEndedAt" class="flex items-center justify-between">
              <dt class="text-slate-500">Фактическое окончание</dt>
              <dd class="font-medium text-slate-900">{{ formatDateTime(session.actualEndedAt) }}</dd>
            </div>
            <div v-if="session.sourceLiveSessionId" class="flex items-center justify-between">
              <dt class="text-slate-500">Исходная LIVE-сессия</dt>
              <dd>
                <NuxtLink
                  :to="`/admin/sessions/${session.sourceLiveSessionId}`"
                  class="text-sm font-medium text-brand-700 hover:text-brand-800"
                >
                  Посмотреть
                </NuxtLink>
              </dd>
            </div>
            <div class="flex items-center justify-between">
              <dt class="text-slate-500">Создана</dt>
              <dd class="text-slate-700">{{ formatDateTime(session.createdAt) }}</dd>
            </div>
            <div class="flex items-center justify-between">
              <dt class="text-slate-500">Обновлена</dt>
              <dd class="text-slate-700">{{ formatDateTime(session.updatedAt) }}</dd>
            </div>
          </dl>
        </UiCard>

        <UiCard title="YouTube">
          <div v-if="session.youtubeUrl" class="space-y-3">
            <div class="text-sm">
              <span class="text-slate-500">URL:</span>
              <a
                :href="session.youtubeUrl"
                target="_blank"
                rel="noopener"
                class="ml-1 break-all text-brand-700 hover:text-brand-800"
              >
                {{ session.youtubeUrl }}
              </a>
            </div>
            <div v-if="session.youtubeEmbedUrl" class="overflow-hidden rounded-lg">
              <iframe
                :src="session.youtubeEmbedUrl"
                class="aspect-video w-full"
                allow="autoplay; encrypted-media"
                allowfullscreen
              />
            </div>
          </div>
          <p v-else class="text-sm italic text-slate-400">YouTube URL не указан.</p>
        </UiCard>
      </div>

      <!-- Navigation to sub-pages -->
      <div class="mt-5">
        <h2 class="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-500">Инструменты</h2>
        <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <NuxtLink
            v-if="isLive"
            :to="`/admin/sessions/${session.id}/live`"
            class="card card-hover flex items-center gap-3 p-4"
          >
            <div class="flex h-9 w-9 items-center justify-center rounded-lg bg-danger-50 text-danger-600">
              <Radio class="h-4 w-4" />
            </div>
            <div>
              <p class="text-sm font-semibold text-slate-900">Управление эфиром</p>
              <p class="text-xs text-slate-500">Чат, модерация, CTA</p>
            </div>
          </NuxtLink>

          <NuxtLink
            v-if="isEnded"
            :to="`/admin/sessions/${session.id}/analytics`"
            class="card card-hover flex items-center gap-3 p-4"
          >
            <div class="flex h-9 w-9 items-center justify-center rounded-lg bg-brand-50 text-brand-600">
              <BarChart3 class="h-4 w-4" />
            </div>
            <div>
              <p class="text-sm font-semibold text-slate-900">Аналитика</p>
              <p class="text-xs text-slate-500">Retention, CTA, аудитория</p>
            </div>
          </NuxtLink>

          <NuxtLink
            v-if="session.type === 'LIVE' && isEnded"
            :to="`/admin/sessions/${session.id}/timeline`"
            class="card card-hover flex items-center gap-3 p-4"
          >
            <div class="flex h-9 w-9 items-center justify-center rounded-lg bg-amber-50 text-amber-600">
              <Clock class="h-4 w-4" />
            </div>
            <div>
              <p class="text-sm font-semibold text-slate-900">Таймлайн</p>
              <p class="text-xs text-slate-500">План действий</p>
            </div>
          </NuxtLink>

          <NuxtLink
            v-if="session.type === 'LIVE' && isEnded"
            :to="`/admin/sessions/${session.id}/chat-review`"
            class="card card-hover flex items-center gap-3 p-4"
          >
            <div class="flex h-9 w-9 items-center justify-center rounded-lg bg-indigo-50 text-indigo-600">
              <MessageSquareText class="h-4 w-4" />
            </div>
            <div>
              <p class="text-sm font-semibold text-slate-900">История чата</p>
              <p class="text-xs text-slate-500">Фильтр воспроизведения</p>
            </div>
          </NuxtLink>

          <button
            v-if="isEnded"
            type="button"
            class="card card-hover flex items-center gap-3 p-4 text-left"
            @click="downloadExport"
          >
            <div class="flex h-9 w-9 items-center justify-center rounded-lg bg-success-50 text-success-600">
              <Download class="h-4 w-4" />
            </div>
            <div>
              <p class="text-sm font-semibold text-slate-900">Excel экспорт</p>
              <p class="text-xs text-slate-500">Полный отчёт</p>
            </div>
          </button>
        </div>
      </div>
    </template>

    <!-- Confirm modal -->
    <UiModal
      v-if="confirmModal"
      :model-value="true"
      :title="{
        'start-live': 'Начать эфир',
        'end-live': 'Завершить эфир',
        'start-auto': 'Начать авто-сессию',
        'end-auto': 'Завершить авто-сессию',
        'cancel': 'Отменить сессию',
      }[confirmModal]"
      @update:model-value="confirmModal = null"
    >
      <p class="text-sm text-slate-600">
        {{ {
          'start-live': 'Эфир начнётся, комната откроется для участников.',
          'end-live': 'Эфир завершится, комната закроется. Это действие нельзя отменить.',
          'start-auto': 'Авто-сессия начнётся, видео будет воспроизведено.',
          'end-auto': 'Авто-сессия завершится.',
          'cancel': 'Сессия будет отменена. Это действие нельзя отменить.',
        }[confirmModal] }}
      </p>
      <template #footer>
        <UiButton variant="outline" :disabled="actionLoading" @click="confirmModal = null">
          Нет
        </UiButton>
        <UiButton
          :variant="confirmModal === 'cancel' || confirmModal.startsWith('end') ? 'danger' : 'primary'"
          :loading="actionLoading"
          @click="doAction(confirmModal!)"
        >
          Да, продолжить
        </UiButton>
      </template>
    </UiModal>
  </div>
</template>
