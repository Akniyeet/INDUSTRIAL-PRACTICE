<script setup lang="ts">
/**
 * Platform admin — single tenant detail.
 * Shows full stats, member list hint, revenue breakdown, and impersonation.
 */
import {
  ArrowLeft, LogIn, Building2, Users, Radio,
  Calendar, DollarSign, AlertTriangle,
} from 'lucide-vue-next'
import { ref, onMounted } from 'vue'
import type { PlatformTenantResponse } from '#shared/api/endpoints/platform'

definePageMeta({ layout: 'platform', middleware: 'platform-auth' })

const route = useRoute()
const api = useApi()
const auth = useAuthStore()
const toast = useToastStore()

const tenant = ref<PlatformTenantResponse | null>(null)
const loading = ref(true)
const impersonating = ref(false)

onMounted(async () => {
  try {
    tenant.value = await api.platform.getTenant(route.params.id as string)
    useHead({ title: `${tenant.value.displayName} — Webizon Platform` })
  } catch (e: any) {
    toast.error(e?.message ?? 'Ошибка загрузки')
  } finally {
    loading.value = false
  }
})

function fmtKzt(v: number | null | undefined) {
  if (!v) return '—'
  return new Intl.NumberFormat('ru-KZ', {
    style: 'currency', currency: 'KZT', maximumFractionDigits: 0,
  }).format(v)
}

function fmtDate(iso: string | null | undefined) {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('ru-KZ', { day: '2-digit', month: 'long', year: 'numeric' })
}

async function impersonate() {
  if (!tenant.value || impersonating.value) return
  impersonating.value = true
  try {
    const ctx = await api.platform.impersonate(tenant.value.id)
    if (auth.user) {
      auth.user.tenantId   = ctx.tenantId
      auth.user.tenantSlug = ctx.tenantSlug
    }
    toast.success(`Переключено на «${ctx.displayName}»`)
    await navigateTo('/admin')
  } catch (e: any) {
    toast.error(e?.message ?? 'Ошибка переключения')
    impersonating.value = false
  }
}
</script>

