<script setup lang="ts">
/**
 * Full-width cover banner for the public event landing / waiting / slot
 * pages. Intentionally presentational and narrow in scope: it only
 * renders the hero image (or a branded fallback if no cover is
 * uploaded).
 *
 * <p>The visual language mirrors the EDUSER marathon/course pages —
 * the banner sits flush to the viewport edges on mobile and rounds to
 * a generous radius on tablet and up. Title, description and speaker
 * metadata render separately in stacked "eduser-section" cards below
 * so the landing reads top-down rather than side-by-side.
 */
import type { PublicEventView } from '#shared/api/types'

defineProps<{ event: PublicEventView }>()
</script>

<template>
  <div
    class="relative aspect-video w-full overflow-hidden md:rounded-[20px] md:shadow-[0_0_12px_rgba(102,141,255,0.25)]"
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
      class="flex h-full w-full items-center justify-center text-6xl font-black text-white/30"
    >
      {{ event.title.slice(0, 1).toUpperCase() }}
    </div>
  </div>
</template>
