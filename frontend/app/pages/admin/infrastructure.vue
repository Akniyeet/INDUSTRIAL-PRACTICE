<script setup lang="ts">
/**
 * Infrastructure health monitoring page.
 * Fetches real service status from {@code GET /api/v1/infrastructure/health}.
 */
import {
  Activity, Server, Database, Zap, Wifi, AlertTriangle,
  RefreshCw, CheckCircle2, XCircle, Clock, HardDrive,
  Radio, Shield, Globe, MessageSquare,
} from 'lucide-vue-next'
import { ref, onMounted, onBeforeUnmount } from 'vue'
import type { ServiceHealth } from '#shared/api/endpoints/infrastructure'

definePageMeta({ layout: 'admin', middleware: 'auth' })
useHead({ title: 'Инфраструктура — Webizon' })

const api = useApi()

const loading = ref(false)
const loadError = ref<string | null>(null)
const overall = ref<'healthy' | 'degraded' | 'down'>('healthy')
const services = ref<ServiceHealth[]>([])
const lastUpdated = ref(new Date())
const autoRefresh = ref(true)
let refreshTimer: ReturnType<typeof setInterval> | null = null

// Icon mapping by service name keyword
function iconForService(name: string) {
  const n = name.toLowerCase()
  if (n.includes('db') || n.includes('postgres') || n.includes('datasource')) return Database
  if (n.includes('redis') || n.includes('lettuce')) return Zap
  if (n.includes('kafka') || n.includes('binder')) return Radio
  if (n.includes('centrifugo')) return Wifi
  if (n.includes('clickhouse')) return HardDrive
  if (n.includes('minio') || n.includes('s3') || n.includes('disk')) return Globe
  if (n.includes('keycloak') || n.includes('oauth') || n.includes('jwt')) return Shield
  if (n.includes('mail') || n.includes('smtp')) return MessageSquare
  return Server
}

function friendlyName(name: string): string {
  const map: Record<string, string> = {
    db: 'PostgreSQL',
    redis: 'Redis',
    kafka: 'Kafka',
    ping: 'Backend API',
    diskSpace: 'Disk Space',
    mail: 'SMTP',
  }
  return map[name] ?? name.charAt(0).toUpperCase() + name.slice(1)
}

async function loadHealth() {
  loading.value = true
  loadError.value = null
  try {
    const resp = await api.infrastructure.health()
    overall.value = resp.overall
    services.value = resp.services
    lastUpdated.value = new Date()
  } catch (err) {
    const e = err as { detail?: string; title?: string }
    loadError.value = e.detail ?? e.title ?? 'Не удалось загрузить данные о состоянии сервисов'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadHealth()
  refreshTimer = setInterval(() => {
    if (autoRefresh.value) loadHealth()
  }, 30_000)
})
onBeforeUnmount(() => { if (refreshTimer) clearInterval(refreshTimer) })

function statusColor(status: string) {
  if (status === 'healthy') return 'bg-emerald-500'
  if (status === 'degraded') return 'bg-amber-500'
  return 'bg-red-500'
}

function statusText(status: string) {
  if (status === 'healthy') return 'Работает'
  if (status === 'degraded') return 'Деградация'
  return 'Недоступен'
}

function overallBanner() {
  if (overall.value === 'healthy') return { text: 'Все сервисы работают нормально', cls: 'bg-emerald-50 text-emerald-700 border-emerald-200' }
  if (overall.value === 'degraded') return { text: 'Некоторые сервисы деградированы', cls: 'bg-amber-50 text-amber-700 border-amber-200' }
  return { text: 'Один или несколько сервисов недоступны', cls: 'bg-red-50 text-red-700 border-red-200' }
}
</script>

<template>
  <div class="space-y-6">
    <PageHeader
      title="Инфраструктура"
      subtitle="Мониторинг сервисов и метрик"
      :breadcrumbs="[{ label: 'Главная', to: '/admin' }, { label: 'Инфраструктура' }]"
    >
      <template #actions>
        <label class="flex items-center gap-2 text-xs text-slate-500">
          <input v-model="autoRefresh" type="checkbox" class="h-3.5 w-3.5 rounded border-slate-300 text-brand-600" />
          Авто-обновление (30с)
        </label>
        <UiButton variant="outline" size="sm" :loading="loading" @click="loadHealth">
          <RefreshCw class="h-4 w-4" />
          Обновить
        </UiButton>
        <span class="text-[10px] text-slate-400">{{ lastUpdated.toLocaleTimeString() }}</span>
      </template>
    </PageHeader>

    <!-- Load error -->
    <UiCard v-if="loadError" class="border-danger-200 bg-danger-50/60">
      <div class="flex items-center gap-3">
        <AlertTriangle class="h-5 w-5 text-danger-500" />
        <p class="text-sm text-danger-700">{{ loadError }}</p>
      </div>
      <template #footer>
        <UiButton variant="outline" size="sm" @click="loadHealth">Повторить</UiButton>
      </template>
    </UiCard>

    <template v-else>
      <!-- Overall status banner -->
      <div
        class="flex items-center gap-3 rounded-xl border px-4 py-3 text-sm font-medium"
        :class="overallBanner().cls"
      >
        <CheckCircle2 v-if="overall === 'healthy'" class="h-5 w-5" />
        <AlertTriangle v-else-if="overall === 'degraded'" class="h-5 w-5" />
        <XCircle v-else class="h-5 w-5" />
        {{ overallBanner().text }}
      </div>

      <!-- Loading skeletons -->
      <div v-if="loading && services.length === 0" class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <UiSkeleton v-for="i in 6" :key="i" h="h-[72px]" rounded="rounded-xl" />
      </div>

      <!-- Services grid -->
      <div v-else-if="services.length > 0" class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <div
          v-for="s in services"
          :key="s.name"
          class="flex items-center gap-3 rounded-xl border border-slate-200 bg-white p-4 transition hover:shadow-soft"
        >
          <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-slate-50">
            <component :is="iconForService(s.name)" class="h-5 w-5 text-slate-500" />
          </div>
          <div class="min-w-0 flex-1">
            <div class="flex items-center gap-2">
              <span class="text-sm font-medium text-slate-900">{{ friendlyName(s.name) }}</span>
              <span class="h-2 w-2 rounded-full" :class="statusColor(s.status)" />
            </div>
            <span class="text-xs text-slate-500">{{ statusText(s.status) }}</span>
          </div>
        </div>
      </div>

      <!-- Empty (backend returned no components) -->
      <UiEmpty
        v-else
        title="Нет данных"
        description="Backend не вернул информацию о компонентах."
        class="mt-4"
      >
        <template #icon><Activity class="h-5 w-5" /></template>
      </UiEmpty>

      <!-- Info note -->
      <UiCard class="border-slate-200">
        <div class="flex items-start gap-3 text-sm text-slate-500">
          <Clock class="mt-0.5 h-4 w-4 shrink-0" />
          <p>
            Данные получены от Spring Actuator Health Endpoint. Обновляются автоматически каждые 30 секунд.
            Для полного мониторинга подключите Grafana + Prometheus.
          </p>
        </div>
      </UiCard>
    </template>
  </div>
</template>
