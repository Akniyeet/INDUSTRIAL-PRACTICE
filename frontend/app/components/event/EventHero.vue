<script setup lang="ts">
/**
 * Visual identity block for a public event landing/waiting/slot page.
 *
 * <p>Renders the cover image (or a brand-tone gradient fallback when the
 * tenant has not uploaded one yet), the event title, the human description,
 * and an optional speaker block. Layout is mobile-first: cover stacks above
 * text on narrow screens and shifts to a left rail on >= md.
 *
 * <p>The component is intentionally presentational — it does not know about
 * sessions, countdowns, or auth. Surrounding pages render their state-driven
 * action card next to it.
 */
import type { PublicEventView } from '#shared/api/types'

defineProps<{ event: PublicEventView }>()
</script>

<template>
  <header class="grid gap-6 md:grid-cols-[minmax(0,1fr)_minmax(0,1.2fr)] md:items-start">
    <div
      class="relative aspect-video w-full overflow-hidden rounded-2xl shadow-soft"
      :class="{ 'bg-gradient-to-br from-brand-500 to-brand-700': !event.coverImageUrl }"
    >
      <img
        v-if="event.coverImageUrl"
        :src="event.coverImageUrl"
        :alt="event.title"
        class="h-full w-full object-cover"
      >
      <div
        v-else
        class="flex h-full w-full items-center justify-center text-5xl font-black text-white/30"
      >
        {{ event.title.slice(0, 1).toUpperCase() }}
      </div>
    </div>

    <div class="flex flex-col gap-4">
      <h1 class="text-3xl font-bold leading-tight tracking-tight md:text-4xl">
        {{ event.title }}
      </h1>
      <p v-if="event.description" class="text-base leading-relaxed text-slate-600">
        {{ event.description }}
      </p>
      <div
        v-if="event.speakerName"
        class="mt-2 flex items-start gap-3 rounded-xl border border-slate-200 bg-white p-4"
      >
        <div
          class="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-full bg-brand-100 font-semibold text-brand-700"
        >
          {{ event.speakerName.slice(0, 1).toUpperCase() }}
        </div>
        <div class="min-w-0">
          <div class="font-semibold text-slate-900">{{ event.speakerName }}</div>
          <p v-if="event.speakerBio" class="mt-0.5 text-sm text-slate-500">
            {{ event.speakerBio }}
          </p>
        </div>
      </div>
    </div>
  </header>
</template>
