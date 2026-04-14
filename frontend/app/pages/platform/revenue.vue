<script setup lang="ts">
/**
 * Platform admin — financial analytics.
 * Full revenue breakdown: all-time totals, VAT, outstanding, top clients, top events.
 */
import {
  TrendingUp, DollarSign, RefreshCw, Building2, Calendar,
  AlertTriangle, CheckCircle,
} from 'lucide-vue-next'
import { ref, onMounted } from 'vue'
import type { PlatformRevenueResponse } from '#shared/api/endpoints/platform'

definePageMeta({ layout: 'platform', middleware: 'platform-auth' })
useHead({ title: 'Финансы — Webizon Platform' })

const api = useApi()
const toast = useToastStore()
const loading = ref(true)
const data = ref<PlatformRevenueResponse | null>(null)

async function refresh() {
  loading.value = true
  try {
    data.value = await api.platform.revenue()
  } catch (e: any) {
    toast.error(e?.message ?? 'Ошибка загрузки')
  } finally {
    loading.value = false
  }
}

onMounted(refresh)

function fmtKzt(v: number | null | undefined) {
  if (v == null || v === 0) return '—'
  return new Intl.NumberFormat('ru-KZ', {
    style: 'currency', currency: 'KZT', maximumFractionDigits: 0,
  }).format(v)
}

const maxMonthly = computed(() =>
  Math.max(...(data.value?.monthlyTrend ?? []).map(r => r.totalKzt), 1)
)

// VAT rate approximation from data
const vatPercent = computed(() => {
  const sub = data.value?.allTimeSubtotalKzt ?? 0
  const vat = data.value?.allTimeVatKzt ?? 0
  if (!sub) return 12
  return Math.round((vat / sub) * 100)
})
</script>

