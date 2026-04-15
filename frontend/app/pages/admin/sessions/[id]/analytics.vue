<script setup lang="ts">
/**
 * Session analytics dashboard at {@code /admin/sessions/{id}/analytics}.
 *
 * <p>Loads the full session report bundle from
 * {@code GET /api/v1/events/{eventId}/sessions/{sessionId}/report} and
 * renders three blocks:
 * <ol>
 *   <li>Summary strip — top-line KPIs (attendees, long watchers, chat, CTA clicks, moderation)</li>
 *   <li>Retention chart — bar chart showing viewer drop-off at sample points</li>
 *   <li>CTA CTR table — per-CTA impressions, clicks, downloads, CTR%</li>
 * </ol>
 *
 * <p>The session and event ids are both needed for the report endpoint.
 * We first load the session to get {@code eventId}, then fire the report.
 */
import type { SessionReportResponse, SessionResponse } from '#shared/api/types'
import { ArrowLeft, Download, RefreshCw, BarChart3 } from 'lucide-vue-next'
import { format } from 'date-fns'
import { ref } from 'vue'

definePageMeta({
  layout: 'admin',
  middleware: 'auth',
})

const route = useRoute()
const api = useApi()

const sessionId = computed(() => String(route.params.id))

// Step 1: load session to get eventId
const { data: session, error: sessionError } = await useAsyncData(
  () => `analytics-session:${sessionId.value}`,
  () => api.sessions.getById(sessionId.value),
)

// Step 2: load report
const report = ref<SessionReportResponse | null>(null)
const reportLoading = ref(false)
const reportError = ref<string | null>(null)

async function loadReport() {
  if (!session.value) return
  reportLoading.value = true
  reportError.value = null
  try {
    report.value = await api.analytics.report(session.value.eventId, sessionId.value)
  } catch (err) {
    const apiErr = err as { detail?: string; title?: string }
    reportError.value = apiErr.detail ?? apiErr.title ?? 'Ошибка загрузки отчёта'
  } finally {
    reportLoading.value = false
  }
}

onMounted(loadReport)

useHead(() => ({
  title: session.value
    ? `Аналитика — ${format(new Date(session.value.startTime), 'dd.MM.yyyy HH:mm')}`
    : 'Аналитика сессии',
}))

function downloadExport() {
  const config = useRuntimeConfig()
  const base = config.public.apiBase || '/api/backend'
  window.open(`${base}/v1/sessions/${sessionId.value}/export`, '_blank')
}

function formatDateTime(iso: string) {
  try { return format(new Date(iso), 'dd.MM.yyyy HH:mm') } catch { return iso }
}

function formatDuration(seconds: number) {
  const m = Math.round(seconds / 60)
  if (m < 60) return `${m} мин`
  const h = Math.floor(m / 60)
  const rem = m % 60
  return rem === 0 ? `${h} ч` : `${h} ч ${rem} мин`
}
</script>

<template>
  <div class="mx-auto w-full max-w-6xl px-4 py-6 md:px-6 md:py-8">
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
        :title="`Аналитика: ${formatDateTime(session.startTime)}`"
        :subtitle="`${session.type} сессия · ${formatDuration(session.plannedDurationSeconds)} · ${session.status}`"
      >
        <template #actions>
          <UiButton variant="outline" size="md" :loading="reportLoading" @click="loadReport">
            <RefreshCw class="h-4 w-4" />
            Обновить
          </UiButton>
          <UiButton variant="outline" size="md" @click="downloadExport">
            <Download class="h-4 w-4" />
            Excel
          </UiButton>
          <UiButton variant="ghost" size="md" to="/admin/events">
            <ArrowLeft class="h-4 w-4" />
            К мероприятиям
          </UiButton>
        </template>
      </PageHeader>

      <!-- Report error -->
      <UiCard v-if="reportError" class="mt-5 border-danger-200 bg-danger-50/60">
        <p class="text-sm text-danger-700">{{ reportError }}</p>
        <template #footer>
          <UiButton variant="outline" size="sm" @click="loadReport">Повторить</UiButton>
        </template>
      </UiCard>

      <!-- Loading -->
      <div v-else-if="reportLoading && !report" class="mt-5 space-y-4">
        <div class="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
          <UiSkeleton v-for="i in 5" :key="i" h="h-[80px]" rounded="rounded-xl" />
        </div>
        <UiSkeleton h="h-[300px]" rounded="rounded-xl" />
        <UiSkeleton h="h-[200px]" rounded="rounded-xl" />
      </div>

      <!-- Report content -->
      <div v-else-if="report" class="mt-5 space-y-5">
        <!-- Summary strip -->
        <SummaryStrip :summary="report.summary" />

        <!-- Two-column layout on desktop -->
        <div class="grid gap-5 lg:grid-cols-2">
          <!-- Retention chart -->
          <RetentionChart :points="report.retentionCurve" />

          <!-- CTA CTR table -->
          <CtaCtrTable :rows="report.ctaCtr" />
        </div>

        <!-- AI Lead Scoring -->
        <UiCard class="bg-slate-950 border-violet-800/30">
          <AiLeadScorePanel :session-id="sessionId" />
        </UiCard>
      </div>

      <!-- No report yet -->
      <UiEmpty
        v-else
        title="Аналитика отсутствует"
        description="Данные появятся здесь после завершения сессии."
        class="mt-5"
      >
        <template #icon><BarChart3 class="h-5 w-5" /></template>
      </UiEmpty>
    </template>
  </div>
</template>
