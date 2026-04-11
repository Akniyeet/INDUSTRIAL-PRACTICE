<script setup lang="ts">
/**
 * Admin → Events list.
 *
 * <p>First admin page of the vertical slice. Lists tenant events with a
 * status filter + pagination, and routes into the detail/create flows.
 * The backend returns a Spring {@link Page}, so pagination is zero-indexed
 * and we surface "page N of M" in the UI.
 *
 * <h2>Data lifecycle</h2>
 * <ul>
 *   <li>Load is triggered by a `watch` on page + status filter. We avoid
 *       `useAsyncData` for the list because the filter state is fully
 *       client-driven and we want snappy in-place updates rather than a
 *       Nuxt payload round-trip.</li>
 *   <li>Errors surface through the shared `useApi` → toast pipeline; we
 *       additionally keep an inline message so the empty state doesn't
 *       look like "zero events" when it's really "backend down".</li>
 * </ul>
 */
import { Calendar, Plus, RefreshCw, Search, ChevronLeft, ChevronRight } from 'lucide-vue-next'
import { ref, computed, watch, onMounted } from 'vue'
import { format } from 'date-fns'
import type { EventResponse, EventStatus, Page } from '~/shared/api/types'

definePageMeta({
  layout: 'admin',
  middleware: 'auth',
})

useHead({ title: 'Ивенттер — Webizon' })

const api = useApi()

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

const page = ref(0)
const size = ref(20)
const statusFilter = ref<EventStatus | ''>('')
const searchQuery = ref('')

const loading = ref(false)
const errorMessage = ref<string | null>(null)
const data = ref<Page<EventResponse> | null>(null)

const statusOptions = [
  { value: '',          label: 'Барлық күй' },
  { value: 'DRAFT',     label: 'Жоба' },
  { value: 'PUBLISHED', label: 'Жарияланған' },
  { value: 'ARCHIVED',  label: 'Мұрағатта' },
]

// ---------------------------------------------------------------------------
// Derived
// ---------------------------------------------------------------------------

const events = computed(() => data.value?.content ?? [])

const filteredEvents = computed(() => {
  const q = searchQuery.value.trim().toLowerCase()
  if (!q) return events.value
  return events.value.filter(
    (e) =>
      e.title.toLowerCase().includes(q) ||
      e.slug.toLowerCase().includes(q) ||
      (e.speakerName?.toLowerCase().includes(q) ?? false),
  )
})

const totalPages = computed(() => data.value?.totalPages ?? 0)
const totalElements = computed(() => data.value?.totalElements ?? 0)
const isFirst = computed(() => data.value?.first ?? true)
const isLast = computed(() => data.value?.last ?? true)

// ---------------------------------------------------------------------------
// Fetch
// ---------------------------------------------------------------------------

async function fetchEvents() {
  loading.value = true
  errorMessage.value = null
  try {
    data.value = await api.events.list({
      page: page.value,
      size: size.value,
      status: statusFilter.value || undefined,
    })
  } catch (err) {
    const apiErr = err as { status?: number; detail?: string; title?: string }
    errorMessage.value =
      apiErr.detail ??
      apiErr.title ??
      'Ивенттер тізімін жүктеу кезінде қате орын алды.'
    data.value = null
  } finally {
    loading.value = false
  }
}

onMounted(fetchEvents)

watch(statusFilter, () => {
  page.value = 0
  fetchEvents()
})

function goToPage(next: number) {
  if (next < 0 || next >= totalPages.value) return
  page.value = next
  fetchEvents()
}

// ---------------------------------------------------------------------------
// Formatters
// ---------------------------------------------------------------------------

function formatDate(iso: string) {
  try {
    return format(new Date(iso), 'dd.MM.yyyy HH:mm')
  } catch {
    return iso
  }
}
</script>

