<script setup lang="ts">
/**
 * Timeline editor at {@code /admin/sessions/{id}/timeline}.
 *
 * <p>Shows all timeline actions for a LIVE source session, ordered by
 * offset. Admins can create, edit, toggle, and delete actions. These
 * actions replay automatically in AUTO sessions at the correct video
 * offset.
 */
import type {
  TimelineActionResponse,
  TimelineActionType,
  SessionResponse,
  CtaResponse,
  UUID,
} from '#shared/api/types'
import {
  ArrowLeft,
  RefreshCw,
  Plus,
  Clock,
  Megaphone,
  EyeOff as MegaphoneOff,
  MessageSquareText,
  AlertCircle,
  History,
  Zap,
  Pencil,
  Trash2,
  ToggleLeft,
  ToggleRight,
  Play,
  X,
  Check,
} from 'lucide-vue-next'
import { format } from 'date-fns'
import { ref, computed } from 'vue'

definePageMeta({
  layout: 'admin',
  middleware: 'auth',
})

const route = useRoute()
const api = useApi()
const toast = useToastStore()

const sessionId = computed(() => String(route.params.id))

// Load session
const { data: session, error: sessionError } = await useAsyncData(
  () => `timeline-session:${sessionId.value}`,
  () => api.sessions.getById(sessionId.value),
)

// Load CTAs for the event (needed for CTA_SHOW/CTA_HIDE payload selection)
const ctas = ref<CtaResponse[]>([])

async function loadCtas() {
  if (!session.value) return
  try {
    ctas.value = await api.cta.list(session.value.eventId)
  } catch { /* non-critical */ }
}

// Load timeline actions
const actions = ref<TimelineActionResponse[]>([])
const loading = ref(false)
const loadError = ref<string | null>(null)

async function loadTimeline() {
  if (!session.value) return
  loading.value = true
  loadError.value = null
  try {
    actions.value = await api.timeline.list(session.value.eventId, sessionId.value)
  } catch (err) {
    const apiErr = err as { detail?: string; title?: string }
    loadError.value = apiErr.detail ?? apiErr.title ?? 'Ошибка загрузки таймлайна'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadTimeline()
  loadCtas()
})

// ---------------------------------------------------------------------------
// Action type labels and icons
// ---------------------------------------------------------------------------

const ACTION_TYPE_LABELS: Record<TimelineActionType, string> = {
  CTA_SHOW: 'Показать CTA',
  CTA_HIDE: 'Скрыть CTA',
  ADMIN_MESSAGE_SHOW: 'Сообщение админа',
  SYSTEM_MESSAGE_SHOW: 'Системное сообщение',
  HISTORICAL_CHAT_REPLAY: 'Воспроизведение чата',
  ROOM_STATE_CHANGE: 'Состояние комнаты',
  FUTURE_RESERVED: 'Резерв',
}

const ACTION_TYPE_ICONS: Record<TimelineActionType, typeof Megaphone> = {
  CTA_SHOW: Megaphone,
  CTA_HIDE: MegaphoneOff,
  ADMIN_MESSAGE_SHOW: MessageSquareText,
  SYSTEM_MESSAGE_SHOW: AlertCircle,
  HISTORICAL_CHAT_REPLAY: History,
  ROOM_STATE_CHANGE: Zap,
  FUTURE_RESERVED: Clock,
}

const ACTION_TYPE_COLORS: Record<TimelineActionType, string> = {
  CTA_SHOW: 'text-success-600 bg-success-50',
  CTA_HIDE: 'text-warning-600 bg-warning-50',
  ADMIN_MESSAGE_SHOW: 'text-brand-600 bg-brand-50',
  SYSTEM_MESSAGE_SHOW: 'text-slate-600 bg-slate-100',
  HISTORICAL_CHAT_REPLAY: 'text-violet-600 bg-violet-50',
  ROOM_STATE_CHANGE: 'text-orange-600 bg-orange-50',
  FUTURE_RESERVED: 'text-slate-400 bg-slate-50',
}

/** Creatable action types (exclude auto-generated ones) */
const CREATABLE_TYPES: TimelineActionType[] = [
  'CTA_SHOW',
  'CTA_HIDE',
  'ADMIN_MESSAGE_SHOW',
  'SYSTEM_MESSAGE_SHOW',
  'ROOM_STATE_CHANGE',
]

