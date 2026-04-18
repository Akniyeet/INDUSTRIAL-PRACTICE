<script setup lang="ts">
/**
 * Admin → Event edit.
 *
 * <p>Loads the event, feeds it into {@code EventForm} in edit mode. On
 * successful save we push back to the detail page so the admin lands on the
 * fresh overview immediately — no stale cached read.
 */
import { AlertTriangle, Pencil } from 'lucide-vue-next'
import type { EventResponse } from '#shared/api/types'

definePageMeta({
  layout: 'admin',
  middleware: 'auth',
})

const route = useRoute()
const router = useRouter()
const api = useApi()

const eventId = computed(() => route.params.id as string)

const { data: event, pending, error } = useAsyncData<EventResponse>(
  () => `event-edit-${eventId.value}`,
  () => api.events.getById(eventId.value),
  { watch: [eventId] },
)

useHead({
  title: () => (event.value ? `${event.value.title} — редактирование` : 'Редактирование — Webizon'),
})

function onSaved(_updated: EventResponse) {
  router.push('/admin/events')
}

function onCancel() {
  router.push('/admin/events')
}
</script>

<template>
  <div class="mx-auto max-w-4xl">
    <PageHeader
      :title="event?.title ? event.title : 'Редактировать мероприятие'"
      subtitle="Slug не изменяется. Изменения вступают в силу сразу после сохранения."
      :breadcrumbs="[
        { label: 'Главная', to: '/admin' },
        { label: 'Мероприятия', to: '/admin/events' },
        { label: event?.title || 'Редактирование' },
      ]"
    />

    <!-- Edit-mode hero — subtly different from create (amber instead of brand) -->
    <div
      v-if="event"
      class="relative mb-6 overflow-hidden rounded-2xl border border-amber-200/60 bg-gradient-to-br from-amber-50 via-white to-orange-50 p-6"
    >
      <div class="pointer-events-none absolute -right-16 -top-16 h-48 w-48 rounded-full bg-amber-300/20 blur-3xl" />
      <div class="pointer-events-none absolute -bottom-20 -left-10 h-40 w-40 rounded-full bg-orange-300/15 blur-3xl" />

      <div class="relative flex items-start gap-4">
        <div class="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-amber-500 to-orange-500 text-white shadow-lg shadow-amber-500/25">
          <Pencil class="h-5 w-5" />
        </div>
        <div>
          <p class="text-sm font-semibold text-slate-900">Редактирование публичного лендинга</p>
          <p class="mt-1 text-sm text-slate-500">
            Внесённые изменения сразу отразятся на публичной странице события.
            Если мероприятие сейчас в эфире — зрители увидят обновления после перезагрузки страницы.
          </p>
        </div>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="pending && !event" class="space-y-4">
      <UiSkeleton h="h-[200px]" rounded="rounded-xl" />
      <UiSkeleton h="h-[140px]" rounded="rounded-xl" />
    </div>

    <!-- Error -->
    <UiCard v-else-if="error || !event" class="border-danger-200 bg-danger-50/60">
      <div class="flex items-start gap-3">
        <AlertTriangle class="mt-0.5 h-5 w-5 text-danger-500" />
        <div>
          <h3 class="text-sm font-semibold text-danger-800">Мероприятие не найдено</h3>
          <p class="mt-1 text-sm text-danger-700">
            Это мероприятие удалено или у вас нет к нему доступа.
          </p>
        </div>
      </div>
      <template #footer>
        <UiButton variant="outline" size="sm" to="/admin/events">Вернуться к списку</UiButton>
      </template>
    </UiCard>

    <!-- Form -->
    <EventForm
      v-else
      mode="edit"
      :initial="event"
      @submit="onSaved"
      @cancel="onCancel"
    />
  </div>
</template>
