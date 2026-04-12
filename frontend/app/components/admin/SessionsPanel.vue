<script setup lang="ts">
/**
 * Sessions management panel for an event.
 *
 * <p>Mounted as the {@code "sessions"} tab on the event detail page. Owns
 * the full CRUD + lifecycle surface for sessions of a single event so the
 * parent detail page stays readable — the sessions surface alone is larger
 * than the entire event overview tab.
 *
 * <h2>What this panel does</h2>
 * <ul>
 *   <li>Lists all sessions (LIVE + AUTO) of the event, grouped by status so
 *       admins can see "what's live right now" and "what's coming up" at a
 *       glance.</li>
 *   <li>Creates new sessions via a modal with {@link SessionForm}.</li>
 *   <li>Edits scheduled sessions via the same form in edit mode.</li>
 *   <li>Runs lifecycle verbs (start/end live, start/end auto, cancel) with
 *       confirm modals for the destructive ones.</li>
 * </ul>
 *
 * <h2>Why inline instead of a separate page</h2>
 * <p>Most admin sessions work happens in-context — you schedule, you edit,
 * you start, you watch the live chat. Routing between pages for every action
 * would break that flow. The list + modal pattern keeps the admin's mental
 * model attached to the event they're working on.
 */
import {
  Plus,
  RefreshCw,
  Calendar,
  Radio,
  History,
  Play,
  Square,
  Edit3,
  Ban,
  ExternalLink,
  AlertTriangle,
  Download,
  BarChart3,
  MessageSquareText,
} from 'lucide-vue-next'
import { ref, computed } from 'vue'
import { format, formatDistanceToNow } from 'date-fns'
import type { SessionResponse, SessionStatus, UUID } from '#shared/api/types'

const props = defineProps<{
  eventId: UUID
}>()

const api = useApi()
const toast = useToastStore()

// ---------------------------------------------------------------------------
// Data
// ---------------------------------------------------------------------------

const sessions = ref<SessionResponse[]>([])
const loading = ref(false)
const errorMessage = ref<string | null>(null)

async function refresh() {
  loading.value = true
  errorMessage.value = null
  try {
    sessions.value = await api.sessions.listByEvent(props.eventId)
  } catch (err) {
    const apiErr = err as { detail?: string; title?: string }
    errorMessage.value =
      apiErr.detail ?? apiErr.title ?? 'Сессияларды жүктеу қатесі'
  } finally {
    loading.value = false
  }
}

onMounted(refresh)

// ---------------------------------------------------------------------------
// Derived — group by lifecycle bucket
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
  sessions.value
    .filter((s) => bucketOf(s.status) === 'live')
    .sort((a, b) => new Date(a.startTime).getTime() - new Date(b.startTime).getTime()),
)

const upcomingSessions = computed(() =>
  sessions.value
    .filter((s) => bucketOf(s.status) === 'upcoming')
    .sort((a, b) => new Date(a.startTime).getTime() - new Date(b.startTime).getTime()),
)

const endedSessions = computed(() =>
  sessions.value
    .filter((s) => bucketOf(s.status) === 'ended')
    .sort((a, b) => new Date(b.startTime).getTime() - new Date(a.startTime).getTime()),
)

const cancelledSessions = computed(() =>
  sessions.value
    .filter((s) => bucketOf(s.status) === 'cancelled')
    .sort((a, b) => new Date(b.startTime).getTime() - new Date(a.startTime).getTime()),
)

/** Eligible AUTO source sessions — ENDED LIVE sessions of the same event. */
const autoSources = computed(() =>
  sessions.value.filter((s) => s.status === 'ENDED' && s.type === 'LIVE'),
)

// ---------------------------------------------------------------------------
// Create / edit modal
// ---------------------------------------------------------------------------

const formModalOpen = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const editingSession = ref<SessionResponse | null>(null)

function openCreate() {
  formMode.value = 'create'
  editingSession.value = null
  formModalOpen.value = true
}

function openEdit(session: SessionResponse) {
  formMode.value = 'edit'
  editingSession.value = session
  formModalOpen.value = true
}

function onFormSubmit(saved: SessionResponse) {
  formModalOpen.value = false
  // Replace or prepend without a full refetch — snappier UX.
  const idx = sessions.value.findIndex((s) => s.id === saved.id)
  if (idx >= 0) {
    sessions.value[idx] = saved
  } else {
    sessions.value.push(saved)
  }
}

// ---------------------------------------------------------------------------
// Lifecycle verbs
// ---------------------------------------------------------------------------

