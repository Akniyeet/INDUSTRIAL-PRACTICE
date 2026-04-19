<script setup lang="ts">
/**
 * Active CTA list rendered in the room sidebar (or as a tab on mobile).
 *
 * <p>The room bootstrap returns the CTAs that are currently visible
 * ({@code RoomBootstrapResponse.activeCtas}); the parent page replaces this
 * list as Centrifugo broadcasts {@code CTA_SHOW} / {@code CTA_HIDE} events
 * on the timeline channel.
 *
 * <p>CTAs are sorted by {@code priority} ascending — the lower the number,
 * the more important — and stacked vertically. Per CLAUDE.md §57 the
 * deterministic resolution rules (priority + stacking) are honoured by the
 * backend before this component sees the list, so the frontend only renders;
 * it does not re-order or filter.
 *
 * <p>The action button is wired through the {@code onClick} prop so the
 * parent can record the {@code CTA_CLICK} analytics event before navigating.
 * The component itself never opens a new window without going through the
 * callback first.
 */
import type { CtaResponse } from '#shared/api/types'
import { ExternalLink, Download, BookOpen, FileText } from 'lucide-vue-next'
import { computed } from 'vue'

const props = defineProps<{
  ctas: CtaResponse[]
  onClick: (cta: CtaResponse) => void
}>()

const sorted = computed(() =>
  [...props.ctas].sort((a, b) => a.priority - b.priority),
)

function iconFor(type: CtaResponse['type']) {
  switch (type) {
    case 'FILE':   return Download
    case 'LINK':   return ExternalLink
    case 'COURSE': return BookOpen
    case 'FORM':   return FileText
    default:       return ExternalLink
  }
}
</script>

<template>
  <div class="flex h-full flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-soft">
    <header class="border-b border-slate-100 px-4 py-3">
      <h3 class="text-sm font-semibold text-slate-900">Ұсыныстар</h3>
    </header>

    <div class="flex-1 overflow-y-auto p-4">
      <div v-if="sorted.length === 0" class="flex h-full items-center justify-center text-sm text-slate-400">
        Әзірге белсенді ұсыныстар жоқ
      </div>
      <ul v-else class="space-y-3">
        <li
          v-for="cta in sorted"
          :key="cta.id"
          class="rounded-xl border border-slate-200 bg-slate-50 p-4 transition hover:border-brand-300 hover:bg-white"
        >
          <div class="flex items-start gap-3">
            <div class="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-lg bg-brand-100 text-brand-700">
              <component :is="iconFor(cta.type)" class="h-4 w-4" />
            </div>
            <div class="min-w-0 flex-1">
              <h4 class="truncate text-sm font-semibold text-slate-900">{{ cta.title }}</h4>
              <p v-if="cta.description" class="mt-1 line-clamp-2 text-xs text-slate-500">
                {{ cta.description }}
              </p>
            </div>
          </div>
          <button
            type="button"
            class="mt-3 inline-flex w-full items-center justify-center rounded-lg bg-brand-600 px-3 py-2 text-xs font-semibold text-white transition hover:bg-brand-700"
            @click="onClick(cta)"
          >
            {{ cta.buttonText }}
          </button>
        </li>
      </ul>
    </div>
  </div>
</template>
