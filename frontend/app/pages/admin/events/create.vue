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

useHead({ title: 'Жаңа ивент — Webizon' })

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
      title="Жаңа ивент"
      subtitle="Жариялау алдында барлық негізгі деректерді толтырыңыз."
      :breadcrumbs="[
        { label: 'Басты бет', to: '/admin' },
        { label: 'Ивенттер', to: '/admin/events' },
        { label: 'Жаңа' },
      ]"
    />

    <EventForm mode="create" @submit="onCreated" @cancel="onCancel" />
  </div>
</template>
