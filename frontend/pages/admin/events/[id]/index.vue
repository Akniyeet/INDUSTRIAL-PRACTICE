<script setup lang="ts">
/**
 * Admin → Event detail.
 *
 * <p>The hub every other admin flow lands on. Four tabs today:
 * <ol>
 *   <li>Шолу — summary card with all event metadata + danger zone</li>
 *   <li>Сессиялар — placeholder until F3 lands the session manager</li>
 *   <li>CTA — placeholder until the CTA editor ships</li>
 *   <li>Чат — placeholder until chat settings editor ships</li>
 * </ol>
 *
 * <p>The overview tab also owns the publish/unpublish/delete actions. Those
 * are the only destructive operations on an event and we gate them behind a
 * confirm modal — especially important once a PUBLISHED event has been seen
 * by real users. The logic mirrors the backend rules in
 * {@code EventService.publish} / {@code unpublish}.
 */
import {
  Edit3,
  ExternalLink,
  Globe,
  Eye,
  EyeOff,
  Trash2,
  AlertTriangle,
  RefreshCw,
  Calendar,
  Radio,
  Megaphone,
  MessageSquare,
} from 'lucide-vue-next'
import { ref, computed, watch } from 'vue'
import { format } from 'date-fns'
import type { EventResponse } from '~/shared/api/types'

definePageMeta({
  layout: 'admin',
  middleware: 'auth',
})

const route = useRoute()
const router = useRouter()
const api = useApi()
const toast = useToastStore()

const eventId = computed(() => route.params.id as string)

// ---------------------------------------------------------------------------
// Data
// ---------------------------------------------------------------------------

const { data: event, pending, error, refresh } = useAsyncData<EventResponse>(
  () => `event-${eventId.value}`,
  () => api.events.getById(eventId.value),
  { watch: [eventId] },
)

useHead({
  title: () => (event.value ? `${event.value.title} — Webizon` : 'Ивент — Webizon'),
})

// ---------------------------------------------------------------------------
// Tabs
// ---------------------------------------------------------------------------

type TabKey = 'overview' | 'sessions' | 'cta' | 'chat'
const tabs: { key: TabKey; label: string; icon: typeof Calendar }[] = [
  { key: 'overview', label: 'Шолу',           icon: Calendar },
  { key: 'sessions', label: 'Сессиялар',      icon: Radio },
  { key: 'cta',      label: 'CTA',            icon: Megaphone },
  { key: 'chat',     label: 'Чат баптаулары', icon: MessageSquare },
]

const activeTab = ref<TabKey>((route.query.tab as TabKey) || 'overview')

watch(activeTab, (next) => {
  router.replace({ query: { ...route.query, tab: next } })
})

// ---------------------------------------------------------------------------
// Actions
// ---------------------------------------------------------------------------

const publishModalOpen = ref(false)
const unpublishModalOpen = ref(false)
const deleteModalOpen = ref(false)
const actionLoading = ref(false)

async function doPublish() {
  if (!event.value) return
  actionLoading.value = true
  try {
    const updated = await api.events.publish(event.value.id)
    event.value = updated
    toast.success('Ивент жарияланды')
    publishModalOpen.value = false
  } finally {
    actionLoading.value = false
  }
}

async function doUnpublish() {
  if (!event.value) return
  actionLoading.value = true
  try {
    const updated = await api.events.unpublish(event.value.id)
    event.value = updated
    toast.success('Жариялау тоқтатылды')
    unpublishModalOpen.value = false
  } finally {
    actionLoading.value = false
  }
}

