<script setup lang="ts">
/**
 * AI Lead Score panel — shows lead classification summary and detail list
 * for a session. Includes on-demand scoring trigger.
 */
import {
  Brain, Flame, ThermometerSun, Snowflake,
  Sparkles, MessageSquare, MousePointerClick,
  Clock, ChevronDown, ChevronUp,
} from 'lucide-vue-next'
import type { AiLeadScoreResponse, SessionLeadSummaryResponse, LeadClassification } from '#shared/api/endpoints/aiLeadScores'

const props = defineProps<{ sessionId: string }>()

const api = useApi()
const toast = useToastStore()

const summary = ref<SessionLeadSummaryResponse | null>(null)
const scores = ref<AiLeadScoreResponse[]>([])
const loading = ref(true)
const scoring = ref(false)
const filter = ref<LeadClassification | null>(null)
const expandedId = ref<string | null>(null)

async function loadData() {
  loading.value = true
  try {
    const [s, list] = await Promise.all([
      api.aiLeadScores.getSessionSummary(props.sessionId),
      api.aiLeadScores.getSessionScores(props.sessionId, filter.value ?? undefined),
    ])
    summary.value = s
    scores.value = list
  } catch {
    // silently fail — panel is informational
  } finally {
    loading.value = false
  }
}

async function triggerScoring() {
  scoring.value = true
  try {
    const results = await api.aiLeadScores.scoreSession(props.sessionId)
    scores.value = results
    toast.success(`AI-анализ завершён: ${results.length} зрителей оценено`)
    await loadData()
  } catch (e: any) {
    toast.error(e?.message ?? 'Ошибка AI-скоринга')
  } finally {
    scoring.value = false
  }
}

function toggleExpand(id: string) {
  expandedId.value = expandedId.value === id ? null : id
}

function classIcon(c: LeadClassification) {
  if (c === 'HOT') return Flame
  if (c === 'WARM') return ThermometerSun
  return Snowflake
}

function classColor(c: LeadClassification) {
  if (c === 'HOT') return 'text-red-400'
  if (c === 'WARM') return 'text-amber-400'
  return 'text-blue-400'
}

function classBg(c: LeadClassification) {
  if (c === 'HOT') return 'bg-red-500/10 border-red-500/20'
  if (c === 'WARM') return 'bg-amber-500/10 border-amber-500/20'
  return 'bg-blue-500/10 border-blue-500/20'
}

function classLabel(c: LeadClassification) {
  if (c === 'HOT') return 'Горячий'
  if (c === 'WARM') return 'Тёплый'
  return 'Холодный'
}

function fmtMinutes(seconds: number) {
  return `${Math.round(seconds / 60)} мин`
}

function fmtPercent(v: number) {
  return `${Math.round(v)}%`
}

onMounted(loadData)

watch(filter, () => loadData())
</script>

