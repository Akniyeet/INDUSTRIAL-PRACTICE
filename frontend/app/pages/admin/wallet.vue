<script setup lang="ts">
/**
 * Admin → Кошелёк (Wallet).
 *
 * Balance display, top-up, transaction history, and spending analytics.
 */
import {
  Wallet2,
  Plus,
  ArrowUpRight,
  ArrowDownLeft,
  Radio,
  RefreshCw,
  CreditCard,
  Eye,
} from 'lucide-vue-next'
import { ref, computed } from 'vue'
import { format } from 'date-fns'

definePageMeta({
  layout: 'admin',
  middleware: 'auth',
})

useHead({ title: 'Кошелёк — Webizon' })

// ---------------------------------------------------------------------------
// Mock data (will be replaced with real API calls)
// ---------------------------------------------------------------------------
const balance = ref(12_450)
const currency = '₸'
const loading = ref(false)

const showTopUpModal = ref(false)
const topUpAmount = ref<number | null>(null)
const topUpAmounts = [5000, 10000, 25000, 50000, 100000]

interface Transaction {
  id: string
  type: 'topup' | 'charge'
  amount: number
  description: string
  date: string
  eventTitle?: string
  viewers?: number
}

const transactions = ref<Transaction[]>([
  { id: '1', type: 'charge', amount: -3600, description: 'Live-эфир: 240 зрителей × 15 ₸', date: '2026-04-12T10:00:00Z', eventTitle: 'Java Backend — открытый урок', viewers: 240 },
  { id: '2', type: 'charge', amount: -1200, description: 'Авто-повтор: 120 зрителей × 10 ₸', date: '2026-04-11T18:00:00Z', eventTitle: 'Java Backend — открытый урок', viewers: 120 },
  { id: '3', type: 'topup', amount: 25000, description: 'Пополнение (Kaspi)', date: '2026-04-10T09:30:00Z' },
  { id: '4', type: 'charge', amount: -2100, description: 'Live-эфир: 140 зрителей × 15 ₸', date: '2026-04-09T14:00:00Z', eventTitle: 'English for IT', viewers: 140 },
  { id: '5', type: 'topup', amount: 10000, description: 'Пополнение (Visa)', date: '2026-04-05T12:00:00Z' },
  { id: '6', type: 'charge', amount: -750, description: 'Авто-повтор: 75 зрителей × 10 ₸', date: '2026-04-04T20:00:00Z', eventTitle: 'English for IT', viewers: 75 },
])

// Analytics data
const monthlySpending = ref([
  { month: 'Янв', amount: 8200 },
  { month: 'Фев', amount: 12400 },
  { month: 'Мар', amount: 15800 },
  { month: 'Апр', amount: 7650 },
])

const maxMonthly = computed(() => Math.max(...monthlySpending.value.map(m => m.amount)))

const totalSpent = computed(() =>
  transactions.value.filter(t => t.type === 'charge').reduce((sum, t) => sum + Math.abs(t.amount), 0)
)

const totalTopUp = computed(() =>
  transactions.value.filter(t => t.type === 'topup').reduce((sum, t) => sum + t.amount, 0)
)

const totalViewers = computed(() =>
  transactions.value.filter(t => t.viewers).reduce((sum, t) => sum + (t.viewers || 0), 0)
)

// Formatters
function formatMoney(amount: number) {
  return new Intl.NumberFormat('ru-RU').format(amount) + ' ' + currency
}

function formatDate(iso: string) {
  try { return format(new Date(iso), 'dd.MM.yyyy HH:mm') } catch { return iso }
}

// Top-up handler
async function handleTopUp() {
  if (!topUpAmount.value || topUpAmount.value <= 0) return
  loading.value = true
  // TODO: Call real payment API
  await new Promise(r => setTimeout(r, 1500))
  balance.value += topUpAmount.value
  transactions.value.unshift({
    id: Date.now().toString(),
    type: 'topup',
    amount: topUpAmount.value,
    description: 'Пополнение',
    date: new Date().toISOString(),
  })
  loading.value = false
  showTopUpModal.value = false
  topUpAmount.value = null
}
</script>