// ---------------------------------------------------------------------------
// Stats
// ---------------------------------------------------------------------------

const totalCount = computed(() => actions.value.length)
const activeCount = computed(() => actions.value.filter((a) => a.active).length)
const ctaShowCount = computed(() => actions.value.filter((a) => a.actionType === 'CTA_SHOW').length)

// ---------------------------------------------------------------------------
// Create modal
// ---------------------------------------------------------------------------

const createModalOpen = ref(false)
const createForm = ref({
  offsetMinutes: 0,
  offsetExtraSeconds: 0,
  actionType: 'CTA_SHOW' as TimelineActionType,
  ctaId: '' as string,
  text: '' as string,
  state: '' as string,
})
const creating = ref(false)

function openCreate() {
  createForm.value = {
    offsetMinutes: 0,
    offsetExtraSeconds: 0,
    actionType: 'CTA_SHOW',
    ctaId: ctas.value[0]?.id ?? '',
    text: '',
    state: '',
  }
  createModalOpen.value = true
}

function buildPayload(): Record<string, unknown> {
  const t = createForm.value.actionType
  if (t === 'CTA_SHOW' || t === 'CTA_HIDE') return { ctaId: createForm.value.ctaId }
  if (t === 'ADMIN_MESSAGE_SHOW' || t === 'SYSTEM_MESSAGE_SHOW') return { text: createForm.value.text }
  if (t === 'ROOM_STATE_CHANGE') return { state: createForm.value.state }
  return {}
}

async function submitCreate() {
  if (!session.value) return
  creating.value = true
  try {
    const offsetSeconds = createForm.value.offsetMinutes * 60 + createForm.value.offsetExtraSeconds
    const created = await api.timeline.create(session.value.eventId, sessionId.value, {
      offsetSeconds,
      actionType: createForm.value.actionType,
      payload: buildPayload(),
    })
    actions.value.push(created)
    actions.value.sort((a, b) => a.offsetSeconds - b.offsetSeconds)
    createModalOpen.value = false
    toast.success('Действие добавлено в таймлайн')
  } catch (err) {
    const apiErr = err as { detail?: string; title?: string }
    toast.error(apiErr.detail ?? 'Ошибка добавления')
  } finally {
    creating.value = false
  }
}

// ---------------------------------------------------------------------------
// Edit inline
// ---------------------------------------------------------------------------

const editingId = ref<string | null>(null)
const editForm = ref({ offsetMinutes: 0, offsetExtraSeconds: 0 })

function startEdit(action: TimelineActionResponse) {
  editingId.value = action.id
  editForm.value = {
    offsetMinutes: Math.floor(action.offsetSeconds / 60),
    offsetExtraSeconds: action.offsetSeconds % 60,
  }
}

function cancelEdit() {
  editingId.value = null
}

async function saveEdit(action: TimelineActionResponse) {
  if (!session.value) return
  const newOffset = editForm.value.offsetMinutes * 60 + editForm.value.offsetExtraSeconds
  try {
    const updated = await api.timeline.update(session.value.eventId, action.id, {
      offsetSeconds: newOffset,
    })
    const idx = actions.value.findIndex((a) => a.id === action.id)
    if (idx >= 0) actions.value[idx] = updated
    actions.value.sort((a, b) => a.offsetSeconds - b.offsetSeconds)
    editingId.value = null
    toast.success('Время обновлено')
  } catch {
    toast.error('Ошибка обновления')
  }
}

// ---------------------------------------------------------------------------
// Toggle active
// ---------------------------------------------------------------------------

const togglingId = ref<string | null>(null)

async function toggleActive(action: TimelineActionResponse) {
  if (!session.value) return
  togglingId.value = action.id
  try {
    const updated = await api.timeline.setActive(session.value.eventId, action.id, !action.active)
    const idx = actions.value.findIndex((a) => a.id === action.id)
    if (idx >= 0) actions.value[idx] = updated
  } catch {
    toast.error('Ошибка изменения состояния')
  } finally {
    togglingId.value = null
  }
}

// ---------------------------------------------------------------------------
// Delete
// ---------------------------------------------------------------------------

const deleteModalOpen = ref(false)
const pendingDelete = ref<TimelineActionResponse | null>(null)
const deleting = ref(false)

