<script setup lang="ts">
/**
 * Admin → Event edit.
 *
 * <p>Loads the event, feeds it into {@code EventForm} in edit mode. On
 * successful save we push back to the detail page so the admin lands on the
 * fresh overview immediately — no stale cached read.
 */
import { AlertTriangle } from 'lucide-vue-next'
import type { EventResponse } from '~/shared/api/types'

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
  title: () => (event.value ? `${event.value.title} — өңдеу` : 'Өңдеу — Webizon'),
})

function onSaved(updated: EventResponse) {
  router.push(`/admin/events/${updated.id}`)
}

function onCancel() {
  router.push(`/admin/events/${eventId.value}`)
}
</script>

<template>
  <div class="mx-auto max-w-3xl">
    <PageHeader
      :title="event?.title ? `${event.title} — өңдеу` : 'Ивентті өңдеу'"
      subtitle="Slug өзгертілмейді. Өзгертулер сақталғаннан кейін бірден күшіне енеді."
      :breadcrumbs="[
        { label: 'Басты бет', to: '/admin' },
        { label: 'Ивенттер', to: '/admin/events' },
        { label: event?.title || '...', to: `/admin/events/${eventId}` },
        { label: 'Өңдеу' },
      ]"
    />

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
          <h3 class="text-sm font-semibold text-danger-800">Ивент табылмады</h3>
          <p class="mt-1 text-sm text-danger-700">
            Бұл ивент жойылған немесе сізде оған қолжетім жоқ.
          </p>
        </div>
      </div>
      <template #footer>
        <UiButton variant="outline" size="sm" to="/admin/events">Тізімге қайту</UiButton>
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
