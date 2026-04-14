<script setup lang="ts">
/**
 * Platform super-admin dashboard.
 *
 * Shows KPIs across all tenants: registered clients, active users,
 * live broadcasts, and financial summary.
 */
import {
  Building2, Users, Radio, TrendingUp, DollarSign,
  RefreshCw, ArrowRight, ShieldCheck, AlertCircle,
} from 'lucide-vue-next'
import { ref, onMounted } from 'vue'
import type { PlatformDashboardResponse } from '#shared/api/endpoints/platform'

definePageMeta({ layout: 'platform', middleware: 'platform-auth' })
useHead({ title: 'Platform Dashboard — Webizon' })

const api = useApi()
const loading = ref(true)
const error = ref<string | null>(null)
const data = ref<PlatformDashboardResponse | null>(null)

async function refresh() {
  loading.value = true
  error.value = null
  try {
    data.value = await api.platform.dashboard()
  } catch (e: any) {
    error.value = e?.message ?? 'Ошибка загрузки'
  } finally {
    loading.value = false
  }
}

onMounted(refresh)

function fmtKzt(v: number | undefined | null): string {
  if (v == null) return '—'
  return new Intl.NumberFormat('ru-KZ', {
    style: 'currency', currency: 'KZT',
    maximumFractionDigits: 0,
  }).format(v)
}

const maxMonthlyRevenue = computed(() =>
  Math.max(...(data.value?.monthlyRevenue ?? []).map(r => r.totalKzt), 1)
)

const kpis = computed(() => [
  {
    label: 'Клиентов (тенантов)',
    value: data.value?.totalTenants ?? 0,
    sub: `${data.value?.activeTenants ?? 0} активных`,
    icon: Building2,
    bg: 'bg-violet-50', color: 'text-violet-600',
  },
  {
    label: 'Пользователей',
    value: data.value?.totalUsers ?? 0,
    sub: 'всего в системе',
    icon: Users,
    bg: 'bg-brand-50', color: 'text-brand-600',
  },
  {
    label: 'Сейчас в эфире',
    value: data.value?.currentlyLiveSessions ?? 0,
    sub: `${data.value?.totalSessions ?? 0} сессий всего`,
    icon: Radio,
    bg: 'bg-danger-50', color: 'text-danger-600',
    pulse: (data.value?.currentlyLiveSessions ?? 0) > 0,
  },
  {
    label: 'Доход за месяц',
    value: fmtKzt(data.value?.revenueThisMonthKzt),
    sub: `НДС: ${fmtKzt(data.value?.vatThisMonthKzt)}`,
    icon: DollarSign,
    bg: 'bg-success-50', color: 'text-success-600',
    isString: true,
  },
])
</script>

