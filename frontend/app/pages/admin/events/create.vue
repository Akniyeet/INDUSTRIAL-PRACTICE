<script setup lang="ts">
/**
 * Admin → Events → Create.
 *
 * <p>Thin wrapper around {@code EventForm}. The form owns validation and the
 * API call; this page just provides the surrounding chrome and handles the
 * post-submit navigation to the newly created event.
 */
import { Sparkles } from 'lucide-vue-next'
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
    <PageHeader
      title="Новое мероприятие"
      subtitle="Соберите лендинг, чат, CTA и модерацию за 6 шагов — сохраним всё сразу."
      :breadcrumbs="[
        { label: 'Главная', to: '/admin' },
        { label: 'Мероприятия', to: '/admin/events' },
        { label: 'Новое' },
      ]"
    />

    <!-- Premium hero panel — gives the form a visible starting point -->
    <div
      class="relative mb-6 overflow-hidden rounded-2xl border border-brand-200/60 bg-gradient-to-br from-brand-50 via-white to-violet-50 p-6"
    >
      <div class="pointer-events-none absolute -right-16 -top-16 h-48 w-48 rounded-full bg-brand-300/20 blur-3xl" />
      <div class="pointer-events-none absolute -bottom-20 -left-10 h-40 w-40 rounded-full bg-violet-300/15 blur-3xl" />

      <div class="relative flex items-start gap-4">
        <div class="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-brand-500 to-violet-500 text-white shadow-lg shadow-brand-500/25">
          <Sparkles class="h-5 w-5" />
        </div>
        <div>
          <p class="text-sm font-semibold text-slate-900">Черновик можно вернуться и дополнить в любой момент</p>
          <p class="mt-1 text-sm text-slate-500">
            Заполните обязательные поля на шаге «Информация» — остальное можно оставить на потом.
            После публикации событие сразу появится по публичной ссылке.
          </p>
        </div>
      </div>
    </div>

    <EventForm mode="create" @submit="onCreated" @cancel="onCancel" />
  </div>
</template>
