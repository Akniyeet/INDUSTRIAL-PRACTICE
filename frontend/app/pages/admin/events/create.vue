<script setup lang="ts">
import type { EventResponse } from '#shared/api/types'

definePageMeta({
  layout: 'admin',
  middleware: 'auth',
})

useHead({ title: 'Новое мероприятие — Webizon' })

const router = useRouter()

function onCreated(_event: EventResponse) {
  router.push('/admin/events')
}

function onCancel() {
  router.push('/admin/events')
}
</script>

<template>
  <div class="mx-auto max-w-4xl">
    <EventForm mode="create" @submit="onCreated" @cancel="onCancel" />
  </div>
</template>