async function doDelete() {
  if (!event.value) return
  actionLoading.value = true
  try {
    await api.events.remove(event.value.id)
    toast.success('Ивент жойылды')
    router.push('/admin/events')
  } finally {
    actionLoading.value = false
  }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function formatDate(iso: string | null) {
  if (!iso) return '—'
  try {
    return format(new Date(iso), 'dd.MM.yyyy HH:mm')
  } catch {
    return iso
  }
}
</script>

<template>
  <div>
    <!-- Loading -->
    <div v-if="pending && !event" class="space-y-4">
      <UiSkeleton h="h-6" w="w-1/3" rounded="rounded-md" />
      <UiSkeleton h="h-[200px]" rounded="rounded-xl" />
      <UiSkeleton h="h-[140px]" rounded="rounded-xl" />
    </div>

    <!-- Error -->
    <UiCard v-else-if="error || !event" class="border-danger-200 bg-danger-50/60">
      <div class="flex items-start gap-3">
        <AlertTriangle class="mt-0.5 h-5 w-5 text-danger-500" />
        <div class="flex-1">
          <h3 class="text-sm font-semibold text-danger-800">Ивент табылмады</h3>
          <p class="mt-1 text-sm text-danger-700">
            Бұл ивент жойылған немесе сізде оған қолжетім жоқ.
          </p>
        </div>
      </div>
      <template #footer>
        <UiButton variant="outline" size="sm" to="/admin/events">Тізімге қайту</UiButton>
        <UiButton variant="primary" size="sm" @click="refresh()">Қайта көру</UiButton>
      </template>
    </UiCard>

    <!-- Content -->
    <template v-else>
      <PageHeader
        :title="event.title"
        :subtitle="event.description || '—'"
        :breadcrumbs="[
          { label: 'Басты бет', to: '/admin' },
          { label: 'Ивенттер', to: '/admin/events' },
          { label: event.title },
        ]"
      >
        <template #actions>
          <UiButton
            variant="ghost"
            size="md"
            :disabled="pending"
            @click="refresh()"
          >
            <RefreshCw class="h-4 w-4" :class="pending && 'animate-spin'" />
            Жаңарту
          </UiButton>
          <UiButton variant="outline" size="md" :to="`/admin/events/${event.id}/edit`">
            <Edit3 class="h-4 w-4" />
            Өңдеу
          </UiButton>
          <UiButton
            v-if="event.status === 'DRAFT' || event.status === 'ARCHIVED'"
            variant="primary"
            size="md"
            @click="publishModalOpen = true"
          >
            <Eye class="h-4 w-4" />
            Жариялау
          </UiButton>
          <UiButton
            v-else-if="event.status === 'PUBLISHED'"
            variant="outline"
            size="md"
            @click="unpublishModalOpen = true"
          >
            <EyeOff class="h-4 w-4" />
            Жариялауды тоқтату
          </UiButton>
        </template>
      </PageHeader>

      <!-- Status strip -->
      <div
        class="mb-5 flex flex-wrap items-center gap-3 rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm"
      >
        <EventStatusBadge :status="event.status" />
        <span class="text-slate-500">·</span>
        <span class="text-slate-500">
          <span class="font-medium text-slate-700">Slug:</span> /{{ event.slug }}
        </span>
        <span class="text-slate-300">·</span>
        <span class="text-slate-500">
          <span class="font-medium text-slate-700">Жасалған:</span> {{ formatDate(event.createdAt) }}
        </span>
        <span class="text-slate-300">·</span>
        <span class="text-slate-500">
          <span class="font-medium text-slate-700">Жаңартылған:</span> {{ formatDate(event.updatedAt) }}
        </span>
      </div>

      <!-- Tabs -->
      <div class="mb-5 border-b border-slate-200">
        <nav class="-mb-px flex gap-6">
          <button
            v-for="t in tabs"
            :key="t.key"
            type="button"
            class="flex items-center gap-2 border-b-2 px-1 py-3 text-sm font-medium transition-colors"
            :class="
              activeTab === t.key
                ? 'border-brand-600 text-brand-700'
                : 'border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-700'
            "
            @click="activeTab = t.key"
          >
            <component :is="t.icon" class="h-4 w-4" />
            {{ t.label }}
          </button>
        </nav>
      </div>

      <!-- Tab: Overview -->
      <div v-if="activeTab === 'overview'" class="grid gap-5 lg:grid-cols-3">
        <div class="space-y-5 lg:col-span-2">
          <UiCard title="Сипаттама">
            <p v-if="event.description" class="whitespace-pre-wrap text-sm leading-relaxed text-slate-700">
              {{ event.description }}
            </p>
            <p v-else class="text-sm italic text-slate-400">Сипаттама қосылмаған.</p>
          </UiCard>

          <UiCard title="Спикер">
            <div v-if="event.speakerName" class="space-y-2">
              <p class="text-sm font-medium text-slate-900">{{ event.speakerName }}</p>
              <p
                v-if="event.speakerBio"
                class="whitespace-pre-wrap text-sm text-slate-600"
              >
                {{ event.speakerBio }}
              </p>
            </div>
            <p v-else class="text-sm italic text-slate-400">Спикер көрсетілмеген.</p>
          </UiCard>

          <UiCard title="Қауіпті аймақ" class="border-danger-200">
            <div class="flex items-start justify-between gap-4">
              <div>
                <p class="text-sm font-medium text-slate-900">Ивентті жою</p>
                <p class="mt-1 text-sm text-slate-500">
                  Ивент, оның барлық сессиялары және аналитикасы біржола жойылады.
                  Бұл әрекетті қайтару мүмкін емес.
                </p>
              </div>
              <UiButton variant="danger" size="sm" @click="deleteModalOpen = true">
                <Trash2 class="h-4 w-4" />
                Жою
              </UiButton>
            </div>
          </UiCard>
        </div>

        <div class="space-y-5">
          <UiCard :padded="false">
            <div class="aspect-video overflow-hidden bg-slate-100">
              <img
                v-if="event.coverImageUrl"
                :src="event.coverImageUrl"
                :alt="event.title"
                class="h-full w-full object-cover"
              />
              <div v-else class="flex h-full w-full items-center justify-center text-slate-300">
                <Calendar class="h-8 w-8" />
              </div>
            </div>
            <div class="p-4">
              <p class="text-xs font-medium uppercase tracking-wide text-slate-400">Мұқаба</p>
              <p class="mt-1 break-all text-xs text-slate-500">
                {{ event.coverImageUrl || '—' }}
              </p>
            </div>
          </UiCard>

          <UiCard title="Мета">
            <dl class="space-y-2 text-sm">
              <div class="flex items-center justify-between">
                <dt class="text-slate-500">Уақыт белдеуі</dt>
                <dd class="font-medium text-slate-900">{{ event.timezone || '—' }}</dd>
              </div>
              <div class="flex items-center justify-between">
                <dt class="text-slate-500">Тіл</dt>
                <dd class="font-medium text-slate-900">{{ event.language || '—' }}</dd>
              </div>
              <div class="flex items-center justify-between">
                <dt class="text-slate-500">Версия</dt>
                <dd class="font-medium text-slate-900">{{ event.version }}</dd>
              </div>
            </dl>
            <template #footer>
              <NuxtLink
                v-if="event.status === 'PUBLISHED'"
                :to="`/event/${event.slug}`"
                target="_blank"
                class="inline-flex items-center gap-1 text-xs font-medium text-brand-700 hover:text-brand-800"
              >
                <ExternalLink class="h-3.5 w-3.5" />
                Ашық бет
              </NuxtLink>
              <span v-else class="inline-flex items-center gap-1 text-xs text-slate-400">
                <Globe class="h-3.5 w-3.5" />
                Жарияланбаған
              </span>
            </template>
          </UiCard>
        </div>
      </div>

      <!-- Tab: Sessions (placeholder) -->
      <UiEmpty
        v-else-if="activeTab === 'sessions'"
        title="Сессиялар әлі қосылмаған"
        description="Сессия менеджері келесі итерацияда қосылады. Ол жерде live және auto сессияларды жоспарлау, эфирді бастау және аяқтау мүмкіндіктері болады."
      >
        <template #icon><Radio class="h-5 w-5" /></template>
      </UiEmpty>

      <!-- Tab: CTA (placeholder) -->
      <UiEmpty
        v-else-if="activeTab === 'cta'"
        title="CTA редакторы әлі жоқ"
        description="CTA редакторы F2-ден кейін келеді. Ол жерде файл/сілтеме/курс/форма типіндегі CTA-ларды баптап, эфирге жіберу мүмкіндігі болады."
      >
        <template #icon><Megaphone class="h-5 w-5" /></template>
      </UiEmpty>

      <!-- Tab: Chat settings (placeholder) -->
      <UiEmpty
        v-else-if="activeTab === 'chat'"
        title="Чат баптаулары"
        description="Slow mode, анти-спам, сәлемдесу — чат баптаулары бөлек итерацияда баяндалады."
      >
        <template #icon><MessageSquare class="h-5 w-5" /></template>
      </UiEmpty>
    </template>

    <!-- Publish confirm -->
    <UiModal v-model="publishModalOpen" title="Ивентті жариялау">
      <p class="text-sm text-slate-600">
        Жарияланғаннан кейін ивент тенанттың жалпыға ашық тізімінде көрінеді
        және /event/<span class="font-mono text-slate-900">{{ event?.slug }}</span> мекенжайы арқылы қолжетімді болады.
      </p>
      <template #footer>
        <UiButton variant="outline" :disabled="actionLoading" @click="publishModalOpen = false">
          Бас тарту
        </UiButton>
        <UiButton variant="primary" :loading="actionLoading" @click="doPublish">
          Жариялау
        </UiButton>
      </template>
    </UiModal>

    <!-- Unpublish confirm -->
    <UiModal v-model="unpublishModalOpen" title="Жариялауды тоқтату">
      <p class="text-sm text-slate-600">
        Жариялауды тоқтатқаннан кейін ашық бет қолжетімсіз болады. Ивент қара
        жобаға айналады, бірақ барлық деректер сақталады.
      </p>
      <template #footer>
        <UiButton variant="outline" :disabled="actionLoading" @click="unpublishModalOpen = false">
          Бас тарту
        </UiButton>
        <UiButton variant="primary" :loading="actionLoading" @click="doUnpublish">
          Тоқтату
        </UiButton>
      </template>
    </UiModal>

    <!-- Delete confirm -->
    <UiModal v-model="deleteModalOpen" title="Ивентті жою" size="md">
      <div class="space-y-3">
        <div class="flex items-start gap-3 rounded-lg border border-danger-200 bg-danger-50 p-3">
          <AlertTriangle class="mt-0.5 h-5 w-5 shrink-0 text-danger-500" />
          <p class="text-sm text-danger-800">
            Бұл әрекет қайтарылмайды. Барлық сессиялар, аналитика және чат тарихы
            жойылады.
          </p>
        </div>
        <p class="text-sm text-slate-600">
          Жалғастыру үшін "Жою" батырмасын басыңыз.
        </p>
      </div>
      <template #footer>
        <UiButton variant="outline" :disabled="actionLoading" @click="deleteModalOpen = false">
          Бас тарту
        </UiButton>
        <UiButton variant="danger" :loading="actionLoading" @click="doDelete">
          <Trash2 class="h-4 w-4" />
          Жою
        </UiButton>
      </template>
    </UiModal>
  </div>
</template>
