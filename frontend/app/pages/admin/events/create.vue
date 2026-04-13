<script setup lang="ts">
/**
 * Admin → Events → Create.
 *
 * <p>Thin wrapper around {@code EventForm}. The form owns validation and the
 * API call; this page just provides the surrounding chrome and handles the
 * post-submit navigation to the newly created event.
 */
import type { EventResponse } from '#shared/api/types'

definePageMeta({
  layout: 'admin',
  middleware: 'auth',
})

useHead({ title: 'Новое мероприятие — Webizon' })

const router = useRouter()

function onCreated(event: EventResponse) {
  router.push(`/admin/events/${event.id}`)
}

function onCancel() {
  router.push('/admin/events')
}
</script>

<template>
  <div class="mx-auto max-w-3xl">
    <PageHeader
      title="Новое мероприятие"
      subtitle="Заполните основную информацию перед публикацией."
      :breadcrumbs="[
        { label: 'Главная', to: '/admin' },
        { label: 'Мероприятия', to: '/admin/events' },
        { label: 'Новое' },
      ]"
    />

    <EventForm mode="create" @submit="onCreated" @cancel="onCancel" />
  </div>
</template>
