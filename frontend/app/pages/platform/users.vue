<script setup lang="ts">
/**
 * Platform admin — all users list.
 * Shows every registered user across all tenants.
 */
import {
  Users, RefreshCw, Search, ShieldCheck, CheckCircle, XCircle,
} from 'lucide-vue-next'
import { ref, onMounted } from 'vue'
import type { PlatformUserResponse } from '#shared/api/endpoints/platform'

definePageMeta({ layout: 'platform', middleware: 'platform-auth' })
useHead({ title: 'Пользователи — Webizon Platform' })

const api = useApi()
const toast = useToastStore()

const users = ref<PlatformUserResponse[]>([])
const total = ref(0)
const loading = ref(true)
const searchQ = ref('')
let searchTimer: ReturnType<typeof setTimeout> | null = null

async function load(search?: string) {
  loading.value = true
  try {
    const res = await api.platform.listUsers({ search, size: 100 })
    users.value = res.content
    total.value = res.totalElements
  } catch (e: any) {
    toast.error(e?.message ?? 'Ошибка загрузки')
  } finally {
    loading.value = false
  }
}

onMounted(() => load())

watch(searchQ, (q) => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => load(q || undefined), 400)
})

function fmtDate(iso: string | null | undefined) {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('ru-KZ', { day: '2-digit', month: 'short', year: 'numeric' })
}

function fmtRelative(iso: string | null | undefined) {
  if (!iso) return '—'
  const ms = Date.now() - new Date(iso).getTime()
  const days = Math.floor(ms / 86400000)
  if (days === 0) return 'сегодня'
  if (days === 1) return 'вчера'
  if (days < 7) return `${days}д назад`
  if (days < 30) return `${Math.floor(days / 7)}нед назад`
  return `${Math.floor(days / 30)}мес назад`
}
</script>

<template>
  <div class="space-y-5">
    <!-- Header -->
    <div class="flex items-center justify-between gap-4">
      <div>
        <h1 class="text-lg font-bold text-slate-900 dark:text-white">Пользователи ({{ total.toLocaleString() }})</h1>
        <p class="text-xs text-slate-400">Все зарегистрированные аккаунты на платформе</p>
      </div>
      <button
        class="flex items-center gap-2 rounded-lg border border-slate-300 dark:border-slate-700 bg-slate-100 dark:bg-slate-800 px-3 py-2 text-sm text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-700"
        :disabled="loading"
        @click="load(searchQ || undefined)"
      >
        <RefreshCw class="h-4 w-4" :class="loading && 'animate-spin'" />
      </button>
    </div>

    <!-- Search -->
    <div class="relative">
      <Search class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" />
      <input
        v-model="searchQ"
        class="w-full rounded-lg border border-slate-300 dark:border-slate-700 bg-slate-100 dark:bg-slate-800 pl-9 pr-4 py-2 text-sm text-slate-900 dark:text-white placeholder:text-slate-500 focus:border-violet-500 focus:outline-none"
        placeholder="Поиск по имени или email…"
      />
    </div>

    <!-- Loading -->
    <div v-if="loading" class="space-y-2">
      <div v-for="i in 8" :key="i" class="h-14 animate-pulse rounded-xl bg-slate-100 dark:bg-slate-800" />
    </div>

    <!-- Table -->
    <div v-else-if="users.length" class="overflow-hidden rounded-xl border border-slate-200 dark:border-slate-800">
      <table class="w-full text-sm">
        <thead>
          <tr class="border-b border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900">
            <th class="px-4 py-3 text-left text-xs font-medium text-slate-400">Пользователь</th>
            <th class="px-4 py-3 text-left text-xs font-medium text-slate-400">Email</th>
            <th class="px-4 py-3 text-center text-xs font-medium text-slate-400">Верифицирован</th>
            <th class="px-4 py-3 text-right text-xs font-medium text-slate-400">Клиентов</th>
            <th class="px-4 py-3 text-right text-xs font-medium text-slate-400">Последний вход</th>
            <th class="px-4 py-3 text-right text-xs font-medium text-slate-400">Зарег.</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="u in users"
            :key="u.id"
            class="border-b border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 transition-colors hover:bg-slate-100 dark:bg-slate-800/60"
          >
            <!-- Name -->
            <td class="px-4 py-3">
              <div class="flex items-center gap-3">
                <div
                  class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-xs font-bold"
                  :class="u.platformAdmin ? 'bg-violet-600 text-white' : 'bg-slate-200 dark:bg-slate-700 text-slate-600 dark:text-slate-300'"
                >
                  {{ (u.fullName || u.email).charAt(0).toUpperCase() }}
                </div>
                <div>
                  <div class="flex items-center gap-1.5">
                    <span class="font-medium text-slate-900 dark:text-white">{{ u.fullName || '—' }}</span>
                    <ShieldCheck v-if="u.platformAdmin" class="h-3.5 w-3.5 text-violet-400" title="Platform Admin" />
                  </div>
                  <p v-if="u.tenantSlugs.length" class="text-xs text-slate-500">
                    {{ u.tenantSlugs.join(', ') }}
                  </p>
                </div>
              </div>
            </td>

            <!-- Email -->
            <td class="px-4 py-3 text-slate-600 dark:text-slate-300">{{ u.email }}</td>

            <!-- Verified -->
            <td class="px-4 py-3 text-center">
              <component
                :is="u.emailVerified ? CheckCircle : XCircle"
                :class="u.emailVerified ? 'text-emerald-400' : 'text-slate-600'"
                class="h-4 w-4 mx-auto"
              />
            </td>

            <!-- Tenants -->
            <td class="px-4 py-3 text-right font-medium text-slate-900 dark:text-white">{{ u.tenantCount }}</td>

            <!-- Last login -->
            <td class="px-4 py-3 text-right text-xs text-slate-400">{{ fmtRelative(u.lastLoginAt) }}</td>

            <!-- Created -->
            <td class="px-4 py-3 text-right text-xs text-slate-500">{{ fmtDate(u.createdAt) }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Empty -->
    <div v-else class="flex flex-col items-center gap-3 py-16">
      <Users class="h-12 w-12 text-slate-700" />
      <p class="text-slate-400">Пользователи не найдены</p>
    </div>
  </div>
</template>
