<script setup lang="ts">
/**
 * CTA click-through rate table. Renders one row per CTA with
 * impressions, clicks, downloads, and CTR percentage.
 */
import type { CtaCtrRow } from '#shared/api/types'
import { Download, ExternalLink, BookOpen, FileText, MousePointerClick } from 'lucide-vue-next'

const props = defineProps<{
  rows: CtaCtrRow[]
}>()

const typeIcon: Record<string, typeof Download> = {
  FILE: Download,
  LINK: ExternalLink,
  COURSE: BookOpen,
  FORM: FileText,
}

function formatCtr(ctr: number): string {
  return (ctr * 100).toFixed(1) + '%'
}
</script>

<template>
  <div class="card overflow-hidden">
    <div class="border-b border-slate-100 px-5 py-3">
      <h3 class="text-sm font-semibold text-slate-900">CTA нәтижелері</h3>
    </div>

    <div v-if="rows.length === 0" class="px-5 py-8 text-center text-sm text-slate-400">
      CTA деректері жоқ
    </div>

    <table v-else class="w-full text-sm">
      <thead>
        <tr class="border-b border-slate-100 bg-slate-50 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
          <th class="px-5 py-2.5">CTA</th>
          <th class="px-3 py-2.5 text-right">Көрсетілім</th>
          <th class="px-3 py-2.5 text-right">Клик</th>
          <th class="px-3 py-2.5 text-right">Жүктеу</th>
          <th class="px-3 py-2.5 text-right">CTR</th>
        </tr>
      </thead>
      <tbody class="divide-y divide-slate-100">
        <tr v-for="row in rows" :key="row.ctaId" class="hover:bg-slate-50/60">
          <td class="px-5 py-3">
            <div class="flex items-center gap-2">
              <component
                :is="typeIcon[row.type] || MousePointerClick"
                class="h-4 w-4 flex-shrink-0 text-slate-400"
              />
              <div class="min-w-0">
                <p class="truncate font-medium text-slate-900">{{ row.title }}</p>
                <p class="text-xs text-slate-400">{{ row.type }}</p>
              </div>
            </div>
          </td>
          <td class="px-3 py-3 text-right tabular-nums text-slate-700">
            {{ row.impressions.toLocaleString() }}
          </td>
          <td class="px-3 py-3 text-right tabular-nums text-slate-700">
            {{ row.clicks.toLocaleString() }}
          </td>
          <td class="px-3 py-3 text-right tabular-nums text-slate-700">
            {{ row.downloads.toLocaleString() }}
          </td>
          <td class="px-3 py-3 text-right">
            <span
              class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-semibold"
              :class="row.ctr >= 0.05 ? 'bg-success-50 text-success-700' : row.ctr > 0 ? 'bg-warning-50 text-warning-700' : 'bg-slate-100 text-slate-500'"
            >
              {{ formatCtr(row.ctr) }}
            </span>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
