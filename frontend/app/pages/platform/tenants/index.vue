<script setup lang="ts">
/**
 * Platform admin — all tenants list.
 * Shows every workspace: owner, status, event/session counts, revenue.
 * Super admin can impersonate any tenant to edit their workspace directly.
 */
import {
  Building2, RefreshCw, Search, ArrowRight, LogIn,
  CheckCircle, AlertTriangle, Clock, XCircle,
} from 'lucide-vue-next'
import { ref, onMounted, computed } from 'vue'
import type { PlatformTenantResponse } from '#shared/api/endpoints/platform'

definePageMeta({ layout: 'platform', middleware: 'platform-auth' })
useHead({ title: 'Клиенты — Webizon Platform' })

const api = useApi()
const auth = useAuthStore()
const toast = useToastStore()

const tenants = ref<PlatformTenantResponse[]>([])
const total = ref(0)
const loading = ref(true)
const searchQ = ref('')
const impersonating = ref<string | null>(null)

async function refresh() {
  loading.value = true
  try {
    const res = await api.platform.listTenants({ size: 100 })
    tenants.value = res.content
    total.value = res.totalElements
  } catch (e: any) {
    toast.error(e?.message ?? 'Ошибка загрузки клиентов')
  } finally {
    loading.value = false
  }
}

onMounted(refresh)

const filtered = computed(() => {
  const q = searchQ.value.toLowerCase()
  if (!q) return tenants.value
  return tenants.value.filter(t =>
    t.displayName.toLowerCase().includes(q) ||
    t.slug.toLowerCase().includes(q) ||
    (t.ownerEmail ?? '').toLowerCase().includes(q)
  )
})

function statusIcon(status: string) {
  switch (status) {
    case 'ACTIVE':    return { icon: CheckCircle, cls: 'text-emerald-400' }
    case 'TRIAL':     return { icon: Clock,        cls: 'text-amber-400' }
    case 'PAST_DUE':  return { icon: AlertTriangle, cls: 'text-orange-400' }
    case 'SUSPENDED': return { icon: XCircle,       cls: 'text-red-400' }
    default:          return { icon: XCircle,       cls: 'text-slate-500' }
  }
}

function fmtKzt(v: number) {
  if (!v) return '—'
  return new Intl.NumberFormat('ru-KZ', {
    style: 'currency', currency: 'KZT', maximumFractionDigits: 0,
  }).format(v)
}

function fmtDate(iso: string | null) {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('ru-KZ', { day: '2-digit', month: 'short', year: 'numeric' })
}

async function impersonate(tenant: PlatformTenantResponse) {
  if (impersonating.value) return
  impersonating.value = tenant.id
  try {
    const ctx = await api.platform.impersonate(tenant.id)
    // Patch the auth store to use the impersonated tenant
    if (auth.user) {
      auth.user.tenantId   = ctx.tenantId
      auth.user.tenantSlug = ctx.tenantSlug
    }
    toast.success(`Переключено на «${ctx.displayName}»`)
    await navigateTo('/admin')
  } catch (e: any) {
    toast.error(e?.message ?? 'Ошибка переключения')
  } finally {
    impersonating.value = null
  }
}
</script>