<template>
  <div class="space-y-6">
    <!-- Header -->
    <PageHeader
      title="Кошелёк"
      subtitle="Баланс, пополнение и история расходов."
      :breadcrumbs="[
        { label: 'Главная', to: '/admin' },
        { label: 'Кошелёк' },
      ]"
    />

    <!-- Balance + Stats strip -->
    <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <!-- Main balance card -->
      <div class="relative col-span-2 overflow-hidden rounded-2xl bg-gradient-to-br from-brand-600 to-brand-800 p-6 text-white shadow-lg shadow-brand-600/20 sm:col-span-1 lg:col-span-2">
        <div class="pointer-events-none absolute -right-10 -top-10 h-40 w-40 rounded-full bg-white/10 blur-2xl" />
        <div class="pointer-events-none absolute -bottom-5 -left-5 h-28 w-28 rounded-full bg-white/5 blur-xl" />
        <div class="relative">
          <div class="flex items-center gap-2 text-sm text-white/70">
            <Wallet2 class="h-4 w-4" />
            Текущий баланс
          </div>
          <p class="mt-2 text-4xl font-black tracking-tight">{{ formatMoney(balance) }}</p>
          <div class="mt-4 flex gap-2">
            <button
              class="inline-flex items-center gap-2 rounded-xl bg-white px-5 py-2.5 text-sm font-semibold text-brand-700 shadow-md transition-all hover:shadow-lg hover:-translate-y-0.5"
              @click="showTopUpModal = true"
            >
              <Plus class="h-4 w-4" />
              Пополнить
            </button>
          </div>
        </div>
      </div>

      <!-- Stats cards -->
      <UiCard>
        <div class="flex items-center gap-3">
          <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-red-50 text-red-500">
            <ArrowUpRight class="h-5 w-5" />
          </div>
          <div>
            <p class="text-2xl font-bold text-slate-900">{{ formatMoney(totalSpent) }}</p>
            <p class="text-xs text-slate-500">Потрачено всего</p>
          </div>
        </div>
      </UiCard>

      <UiCard>
        <div class="flex items-center gap-3">
          <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-brand-50 text-brand-600">
            <Eye class="h-5 w-5" />
          </div>
          <div>
            <p class="text-2xl font-bold text-slate-900">{{ totalViewers }}</p>
            <p class="text-xs text-slate-500">Зрителей обслужено</p>
          </div>
        </div>
      </UiCard>
    </div>

    <!-- Monthly spending chart + Usage breakdown -->
    <div class="grid gap-4 lg:grid-cols-3">
      <!-- Spending chart -->
      <UiCard class="lg:col-span-2" title="Расходы по месяцам">
        <div class="flex items-end gap-3" style="height: 160px">
          <div
            v-for="m in monthlySpending"
            :key="m.month"
            class="group flex flex-1 flex-col items-center gap-2"
          >
            <span class="text-xs font-semibold text-slate-900 opacity-0 transition-opacity group-hover:opacity-100">
              {{ formatMoney(m.amount) }}
            </span>
            <div
              class="w-full rounded-t-lg bg-gradient-to-t from-brand-600 to-brand-400 transition-all duration-500 group-hover:from-brand-500 group-hover:to-brand-300"
              :style="{ height: `${(m.amount / maxMonthly) * 120}px`, minHeight: '8px' }"
            />
            <span class="text-xs text-slate-500">{{ m.month }}</span>
          </div>
        </div>
      </UiCard>

      <!-- Usage breakdown -->
      <UiCard title="Структура расходов">
        <div class="space-y-4">
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-2">
              <Radio class="h-4 w-4 text-red-500" />
              <span class="text-sm text-slate-700">Live-эфиры</span>
            </div>
            <span class="text-sm font-semibold text-slate-900">5 700 ₸</span>
          </div>
          <div class="h-2 overflow-hidden rounded-full bg-slate-100">
            <div class="h-full w-[74%] rounded-full bg-red-500" />
          </div>

          <div class="flex items-center justify-between">
            <div class="flex items-center gap-2">
              <RefreshCw class="h-4 w-4 text-violet-500" />
              <span class="text-sm text-slate-700">Авто-повторы</span>
            </div>
            <span class="text-sm font-semibold text-slate-900">1 950 ₸</span>
          </div>
          <div class="h-2 overflow-hidden rounded-full bg-slate-100">
            <div class="h-full w-[26%] rounded-full bg-violet-500" />
          </div>
        </div>

        <div class="mt-6 rounded-xl bg-slate-50 p-3 text-center">
          <p class="text-xs text-slate-500">Средняя цена за зрителя</p>
          <p class="text-lg font-bold text-slate-900">13.2 ₸</p>
        </div>
      </UiCard>
    </div>

    <!-- Transaction history -->
    <UiCard title="История операций" :padded="false">
      <div v-if="transactions.length === 0" class="p-8 text-center">
        <UiEmpty icon="wallet" message="Операций пока нет" />
      </div>
      <ul v-else class="divide-y divide-slate-100">
        <li
          v-for="tx in transactions"
          :key="tx.id"
          class="flex items-center justify-between px-5 py-3.5 transition-colors hover:bg-slate-50"
        >
          <div class="flex items-center gap-3">
            <div
              class="flex h-9 w-9 items-center justify-center rounded-lg"
              :class="tx.type === 'topup' ? 'bg-emerald-50 text-emerald-600' : 'bg-slate-100 text-slate-500'"
            >
              <ArrowDownLeft v-if="tx.type === 'topup'" class="h-4 w-4" />
              <ArrowUpRight v-else class="h-4 w-4" />
            </div>
            <div>
              <p class="text-sm font-medium text-slate-900">{{ tx.description }}</p>
              <p class="text-xs text-slate-400">{{ formatDate(tx.date) }}</p>
            </div>
          </div>
          <span
            class="text-sm font-semibold"
            :class="tx.type === 'topup' ? 'text-emerald-600' : 'text-slate-900'"
          >
            {{ tx.type === 'topup' ? '+' : '' }}{{ formatMoney(tx.amount) }}
          </span>
        </li>
      </ul>
    </UiCard>

    <!-- Top-up modal -->
    <UiModal :open="showTopUpModal" title="Пополнить баланс" @close="showTopUpModal = false">
      <div class="space-y-5">
        <p class="text-sm text-slate-500">
          Выберите сумму или введите свою. Средства поступят на баланс мгновенно.
        </p>

        <!-- Quick amounts -->
        <div class="grid grid-cols-3 gap-2">
          <button
            v-for="amt in topUpAmounts"
            :key="amt"
            class="rounded-xl border py-3 text-sm font-semibold transition-all"
            :class="topUpAmount === amt
              ? 'border-brand-500 bg-brand-50 text-brand-700 shadow-sm'
              : 'border-slate-200 text-slate-700 hover:border-brand-300 hover:bg-brand-50/50'"
            @click="topUpAmount = amt"
          >
            {{ formatMoney(amt) }}
          </button>
        </div>

        <!-- Custom amount -->
        <UiInput
          :model-value="topUpAmount?.toString() ?? ''"
          label="Или введите сумму"
          placeholder="10000"
          type="number"
          @update:model-value="topUpAmount = Number($event) || null"
        />

        <!-- Payment methods -->
        <div>
          <p class="mb-2 text-xs font-medium text-slate-500">Способ оплаты</p>
          <div class="flex gap-2">
            <div class="flex h-9 w-14 items-center justify-center rounded-lg border border-slate-200 bg-slate-50 text-[10px] font-bold text-blue-600">VISA</div>
            <div class="flex h-9 w-14 items-center justify-center rounded-lg border border-slate-200 bg-slate-50 text-[10px] font-bold text-orange-500">MC</div>
            <div class="flex h-9 w-14 items-center justify-center rounded-lg border border-slate-200 bg-slate-50 text-[10px] font-bold text-green-600">Kaspi</div>
          </div>
        </div>

        <div class="flex gap-3">
          <UiButton variant="outline" class="flex-1" @click="showTopUpModal = false">
            Отмена
          </UiButton>
          <UiButton
            variant="primary"
            class="flex-1"
            :loading="loading"
            :disabled="!topUpAmount || topUpAmount <= 0"
            @click="handleTopUp"
          >
            <CreditCard class="h-4 w-4" />
            Оплатить {{ topUpAmount ? formatMoney(topUpAmount) : '' }}
          </UiButton>
        </div>
      </div>
    </UiModal>
  </div>
</template>