<template>
  <div class="space-y-4">
    <!-- Header -->
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-2">
        <Brain class="h-5 w-5 text-violet-400" />
        <h3 class="text-sm font-semibold text-white">AI Lead Scoring</h3>
        <span class="rounded-full bg-violet-500/10 px-2 py-0.5 text-[10px] font-medium text-violet-400">
          Powered by Claude
        </span>
      </div>
      <button
        class="flex items-center gap-1.5 rounded-lg border border-violet-600/30 bg-violet-600/10 px-3 py-1.5 text-xs font-medium text-violet-400 hover:bg-violet-600/20 disabled:opacity-50"
        :disabled="scoring"
        @click="triggerScoring"
      >
        <Sparkles class="h-3.5 w-3.5" :class="scoring && 'animate-spin'" />
        {{ scoring ? 'Анализирую...' : 'Запустить AI-анализ' }}
      </button>
    </div>

    <!-- Summary strip -->
    <div v-if="summary && summary.total > 0" class="grid grid-cols-4 gap-2">
      <div class="rounded-xl border border-slate-700 bg-slate-800/50 p-3 text-center">
        <p class="text-lg font-bold text-white">{{ summary.total }}</p>
        <p class="text-[10px] text-slate-500">Всего</p>
      </div>
      <button
        class="rounded-xl border p-3 text-center transition-colors"
        :class="filter === 'HOT' ? 'border-red-500/40 bg-red-500/10' : 'border-slate-700 bg-slate-800/50 hover:border-red-500/20'"
        @click="filter = filter === 'HOT' ? null : 'HOT'"
      >
        <div class="flex items-center justify-center gap-1">
          <Flame class="h-4 w-4 text-red-400" />
          <p class="text-lg font-bold text-red-400">{{ summary.hot }}</p>
        </div>
        <p class="text-[10px] text-slate-500">Горячие</p>
      </button>
      <button
        class="rounded-xl border p-3 text-center transition-colors"
        :class="filter === 'WARM' ? 'border-amber-500/40 bg-amber-500/10' : 'border-slate-700 bg-slate-800/50 hover:border-amber-500/20'"
        @click="filter = filter === 'WARM' ? null : 'WARM'"
      >
        <div class="flex items-center justify-center gap-1">
          <ThermometerSun class="h-4 w-4 text-amber-400" />
          <p class="text-lg font-bold text-amber-400">{{ summary.warm }}</p>
        </div>
        <p class="text-[10px] text-slate-500">Тёплые</p>
      </button>
      <button
        class="rounded-xl border p-3 text-center transition-colors"
        :class="filter === 'COLD' ? 'border-blue-500/40 bg-blue-500/10' : 'border-slate-700 bg-slate-800/50 hover:border-blue-500/20'"
        @click="filter = filter === 'COLD' ? null : 'COLD'"
      >
        <div class="flex items-center justify-center gap-1">
          <Snowflake class="h-4 w-4 text-blue-400" />
          <p class="text-lg font-bold text-blue-400">{{ summary.cold }}</p>
        </div>
        <p class="text-[10px] text-slate-500">Холодные</p>
      </button>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="space-y-2">
      <div v-for="i in 5" :key="i" class="h-16 animate-pulse rounded-xl bg-slate-800" />
    </div>

    <!-- Empty state -->
    <div v-else-if="!scores.length" class="flex flex-col items-center gap-3 py-10">
      <Brain class="h-10 w-10 text-slate-600" />
      <p class="text-sm text-slate-500">
        {{ summary && summary.total > 0 ? 'Нет лидов с таким фильтром' : 'AI-анализ ещё не запущен' }}
      </p>
      <button
        v-if="!summary || summary.total === 0"
        class="rounded-lg bg-violet-600 px-4 py-2 text-sm font-medium text-white hover:bg-violet-700"
        :disabled="scoring"
        @click="triggerScoring"
      >
        {{ scoring ? 'Анализирую...' : 'Запустить анализ' }}
      </button>
    </div>

    <!-- Score cards -->
    <div v-else class="space-y-2">
      <div
        v-for="s in scores"
        :key="s.id"
        class="rounded-xl border transition-colors"
        :class="classBg(s.classification)"
      >
        <!-- Row -->
        <button
          class="flex w-full items-center gap-3 px-4 py-3 text-left"
          @click="toggleExpand(s.id)"
        >
          <component :is="classIcon(s.classification)" class="h-5 w-5 shrink-0" :class="classColor(s.classification)" />
          <div class="min-w-0 flex-1">
            <div class="flex items-center gap-2">
              <span class="text-sm font-medium text-white">{{ s.profileId.slice(0, 8) }}...</span>
              <span
                class="rounded-full px-2 py-0.5 text-[10px] font-bold"
                :class="classColor(s.classification)"
              >{{ classLabel(s.classification) }}</span>
              <span class="text-[10px] text-slate-500">{{ fmtPercent(s.confidence * 100) }} уверенность</span>
            </div>
            <p v-if="s.reasoning" class="mt-0.5 truncate text-xs text-slate-400">{{ s.reasoning }}</p>
          </div>
          <div class="flex shrink-0 items-center gap-3 text-xs text-slate-400">
            <span class="flex items-center gap-1"><Clock class="h-3 w-3" /> {{ fmtMinutes(s.watchDurationSeconds) }}</span>
            <span class="flex items-center gap-1"><MessageSquare class="h-3 w-3" /> {{ s.chatMessagesCount }}</span>
            <span class="flex items-center gap-1"><MousePointerClick class="h-3 w-3" /> {{ s.ctaClicksCount }}</span>
          </div>
          <component :is="expandedId === s.id ? ChevronUp : ChevronDown" class="h-4 w-4 shrink-0 text-slate-500" />
        </button>

        <!-- Expanded detail -->
        <div v-if="expandedId === s.id" class="border-t border-white/5 px-4 py-3 space-y-2">
          <div class="grid grid-cols-2 gap-3 sm:grid-cols-4">
            <div>
              <p class="text-[10px] text-slate-500">Просмотр</p>
              <p class="text-sm font-medium text-white">{{ fmtMinutes(s.watchDurationSeconds) }} ({{ fmtPercent(s.watchPercent) }})</p>
            </div>
            <div>
              <p class="text-[10px] text-slate-500">Чат</p>
              <p class="text-sm font-medium text-white">{{ s.chatMessagesCount }} сообщений</p>
            </div>
            <div>
              <p class="text-[10px] text-slate-500">CTA клики</p>
              <p class="text-sm font-medium text-white">{{ s.ctaClicksCount }}</p>
            </div>
            <div>
              <p class="text-[10px] text-slate-500">Rule-based score</p>
              <p class="text-sm font-medium text-white">{{ s.ruleScore }} баллов</p>
            </div>
          </div>
          <div v-if="s.reasoning" class="rounded-lg bg-slate-900/50 p-3">
            <p class="text-[10px] font-medium text-violet-400 mb-1">AI-анализ</p>
            <p class="text-xs text-slate-300">{{ s.reasoning }}</p>
          </div>
          <div v-if="s.recommendedAction" class="rounded-lg bg-slate-900/50 p-3">
            <p class="text-[10px] font-medium text-emerald-400 mb-1">Рекомендация</p>
            <p class="text-xs text-slate-300">{{ s.recommendedAction }}</p>
            <p v-if="s.followUpHours" class="mt-1 text-[10px] text-slate-500">
              Связаться в течение {{ s.followUpHours }}ч
            </p>
          </div>
          <div v-if="s.aiModel" class="text-[10px] text-slate-600">
            Модель: {{ s.aiModel }} · {{ s.returnedForAuto ? 'Возвращался на повтор' : 'Одно посещение' }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