<template>
  <div class="space-y-5">
    <!-- Header row -->
    <div class="flex items-center justify-between gap-4">
      <div>
        <h1 class="text-lg font-bold text-white">Клиенты ({{ total }})</h1>
        <p class="text-xs text-slate-400">Все рабочие пространства на платформе</p>
      </div>
      <button
        class="flex items-center gap-2 rounded-lg border border-slate-700 bg-slate-800 px-3 py-2 text-sm text-slate-300 hover:bg-slate-700"
        :disabled="loading"
        @click="refresh"
      >
        <RefreshCw class="h-4 w-4" :class="loading && 'animate-spin'" />
      </button>
    </div>

    <!-- Search -->
    <div class="relative">
      <Search class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" />
      <input
        v-model="searchQ"
        class="w-full rounded-lg border border-slate-700 bg-slate-800 pl-9 pr-4 py-2 text-sm text-white placeholder:text-slate-500 focus:border-violet-500 focus:outline-none"
        placeholder="Поиск по названию, slug или email владельца…"
      />
    </div>

    <!-- Loading skeletons -->
    <div v-if="loading" class="space-y-2">
      <div v-for="i in 6" :key="i" class="h-[68px] animate-pulse rounded-xl bg-slate-800" />
    </div>

    <!-- Table -->
    <div v-else-if="filtered.length" class="overflow-hidden rounded-xl border border-slate-800">
      <table class="w-full text-sm">
        <thead>
          <tr class="border-b border-slate-800 bg-slate-900">
            <th class="px-4 py-3 text-left text-xs font-medium text-slate-400">Клиент</th>
            <th class="px-4 py-3 text-left text-xs font-medium text-slate-400">Владелец</th>
            <th class="px-4 py-3 text-center text-xs font-medium text-slate-400">Статус</th>
            <th class="px-4 py-3 text-right text-xs font-medium text-slate-400">Мероприятий</th>
            <th class="px-4 py-3 text-right text-xs font-medium text-slate-400">Доход</th>
            <th class="px-4 py-3 text-right text-xs font-medium text-slate-400">Зарег.</th>
            <th class="w-20 px-4 py-3" />
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="t in filtered"
            :key="t.id"
            class="border-b border-slate-800 bg-slate-900 transition-colors hover:bg-slate-800/60"
          >
            <!-- Name + slug -->
            <td class="px-4 py-3">
              <div class="flex items-center gap-3">
                <div class="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-violet-600/20 text-violet-400 text-xs font-bold">
                  {{ t.displayName.charAt(0).toUpperCase() }}
                </div>
                <div>
                  <p class="font-medium text-white">{{ t.displayName }}</p>
                  <p class="text-xs text-slate-500">{{ t.slug }}</p>
                </div>
              </div>
            </td>

            <!-- Owner -->
            <td class="px-4 py-3">
              <p class="text-white">{{ t.ownerFullName || '—' }}</p>
              <p class="text-xs text-slate-500">{{ t.ownerEmail || '—' }}</p>
            </td>

            <!-- Status -->
            <td class="px-4 py-3 text-center">
              <span class="inline-flex items-center gap-1">
                <component :is="statusIcon(t.status).icon" :class="['h-3.5 w-3.5', statusIcon(t.status).cls]" />
                <span class="text-xs" :class="statusIcon(t.status).cls">{{ t.status }}</span>
              </span>
            </td>

            <!-- Events -->
            <td class="px-4 py-3 text-right">
              <span class="font-medium text-white">{{ t.eventCount }}</span>
              <span v-if="t.currentlyLive > 0" class="ml-1 inline-flex items-center gap-0.5 rounded-full bg-red-500/20 px-1.5 py-0.5 text-[10px] font-semibold text-red-400">
                <span class="h-1 w-1 rounded-full bg-red-500 animate-pulse" />
                {{ t.currentlyLive }} live
              </span>
            </td>

            <!-- Revenue -->
            <td class="px-4 py-3 text-right">
              <p class="font-medium text-emerald-400">{{ fmtKzt(t.totalPaidKzt) }}</p>
              <p v-if="t.totalOutstandingKzt > 0" class="text-xs text-amber-400">+{{ fmtKzt(t.totalOutstandingKzt) }} в ожид.</p>
            </td>

            <!-- Created -->
            <td class="px-4 py-3 text-right text-xs text-slate-500">
              {{ fmtDate(t.createdAt) }}
            </td>

            <!-- Actions -->
            <td class="px-4 py-3">
              <div class="flex items-center justify-end gap-1">
                <NuxtLink
                  :to="`/platform/tenants/${t.id}`"
                  class="flex h-7 w-7 items-center justify-center rounded-lg border border-slate-700 text-slate-400 hover:border-violet-600 hover:text-violet-400"
                  title="Детали"
                >
                  <ArrowRight class="h-3.5 w-3.5" />
                </NuxtLink>
                <button
                  class="flex h-7 w-7 items-center justify-center rounded-lg border border-slate-700 text-slate-400 hover:border-violet-600 hover:text-violet-400"
                  title="Войти в workspace"
                  :disabled="impersonating === t.id"
                  @click="impersonate(t)"
                >
                  <LogIn class="h-3.5 w-3.5" :class="impersonating === t.id && 'animate-pulse'" />
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Empty -->
    <div v-else class="flex flex-col items-center gap-3 py-16">
      <Building2 class="h-12 w-12 text-slate-700" />
      <p class="text-slate-400">Клиентов не найдено</p>
    </div>
  </div>
</template>