function askDelete(action: TimelineActionResponse) {
  pendingDelete.value = action
  deleteModalOpen.value = true
}

async function confirmDelete() {
  if (!session.value || !pendingDelete.value) return
  deleting.value = true
  try {
    await api.timeline.remove(session.value.eventId, pendingDelete.value.id)
    actions.value = actions.value.filter((a) => a.id !== pendingDelete.value!.id)
    deleteModalOpen.value = false
    pendingDelete.value = null
    toast.success('Действие удалено')
  } catch {
    toast.error('Ошибка удаления')
  } finally {
    deleting.value = false
  }
}

// ---------------------------------------------------------------------------
// Formatters
// ---------------------------------------------------------------------------

function formatOffset(seconds: number): string {
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60
  if (h > 0) return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

function payloadSummary(action: TimelineActionResponse): string {
  const p = action.payload
  if (action.actionType === 'CTA_SHOW' || action.actionType === 'CTA_HIDE') {
    const cta = ctas.value.find((c) => c.id === p.ctaId)
    return cta ? cta.title : String(p.ctaId ?? '').slice(0, 8) + '…'
  }
  if (action.actionType === 'ADMIN_MESSAGE_SHOW' || action.actionType === 'SYSTEM_MESSAGE_SHOW') {
    const text = String(p.text ?? '')
    return text.length > 60 ? text.slice(0, 60) + '…' : text
  }
  if (action.actionType === 'ROOM_STATE_CHANGE') return String(p.state ?? '')
  if (action.actionType === 'HISTORICAL_CHAT_REPLAY') return `msg:${String(p.messageId ?? '').slice(0, 8)}…`
  return JSON.stringify(p).slice(0, 40)
}

function formatDateTime(iso: string) {
  try { return format(new Date(iso), 'dd.MM.yyyy HH:mm') } catch { return iso }
}

useHead(() => ({
  title: session.value
    ? `Таймлайн — ${formatDateTime(session.value.startTime)}`
    : 'Редактор таймлайна',
}))
</script>

<template>
  <div class="mx-auto w-full max-w-5xl px-4 py-6 md:px-6 md:py-8">
    <!-- Session load error -->
    <UiCard v-if="sessionError" class="border-danger-200">
      <div class="py-8 text-center">
        <h2 class="text-lg font-semibold text-slate-900">Сессия не найдена</h2>
        <p class="mt-2 text-sm text-slate-500">Эта сессия не существует или у вас нет прав доступа.</p>
        <NuxtLink to="/admin" class="btn-ghost mt-4 inline-flex">
          <ArrowLeft class="h-4 w-4" />
          Назад
        </NuxtLink>
      </div>
    </UiCard>

    <template v-else-if="session">
      <!-- Header -->
      <PageHeader
        :title="`Таймлайн: ${formatDateTime(session.startTime)}`"
        subtitle="Действия, воспроизводимые в AUTO-сессиях: CTA, сообщения, изменения состояния"
      >
        <template #actions>
          <UiButton variant="primary" size="md" @click="openCreate">
            <Plus class="h-4 w-4" />
            Новое действие
          </UiButton>
          <UiButton variant="outline" size="md" :loading="loading" @click="loadTimeline">
            <RefreshCw class="h-4 w-4" />
            Обновить
          </UiButton>
          <UiButton variant="ghost" size="md" :to="`/admin/sessions/${sessionId}/analytics`">
            <ArrowLeft class="h-4 w-4" />
            К аналитике
          </UiButton>
        </template>
      </PageHeader>

      <!-- Stats strip -->
      <div class="mt-5 grid grid-cols-3 gap-3">
        <UiCard class="!py-3 text-center">
          <p class="text-2xl font-bold text-slate-900">{{ totalCount }}</p>
          <p class="text-xs text-slate-500">Всего действий</p>
        </UiCard>
        <UiCard class="!py-3 text-center">
          <p class="text-2xl font-bold text-success-600">{{ activeCount }}</p>
          <p class="text-xs text-slate-500">Активных</p>
        </UiCard>
        <UiCard class="!py-3 text-center">
          <p class="text-2xl font-bold text-brand-600">{{ ctaShowCount }}</p>
          <p class="text-xs text-slate-500">Показов CTA</p>
        </UiCard>
      </div>

      <!-- Load error -->
      <UiCard v-if="loadError" class="mt-4 border-danger-200 bg-danger-50/60">
        <p class="text-sm text-danger-700">{{ loadError }}</p>
        <template #footer>
          <UiButton variant="outline" size="sm" @click="loadTimeline">Повторить</UiButton>
        </template>
      </UiCard>

      <!-- Loading -->
      <div v-else-if="loading && actions.length === 0" class="mt-4 space-y-2">
        <UiSkeleton v-for="i in 6" :key="i" h="h-[56px]" rounded="rounded-lg" />
      </div>

      <!-- Empty -->
      <UiEmpty
        v-else-if="actions.length === 0"
        title="Таймлайн пуст"
        description="Добавьте первое действие -- показ CTA, отправка сообщения или изменение состояния комнаты."
        class="mt-4"
      >
        <template #icon><Clock class="h-5 w-5" /></template>
        <template #actions>
          <UiButton variant="primary" @click="openCreate">
            <Plus class="h-4 w-4" />
            Новое действие
          </UiButton>
        </template>
      </UiEmpty>

      <!-- Action list -->
      <div v-else class="mt-4">
        <UiCard :padded="false">
          <!-- Header row -->
          <div class="border-b border-slate-100 bg-slate-50 px-4 py-2.5">
            <div class="grid grid-cols-[80px_1fr_2fr_auto] items-center gap-3 text-xs font-medium uppercase tracking-wide text-slate-500">
              <span>Время</span>
              <span>Тип</span>
              <span>Содержимое</span>
              <span class="text-right">Действия</span>
            </div>
          </div>

          <ul class="divide-y divide-slate-100">
            <li
              v-for="action in actions"
              :key="action.id"
              class="px-4 py-3 transition-colors"
              :class="!action.active ? 'bg-slate-50/80 opacity-60' : ''"
            >
              <div class="grid grid-cols-[80px_1fr_2fr_auto] items-center gap-3">
                <!-- Offset -->
                <div v-if="editingId === action.id" class="flex items-center gap-1">
                  <input
                    v-model.number="editForm.offsetMinutes"
                    type="number"
                    min="0"
                    class="w-10 rounded border border-slate-300 px-1 py-0.5 text-center font-mono text-xs"
                  />
                  <span class="text-xs text-slate-400">:</span>
                  <input
                    v-model.number="editForm.offsetExtraSeconds"
                    type="number"
                    min="0"
                    max="59"
                    class="w-10 rounded border border-slate-300 px-1 py-0.5 text-center font-mono text-xs"
                  />
                </div>
                <span v-else class="rounded bg-slate-100 px-2 py-1 text-center font-mono text-xs text-slate-600">
                  {{ formatOffset(action.offsetSeconds) }}
                </span>

                <!-- Type badge -->
                <div class="flex items-center gap-2">
                  <span
                    class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium"
                    :class="ACTION_TYPE_COLORS[action.actionType]"
                  >
                    <component :is="ACTION_TYPE_ICONS[action.actionType]" class="h-3.5 w-3.5" />
                    {{ ACTION_TYPE_LABELS[action.actionType] }}
                  </span>
                </div>

                <!-- Payload summary -->
                <p class="truncate text-sm text-slate-700">
                  {{ payloadSummary(action) }}
                </p>

                <!-- Actions -->
                <div class="flex items-center justify-end gap-1">
                  <template v-if="editingId === action.id">
                    <button
                      class="rounded p-1.5 text-success-600 hover:bg-success-50"
                      title="Сохранить"
                      @click="saveEdit(action)"
                    >
                      <Check class="h-4 w-4" />
                    </button>
                    <button
                      class="rounded p-1.5 text-slate-400 hover:bg-slate-100"
                      title="Отмена"
                      @click="cancelEdit"
                    >
                      <X class="h-4 w-4" />
                    </button>
                  </template>
                  <template v-else>
                    <button
                      class="rounded p-1.5 transition-colors"
                      :class="action.active
                        ? 'text-success-600 hover:bg-success-50'
                        : 'text-slate-400 hover:bg-slate-100'"
                      :disabled="togglingId === action.id"
                      :title="action.active ? 'Выключить' : 'Включить'"
                      @click="toggleActive(action)"
                    >
                      <component
                        :is="action.active ? ToggleRight : ToggleLeft"
                        class="h-4 w-4"
                        :class="togglingId === action.id && 'animate-pulse'"
                      />
                    </button>
                    <button
                      class="rounded p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600"
                      title="Изменить время"
                      @click="startEdit(action)"
                    >
                      <Pencil class="h-4 w-4" />
                    </button>
                    <button
                      class="rounded p-1.5 text-slate-400 hover:bg-danger-50 hover:text-danger-600"
                      title="Удалить"
                      @click="askDelete(action)"
                    >
                      <Trash2 class="h-4 w-4" />
                    </button>
                  </template>
                </div>
              </div>
            </li>
          </ul>
        </UiCard>
      </div>
    </template>

    <!-- Create modal -->
    <UiModal v-model="createModalOpen" title="Новое действие таймлайна" size="md">
      <div class="space-y-4">
        <!-- Offset -->
        <div>
          <label class="mb-1.5 block text-sm font-medium text-slate-700">Время (мин:сек)</label>
          <div class="flex items-center gap-2">
            <UiInput
              v-model.number="createForm.offsetMinutes"
              type="number"
              :min="0"
              placeholder="мин"
              class="w-24"
            />
            <span class="text-slate-400">:</span>
            <UiInput
              v-model.number="createForm.offsetExtraSeconds"
              type="number"
              :min="0"
              :max="59"
              placeholder="сек"
              class="w-24"
            />
          </div>
        </div>

        <!-- Action type -->
        <div>
          <label class="mb-1.5 block text-sm font-medium text-slate-700">Тип действия</label>
          <UiSelect v-model="createForm.actionType">
            <option v-for="t in CREATABLE_TYPES" :key="t" :value="t">
              {{ ACTION_TYPE_LABELS[t] }}
            </option>
          </UiSelect>
        </div>

        <!-- CTA select (for CTA_SHOW / CTA_HIDE) -->
        <div v-if="createForm.actionType === 'CTA_SHOW' || createForm.actionType === 'CTA_HIDE'">
          <label class="mb-1.5 block text-sm font-medium text-slate-700">CTA</label>
          <UiSelect v-model="createForm.ctaId">
            <option v-for="c in ctas" :key="c.id" :value="c.id">
              {{ c.title }} ({{ c.type }})
            </option>
          </UiSelect>
          <p v-if="ctas.length === 0" class="mt-1 text-xs text-warning-600">
            Нет CTA. Сначала добавьте CTA на странице мероприятия.
          </p>
        </div>

        <!-- Text (for message types) -->
        <div v-if="createForm.actionType === 'ADMIN_MESSAGE_SHOW' || createForm.actionType === 'SYSTEM_MESSAGE_SHOW'">
          <label class="mb-1.5 block text-sm font-medium text-slate-700">Текст сообщения</label>
          <textarea
            v-model="createForm.text"
            rows="3"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:ring-1 focus:ring-brand-500"
            placeholder="Введите текст сообщения..."
          />
        </div>

        <!-- State (for ROOM_STATE_CHANGE) -->
        <div v-if="createForm.actionType === 'ROOM_STATE_CHANGE'">
          <label class="mb-1.5 block text-sm font-medium text-slate-700">Новое состояние</label>
          <UiInput v-model="createForm.state" placeholder="Например: CHAT_DISABLED" />
        </div>
      </div>

      <template #footer>
        <UiButton variant="outline" @click="createModalOpen = false">Отмена</UiButton>
        <UiButton variant="primary" :loading="creating" @click="submitCreate">
          <Plus class="h-4 w-4" />
          Добавить
        </UiButton>
      </template>
    </UiModal>

    <!-- Delete confirm -->
    <UiModal v-model="deleteModalOpen" title="Удалить действие" size="sm">
      <p class="text-sm text-slate-600">
        Это действие таймлайна будет удалено безвозвратно. Оно не будет воспроизводиться в AUTO-сессиях.
      </p>
      <template #footer>
        <UiButton variant="outline" :disabled="deleting" @click="deleteModalOpen = false">
          Отмена
        </UiButton>
        <UiButton variant="danger" :loading="deleting" @click="confirmDelete">
          <Trash2 class="h-4 w-4" />
          Удалить
        </UiButton>
      </template>
    </UiModal>
  </div>
</template>
