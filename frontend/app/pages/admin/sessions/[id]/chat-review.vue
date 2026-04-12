<script setup lang="ts">
/**
 * Historical chat review page at {@code /admin/sessions/{id}/chat-review}.
 *
 * <p>Loads the full replay-eligible transcript from the backend and lets
 * admins toggle individual messages in/out of the replay stream. Provides
 * bulk-exclude and bulk-include for efficient cleanup of noisy live chats
 * before they replay in AUTO sessions.
 */
import type { HistoricalChatMessageView, SessionResponse } from '#shared/api/types'
import {
  ArrowLeft,
  RefreshCw,
  MessageSquare,
  EyeOff,
  Eye,
  CheckSquare,
  Square,
  Filter,
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

// Step 1: load session metadata
const { data: session, error: sessionError } = await useAsyncData(
  () => `chat-review-session:${sessionId.value}`,
  () => api.sessions.getById(sessionId.value),
)

// Step 2: load transcript
const messages = ref<HistoricalChatMessageView[]>([])
const loading = ref(false)
const loadError = ref<string | null>(null)

async function loadTranscript() {
  loading.value = true
  loadError.value = null
  try {
    messages.value = await api.historicalChat.transcript(sessionId.value)
  } catch (err) {
    const apiErr = err as { detail?: string; title?: string }
    loadError.value = apiErr.detail ?? apiErr.title ?? 'Транскрипт жүктеу қатесі'
  } finally {
    loading.value = false
  }
}

onMounted(loadTranscript)

// Filter: all / included / excluded
type FilterMode = 'all' | 'included' | 'excluded'
const filterMode = ref<FilterMode>('all')

const filteredMessages = computed(() => {
  if (filterMode.value === 'included') return messages.value.filter((m) => !m.excludedFromReplay)
  if (filterMode.value === 'excluded') return messages.value.filter((m) => m.excludedFromReplay)
  return messages.value
})

// Selection for bulk actions
const selected = ref<Set<string>>(new Set())

function toggleSelect(id: string) {
  if (selected.value.has(id)) {
    selected.value.delete(id)
  } else {
    selected.value.add(id)
  }
}

function selectAll() {
  filteredMessages.value.forEach((m) => selected.value.add(m.id))
}

function deselectAll() {
  selected.value.clear()
}

const hasSelection = computed(() => selected.value.size > 0)

// Toggle single message
const togglingId = ref<string | null>(null)

async function toggleReplay(msg: HistoricalChatMessageView) {
  togglingId.value = msg.id
  try {
    const updated = await api.historicalChat.toggleReplay(sessionId.value, msg.id)
    const idx = messages.value.findIndex((m) => m.id === msg.id)
    if (idx >= 0) messages.value[idx] = updated
  } catch {
    toast.error('Қайта ойнату күйін өзгерту қатесі')
  } finally {
    togglingId.value = null
  }
}

// Bulk actions
const bulkActing = ref(false)

async function bulkExclude() {
  if (selected.value.size === 0) return
  bulkActing.value = true
  try {
    const ids = [...selected.value]
    const count = await api.historicalChat.bulkExclude(sessionId.value, ids)
    // Update local state
    for (const id of ids) {
      const idx = messages.value.findIndex((m) => m.id === id)
      if (idx >= 0) messages.value[idx] = { ...messages.value[idx], excludedFromReplay: true }
    }
    selected.value.clear()
    toast.success(`${count} хабарлама қайта ойнатудан шығарылды`)
  } catch {
    toast.error('Жаппай шығару қатесі')
  } finally {
    bulkActing.value = false
  }
}

async function bulkInclude() {
  if (selected.value.size === 0) return
  bulkActing.value = true
  try {
    const ids = [...selected.value]
    const count = await api.historicalChat.bulkInclude(sessionId.value, ids)
    for (const id of ids) {
      const idx = messages.value.findIndex((m) => m.id === id)
      if (idx >= 0) messages.value[idx] = { ...messages.value[idx], excludedFromReplay: false }
    }
    selected.value.clear()
    toast.success(`${count} хабарлама қайта ойнатуға қосылды`)
  } catch {
    toast.error('Жаппай қосу қатесі')
  } finally {
    bulkActing.value = false
  }
}

// Stats
const totalCount = computed(() => messages.value.length)
const includedCount = computed(() => messages.value.filter((m) => !m.excludedFromReplay).length)
const excludedCount = computed(() => messages.value.filter((m) => m.excludedFromReplay).length)

// Formatters
function formatOffset(seconds: number | null): string {
  if (seconds == null) return '--:--'
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

function formatDateTime(iso: string) {
  try { return format(new Date(iso), 'dd.MM.yyyy HH:mm') } catch { return iso }
}

function authorLabel(msg: HistoricalChatMessageView): string {
  if (msg.messageType === 'SYSTEM') return 'Жүйе'
  if (msg.messageType === 'ADMIN') return 'Админ'
  return msg.userId ? `${msg.userId.slice(0, 8)}…` : 'Аноним'
}

useHead(() => ({
  title: session.value
    ? `Чат тарихы — ${formatDateTime(session.value.startTime)}`
    : 'Чат тарихын қарау',
}))
</script>

<template>
  <div class="mx-auto w-full max-w-5xl px-4 py-6 md:px-6 md:py-8">
    <!-- Session load error -->
    <UiCard v-if="sessionError" class="border-danger-200">
      <div class="py-8 text-center">
        <h2 class="text-lg font-semibold text-slate-900">Сессия табылмады</h2>
        <p class="mt-2 text-sm text-slate-500">Бұл сессия жоқ немесе сізде кіру құқығы жоқ.</p>
        <NuxtLink to="/admin" class="btn-ghost mt-4 inline-flex">
          <ArrowLeft class="h-4 w-4" />
          Артқа
        </NuxtLink>
      </div>
    </UiCard>

    <template v-else-if="session">
      <!-- Header -->
      <PageHeader
        :title="`Чат тарихы: ${formatDateTime(session.startTime)}`"
        subtitle="AUTO сессияларда қайта ойнатылатын хабарламаларды басқару"
      >
        <template #actions>
          <UiButton variant="outline" size="md" :loading="loading" @click="loadTranscript">
            <RefreshCw class="h-4 w-4" />
            Жаңарту
          </UiButton>
          <UiButton variant="ghost" size="md" :to="`/admin/sessions/${sessionId}/analytics`">
            <ArrowLeft class="h-4 w-4" />
            Аналитикаға оралу
          </UiButton>
        </template>
      </PageHeader>

      <!-- Stats strip -->
      <div class="mt-5 grid grid-cols-3 gap-3">
        <UiCard class="!py-3 text-center">
          <p class="text-2xl font-bold text-slate-900">{{ totalCount }}</p>
          <p class="text-xs text-slate-500">Барлық хабарлама</p>
        </UiCard>
        <UiCard class="!py-3 text-center">
          <p class="text-2xl font-bold text-success-600">{{ includedCount }}</p>
          <p class="text-xs text-slate-500">Қайта ойнатылады</p>
        </UiCard>
        <UiCard class="!py-3 text-center">
          <p class="text-2xl font-bold text-warning-600">{{ excludedCount }}</p>
          <p class="text-xs text-slate-500">Шығарылған</p>
        </UiCard>
      </div>

      <!-- Toolbar -->
      <div class="mt-4 flex flex-wrap items-center justify-between gap-3">
        <!-- Filter tabs -->
        <div class="flex items-center gap-1 rounded-lg bg-slate-100 p-0.5">
          <button
            v-for="f in (['all', 'included', 'excluded'] as const)"
            :key="f"
            class="rounded-md px-3 py-1.5 text-xs font-medium transition-colors"
            :class="filterMode === f
              ? 'bg-white text-slate-900 shadow-sm'
              : 'text-slate-500 hover:text-slate-700'"
            @click="filterMode = f; deselectAll()"
          >
            {{ f === 'all' ? `Барлығы (${totalCount})` : f === 'included' ? `Қосылған (${includedCount})` : `Шығарылған (${excludedCount})` }}
          </button>
        </div>

        <!-- Selection + bulk actions -->
        <div class="flex items-center gap-2">
          <template v-if="hasSelection">
            <span class="text-xs text-slate-500">{{ selected.size }} таңдалған</span>
            <UiButton variant="outline" size="sm" :loading="bulkActing" @click="bulkExclude">
              <EyeOff class="h-3.5 w-3.5" />
              Шығару
            </UiButton>
            <UiButton variant="outline" size="sm" :loading="bulkActing" @click="bulkInclude">
              <Eye class="h-3.5 w-3.5" />
              Қосу
            </UiButton>
            <UiButton variant="ghost" size="sm" @click="deselectAll">
              Бас тарту
            </UiButton>
          </template>
          <template v-else>
            <UiButton variant="ghost" size="sm" @click="selectAll">
              <CheckSquare class="h-3.5 w-3.5" />
              Барлығын таңдау
            </UiButton>
          </template>
        </div>
      </div>

      <!-- Load error -->
      <UiCard v-if="loadError" class="mt-4 border-danger-200 bg-danger-50/60">
        <p class="text-sm text-danger-700">{{ loadError }}</p>
        <template #footer>
          <UiButton variant="outline" size="sm" @click="loadTranscript">Қайта көру</UiButton>
        </template>
      </UiCard>

      <!-- Loading -->
      <div v-else-if="loading && messages.length === 0" class="mt-4 space-y-2">
        <UiSkeleton v-for="i in 8" :key="i" h="h-[52px]" rounded="rounded-lg" />
      </div>

      <!-- Empty state -->
      <UiEmpty
        v-else-if="messages.length === 0"
        title="Чат хабарламалары жоқ"
        description="Бұл сессияда қайта ойнатуға жарамды хабарламалар жоқ."
        class="mt-4"
      >
        <template #icon><MessageSquare class="h-5 w-5" /></template>
      </UiEmpty>

      <!-- Filtered empty -->
      <UiEmpty
        v-else-if="filteredMessages.length === 0"
        title="Сүзгіге сәйкес хабарламалар жоқ"
        description="Басқа сүзгіні таңдап көріңіз."
        class="mt-4"
      >
        <template #icon><Filter class="h-5 w-5" /></template>
      </UiEmpty>

      <!-- Message list -->
      <div v-else class="mt-4">
        <UiCard :padded="false">
          <ul class="divide-y divide-slate-100">
            <li
              v-for="msg in filteredMessages"
              :key="msg.id"
              class="flex items-start gap-3 px-4 py-3 transition-colors"
              :class="[
                msg.excludedFromReplay ? 'bg-slate-50/80' : '',
                selected.has(msg.id) ? 'bg-brand-50/40' : '',
              ]"
            >
              <!-- Checkbox -->
              <button
                class="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded border transition-colors"
                :class="selected.has(msg.id)
                  ? 'border-brand-500 bg-brand-500 text-white'
                  : 'border-slate-300 text-transparent hover:border-slate-400'"
                @click="toggleSelect(msg.id)"
              >
                <svg v-if="selected.has(msg.id)" class="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="3">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" />
                </svg>
              </button>

              <!-- Offset time -->
              <span class="mt-0.5 shrink-0 rounded bg-slate-100 px-2 py-0.5 font-mono text-xs text-slate-500">
                {{ formatOffset(msg.offsetSeconds) }}
              </span>

              <!-- Content -->
              <div class="min-w-0 flex-1">
                <div class="flex items-center gap-2">
                  <span
                    class="text-xs font-semibold"
                    :class="{
                      'text-brand-600': msg.messageType === 'USER',
                      'text-danger-600': msg.messageType === 'ADMIN',
                      'text-slate-400': msg.messageType === 'SYSTEM',
                      'text-violet-600': msg.messageType === 'HISTORICAL',
                    }"
                  >
                    {{ authorLabel(msg) }}
                  </span>
                  <span
                    v-if="msg.excludedFromReplay"
                    class="inline-flex items-center gap-1 rounded-full bg-warning-100 px-1.5 py-0.5 text-[10px] font-medium text-warning-700"
                  >
                    <EyeOff class="h-2.5 w-2.5" />
                    Шығарылған
                  </span>
                </div>
                <p
                  class="mt-0.5 text-sm leading-relaxed"
                  :class="msg.excludedFromReplay ? 'text-slate-400 line-through' : 'text-slate-700'"
                >
                  {{ msg.text }}
                </p>
              </div>

              <!-- Toggle button -->
              <button
                class="mt-0.5 shrink-0 rounded-md p-1.5 transition-colors"
                :class="msg.excludedFromReplay
                  ? 'text-success-600 hover:bg-success-50'
                  : 'text-warning-600 hover:bg-warning-50'"
                :disabled="togglingId === msg.id"
                :title="msg.excludedFromReplay ? 'Қайта ойнатуға қосу' : 'Қайта ойнатудан шығару'"
                @click="toggleReplay(msg)"
              >
                <component
                  :is="msg.excludedFromReplay ? Eye : EyeOff"
                  class="h-4 w-4"
                  :class="togglingId === msg.id && 'animate-pulse'"
                />
              </button>
            </li>
          </ul>
        </UiCard>
      </div>
    </template>
  </div>
</template>