<template>
  <div class="space-y-6">
    <!-- Back -->
    <NuxtLink
      to="/platform/tenants"
      class="inline-flex items-center gap-1.5 text-sm text-slate-400 hover:text-slate-900 dark:text-white"
    >
      <ArrowLeft class="h-4 w-4" />
      Все клиенты
    </NuxtLink>

    <!-- Skeleton -->
    <div v-if="loading" class="space-y-4">
      <div class="h-24 animate-pulse rounded-xl bg-slate-100 dark:bg-slate-800" />
      <div class="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <div v-for="i in 4" :key="i" class="h-20 animate-pulse rounded-xl bg-slate-100 dark:bg-slate-800" />
      </div>
    </div>

    <template v-else-if="tenant">
      <!-- Header card -->
      <div class="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-6">
        <div class="flex items-start justify-between gap-4">
          <div class="flex items-center gap-4">
            <div class="flex h-14 w-14 items-center justify-center rounded-xl bg-violet-600/20 text-violet-400 text-2xl font-black">
              {{ tenant.displayName.charAt(0).toUpperCase() }}
            </div>
            <div>
              <h1 class="text-xl font-bold text-slate-900 dark:text-white">{{ tenant.displayName }}</h1>
              <p class="text-sm text-slate-400">slug: <span class="font-mono text-violet-400">{{ tenant.slug }}</span></p>
              <div class="mt-1 flex items-center gap-2">
                <span class="rounded-full bg-slate-100 dark:bg-slate-800 px-2 py-0.5 text-xs font-medium text-slate-300">{{ tenant.status }}</span>
                <span v-if="tenant.trialEndsAt" class="text-xs text-amber-400">
                  Пробный до {{ fmtDate(tenant.trialEndsAt) }}
                </span>
              </div>
            </div>
          </div>
          <button
            class="flex items-center gap-2 rounded-lg bg-violet-600 px-4 py-2 text-sm font-medium text-slate-900 dark:text-white hover:bg-violet-700 disabled:opacity-50"
            :disabled="impersonating"
            @click="impersonate"
          >
            <LogIn class="h-4 w-4" :class="impersonating && 'animate-pulse'" />
            {{ impersonating ? 'Переключение…' : 'Войти в workspace' }}
          </button>
        </div>

        <!-- Owner info -->
        <div class="mt-4 flex flex-wrap gap-4 border-t border-slate-200 dark:border-slate-800 pt-4">
          <div>
            <p class="text-xs text-slate-500">Владелец</p>
            <p class="text-sm font-medium text-slate-900 dark:text-white">{{ tenant.ownerFullName || '—' }}</p>
          </div>
          <div>
            <p class="text-xs text-slate-500">Email</p>
            <p class="text-sm text-violet-400">{{ tenant.ownerEmail || '—' }}</p>
          </div>
          <div>
            <p class="text-xs text-slate-500">Участников</p>
            <p class="text-sm font-medium text-slate-900 dark:text-white">{{ tenant.memberCount }}</p>
          </div>
          <div>
            <p class="text-xs text-slate-500">Зарегистрирован</p>
            <p class="text-sm text-slate-900 dark:text-white">{{ fmtDate(tenant.createdAt) }}</p>
          </div>
        </div>
      </div>

      <!-- Stats grid -->
      <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <div
          v-for="stat in [
            { label: 'Мероприятий', value: tenant.eventCount, icon: Calendar, color: 'text-brand-400' },
            { label: 'Сессий', value: tenant.sessionCount, icon: Radio, color: 'text-slate-300' },
            { label: 'Участников', value: tenant.memberCount, icon: Users, color: 'text-slate-300' },
            { label: 'Сейчас в эфире', value: tenant.currentlyLive, icon: Radio, color: tenant.currentlyLive > 0 ? 'text-red-400' : 'text-slate-500' },
          ]"
          :key="stat.label"
          class="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-4"
        >
          <div class="flex items-center gap-3">
            <component :is="stat.icon" :class="['h-5 w-5', stat.color]" />
            <div>
              <p class="text-xl font-bold text-slate-900 dark:text-white">{{ stat.value }}</p>
              <p class="text-xs text-slate-500">{{ stat.label }}</p>
            </div>
          </div>
        </div>
      </div>

      <!-- Revenue -->
      <div class="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-5">
        <h2 class="mb-4 flex items-center gap-2 text-sm font-semibold text-slate-900 dark:text-white">
          <DollarSign class="h-4 w-4 text-emerald-400" />
          Финансы
        </h2>
        <div class="grid gap-4 sm:grid-cols-3">
          <div class="rounded-lg bg-slate-100 dark:bg-slate-800/50 p-4">
            <p class="text-xs text-slate-400">Оплачено (всего)</p>
            <p class="mt-1 text-xl font-bold text-emerald-400">{{ fmtKzt(tenant.totalPaidKzt) }}</p>
          </div>
          <div class="rounded-lg bg-slate-100 dark:bg-slate-800/50 p-4">
            <p class="text-xs text-slate-400">Ожидает оплаты</p>
            <p class="mt-1 text-xl font-bold" :class="tenant.totalOutstandingKzt > 0 ? 'text-amber-400' : 'text-slate-500'">
              {{ fmtKzt(tenant.totalOutstandingKzt) }}
            </p>
          </div>
          <div class="rounded-lg bg-slate-100 dark:bg-slate-800/50 p-4">
            <p class="text-xs text-slate-400">Последний платёж</p>
            <p class="mt-1 text-sm font-medium text-slate-900 dark:text-white">{{ fmtDate(tenant.lastPaymentAt) }}</p>
          </div>
        </div>

        <div v-if="tenant.totalOutstandingKzt > 0" class="mt-3 flex items-center gap-2 rounded-lg border border-amber-800 bg-amber-900/20 p-3">
          <AlertTriangle class="h-4 w-4 text-amber-400 shrink-0" />
          <p class="text-xs text-amber-300">Есть просроченные или ожидающие счета</p>
        </div>
      </div>
    </template>

    <div v-else class="text-center py-16 text-slate-500">Клиент не найден</div>
  </div>
</template>