const actingSessionId = ref<UUID | null>(null)

async function runLifecycleAction(
  sessionId: UUID,
  action: () => Promise<SessionResponse>,
  successMessage: string,
) {
  actingSessionId.value = sessionId
  try {
    const updated = await action()
    const idx = sessions.value.findIndex((s) => s.id === updated.id)
    if (idx >= 0) sessions.value[idx] = updated
    toast.success(successMessage)
  } finally {
    actingSessionId.value = null
  }
}

async function startLive(s: SessionResponse) {
  await runLifecycleAction(s.id, () => api.sessions.startLive(s.id), 'Эфир басталды')
}

async function startAuto(s: SessionResponse) {
  await runLifecycleAction(s.id, () => api.sessions.startAuto(s.id), 'Авто-эфир басталды')
}

// Destructive actions go through confirm modals.
const endModalOpen = ref(false)
const cancelModalOpen = ref(false)
const pendingSession = ref<SessionResponse | null>(null)

function askEnd(s: SessionResponse) {
  pendingSession.value = s
  endModalOpen.value = true
}

function askCancel(s: SessionResponse) {
  pendingSession.value = s
  cancelModalOpen.value = true
}

async function confirmEnd() {
  if (!pendingSession.value) return
  const s = pendingSession.value
  const isAuto = s.status === 'AUTO_LIVE'
  await runLifecycleAction(
    s.id,
    () => (isAuto ? api.sessions.endAuto(s.id) : api.sessions.endLive(s.id)),
    isAuto ? 'Авто-эфир аяқталды' : 'Эфир аяқталды',
  )
  endModalOpen.value = false
  pendingSession.value = null
}

async function confirmCancel() {
  if (!pendingSession.value) return
  const s = pendingSession.value
  await runLifecycleAction(s.id, () => api.sessions.cancel(s.id), 'Сессия бас тартылды')
  cancelModalOpen.value = false
  pendingSession.value = null
}

// ---------------------------------------------------------------------------
// Formatters
// ---------------------------------------------------------------------------

function formatDateTime(iso: string) {
  try {
    return format(new Date(iso), 'dd.MM.yyyy HH:mm')
  } catch {
    return iso
  }
}

function formatRelative(iso: string) {
  try {
    return formatDistanceToNow(new Date(iso), { addSuffix: true })
  } catch {
    return ''
  }
}

function formatDuration(seconds: number) {
  const m = Math.round(seconds / 60)
  if (m < 60) return `${m} мин`
  const h = Math.floor(m / 60)
  const rem = m % 60
  return rem === 0 ? `${h} сағ` : `${h} сағ ${rem} мин`
}

function isActing(id: UUID): boolean {
  return actingSessionId.value === id
}

// ---------------------------------------------------------------------------
// Export
// ---------------------------------------------------------------------------

function downloadExport(sessionId: UUID) {
  const config = useRuntimeConfig()
  const base = config.public.apiBase || '/api/backend'
  window.open(`${base}/v1/sessions/${sessionId}/export`, '_blank')
}

const totalCount = computed(() => sessions.value.length)
</script>