<template>
  <div class="space-y-6">
    <!-- Header -->
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-3">
        <div class="flex h-10 w-10 items-center justify-center rounded-xl bg-violet-600">
          <ShieldCheck class="h-5 w-5 text-white" />
        </div>
        <div>
          <h1 class="text-xl font-bold text-white">Platform Dashboard</h1>
          <p class="text-xs text-slate-400">Обзор всей платформы в реальном времени</p>
        </div>
      </div>
      <button
        class="flex items-center gap-2 rounded-lg border border-slate-700 bg-slate-800 px-3 py-2 text-sm font-medium text-slate-300 hover:bg-slate-700"
        :disabled="loading"
        @click="refresh"
      >
        <RefreshCw class="h-4 w-4" :class="loading && 'animate-spin'" />
        Обновить
      </button>
    </div>

    <!-- Error -->
    <div
      v-if="error"
      class="flex items-center gap-3 rounded-xl border border-red-800 bg-red-900/30 p-4"
    >
      <AlertCircle class="h-5 w-5 shrink-0 text-red-400" />
      <p class="text-sm text-red-300">{{ error }}</p>
    </div>

    <!-- KPI strip -->
    <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
      <div
        v-for="kpi in kpis"
        :key="kpi.label"
        class="rounded-xl border border-slate-800 bg-slate-900 p-5"
      >
        <div class="flex items-start justify-between">
          <div>
            <p class="text-xs font-medium text-slate-400">{{ kpi.label }}</p>
            <div class="mt-1 flex items-center gap-2">
              <p class="text-2xl font-bold text-white">
                <span v-if="loading" class="inline-block h-6 w-24 animate-pulse rounded bg-slate-700" />
                <span v-else>{{ kpi.isString ? kpi.value : kpi.value.toLocaleString() }}</span>
              </p>
              <span v-if="kpi.pulse && !loading" class="relative flex h-2.5 w-2.5">
                <span class="absolute inline-flex h-full w-full animate-ping rounded-full bg-red-500 opacity-75" />
                <span class="relative inline-flex h-2.5 w-2.5 rounded-full bg-red-500" />
              </span>
            </div>
            <p class="mt-0.5 text-xs text-slate-500">{{ kpi.sub }}</p>
          </div>
          <div :class="['flex h-10 w-10 items-center justify-center rounded-lg', kpi.bg]">
            <component :is="kpi.icon" :class="['h-5 w-5', kpi.color]" />
          </div>
        </div>
      </div>
    </div>

    <!-- Financial summary row -->
    <div class="grid gap-3 sm:grid-cols-3">
      <div class="rounded-xl border border-slate-800 bg-slate-900 p-5">
        <p class="text-xs text-slate-400">Общий доход (за всё время)</p>
        <p class="mt-1 text-xl font-bold text-white">
          <span v-if="loading" class="inline-block h-6 w-32 animate-pulse rounded bg-slate-700" />
          <span v-else>{{ fmtKzt(data?.revenueAllTimeKzt) }}</span>
        </p>
        <p class="mt-0.5 text-xs text-slate-500">до НДС</p>
      </div>
      <div class="rounded-xl border border-slate-800 bg-slate-900 p-5">
        <p class="text-xs text-slate-400">НДС собрано (за всё время)</p>
        <p class="mt-1 text-xl font-bold text-emerald-400">
          <span v-if="loading" class="inline-block h-6 w-32 animate-pulse rounded bg-slate-700" />
          <span v-else>{{ fmtKzt((data?.revenueAllTimeKzt ?? 0) + (data?.vatThisMonthKzt ?? 0)) }}</span>
        </p>
        <p class="mt-0.5 text-xs text-slate-500">включен в платежи</p>
      </div>
      <div class="rounded-xl border border-slate-800 bg-slate-900 p-5">
        <p class="text-xs text-slate-400">Мероприятий / Сессий</p>
        <p class="mt-1 text-xl font-bold text-white">
          <span v-if="loading" class="inline-block h-6 w-24 animate-pulse rounded bg-slate-700" />
          <span v-else>{{ (data?.totalEvents ?? 0).toLocaleString() }} / {{ (data?.totalSessions ?? 0).toLocaleString() }}</span>
        </p>
        <p class="mt-0.5 text-xs text-slate-500">создано на платформе</p>
      </div>
    </div>

    <!-- Revenue chart (monthly) -->
    <div class="rounded-xl border border-slate-800 bg-slate-900 p-5">
      <div class="mb-4 flex items-center justify-between">
        <h2 class="text-sm font-semibold text-white">Доход по месяцам</h2>
        <NuxtLink to="/platform/revenue" class="flex items-center gap-1 text-xs text-violet-400 hover:text-violet-300">
          Подробнее <ArrowRight class="h-3.5 w-3.5" />
        </NuxtLink>
      </div>
      <div v-if="loading" class="flex gap-2 items-end h-24">
        <div v-for="i in 12" :key="i" class="flex-1 animate-pulse rounded-t bg-slate-700" :style="`height: ${20 + Math.random() * 60}%`" />
      </div>
      <div v-else-if="!data?.monthlyRevenue?.length" class="flex h-24 items-center justify-center text-sm text-slate-500">
        Нет данных
      </div>
      <div v-else class="flex items-end gap-1 h-24">
        <div
          v-for="row in [...(data?.monthlyRevenue ?? [])].reverse()"
          :key="row.month"
          class="group relative flex-1"
        >
          <div
            class="w-full rounded-t bg-violet-600/70 transition-all hover:bg-violet-500"
            :style="`height: ${maxMonthlyRevenue ? Math.max(4, (row.totalKzt / maxMonthlyRevenue) * 96) : 4}px`"
            :title="`${row.month}: ${fmtKzt(row.totalKzt)}`"
          />
          <p class="mt-1 truncate text-center text-[9px] text-slate-500">
            {{ row.month.slice(5) }}
          </p>
        </div>
      </div>
    </div>

    <!-- Quick links -->
    <div class="grid gap-3 sm:grid-cols-3">
      <NuxtLink
        v-for="link in [
          { to: '/platform/tenants', label: 'Управление клиентами', sub: 'Все тенанты, тарифы, статусы', icon: Building2 },
          { to: '/platform/users',   label: 'Все пользователи',     sub: 'Регистрации, активность',      icon: Users },
          { to: '/platform/revenue', label: 'Финансовая аналитика', sub: 'Доходы, НДС, комиссии',       icon: TrendingUp },
        ]"
        :key="link.to"
        :to="link.to"
        class="flex items-start gap-3 rounded-xl border border-slate-800 bg-slate-900 p-4 transition-colors hover:border-violet-700 hover:bg-slate-800"
      >
        <div class="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-violet-600/20 text-violet-400">
          <component :is="link.icon" class="h-4 w-4" />
        </div>
        <div class="min-w-0">
          <p class="text-sm font-medium text-white">{{ link.label }}</p>
          <p class="mt-0.5 text-xs text-slate-500">{{ link.sub }}</p>
        </div>
        <ArrowRight class="ml-auto mt-1 h-4 w-4 shrink-0 text-slate-600" />
      </NuxtLink>
    </div>
  </div>
</template>