<template>
  <div>
    <PageHeader
      title="Ивенттер"
      subtitle="Тенант аясындағы барлық ивенттерді басқарыңыз."
      :breadcrumbs="[{ label: 'Басты бет', to: '/admin' }, { label: 'Ивенттер' }]"
    >
      <template #actions>
        <UiButton variant="outline" size="md" :disabled="loading" @click="fetchEvents">
          <RefreshCw class="h-4 w-4" :class="loading && 'animate-spin'" />
          Жаңарту
        </UiButton>
        <UiButton variant="primary" size="md" to="/admin/events/create">
          <Plus class="h-4 w-4" />
          Жаңа ивент
        </UiButton>
      </template>
    </PageHeader>

    <!-- Filters -->
    <div class="mb-5 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div class="flex flex-1 flex-col gap-3 sm:flex-row sm:items-end">
        <div class="relative w-full sm:max-w-sm">
          <label class="field-label" for="event-search">Іздеу</label>
          <div class="relative">
            <Search
              class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400"
            />
            <input
              id="event-search"
              v-model="searchQuery"
              type="search"
              class="input-base pl-9"
              placeholder="Атауы, slug, спикер..."
            />
          </div>
        </div>
        <div class="w-full sm:max-w-[200px]">
          <UiSelect
            v-model="statusFilter"
            label="Күйі"
            :options="statusOptions"
          />
        </div>
      </div>
    </div>

    <!-- Error -->
    <UiCard v-if="errorMessage && !loading" class="border-danger-200 bg-danger-50/60">
      <p class="text-sm text-danger-700">{{ errorMessage }}</p>
      <template #footer>
        <UiButton variant="outline" size="sm" @click="fetchEvents">Қайта көру</UiButton>
      </template>
    </UiCard>

    <!-- Loading skeleton -->
    <div v-else-if="loading && !data" class="grid gap-3">
      <UiSkeleton v-for="i in 5" :key="i" h="h-[72px]" rounded="rounded-xl" />
    </div>

    <!-- Empty -->
    <UiEmpty
      v-else-if="!loading && events.length === 0"
      title="Әзірге ивенттер жоқ"
      description="Алғашқы ивентіңізді жасаңыз — содан кейін сессиялар мен эфирлер тізбегі қолжетімді болады."
    >
      <template #icon><Calendar class="h-5 w-5" /></template>
      <template #actions>
        <UiButton variant="primary" to="/admin/events/create">
          <Plus class="h-4 w-4" />
          Жаңа ивент жасау
        </UiButton>
      </template>
    </UiEmpty>

    <!-- Table -->
    <UiCard v-else :padded="false">
      <div class="overflow-x-auto">
        <table class="min-w-full text-sm">
          <thead>
            <tr class="border-b border-slate-200 bg-slate-50/60 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
              <th class="px-5 py-3">Ивент</th>
              <th class="px-5 py-3">Күйі</th>
              <th class="px-5 py-3">Спикер</th>
              <th class="px-5 py-3">Жаңартылды</th>
              <th class="px-5 py-3"></th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100">
            <tr
              v-for="event in filteredEvents"
              :key="event.id"
              class="group cursor-pointer transition-colors hover:bg-slate-50"
              @click="$router.push(`/admin/events/${event.id}`)"
            >
              <td class="px-5 py-4">
                <div class="flex items-center gap-3">
                  <div
                    class="h-10 w-10 shrink-0 overflow-hidden rounded-lg bg-slate-100"
                  >
                    <img
                      v-if="event.coverImageUrl"
                      :src="event.coverImageUrl"
                      :alt="event.title"
                      class="h-full w-full object-cover"
                    />
                    <div
                      v-else
                      class="flex h-full w-full items-center justify-center text-slate-300"
                    >
                      <Calendar class="h-4 w-4" />
                    </div>
                  </div>
                  <div class="min-w-0">
                    <div class="truncate font-medium text-slate-900 group-hover:text-brand-700">
                      {{ event.title }}
                    </div>
                    <div class="truncate text-xs text-slate-500">/{{ event.slug }}</div>
                  </div>
                </div>
              </td>
              <td class="px-5 py-4">
                <EventStatusBadge :status="event.status" />
              </td>
              <td class="px-5 py-4 text-slate-600">
                {{ event.speakerName ?? '—' }}
              </td>
              <td class="px-5 py-4 text-xs text-slate-500">
                {{ formatDate(event.updatedAt) }}
              </td>
              <td class="px-5 py-4 text-right">
                <span
                  class="inline-flex items-center gap-1 text-xs font-medium text-brand-700 opacity-0 transition-opacity group-hover:opacity-100"
                >
                  Ашу <ChevronRight class="h-3 w-3" />
                </span>
              </td>
            </tr>
            <tr v-if="filteredEvents.length === 0">
              <td colspan="5" class="px-5 py-10 text-center text-sm text-slate-500">
                Іздеуге сәйкес нәтиже табылмады.
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Pagination footer -->
      <div
        v-if="totalPages > 1"
        class="flex items-center justify-between border-t border-slate-100 px-5 py-3 text-xs text-slate-500"
      >
        <span>
          Барлығы {{ totalElements }} ивент · бет {{ page + 1 }} / {{ totalPages }}
        </span>
        <div class="flex items-center gap-1">
          <button
            type="button"
            class="flex h-8 w-8 items-center justify-center rounded-lg border border-slate-200 text-slate-500 transition-colors hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
            :disabled="isFirst || loading"
            @click="goToPage(page - 1)"
          >
            <ChevronLeft class="h-4 w-4" />
          </button>
          <button
            type="button"
            class="flex h-8 w-8 items-center justify-center rounded-lg border border-slate-200 text-slate-500 transition-colors hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
            :disabled="isLast || loading"
            @click="goToPage(page + 1)"
          >
            <ChevronRight class="h-4 w-4" />
          </button>
        </div>
      </div>
    </UiCard>
  </div>
</template>
