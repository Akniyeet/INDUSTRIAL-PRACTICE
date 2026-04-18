<script setup lang="ts">
/**
 * Session analytics dashboard at {@code /admin/sessions/{id}/analytics}.
 *
 * <p>Loads the full session report bundle from
 * {@code GET /api/v1/events/{eventId}/sessions/{sessionId}/report} and
 * renders:
 * <ol>
 *   <li>Summary strip — top-line KPIs (6 metric cards)</li>
 *   <li>Two-column grid — retention curve + CTA performance table</li>
 *   <li>AI Lead Scoring panel — classification + scoring trigger</li>
 * </ol>
 *
 * <p>The session and event ids are both needed for the report endpoint.
 * We first load the session to get {@code eventId}, then fire the report.
 */
import type { SessionReportResponse, SessionResponse } from '#shared/api/types'
import { ArrowLeft, Download, RefreshCw, BarChart3, Activity, Target, Users2 } from 'lucide-vue-next'
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
    reportError.value = apiErr.detail ?? apiErr.title ?? 'Қате кезінде есеп жүктелді'
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
  <div class="mx-auto w-full max-w-7xl px-4 py-6 md:px-6 md:py-8">
    <!-- Session load error -->
    <UiCard v-if="sessionError" class="border-danger-200">
      <div class="py-8 text-center">
        <h2 class="text-lg font-semibold text-slate-900">Сессия табылмады</h2>
        <p class="mt-2 text-sm text-slate-500">Бұл сессия жоқ немесе сізде рұқсат жоқ.</p>
        <NuxtLink to="/admin" class="btn-ghost mt-4 inline-flex">
          <ArrowLeft class="h-4 w-4" />
          Артқа
        </NuxtLink>
      </div>
    </UiCard>

    <template v-else-if="session">
      <!-- Header -->
      <PageHeader
        :title="`Аналитика: ${formatDateTime(session.startTime)}`"
        :subtitle="`${session.type} сессия · ${formatDuration(session.plannedDurationSeconds)} · ${session.status}`"
        :breadcrumbs="[
          { label: 'Басты', to: '/admin' },
          { label: 'Сессиялар', to: '/admin/sessions' },
          { label: 'Аналитика' },
        ]"
      >
        <template #actions>
          <UiButton variant="outline" size="md" :loading="reportLoading" @click="loadReport">
            <RefreshCw class="h-4 w-4" />
            Жаңарту
          </UiButton>
          <UiButton variant="outline" size="md" @click="downloadExport">
            <Download class="h-4 w-4" />
            Excel
          </UiButton>
          <UiButton variant="ghost" size="md" to="/admin/sessions">
            <ArrowLeft class="h-4 w-4" />
            Сессияларға
          </UiButton>
        </template>
      </PageHeader>

      <!-- Report error -->
      <UiCard v-if="reportError" class="mt-5 border-danger-200 bg-danger-50/60">
        <p class="text-sm text-danger-700">{{ reportError }}</p>
        <template #footer>
          <UiButton variant="outline" size="sm" @click="loadReport">Қайталау</UiButton>
        </template>
      </UiCard>

      <!-- Loading skeletons -->
      <div v-else-if="reportLoading && !report" class="mt-6 space-y-6">
        <!-- KPI skeleton -->
        <div class="grid grid-cols-2 gap-3 sm:grid-cols-3 xl:grid-cols-6">
          <UiSkeleton v-for="i in 6" :key="i" h="h-[96px]" rounded="rounded-2xl" />
        </div>
        <!-- Charts skeleton -->
        <div class="grid gap-5 lg:grid-cols-2">
          <UiSkeleton h="h-[340px]" rounded="rounded-2xl" />
          <UiSkeleton h="h-[340px]" rounded="rounded-2xl" />
        </div>
        <!-- AI panel skeleton -->
        <UiSkeleton h="h-[200px]" rounded="rounded-2xl" />
      </div>

      <!-- Report content -->
      <div v-else-if="report" class="mt-6 space-y-6">

        <!-- Section: KPI Summary -->
        <section>
          <div class="mb-3 flex items-center gap-2">
            <Activity class="h-4 w-4 text-slate-400" />
            <h2 class="text-sm font-semibold uppercase tracking-wide text-slate-400">Негізгі метрикалар</h2>
          </div>
          <SummaryStrip :summary="report.summary" />
        </section>

        <!-- Section: Charts -->
        <section>
          <div class="mb-3 flex items-center gap-2">
            <BarChart3 class="h-4 w-4 text-slate-400" />
            <h2 class="text-sm font-semibold uppercase tracking-wide text-slate-400">Retention және CTA</h2>
          </div>
          <div class="grid gap-5 lg:grid-cols-2">
            <RetentionChart :points="report.retentionCurve" />
            <CtaCtrTable :rows="report.ctaCtr" />
          </div>
        </section>

        <!-- Section: AI Lead Scoring -->
        <section>
          <div class="mb-3 flex items-center gap-2">
            <Users2 class="h-4 w-4 text-slate-400" />
            <h2 class="text-sm font-semibold uppercase tracking-wide text-slate-400">AI Lead Scoring</h2>
          </div>
          <UiCard class="bg-slate-950 border-violet-800/30">
            <AiLeadScorePanel :session-id="sessionId" />
          </UiCard>
        </section>
      </div>

      <!-- No report yet -->
      <UiEmpty
        v-else
        title="Аналитика жоқ"
        description="Деректер сессия аяқталғаннан кейін пайда болады."
        class="mt-6"
      >
        <template #icon><BarChart3 class="h-5 w-5" /></template>
      </UiEmpty>
    </template>
  </div>
</template>
