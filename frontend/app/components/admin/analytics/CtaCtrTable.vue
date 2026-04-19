<script setup lang="ts">
/**
 * CTA performance table — enhanced with visual CTR bars and color-coded
 * performance indicators.
 */
import type { CtaCtrRow } from '#shared/api/types'
import { Download, ExternalLink, BookOpen, FileText, MousePointerClick, Target } from 'lucide-vue-next'

const props = defineProps<{
  rows: CtaCtrRow[]
}>()

const typeIcon: Record<string, typeof Download> = {
  FILE: Download,
  LINK: ExternalLink,
  COURSE: BookOpen,
  FORM: FileText,
}

const typeBadge: Record<string, string> = {
  FILE: 'bg-sky-50 text-sky-700',
  LINK: 'bg-violet-50 text-violet-700',
  COURSE: 'bg-emerald-50 text-emerald-700',
  FORM: 'bg-orange-50 text-orange-700',
}

const typeLabel: Record<string, string> = {
  FILE: 'Файл',
  LINK: 'Сілтеме',
  COURSE: 'Курс',
  FORM: 'Форма',
}

function formatCtr(ctr: number): string {
  return (ctr * 100).toFixed(1) + '%'
}

function ctrBarWidth(ctr: number): number {
  // Scale to a max of 20% CTR for the bar
  return Math.min(Math.round((ctr / 0.20) * 100), 100)
}

function ctrColor(ctr: number): string {
  if (ctr >= 0.1) return 'bg-emerald-500'
  if (ctr >= 0.05) return 'bg-brand-500'
  if (ctr > 0) return 'bg-amber-500'
  return 'bg-slate-300'
}

function ctrBadgeColor(ctr: number): string {
  if (ctr >= 0.1) return 'bg-emerald-50 text-emerald-700'
  if (ctr >= 0.05) return 'bg-brand-50 text-brand-700'
  if (ctr > 0) return 'bg-amber-50 text-amber-700'
  return 'bg-slate-100 text-slate-500'
}

// Best performing CTA
const bestCtr = computed(() =>
  props.rows.length ? Math.max(...props.rows.map((r) => r.ctr)) : 0,
)
</script>

<template>
  <div class="card overflow-hidden p-0">
    <!-- Header -->
    <div class="flex items-center justify-between border-b border-slate-100 px-5 py-4">
      <div>
        <h3 class="text-sm font-semibold text-slate-900">CTA нәтижелері</h3>
        <p class="mt-0.5 text-xs text-slate-400">Клик-жылдамдық бойынша</p>
      </div>
      <div v-if="rows.length > 0" class="text-right">
        <p class="text-xs text-slate-400">Үздік CTR</p>
        <p class="text-sm font-bold text-emerald-600">{{ formatCtr(bestCtr) }}</p>
      </div>
    </div>

    <!-- Empty state -->
    <div v-if="rows.length === 0" class="flex flex-col items-center justify-center py-12 text-center">
      <div class="mb-3 flex h-12 w-12 items-center justify-center rounded-xl bg-slate-100">
        <Target class="h-6 w-6 text-slate-400" />
      </div>
      <p class="text-sm font-medium text-slate-500">CTA деректері жоқ</p>
      <p class="mt-1 text-xs text-slate-400">Деректер CTA нәтижелері пайда болғаннан кейін көрінеді</p>
    </div>

    <!-- Table -->
    <div v-else class="divide-y divide-slate-100">
      <div
        v-for="row in rows"
        :key="row.ctaId"
        class="px-5 py-4 hover:bg-slate-50/60 transition-colors"
      >
        <!-- Top row: name + type -->
        <div class="flex items-start justify-between gap-3">
          <div class="flex min-w-0 items-center gap-2.5">
            <div class="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-lg bg-slate-100">
              <component
                :is="typeIcon[row.type] || MousePointerClick"
                class="h-4 w-4 text-slate-500"
              />
            </div>
            <div class="min-w-0">
              <p class="truncate text-sm font-semibold text-slate-900">{{ row.title }}</p>
              <span
                class="mt-0.5 inline-block rounded-full px-2 py-0.5 text-[10px] font-medium"
                :class="typeBadge[row.type] || 'bg-slate-100 text-slate-600'"
              >
                {{ typeLabel[row.type] || row.type }}
              </span>
            </div>
          </div>
          <!-- CTR badge -->
          <span
            class="flex-shrink-0 rounded-full px-2.5 py-1 text-xs font-bold"
            :class="ctrBadgeColor(row.ctr)"
          >
            {{ formatCtr(row.ctr) }}
          </span>
        </div>

        <!-- Stats row -->
        <div class="mt-3 flex items-center gap-4 text-xs text-slate-500">
          <span><span class="font-semibold text-slate-800">{{ row.impressions.toLocaleString() }}</span> көрсетілім</span>
          <span><span class="font-semibold text-slate-800">{{ row.clicks.toLocaleString() }}</span> клик</span>
          <span v-if="row.downloads > 0"><span class="font-semibold text-slate-800">{{ row.downloads.toLocaleString() }}</span> жүктеу</span>
        </div>

        <!-- CTR bar -->
        <div class="mt-2.5 h-1.5 w-full overflow-hidden rounded-full bg-slate-100">
          <div
            class="h-full rounded-full transition-all duration-700"
            :class="ctrColor(row.ctr)"
            :style="{ width: ctrBarWidth(row.ctr) + '%' }"
          />
        </div>
      </div>
    </div>
  </div>
</template>
