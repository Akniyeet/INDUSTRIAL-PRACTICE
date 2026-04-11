<script setup lang="ts">
/**
 * CTA show/hide control panel for the admin live-control page.
 *
 * <p>Every CTA defined on the event appears as a row here with a toggle
 * showing whether it's currently visible in the room. Toggling the switch
 * calls {@code api.cta.show(sessionId, ctaId)} or {@code api.cta.hide(..)}
 * which — on the backend — both (a) fires a timeline action on the
 * {@code CONTROL} Centrifugo channel so viewers react instantly, and
 * (b) writes a row in the source session's timeline so AUTO replays fire
 * the same CTA at the same offset (§23 / §50).
 *
 * <p>The parent owns the {@code activeCtaIds} set (mutated by the timeline
 * Centrifuge subscription) and the full CTA catalogue — we're purely UI.
 */
import type { CtaResponse, UUID } from '#shared/api/types'
import { BookOpen, Download, ExternalLink, FileText, Loader2 } from 'lucide-vue-next'
import { computed } from 'vue'

const props = defineProps<{
  ctas: CtaResponse[]
  activeCtaIds: Set<UUID>
  /** Ids currently mid-flight (debounced toggle guards). */
  pendingIds: Set<UUID>
}>()

const emit = defineEmits<{
  (e: 'toggle', cta: CtaResponse, show: boolean): void
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

function handleToggle(cta: CtaResponse) {
  if (props.pendingIds.has(cta.id)) return
  const currentlyOn = props.activeCtaIds.has(cta.id)
  emit('toggle', cta, !currentlyOn)
}
</script>

<template>
  <div class="flex h-full flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-soft">
    <header class="border-b border-slate-100 px-4 py-3">
      <h3 class="text-sm font-semibold text-slate-900">CTA басқару</h3>
      <p class="text-xs text-slate-500">Көрерменге көрсетуді қосу/өшіру</p>
    </header>

    <div class="flex-1 overflow-y-auto p-4">
      <div v-if="sorted.length === 0" class="flex h-full items-center justify-center text-sm text-slate-400">
        Бұл ивент үшін CTA әзірге жасалмаған
      </div>
      <ul v-else class="space-y-2">
        <li
          v-for="cta in sorted"
          :key="cta.id"
          class="flex items-start gap-3 rounded-xl border border-slate-200 bg-slate-50 p-3 transition hover:border-brand-300 hover:bg-white"
        >
          <div class="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-lg bg-brand-100 text-brand-700">
            <component :is="iconFor(cta.type)" class="h-4 w-4" />
          </div>
          <div class="min-w-0 flex-1">
            <h4 class="truncate text-sm font-semibold text-slate-900">{{ cta.title }}</h4>
            <p class="mt-0.5 text-[11px] text-slate-500">
              priority {{ cta.priority }} · {{ cta.placement.toLowerCase() }} · {{ cta.type.toLowerCase() }}
            </p>
          </div>
          <button
            type="button"
            :aria-pressed="activeCtaIds.has(cta.id)"
            :disabled="pendingIds.has(cta.id)"
            class="relative inline-flex h-6 w-11 flex-shrink-0 items-center rounded-full transition"
            :class="[
              activeCtaIds.has(cta.id) ? 'bg-brand-600' : 'bg-slate-300',
              pendingIds.has(cta.id) && 'opacity-60',
            ]"
            @click="handleToggle(cta)"
          >
            <Loader2
              v-if="pendingIds.has(cta.id)"
              class="absolute inset-0 m-auto h-3 w-3 animate-spin text-white"
            />
            <span
              v-else
              class="inline-block h-5 w-5 transform rounded-full bg-white shadow transition"
              :class="activeCtaIds.has(cta.id) ? 'translate-x-5' : 'translate-x-0.5'"
            />
          </button>
        </li>
      </ul>
    </div>
  </div>
</template>