<template>
  <div class="space-y-6">
    <!-- Header -->
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-lg font-bold text-slate-900 dark:text-white">Финансовая аналитика</h1>
        <p class="text-xs text-slate-400">Доходы, НДС, комиссии — по всей платформе</p>
      </div>
      <button
        class="flex items-center gap-2 rounded-lg border border-slate-300 dark:border-slate-700 bg-slate-100 dark:bg-slate-800 px-3 py-2 text-sm text-slate-300 hover:bg-slate-700"
        :disabled="loading"
        @click="refresh"
      >
        <RefreshCw class="h-4 w-4" :class="loading && 'animate-spin'" />
      </button>
    </div>

    <!-- Top summary cards -->
    <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
      <div class="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-5 lg:col-span-2">
        <p class="text-xs text-slate-400">Общий оборот (все время)</p>
        <p class="mt-1 text-3xl font-black text-slate-900 dark:text-white">
          <span v-if="loading" class="inline-block h-8 w-40 animate-pulse rounded bg-slate-700" />
          <span v-else>{{ fmtKzt(data?.allTimeTotalKzt) }}</span>
        </p>
        <div class="mt-3 flex gap-4 text-sm">
          <div>
            <p class="text-xs text-slate-500">Субтотал (без НДС)</p>
            <p class="font-semibold text-slate-300">{{ fmtKzt(data?.allTimeSubtotalKzt) }}</p>
          </div>
          <div>
            <p class="text-xs text-slate-500">НДС ({{ vatPercent }}%)</p>
            <p class="font-semibold text-emerald-400">{{ fmtKzt(data?.allTimeVatKzt) }}</p>
          </div>
        </div>
      </div>

      <div class="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-5">
        <p class="text-xs text-slate-400">Доход этого месяца</p>
        <p class="mt-1 text-2xl font-bold text-slate-900 dark:text-white">
          <span v-if="loading" class="inline-block h-7 w-28 animate-pulse rounded bg-slate-700" />
          <span v-else>{{ fmtKzt(data?.thisMonthTotalKzt) }}</span>
        </p>
        <div class="mt-2 text-xs">
          <span class="text-slate-500">НДС: </span>
          <span class="text-emerald-400">{{ fmtKzt(data?.thisMonthVatKzt) }}</span>
        </div>
      </div>

      <div class="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-5">
        <div class="flex items-start justify-between">
          <div>
            <p class="text-xs text-slate-400">Дебиторская задолженность</p>
            <p class="mt-1 text-2xl font-bold" :class="(data?.outstandingKzt ?? 0) > 0 ? 'text-amber-400' : 'text-slate-500'">
              <span v-if="loading" class="inline-block h-7 w-24 animate-pulse rounded bg-slate-700" />
              <span v-else>{{ fmtKzt(data?.outstandingKzt) }}</span>
            </p>
            <p class="mt-1 text-xs text-slate-500">выставлено, не оплачено</p>
          </div>
          <component
            :is="(data?.outstandingKzt ?? 0) > 0 ? AlertTriangle : CheckCircle"
            :class="[(data?.outstandingKzt ?? 0) > 0 ? 'text-amber-400' : 'text-slate-600', 'h-5 w-5']"
          />
        </div>
      </div>
    </div>

    <!-- Monthly chart -->
    <div class="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-5">
      <h2 class="mb-4 text-sm font-semibold text-slate-900 dark:text-white">Динамика по месяцам</h2>
      <div v-if="loading" class="flex items-end gap-1 h-36">
        <div v-for="i in 12" :key="i" class="flex-1 animate-pulse rounded-t bg-slate-700" :style="`height: ${20 + i * 5}%`" />
      </div>
      <div v-else-if="!data?.monthlyTrend?.length" class="flex h-36 items-center justify-center text-slate-500 text-sm">
        Нет данных
      </div>
      <div v-else class="space-y-1">
        <div class="flex items-end gap-1 h-36">
          <div
            v-for="row in [...(data?.monthlyTrend ?? [])].reverse()"
            :key="row.month"
            class="group relative flex-1 flex flex-col items-stretch justify-end"
          >
            <!-- VAT part (stacked) -->
            <div
              class="w-full rounded-t-sm bg-emerald-700/60 transition-all"
              :style="`height: ${Math.max(1, (row.vatKzt / maxMonthly) * 120)}px`"
              :title="`НДС ${row.month}: ${fmtKzt(row.vatKzt)}`"
            />
            <!-- Subtotal part -->
            <div
              class="w-full bg-violet-600/80 transition-all"
              :style="`height: ${Math.max(1, (row.subtotalKzt / maxMonthly) * 120)}px`"
              :title="`Доход ${row.month}: ${fmtKzt(row.subtotalKzt)}`"
            />
          </div>
        </div>
        <!-- Labels -->
        <div class="flex items-start gap-1">
          <div
            v-for="row in [...(data?.monthlyTrend ?? [])].reverse()"
            :key="row.month"
            class="flex-1 text-center text-[9px] text-slate-500"
          >
            {{ row.month.slice(5) }}/{{ row.month.slice(2, 4) }}
          </div>
        </div>
        <!-- Legend -->
        <div class="flex gap-4 pt-1">
          <span class="flex items-center gap-1 text-xs text-slate-400">
            <span class="inline-block h-3 w-3 rounded-sm bg-violet-600/80" />Субтотал
          </span>
          <span class="flex items-center gap-1 text-xs text-slate-400">
            <span class="inline-block h-3 w-3 rounded-sm bg-emerald-700/60" />НДС
          </span>
        </div>
      </div>
    </div>

    <!-- Top tenants + Top events -->
    <div class="grid gap-5 lg:grid-cols-2">
      <!-- Top tenants -->
      <div class="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-5">
        <h2 class="mb-4 flex items-center gap-2 text-sm font-semibold text-slate-900 dark:text-white">
          <Building2 class="h-4 w-4 text-violet-400" />
          Топ клиентов по доходу
        </h2>
        <div v-if="loading" class="space-y-2">
          <div v-for="i in 5" :key="i" class="h-10 animate-pulse rounded-lg bg-slate-100 dark:bg-slate-800" />
        </div>
        <div v-else-if="!data?.topTenants?.length" class="text-center py-6 text-sm text-slate-500">
          Нет данных
        </div>
        <ol v-else class="space-y-2">
          <li
            v-for="(t, i) in data.topTenants"
            :key="t.tenantId"
            class="flex items-center gap-3 rounded-lg bg-slate-100 dark:bg-slate-800/40 px-3 py-2"
          >
            <span class="w-5 text-center text-xs font-bold text-slate-500">#{{ i + 1 }}</span>
            <div class="min-w-0 flex-1">
              <p class="truncate text-sm font-medium text-slate-900 dark:text-white">{{ t.displayName }}</p>
              <p class="text-xs text-slate-500">{{ t.invoiceCount }} счетов</p>
            </div>
            <div class="text-right">
              <p class="text-sm font-bold text-emerald-400">{{ fmtKzt(t.totalKzt) }}</p>
              <p class="text-xs text-slate-500">НДС: {{ fmtKzt(t.vatKzt) }}</p>
            </div>
          </li>
        </ol>
      </div>

      <!-- Top events -->
      <div class="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-5">
        <h2 class="mb-4 flex items-center gap-2 text-sm font-semibold text-slate-900 dark:text-white">
          <Calendar class="h-4 w-4 text-brand-400" />
          Топ мероприятий по доходу
        </h2>
        <div v-if="loading" class="space-y-2">
          <div v-for="i in 5" :key="i" class="h-10 animate-pulse rounded-lg bg-slate-100 dark:bg-slate-800" />
        </div>
        <div v-else-if="!data?.topEvents?.length" class="text-center py-6 text-sm text-slate-500">
          Нет данных
        </div>
        <ol v-else class="space-y-2">
          <li
            v-for="(ev, i) in data.topEvents"
            :key="ev.eventId"
            class="flex items-center gap-3 rounded-lg bg-slate-100 dark:bg-slate-800/40 px-3 py-2"
          >
            <span class="w-5 text-center text-xs font-bold text-slate-500">#{{ i + 1 }}</span>
            <div class="min-w-0 flex-1">
              <p class="truncate text-sm font-medium text-slate-900 dark:text-white">{{ ev.eventTitle || 'Без названия' }}</p>
              <p class="text-xs text-slate-500">{{ ev.tenantSlug }} · {{ ev.sessionCount }} сессий</p>
            </div>
            <p class="text-sm font-bold text-brand-400">{{ fmtKzt(ev.totalKzt) }}</p>
          </li>
        </ol>
      </div>
    </div>

    <!-- VAT explainer card -->
    <div class="rounded-xl border border-violet-800/40 bg-violet-900/10 p-5">
      <div class="flex items-start gap-3">
        <DollarSign class="h-5 w-5 shrink-0 text-violet-400 mt-0.5" />
        <div class="space-y-1 text-sm text-slate-300">
          <p class="font-semibold text-slate-900 dark:text-white">Структура платежей</p>
          <p>Субтотал — чистый доход платформы до налогов.</p>
          <p>НДС ({{ vatPercent }}%) — налог на добавленную стоимость, передаётся в бюджет.</p>
          <p>Итоговая сумма = Субтотал + НДС — именно столько платит клиент.</p>
        </div>
      </div>
    </div>
  </div>
</template>