<template>
  <div class="space-y-6">
    <!-- Header row -->
    <div class="flex items-center justify-between gap-3">
      <div>
        <h2 class="text-lg font-semibold text-slate-900">Сессиялар</h2>
        <p class="mt-0.5 text-sm text-slate-500">
          Барлығы {{ totalCount }} сессия. LIVE — нақты эфир, AUTO — жазылған қайта ойнату.
        </p>
      </div>
      <div class="flex items-center gap-2">
        <UiButton variant="outline" size="md" :disabled="loading" @click="refresh">
          <RefreshCw class="h-4 w-4" :class="loading && 'animate-spin'" />
          Жаңарту
        </UiButton>
        <UiButton variant="primary" size="md" @click="openCreate">
          <Plus class="h-4 w-4" />
          Жаңа сессия
        </UiButton>
      </div>
    </div>

    <!-- Error -->
    <UiCard v-if="errorMessage && !loading" class="border-danger-200 bg-danger-50/60">
      <p class="text-sm text-danger-700">{{ errorMessage }}</p>
      <template #footer>
        <UiButton variant="outline" size="sm" @click="refresh">Қайта көру</UiButton>
      </template>
    </UiCard>

    <!-- Loading -->
    <div v-else-if="loading && sessions.length === 0" class="grid gap-3">
      <UiSkeleton v-for="i in 3" :key="i" h="h-[90px]" rounded="rounded-xl" />
    </div>

    <!-- Empty -->
    <UiEmpty
      v-else-if="sessions.length === 0"
      title="Сессиялар жоқ"
      description="Алғашқы сессияны жасаңыз. LIVE сессия — нақты уақыттағы эфир үшін, AUTO сессия — бұрын жазылған эфирді қайта ойнату үшін."
    >
      <template #icon><Calendar class="h-5 w-5" /></template>
      <template #actions>
        <UiButton variant="primary" @click="openCreate">
          <Plus class="h-4 w-4" />
          Жаңа сессия жасау
        </UiButton>
      </template>
    </UiEmpty>

    <!-- Session groups -->
    <template v-else>
      <!-- LIVE -->
      <div v-if="liveSessions.length > 0">
        <div class="mb-2 flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-danger-600">
          <span class="inline-flex h-2 w-2 animate-pulse rounded-full bg-danger-500" />
          Эфирде
        </div>
        <div class="space-y-2">
          <div
            v-for="s in liveSessions"
            :key="s.id"
            class="rounded-xl border border-danger-200 bg-danger-50/40 p-4 shadow-sm"
          >
            <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <div class="flex-1 min-w-0">
                <div class="flex items-center gap-2">
                  <component :is="s.type === 'LIVE' ? Radio : History" class="h-4 w-4 text-danger-600" />
                  <span class="font-medium text-slate-900">{{ s.type }} сессия</span>
                  <SessionStatusBadge :status="s.status" />
                </div>
                <div class="mt-1 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-600">
                  <span>Басталған: {{ formatRelative(s.actualStartedAt || s.startTime) }}</span>
                  <span>·</span>
                  <span>Жоспарлы ұзақтық: {{ formatDuration(s.plannedDurationSeconds) }}</span>
                </div>
              </div>
              <div class="flex items-center gap-2">
                <UiButton variant="primary" size="sm" :to="`/admin/sessions/${s.id}/live`">
                  <Radio class="h-3.5 w-3.5" />
                  Эфирді басқару
                </UiButton>
                <UiButton
                  variant="danger"
                  size="sm"
                  :loading="isActing(s.id)"
                  @click="askEnd(s)"
                >
                  <Square class="h-3.5 w-3.5" />
                  Аяқтау
                </UiButton>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Upcoming -->
      <div v-if="upcomingSessions.length > 0">
        <div class="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">
          Алдағы
        </div>
        <UiCard :padded="false">
          <ul class="divide-y divide-slate-100">
            <li
              v-for="s in upcomingSessions"
              :key="s.id"
              class="flex flex-col gap-3 px-5 py-4 sm:flex-row sm:items-center sm:justify-between"
            >
              <div class="min-w-0 flex-1">
                <div class="flex flex-wrap items-center gap-2">
                  <component :is="s.type === 'LIVE' ? Radio : History" class="h-4 w-4 text-slate-400" />
                  <span class="font-medium text-slate-900">{{ formatDateTime(s.startTime) }}</span>
                  <SessionStatusBadge :status="s.status" />
                </div>
                <div class="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-slate-500">
                  <span>{{ s.type }}</span>
                  <span>·</span>
                  <span>{{ formatDuration(s.plannedDurationSeconds) }}</span>
                  <span>·</span>
                  <span>{{ formatRelative(s.startTime) }}</span>
                </div>
              </div>
              <div class="flex items-center gap-1.5">
                <UiButton variant="ghost" size="sm" @click="openEdit(s)">
                  <Edit3 class="h-3.5 w-3.5" />
                  Өңдеу
                </UiButton>
                <UiButton
                  v-if="s.status === 'SCHEDULED'"
                  variant="primary"
                  size="sm"
                  :loading="isActing(s.id)"
                  @click="startLive(s)"
                >
                  <Play class="h-3.5 w-3.5" />
                  Эфирді бастау
                </UiButton>
                <UiButton
                  v-if="s.status === 'AUTO_SCHEDULED'"
                  variant="primary"
                  size="sm"
                  :loading="isActing(s.id)"
                  @click="startAuto(s)"
                >
                  <Play class="h-3.5 w-3.5" />
                  Автоны бастау
                </UiButton>
                <UiButton
                  variant="ghost"
                  size="sm"
                  :loading="isActing(s.id)"
                  @click="askCancel(s)"
                >
                  <Ban class="h-3.5 w-3.5" />
                  Бас тарту
                </UiButton>
              </div>
            </li>
          </ul>
        </UiCard>
      </div>

      <!-- Ended -->
      <div v-if="endedSessions.length > 0">
        <div class="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">
          Аяқталған
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
                <span class="font-medium text-slate-700">{{ formatDateTime(s.startTime) }}</span>
                <SessionStatusBadge :status="s.status" />
                <span class="text-xs text-slate-400">
                  · {{ s.type }} · {{ formatDuration(s.plannedDurationSeconds) }}
                </span>
              </div>
              <div class="flex items-center gap-1.5">
                <UiButton variant="ghost" size="sm" :to="`/admin/sessions/${s.id}/analytics`">
                  <BarChart3 class="h-3.5 w-3.5" />
                  Аналитика
                </UiButton>
                <UiButton v-if="s.type === 'LIVE'" variant="ghost" size="sm" :to="`/admin/sessions/${s.id}/chat-review`">
                  <MessageSquareText class="h-3.5 w-3.5" />
                  Чат тарихы
                </UiButton>
                <UiButton variant="ghost" size="sm" @click="downloadExport(s.id)">
                  <Download class="h-3.5 w-3.5" />
                  Excel
                </UiButton>
                <UiButton variant="ghost" size="sm" :to="`/admin/sessions/${s.id}`">
                  <ExternalLink class="h-3.5 w-3.5" />
                  Шолу
                </UiButton>
              </div>
            </li>
          </ul>
        </UiCard>
      </div>

      <!-- Cancelled -->
      <div v-if="cancelledSessions.length > 0">
        <details class="group">
          <summary class="mb-2 flex cursor-pointer items-center gap-2 text-xs font-semibold uppercase tracking-wide text-slate-400 transition-colors hover:text-slate-600">
            Бас тартылған ({{ cancelledSessions.length }})
          </summary>
          <UiCard :padded="false">
            <ul class="divide-y divide-slate-100">
              <li
                v-for="s in cancelledSessions"
                :key="s.id"
                class="flex items-center gap-3 px-5 py-3 text-sm text-slate-500"
              >
                <component :is="s.type === 'LIVE' ? Radio : History" class="h-4 w-4 text-slate-300" />
                <span>{{ formatDateTime(s.startTime) }}</span>
                <SessionStatusBadge :status="s.status" />
                <span class="text-xs">· {{ s.type }}</span>
              </li>
            </ul>
          </UiCard>
        </details>
      </div>
    </template>

    <!-- Form modal -->
    <UiModal
      v-model="formModalOpen"
      :title="formMode === 'create' ? 'Жаңа сессия' : 'Сессияны өңдеу'"
      size="lg"
    >
      <SessionForm
        :mode="formMode"
        :event-id="eventId"
        :initial="editingSession"
        :available-sources="autoSources"
        @submit="onFormSubmit"
        @cancel="formModalOpen = false"
      />
    </UiModal>

    <!-- End confirm -->
    <UiModal v-model="endModalOpen" title="Эфирді аяқтау" size="sm">
      <p class="text-sm text-slate-600">
        Эфир аяқталғаннан кейін көрермендер бөлмеден шығарылады. Бұл әрекетті қайтару мүмкін емес.
      </p>
      <template #footer>
        <UiButton
          variant="outline"
          :disabled="actingSessionId !== null"
          @click="endModalOpen = false"
        >
          Бас тарту
        </UiButton>
        <UiButton
          variant="danger"
          :loading="pendingSession !== null && actingSessionId === pendingSession.id"
          @click="confirmEnd"
        >
          <Square class="h-4 w-4" />
          Аяқтау
        </UiButton>
      </template>
    </UiModal>

    <!-- Cancel confirm -->
    <UiModal v-model="cancelModalOpen" title="Сессияны бас тарту" size="sm">
      <div class="flex items-start gap-3 rounded-lg border border-warning-200 bg-warning-50 p-3">
        <AlertTriangle class="mt-0.5 h-5 w-5 shrink-0 text-warning-500" />
        <p class="text-sm text-warning-800">
          Сессия бас тартылғаннан кейін оны қайта бастау мүмкін емес. Жаңа сессия жасау керек болады.
        </p>
      </div>
      <template #footer>
        <UiButton
          variant="outline"
          :disabled="actingSessionId !== null"
          @click="cancelModalOpen = false"
        >
          Жабу
        </UiButton>
        <UiButton
          variant="danger"
          :loading="pendingSession !== null && actingSessionId === pendingSession.id"
          @click="confirmCancel"
        >
          <Ban class="h-4 w-4" />
          Бас тарту
        </UiButton>
      </template>
    </UiModal>
  </div>
</template>
