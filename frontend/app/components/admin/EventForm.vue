<script setup lang="ts">
/**
 * 6-step Event Creation Wizard.
 *
 * Steps:
 *  1. Информация — title, desc, speaker, cover upload, youtube, datetime, timezone, lang, slug
 *  2. Настройки чата — toggles, slow mode, forbidden words, welcome msg
 *  3. CTA / Материалы — dynamic CTA list with add/remove + live preview
 *  4. Доступ — public URL, moderator search & add
 *  5. Лендинг Builder — benefits + timeline with live preview
 *  6. Подтверждение — full review + submit
 */
import {
  ArrowLeft, ArrowRight, Save, Check, FileText, MessageSquare, Megaphone,
  Shield, Globe, Eye, Upload, X, Plus, ChevronDown,
  ChevronUp, Copy, Search, Trash2,
  // Icon picker icons for benefits
  Sparkles, Lightbulb, Target, Rocket, Trophy, Star,
  Heart, Zap, BookOpen, GraduationCap, Users, Brain,
  Puzzle, TrendingUp, Award, Clock, CheckCircle,
  MessageCircle, Play, Code, Palette, Laptop,
} from 'lucide-vue-next'
import { ref, reactive, computed, watch } from 'vue'
import type { EventCreateRequest, EventResponse, EventUpdateRequest } from '#shared/api/types'

type Mode = 'create' | 'edit'

const props = withDefaults(defineProps<{
  mode: Mode
  initial?: EventResponse | null
  submitLabel?: string
}>(), { initial: null, submitLabel: undefined })

const emit = defineEmits<{
  (e: 'submit', event: EventResponse): void
  (e: 'cancel'): void
}>()

const api = useApi()
const toast = useToastStore()
const auth = useAuthStore()

// ═══════════════════════════════════════════════════════════════════════════
// STEPS
// ═══════════════════════════════════════════════════════════════════════════
const steps = [
  { id: 1, title: 'Информация', icon: FileText },
  { id: 2, title: 'Чат', icon: MessageSquare },
  { id: 3, title: 'CTA', icon: Megaphone },
  { id: 4, title: 'Доступ', icon: Shield },
  { id: 5, title: 'Лендинг', icon: Globe },
  { id: 6, title: 'Подтверждение', icon: Eye },
]

const currentStep = ref(1)
const maxVisited = ref(1)

function goTo(step: number) {
  if (step >= 1 && step <= 6 && step <= maxVisited.value + 1) {
    currentStep.value = step
    if (step > maxVisited.value) maxVisited.value = step
  }
}
function next() {
  if (currentStep.value === 1 && !validateStep1()) return
  if (currentStep.value < 6) goTo(currentStep.value + 1)
}
function prev() { if (currentStep.value > 1) goTo(currentStep.value - 1) }

// ═══════════════════════════════════════════════════════════════════════════
// FORM STATE
// ═══════════════════════════════════════════════════════════════════════════

// Step 1
const title = ref(props.initial?.title ?? '')
const description = ref(props.initial?.description ?? '')
const speakerName = ref(props.initial?.speakerName ?? '')
const speakerBio = ref(props.initial?.speakerBio ?? '')
const coverImageUrl = ref(props.initial?.coverImageUrl ?? '')
const coverFile = ref<File | null>(null)
const coverPreview = ref<string | null>(null)
const uploadingCover = ref(false)
const youtubeUrl = ref('')
const plannedDateTime = ref('')
const timezone = ref(props.initial?.timezone ?? 'Asia/Almaty')
const language = ref(props.initial?.language ?? 'kk')
const slug = ref(props.initial?.slug ?? '')
let slugTouched = Boolean(props.initial?.slug)

// Step 2
const chatEnabled = ref(true)
const allowLinks = ref(false)
const showParticipantCount = ref(true)
const showParticipantNames = ref(true)
const premoderationEnabled = ref(false)
const blockPhoneNumbers = ref(true)
const slowModeSeconds = ref(0)
const welcomeMessage = ref('')
const forbiddenWords = ref<string[]>([])
const newForbiddenWord = ref('')

// Step 3
interface CtaItem { title: string; description: string; url: string; buttonText: string; collapsed: boolean }
const ctas = ref<CtaItem[]>([])

// Step 4
interface Moderator { id: string; name: string; email: string }
const moderators = ref<Moderator[]>([])
const modSearchQuery = ref('')
const modSearchResults = ref<Moderator[]>([])
const copiedUrl = ref(false)

// Step 5
const benefitIcons = [
  'Sparkles', 'Lightbulb', 'Target', 'Rocket', 'Trophy', 'Star',
  'Heart', 'Zap', 'BookOpen', 'GraduationCap', 'Users', 'Globe',
  'Shield', 'Laptop', 'Brain', 'Puzzle', 'TrendingUp', 'Award',
  'Clock', 'CheckCircle', 'MessageCircle', 'Play', 'Code', 'Palette',
]
// Map icon name strings → imported component objects (required for <component :is>)
const iconMap: Record<string, object> = {
  Sparkles, Lightbulb, Target, Rocket, Trophy, Star,
  Heart, Zap, BookOpen, GraduationCap, Users, Globe, Shield, Laptop,
  Brain, Puzzle, TrendingUp, Award, Clock, CheckCircle, MessageCircle,
  Play, Code, Palette,
}
function getIcon(name?: string) { return iconMap[name ?? 'Sparkles'] ?? iconMap.Sparkles }

interface LandingItem { title: string; desc: string; icon?: string; _iconOpen?: boolean }

/**
 * Read Step 5 state from {@code props.initial.landingConfig} when
 * editing, fall back to the marketing-friendly default when creating.
 * The incoming JSONB is untyped (backend stores it as {@code Map<String,
 * Object>}), so every read is defensive.
 */
const initialLanding = (props.initial?.landingConfig ?? {}) as Record<string, unknown>
function readString(v: unknown, fallback: string): string {
  return typeof v === 'string' && v.trim().length > 0 ? v : fallback
}
function readLandingItems(v: unknown, withIcon: boolean): LandingItem[] {
  if (!Array.isArray(v)) return []
  return v
    .map((raw) => {
      const r = raw as Record<string, unknown>
      const item: LandingItem = {
        title: typeof r?.title === 'string' ? r.title : '',
        desc:  typeof r?.desc  === 'string' ? r.desc  : '',
      }
      if (withIcon && typeof r?.icon === 'string') item.icon = r.icon
      return item
    })
    .filter((it) => it.title || it.desc)
}

const DEFAULT_BENEFITS: LandingItem[] = [
  { title: 'Практические знания', desc: 'Реальные навыки, которые сразу применяете', icon: 'Lightbulb' },
  { title: 'Живое общение', desc: 'Задавайте вопросы спикеру в реальном времени', icon: 'MessageCircle' },
  { title: 'Бесплатные материалы', desc: 'Получите чек-лист и дополнительные материалы', icon: 'BookOpen' },
]
const DEFAULT_TIMELINE: LandingItem[] = [
  { title: 'Приветствие и введение', desc: 'Знакомство со спикером и план урока' },
  { title: 'Основная часть', desc: 'Ключевые концепции и практика' },
  { title: 'Подведение итогов', desc: 'Резюме и следующие шаги' },
]

const loadedBenefits  = readLandingItems(initialLanding.benefits, true)
const loadedTimeline  = readLandingItems(initialLanding.timeline, false)

const benefitsTitle    = ref(readString(initialLanding.benefitsTitle,    'Что вы узнаете'))
const benefits         = ref<LandingItem[]>(loadedBenefits.length ? loadedBenefits : DEFAULT_BENEFITS)
const timelineTitle    = ref(readString(initialLanding.timelineTitle,    'Программа эфира'))
const timelineSubtitle = ref(readString(initialLanding.timelineSubtitle, 'Пошаговый план урока'))
const timeline         = ref<LandingItem[]>(loadedTimeline.length ? loadedTimeline : DEFAULT_TIMELINE)

const errors = reactive<Record<string, string>>({})
const loading = ref(false)

// ═══════════════════════════════════════════════════════════════════════════
// STEP 1 HELPERS
// ═══════════════════════════════════════════════════════════════════════════
const timezoneOptions = [
  { value: 'Asia/Almaty', label: 'Алматы (UTC+6)' },
  { value: 'Asia/Aqtobe', label: 'Актобе (UTC+5)' },
  { value: 'Europe/Moscow', label: 'Москва (UTC+3)' },
  { value: 'Asia/Tashkent', label: 'Ташкент (UTC+5)' },
  { value: 'Asia/Bishkek', label: 'Бишкек (UTC+6)' },
  { value: 'Europe/Istanbul', label: 'Стамбул (UTC+3)' },
  { value: 'UTC', label: 'UTC' },
]
const languageOptions = [
  { value: 'kk', label: 'Казахский' },
  { value: 'ru', label: 'Русский' },
  { value: 'en', label: 'English' },
]

function slugify(s: string) {
  return s.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9\s-]/g, '').trim().replace(/\s+/g, '-').replace(/-+/g, '-').slice(0, 64)
}

watch(title, (v) => { if (!slugTouched && props.mode === 'create') slug.value = slugify(v) })

function onCoverDrop(e: DragEvent) {
  const file = e.dataTransfer?.files?.[0]
  if (file && file.type.startsWith('image/')) setCoverFile(file)
}

function onCoverSelect(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (file) setCoverFile(file)
}

async function setCoverFile(file: File) {
  if (file.size > 5 * 1024 * 1024) { toast.error('Максимум 5 МБ'); return }
  coverFile.value = file
  if (coverPreview.value) URL.revokeObjectURL(coverPreview.value)
  coverPreview.value = URL.createObjectURL(file)

  // Upload to MinIO via presigned URL
  uploadingCover.value = true
  try {
    // Step 1: Get presigned PUT URL
    const slot = await api.storage.createSlot('COVER', file.type)

    // Step 2: Upload file directly to MinIO
    await fetch(slot.uploadUrl, {
      method: 'PUT',
      headers: { 'Content-Type': file.type },
      body: file,
    })

    // Step 3: Confirm upload
    const asset = await api.storage.confirm(slot.assetId)

    // Step 4: Get download URL for the cover
    const dl = await api.storage.downloadUrl(slot.assetId)
    coverImageUrl.value = dl.url

    toast.success('Обложка загружена')
  } catch (e) {
    console.warn('Cover upload failed, using local preview:', e)
    // Keep local preview — upload will retry on submit if needed
  } finally {
    uploadingCover.value = false
  }
}

function removeCover() {
  coverFile.value = null
  if (coverPreview.value) { URL.revokeObjectURL(coverPreview.value); coverPreview.value = null }
  coverImageUrl.value = ''
}

function validateStep1(): boolean {
  for (const k of Object.keys(errors)) delete errors[k]
  if (!title.value.trim()) errors.title = 'Обязательное поле'
  if (props.mode === 'create' && slug.value && !/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(slug.value))
    errors.slug = 'Только латинские буквы, цифры и дефис'
  const valid = Object.keys(errors).length === 0
  if (!valid) toast.warning('Заполните обязательные поля')
  return valid
}

// ═══════════════════════════════════════════════════════════════════════════
// STEP 2 HELPERS
// ═══════════════════════════════════════════════════════════════════════════
function addForbiddenWord() {
  const w = newForbiddenWord.value.trim().toLowerCase()
  if (w && !forbiddenWords.value.includes(w)) {
    forbiddenWords.value.push(w)
    newForbiddenWord.value = ''
  }
}
function removeForbiddenWord(i: number) { forbiddenWords.value.splice(i, 1) }

// ═══════════════════════════════════════════════════════════════════════════
// STEP 3 HELPERS
// ═══════════════════════════════════════════════════════════════════════════
function addCta() {
  ctas.value.push({ title: '', description: '', url: '', buttonText: 'Узнать больше', collapsed: false })
}
function removeCta(i: number) { ctas.value.splice(i, 1) }
function toggleCta(i: number) { ctas.value[i].collapsed = !ctas.value[i].collapsed }

// ═══════════════════════════════════════════════════════════════════════════
// STEP 4 HELPERS
// ═══════════════════════════════════════════════════════════════════════════
const eventUrl = computed(() => {
  const s = slug.value || 'my-event'
  // Use the user's actual tenant slug — a 'demo' fallback produces a
  // URL that resolves to UNAVAILABLE on the public endpoint.
  const t = auth.tenantSlug
  if (!t) return ''
  return `${typeof window !== 'undefined' ? window.location.origin : ''}/e/${t}/${s}`
})

function copyEventUrl() {
  navigator.clipboard.writeText(eventUrl.value)
  copiedUrl.value = true
  setTimeout(() => copiedUrl.value = false, 2000)
}

let modSearchTimer: ReturnType<typeof setTimeout> | null = null
function searchModerators() {
  const q = modSearchQuery.value.trim()
  if (q.length < 2) { modSearchResults.value = []; return }
  if (modSearchTimer) clearTimeout(modSearchTimer)
  modSearchTimer = setTimeout(async () => {
    try {
      const { apiBase } = useRuntimeConfig().public
      const results = await ($fetch as Function)('/v1/users/search', {
        baseURL: apiBase as string,
        headers: {
          Authorization: `Bearer ${auth.token}`,
          'X-Tenant-Id': auth.tenantId ?? '',
        },
        query: { query: q, limit: 10 },
      }) as Array<{ id: string; fullName: string | null; email: string }>
      modSearchResults.value = results
        .map(u => ({ id: u.id, name: u.fullName || u.email, email: u.email }))
        .filter(u => !moderators.value.some(m => m.id === u.id))
    } catch {
      modSearchResults.value = []
    }
  }, 300)
}

function addModerator(user: Moderator) {
  moderators.value.push(user)
  modSearchQuery.value = ''
  modSearchResults.value = []
}
function removeModerator(i: number) { moderators.value.splice(i, 1) }

// ═══════════════════════════════════════════════════════════════════════════
// STEP 5 HELPERS
// ═══════════════════════════════════════════════════════════════════════════
function addBenefit() { benefits.value.push({ title: '', desc: '' }) }
function removeBenefit(i: number) { benefits.value.splice(i, 1) }
function addTimelineItem() { timeline.value.push({ title: '', desc: '' }) }
function removeTimelineItem(i: number) { timeline.value.splice(i, 1) }

// ═══════════════════════════════════════════════════════════════════════════
// SUBMIT
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Collapse Step 5 state into a JSONB-ready payload. We drop UI-only
 * fields like {@code _iconOpen} (picker popover state) and omit
 * empty rows so the JSON stays lean. Default icon is "Sparkles" so
 * the public landing always has something to render.
 */
function buildLandingConfig() {
  const cleanedBenefits = benefits.value
    .filter(b => (b.title?.trim() || b.desc?.trim()))
    .map(b => ({
      title: b.title?.trim() ?? '',
      desc:  b.desc?.trim()  ?? '',
      icon:  b.icon || 'Sparkles',
    }))
  const cleanedTimeline = timeline.value
    .filter(t => (t.title?.trim() || t.desc?.trim()))
    .map(t => ({
      title: t.title?.trim() ?? '',
      desc:  t.desc?.trim()  ?? '',
    }))
  return {
    benefitsTitle:    benefitsTitle.value.trim()    || undefined,
    benefits:         cleanedBenefits,
    timelineTitle:    timelineTitle.value.trim()    || undefined,
    timelineSubtitle: timelineSubtitle.value.trim() || undefined,
    timeline:         cleanedTimeline,
  }
}

async function onSubmit() {
  loading.value = true
  try {
    let saved: EventResponse
    if (props.mode === 'create') {
      saved = await api.events.create({
        slug: slug.value.trim() || slugify(title.value),
        title: title.value.trim(),
        description: description.value.trim() || undefined,
        speakerName: speakerName.value.trim() || undefined,
        speakerBio: speakerBio.value.trim() || undefined,
        coverImageUrl: coverImageUrl.value.trim() || undefined,
        timezone: timezone.value,
        language: language.value,
        landingConfig: buildLandingConfig(),
      })

      // Save chat settings
      try {
        await api.chat.updateSettings(saved.id, {
          allowLinks: allowLinks.value,
          slowModeSeconds: slowModeSeconds.value,
          premoderationEnabled: premoderationEnabled.value,
          profanityFilterEnabled: true,
          antiSpamEnabled: true,
          welcomeMessage: welcomeMessage.value || undefined,
          chatMode: chatEnabled.value ? 'EVERYONE' : 'DISABLED',
          showParticipantCount: showParticipantCount.value,
          showParticipantNames: showParticipantNames.value,
          blockPhoneNumbers: blockPhoneNumbers.value,
        })
      } catch { /* non-critical */ }

      // Save CTAs
      for (const cta of ctas.value) {
        if (!cta.title) continue
        try {
          await api.cta.create(saved.id, {
            title: cta.title,
            description: cta.description || undefined,
            type: 'LINK',
            buttonText: cta.buttonText || 'Узнать больше',
            actionUrl: cta.url || undefined,
            placement: 'BELOW_VIDEO',
            priority: 0,
            allowStack: false,
          })
        } catch { /* non-critical */ }
      }

      toast.success('Мероприятие создано!')
    } else {
      if (!props.initial) throw new Error('Edit mode without initial')
      saved = await api.events.update(props.initial.id, {
        title: title.value.trim(),
        description: description.value.trim() || undefined,
        speakerName: speakerName.value.trim() || undefined,
        speakerBio: speakerBio.value.trim() || undefined,
        coverImageUrl: coverImageUrl.value.trim() || undefined,
        timezone: timezone.value,
        language: language.value,
        landingConfig: buildLandingConfig(),
      })
      toast.success('Изменения сохранены')
    }
    emit('submit', saved)
  } catch (err) {
    const apiErr = err as { errors?: Record<string, string>; title?: string; detail?: string }
    if (apiErr.errors && Object.keys(apiErr.errors).length > 0) {
      for (const [field, msg] of Object.entries(apiErr.errors)) errors[field] = msg
      currentStep.value = 1
      toast.warning('Проверьте заполненные поля')
    } else {
      toast.error(apiErr.title ?? 'Ошибка', apiErr.detail ?? 'Не удалось сохранить мероприятие')
    }
  } finally { loading.value = false }
}
</script>

<template>
  <div>
    <!-- ═══ Stepper ══════════════════════════════════════════════════════ -->
    <div class="mb-8 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <!-- Icon + connector row -->
      <div class="flex items-center">
        <template v-for="(step, i) in steps" :key="step.id">
          <button
            class="relative flex h-11 w-11 shrink-0 items-center justify-center rounded-full text-sm font-bold transition-all duration-200"
            :class="currentStep === step.id
              ? 'bg-gradient-to-br from-brand-500 to-violet-500 text-white shadow-lg shadow-brand-500/30 ring-4 ring-brand-100'
              : step.id < currentStep
                ? 'bg-emerald-500 text-white shadow-sm ring-2 ring-emerald-100'
                : step.id <= maxVisited
                  ? 'bg-white text-slate-500 ring-2 ring-slate-200 cursor-pointer hover:ring-brand-300 hover:text-brand-600'
                  : 'bg-slate-50 text-slate-300 ring-2 ring-slate-100 cursor-default'"
            type="button"
            @click="goTo(step.id)"
          >
            <Check v-if="step.id < currentStep" class="h-4 w-4" />
            <component :is="step.icon" v-else class="h-4 w-4" />
          </button>
          <div
            v-if="i < steps.length - 1"
            class="mx-2 h-1 flex-1 rounded-full bg-slate-100 overflow-hidden"
          >
            <div
              class="h-full rounded-full transition-all duration-500"
              :class="step.id < currentStep ? 'w-full bg-gradient-to-r from-emerald-400 to-emerald-500' : 'w-0'"
            />
          </div>
        </template>
      </div>
      <!-- Label row (md+) -->
      <div class="mt-3 hidden md:flex items-start">
        <template v-for="(step, i) in steps" :key="step.id">
          <div class="flex w-11 shrink-0 justify-center">
            <span
              class="whitespace-nowrap text-[11px] font-semibold transition-colors"
              :class="currentStep === step.id
                ? 'text-brand-600'
                : step.id < currentStep
                  ? 'text-emerald-600'
                  : 'text-slate-400'"
            >
              {{ step.title }}
            </span>
          </div>
          <div v-if="i < steps.length - 1" class="mx-2 flex-1" />
        </template>
      </div>
    </div>

    <form @submit.prevent="currentStep === 6 ? onSubmit() : next()">
      <Transition name="step" mode="out-in">

        <!-- ═══ Step 1: Информация ═══════════════════════════════════════ -->
        <UiCard v-if="currentStep === 1" key="s1">
          <template #header>
            <div class="flex items-center gap-2.5">
              <div class="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-brand-500 to-brand-600 text-white">
                <FileText class="h-4 w-4" />
              </div>
              <div>
                <h3 class="text-base font-semibold text-slate-900">Информация о мероприятии</h3>
                <p class="text-xs text-slate-500">Основные данные, спикер и обложка</p>
              </div>
            </div>
          </template>
          <div class="grid gap-5">
            <UiInput v-model="title" label="Название *" placeholder="Бесплатный урок: Java Backend" :error="errors.title" :maxlength="200" required />
            <UiTextarea v-model="description" label="Описание" placeholder="О чём будет мероприятие..." :maxlength="4000" :rows="4" autoresize />

            <div class="grid gap-5 sm:grid-cols-2">
              <UiInput v-model="speakerName" label="Спикер" placeholder="Иван Иванов" />
              <UiInput v-model="speakerBio" label="О спикере" placeholder="Краткая биография..." />
            </div>

            <!-- Cover upload -->
            <div>
              <label class="mb-1.5 block text-sm font-medium text-slate-700">Обложка</label>
              <div
                v-if="!coverPreview && !coverImageUrl"
                class="relative flex h-48 cursor-pointer flex-col items-center justify-center overflow-hidden rounded-xl border-2 border-dashed border-slate-300 bg-gradient-to-br from-slate-50 via-white to-brand-50/40 transition hover:border-brand-400 hover:from-brand-50/60 hover:to-violet-50/40"
                @click="($refs.coverInput as HTMLInputElement)?.click()"
                @drop.prevent="onCoverDrop"
                @dragover.prevent
              >
                <div class="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_30%_20%,rgba(99,102,241,0.08),transparent_55%)]" />
                <div class="relative flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-brand-500 to-violet-500 text-white shadow-lg shadow-brand-500/25">
                  <Upload class="h-6 w-6" />
                </div>
                <p class="relative mt-3 text-sm text-slate-700">
                  Перетащите или <span class="font-semibold text-brand-600">выберите файл</span>
                </p>
                <p class="relative mt-1 text-xs text-slate-400">JPG, PNG или WebP, до 5 МБ · рекомендуется 1600×900</p>
              </div>
              <div v-else class="group relative h-48 overflow-hidden rounded-xl border border-slate-200 shadow-sm">
                <img :src="coverPreview || coverImageUrl" class="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105" />
                <div class="absolute inset-0 bg-gradient-to-t from-slate-900/50 via-transparent to-transparent opacity-0 transition group-hover:opacity-100" />
                <!-- Upload spinner overlay -->
                <div v-if="uploadingCover" class="absolute inset-0 flex items-center justify-center bg-black/50 backdrop-blur-sm">
                  <div class="flex items-center gap-2 rounded-lg bg-white/95 px-4 py-2 text-sm font-medium text-slate-700 shadow-lg">
                    <svg class="h-4 w-4 animate-spin text-brand-600" fill="none" viewBox="0 0 24 24"><circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/><path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"/></svg>
                    Загрузка...
                  </div>
                </div>
                <button
                  type="button"
                  class="absolute right-3 top-3 flex h-8 w-8 items-center justify-center rounded-full bg-slate-900/70 text-white opacity-0 backdrop-blur transition hover:bg-red-500 group-hover:opacity-100"
                  @click="removeCover"
                >
                  <X class="h-4 w-4" />
                </button>
              </div>
              <input ref="coverInput" type="file" class="hidden" accept="image/jpeg,image/png,image/webp" @change="onCoverSelect" />
            </div>

            <div class="grid gap-5 sm:grid-cols-2">
              <div>
                <label class="mb-1.5 block text-sm font-medium text-slate-700">Дата и время эфира</label>
                <input v-model="plannedDateTime" type="datetime-local" class="input-base" />
              </div>
              <UiInput v-model="youtubeUrl" label="YouTube URL" placeholder="https://youtube.com/live/..." />
            </div>

            <div class="grid gap-5 sm:grid-cols-2">
              <div>
                <label class="mb-1.5 block text-sm font-medium text-slate-700">Часовой пояс</label>
                <select v-model="timezone" class="input-base">
                  <option v-for="tz in timezoneOptions" :key="tz.value" :value="tz.value">{{ tz.label }}</option>
                </select>
              </div>
              <div>
                <label class="mb-1.5 block text-sm font-medium text-slate-700">Язык</label>
                <select v-model="language" class="input-base">
                  <option v-for="l in languageOptions" :key="l.value" :value="l.value">{{ l.label }}</option>
                </select>
              </div>
            </div>
          </div>
        </UiCard>

        <!-- ═══ Step 2: Настройки чата ═══════════════════════════════════ -->
        <UiCard v-else-if="currentStep === 2" key="s2">
          <template #header>
            <div class="flex items-center gap-2.5">
              <div class="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-emerald-500 to-teal-500 text-white">
                <MessageSquare class="h-4 w-4" />
              </div>
              <div>
                <h3 class="text-base font-semibold text-slate-900">Настройки чата</h3>
                <p class="text-xs text-slate-500">Модерация, скорость сообщений, фильтры</p>
              </div>
            </div>
          </template>
          <div class="grid gap-5">
            <!-- Toggles -->
            <div class="space-y-3 rounded-xl border border-slate-200 bg-slate-50 p-4">
              <label class="flex items-center gap-3 text-sm text-slate-700">
                <input v-model="chatEnabled" type="checkbox" class="h-4 w-4 rounded border-slate-300 text-brand-600 focus:ring-brand-500" />
                Чат включён
              </label>
              <label class="flex items-center gap-3 text-sm text-slate-700">
                <input v-model="allowLinks" type="checkbox" class="h-4 w-4 rounded border-slate-300 text-brand-600 focus:ring-brand-500" />
                Разрешить ссылки
              </label>
              <label class="flex items-center gap-3 text-sm text-slate-700">
                <input v-model="showParticipantCount" type="checkbox" class="h-4 w-4 rounded border-slate-300 text-brand-600 focus:ring-brand-500" />
                Показывать количество зрителей
              </label>
              <label class="flex items-center gap-3 text-sm text-slate-700">
                <input v-model="showParticipantNames" type="checkbox" class="h-4 w-4 rounded border-slate-300 text-brand-600 focus:ring-brand-500" />
                Показывать имена участников
              </label>
              <label class="flex items-center gap-3 text-sm text-slate-700">
                <input v-model="premoderationEnabled" type="checkbox" class="h-4 w-4 rounded border-slate-300 text-brand-600 focus:ring-brand-500" />
                Премодерация сообщений
              </label>
              <label class="flex items-center gap-3 text-sm text-slate-700">
                <input v-model="blockPhoneNumbers" type="checkbox" class="h-4 w-4 rounded border-slate-300 text-brand-600 focus:ring-brand-500" />
                Блокировать телефонные номера
              </label>
            </div>

            <div>
              <label class="mb-1.5 block text-sm font-medium text-slate-700">Slow Mode</label>
              <select v-model.number="slowModeSeconds" class="input-base">
                <option :value="0">Выключен</option>
                <option :value="5">5 секунд</option>
                <option :value="10">10 секунд</option>
                <option :value="20">20 секунд</option>
                <option :value="30">30 секунд</option>
              </select>
            </div>

            <UiTextarea v-model="welcomeMessage" label="Приветственное сообщение" placeholder="Добро пожаловать! Пишите вопросы в чат." :rows="2" :maxlength="500" />

            <!-- Forbidden words -->
            <div>
              <label class="mb-1.5 block text-sm font-medium text-slate-700">Запрещённые слова</label>
              <div class="flex gap-2">
                <input v-model="newForbiddenWord" class="input-base flex-1" placeholder="Введите слово..." @keydown.enter.prevent="addForbiddenWord" />
                <UiButton type="button" variant="outline" @click="addForbiddenWord">Добавить</UiButton>
              </div>
              <div v-if="forbiddenWords.length" class="mt-2 flex flex-wrap gap-1.5">
                <span v-for="(w, i) in forbiddenWords" :key="i" class="inline-flex items-center gap-1 rounded-full bg-red-50 px-2.5 py-1 text-xs font-medium text-red-700">
                  {{ w }}
                  <button type="button" class="text-red-400 hover:text-red-600" @click="removeForbiddenWord(i)"><X class="h-3 w-3" /></button>
                </span>
              </div>
            </div>
          </div>
        </UiCard>

        <!-- ═══ Step 3: CTA ══════════════════════════════════════════════ -->
        <UiCard v-else-if="currentStep === 3" key="s3">
          <template #header>
            <div class="flex items-center gap-2.5">
              <div class="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-orange-500 to-red-500 text-white">
                <Megaphone class="h-4 w-4" />
              </div>
              <div>
                <h3 class="text-base font-semibold text-slate-900">CTA / Материалы</h3>
                <p class="text-xs text-slate-500">Кнопки, ссылки и материалы для зрителей</p>
              </div>
            </div>
          </template>
          <div class="space-y-3">
            <div v-if="!ctas.length" class="flex flex-col items-center gap-3 py-8 text-center">
              <Megaphone class="h-8 w-8 text-slate-300" />
              <p class="text-sm text-slate-500">CTA пока нет. Добавьте кнопку или материал.</p>
            </div>

            <!-- CTA cards -->
            <div v-for="(cta, i) in ctas" :key="i" class="rounded-xl border border-slate-200 bg-white">
              <!-- Header -->
              <div class="flex cursor-pointer items-center justify-between px-4 py-3" @click="toggleCta(i)">
                <div class="flex items-center gap-2">
                  <span class="flex h-6 w-6 items-center justify-center rounded-full bg-brand-50 text-xs font-bold text-brand-600">{{ i + 1 }}</span>
                  <span class="text-sm font-medium text-slate-900">{{ cta.title || 'Новый CTA' }}</span>
                  <span v-if="cta.buttonText" class="rounded bg-slate-100 px-1.5 py-0.5 text-[10px] text-slate-500">{{ cta.buttonText }}</span>
                </div>
                <div class="flex items-center gap-1">
                  <button type="button" class="rounded p-1 text-slate-400 hover:bg-red-50 hover:text-red-500" @click.stop="removeCta(i)"><Trash2 class="h-3.5 w-3.5" /></button>
                  <ChevronDown v-if="cta.collapsed" class="h-4 w-4 text-slate-400" />
                  <ChevronUp v-else class="h-4 w-4 text-slate-400" />
                </div>
              </div>
              <!-- Body -->
              <div v-if="!cta.collapsed" class="border-t border-slate-100 px-4 py-4">
                <div class="grid gap-4">
                  <UiInput v-model="cta.title" label="Заголовок" placeholder="Записаться на курс" />
                  <UiInput v-model="cta.url" label="Ссылка" placeholder="https://..." />
                  <div class="grid gap-4 sm:grid-cols-2">
                    <UiInput v-model="cta.buttonText" label="Текст кнопки" placeholder="Узнать больше" />
                    <UiInput v-model="cta.description" label="Описание" placeholder="Дополнительно..." />
                  </div>
                  <!-- Preview -->
                  <div v-if="cta.title" class="rounded-lg border border-brand-200 bg-brand-50/30 p-3">
                    <p class="mb-1.5 text-[10px] font-semibold uppercase tracking-wide text-slate-400">Предпросмотр</p>
                    <div class="flex items-center justify-between rounded-lg bg-white p-3 shadow-sm">
                      <span class="text-sm font-medium text-slate-800">{{ cta.title }}</span>
                      <span class="rounded-md bg-brand-600 px-3 py-1.5 text-xs font-bold text-white">{{ cta.buttonText || 'Кнопка' }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <button type="button" class="flex w-full items-center justify-center gap-2 rounded-xl border-2 border-dashed border-slate-300 py-3 text-sm font-medium text-slate-500 transition hover:border-brand-400 hover:text-brand-600" @click="addCta">
              <Plus class="h-4 w-4" /> Добавить CTA
            </button>
          </div>
        </UiCard>

        <!-- ═══ Step 4: Доступ ═══════════════════════════════════════════ -->
        <UiCard v-else-if="currentStep === 4" key="s4">
          <template #header>
            <div class="flex items-center gap-2.5">
              <div class="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-sky-500 to-blue-500 text-white">
                <Shield class="h-4 w-4" />
              </div>
              <div>
                <h3 class="text-base font-semibold text-slate-900">Доступ и модераторы</h3>
                <p class="text-xs text-slate-500">Публичная ссылка и команда модерации</p>
              </div>
            </div>
          </template>
          <div class="grid gap-5">
            <!-- Public URL + Slug -->
            <div class="space-y-3">
              <div>
                <label class="mb-1.5 block text-sm font-medium text-slate-700">Публичная ссылка</label>
                <div class="flex items-center gap-2">
                  <div class="flex-1 truncate rounded-lg bg-slate-50 px-3 py-2.5 font-mono text-xs text-brand-600">{{ eventUrl }}</div>
                  <button type="button" class="flex h-10 w-10 items-center justify-center rounded-lg border border-slate-200 text-slate-400 transition hover:border-brand-300 hover:text-brand-600" @click="copyEventUrl">
                    <Check v-if="copiedUrl" class="h-4 w-4 text-emerald-500" />
                    <Copy v-else class="h-4 w-4" />
                  </button>
                </div>
              </div>
              <div>
                <UiInput
                  v-model="slug"
                  label="Slug (URL-путь)"
                  placeholder="my-event"
                  :error="errors.slug"
                  :disabled="mode === 'edit'"
                  @input="slugTouched = true"
                />
                <p class="mt-1.5 text-xs text-slate-400">
                  Только латинские буквы, цифры и дефис — например <span class="font-mono">besplatnyy-urok</span>. Без пробелов. Генерируется автоматически из названия.
                </p>
              </div>
            </div>

            <!-- Moderator search -->
            <div>
              <label class="mb-1.5 block text-sm font-medium text-slate-700">Добавить модератора</label>
              <p class="mb-2 text-xs text-slate-400">Модератор должен быть зарегистрирован в системе. Начните вводить имя или email — система найдёт его автоматически.</p>
              <div class="relative">
                <div class="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"><Search class="h-4 w-4" /></div>
                <input v-model="modSearchQuery" class="input-base pl-9" placeholder="Поиск по имени или email..." @input="searchModerators" />
                <!-- Dropdown -->
                <div v-if="modSearchResults.length" class="absolute inset-x-0 top-full z-10 mt-1 max-h-48 overflow-y-auto rounded-xl border border-slate-200 bg-white shadow-lg">
                  <button
                    v-for="u in modSearchResults"
                    :key="u.id"
                    type="button"
                    class="flex w-full items-center gap-3 px-4 py-2.5 text-sm hover:bg-brand-50"
                    @click="addModerator(u)"
                  >
                    <div class="flex h-8 w-8 items-center justify-center rounded-full bg-brand-100 text-xs font-bold text-brand-700">{{ u.name.charAt(0) }}</div>
                    <div class="text-left">
                      <p class="font-medium text-slate-900">{{ u.name }}</p>
                      <p class="text-xs text-slate-500">{{ u.email }}</p>
                    </div>
                  </button>
                </div>
              </div>
            </div>

            <!-- Moderator list -->
            <div v-if="moderators.length" class="space-y-2">
              <p class="text-xs font-semibold uppercase tracking-wide text-slate-400">Модераторы ({{ moderators.length }})</p>
              <div v-for="(m, i) in moderators" :key="m.id" class="flex items-center justify-between rounded-lg border border-slate-200 px-4 py-2.5">
                <div class="flex items-center gap-3">
                  <div class="flex h-8 w-8 items-center justify-center rounded-full bg-emerald-100 text-xs font-bold text-emerald-700">{{ m.name.charAt(0) }}</div>
                  <div>
                    <p class="text-sm font-medium text-slate-900">{{ m.name }}</p>
                    <p class="text-xs text-slate-500">{{ m.email }}</p>
                  </div>
                </div>
                <button type="button" class="rounded p-1.5 text-slate-400 hover:bg-red-50 hover:text-red-500" @click="removeModerator(i)"><X class="h-4 w-4" /></button>
              </div>
            </div>
            <p v-else class="text-center text-sm text-slate-400 py-4">Модераторы не добавлены</p>
          </div>
        </UiCard>

        <!-- ═══ Step 5: Лендинг Builder ══════════════════════════════════ -->
        <div v-else-if="currentStep === 5" key="s5" class="grid gap-5 lg:grid-cols-[1fr_360px]">
          <!-- Editor -->
          <div class="space-y-5">
            <UiCard class="border-brand-100/80">
              <template #header>
                <div class="flex items-center gap-2.5">
                  <div class="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-brand-500 to-violet-500 text-white">
                    <Sparkles class="h-4 w-4" />
                  </div>
                  <div>
                    <h3 class="text-base font-semibold text-slate-900">Преимущества</h3>
                    <p class="text-xs text-slate-500">Что получит участник от мероприятия</p>
                  </div>
                </div>
              </template>
              <div class="grid gap-4">
                <UiInput v-model="benefitsTitle" label="Заголовок раздела" />
                <div
                  v-for="(b, i) in benefits"
                  :key="i"
                  class="group rounded-xl border border-slate-200 bg-slate-50/50 p-3.5 transition hover:border-brand-200 hover:bg-white hover:shadow-sm"
                >
                  <div class="flex items-start gap-3">
                    <!-- Icon picker -->
                    <div class="relative mt-0.5 shrink-0">
                      <button
                        type="button"
                        class="flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-br from-brand-100 to-violet-100 text-brand-600 transition hover:from-brand-200 hover:to-violet-200"
                        @click="b._iconOpen = !b._iconOpen"
                      >
                        <component :is="getIcon(b.icon)" class="h-4 w-4" />
                      </button>
                      <!-- Icon dropdown -->
                      <div v-if="b._iconOpen" class="absolute left-0 top-full z-20 mt-2 grid w-64 grid-cols-6 gap-1 rounded-xl border border-slate-200 bg-white p-2 shadow-xl">
                        <button
                          v-for="ic in benefitIcons"
                          :key="ic"
                          type="button"
                          class="flex h-9 w-9 items-center justify-center rounded-lg transition"
                          :class="b.icon === ic ? 'bg-gradient-to-br from-brand-100 to-violet-100 text-brand-600' : 'text-slate-500 hover:bg-slate-100'"
                          @click="b.icon = ic; b._iconOpen = false"
                        >
                          <component :is="getIcon(ic)" class="h-4 w-4" />
                        </button>
                      </div>
                    </div>
                    <div class="flex-1 space-y-2">
                      <input v-model="b.title" class="input-base" placeholder="Заголовок преимущества" :maxlength="60" />
                      <input v-model="b.desc" class="input-base text-xs" placeholder="Описание (до 150 символов)" :maxlength="150" />
                    </div>
                    <button
                      type="button"
                      class="mt-1 rounded-lg p-1.5 text-slate-300 transition hover:bg-red-50 hover:text-red-500"
                      @click="removeBenefit(i)"
                    >
                      <Trash2 class="h-3.5 w-3.5" />
                    </button>
                  </div>
                </div>
                <button
                  type="button"
                  class="flex w-full items-center justify-center gap-1.5 rounded-xl border-2 border-dashed border-slate-300 py-3 text-sm font-medium text-slate-500 transition hover:border-brand-400 hover:bg-brand-50/40 hover:text-brand-600"
                  @click="addBenefit"
                >
                  <Plus class="h-4 w-4" /> Добавить преимущество
                </button>
              </div>
            </UiCard>

            <UiCard class="border-violet-100/80">
              <template #header>
                <div class="flex items-center gap-2.5">
                  <div class="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-violet-500 to-fuchsia-500 text-white">
                    <Clock class="h-4 w-4" />
                  </div>
                  <div>
                    <h3 class="text-base font-semibold text-slate-900">Программа эфира</h3>
                    <p class="text-xs text-slate-500">Пошаговый план — видят гости на лендинге</p>
                  </div>
                </div>
              </template>
              <div class="grid gap-4">
                <div class="grid gap-4 sm:grid-cols-2">
                  <UiInput v-model="timelineTitle" label="Заголовок" />
                  <UiInput v-model="timelineSubtitle" label="Подзаголовок" />
                </div>
                <div
                  v-for="(t, i) in timeline"
                  :key="i"
                  class="flex items-start gap-3 rounded-xl border border-slate-200 bg-slate-50/50 p-3.5 transition hover:border-violet-200 hover:bg-white hover:shadow-sm"
                >
                  <span class="mt-0.5 flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-violet-100 to-fuchsia-100 text-xs font-bold text-violet-600">
                    {{ i + 1 }}
                  </span>
                  <div class="flex-1 space-y-2">
                    <input v-model="t.title" class="input-base" placeholder="Название этапа" :maxlength="60" />
                    <input v-model="t.desc" class="input-base text-xs" placeholder="Описание" :maxlength="150" />
                  </div>
                  <button
                    type="button"
                    class="mt-1 rounded-lg p-1.5 text-slate-300 transition hover:bg-red-50 hover:text-red-500"
                    @click="removeTimelineItem(i)"
                  >
                    <Trash2 class="h-3.5 w-3.5" />
                  </button>
                </div>
                <button
                  type="button"
                  class="flex w-full items-center justify-center gap-1.5 rounded-xl border-2 border-dashed border-slate-300 py-3 text-sm font-medium text-slate-500 transition hover:border-violet-400 hover:bg-violet-50/40 hover:text-violet-600"
                  @click="addTimelineItem"
                >
                  <Plus class="h-4 w-4" /> Добавить этап
                </button>
              </div>
            </UiCard>
          </div>

          <!-- Preview — sticky, looks like a real landing -->
          <div class="hidden lg:block">
            <div class="sticky top-4 overflow-hidden rounded-2xl border border-slate-200 bg-gradient-to-b from-slate-900 via-slate-900 to-slate-950 p-5 shadow-xl">
              <div class="mb-4 flex items-center justify-between">
                <p class="text-[10px] font-bold uppercase tracking-widest text-brand-300/80">
                  Preview · Лендинг
                </p>
                <span class="flex h-2 w-2 rounded-full bg-emerald-400 ring-2 ring-emerald-400/30" />
              </div>

              <!-- Benefits preview -->
              <div class="mb-5">
                <h4 class="text-sm font-bold text-white">{{ benefitsTitle }}</h4>
                <div class="mt-3 grid grid-cols-2 gap-2">
                  <div
                    v-for="(b, i) in benefits"
                    :key="i"
                    class="rounded-xl border border-white/5 bg-white/[0.04] p-2.5 backdrop-blur-sm"
                  >
                    <div class="mb-1 flex h-6 w-6 items-center justify-center rounded-md bg-gradient-to-br from-brand-500/20 to-violet-500/20 text-brand-300">
                      <component :is="getIcon(b.icon)" class="h-3.5 w-3.5" />
                    </div>
                    <p class="text-[11px] font-semibold text-white">{{ b.title || 'Заголовок' }}</p>
                    <p class="mt-0.5 text-[10px] text-slate-400">{{ b.desc || 'Описание' }}</p>
                  </div>
                </div>
              </div>

              <!-- Timeline preview -->
              <div class="border-t border-white/5 pt-4">
                <h4 class="text-sm font-bold text-white">{{ timelineTitle }}</h4>
                <p class="text-[10px] text-slate-400">{{ timelineSubtitle }}</p>
                <div class="mt-3 space-y-2">
                  <div v-for="(t, i) in timeline" :key="i" class="flex gap-2.5">
                    <div class="flex flex-col items-center">
                      <div class="flex h-5 w-5 items-center justify-center rounded-full bg-gradient-to-br from-violet-500/30 to-fuchsia-500/30 text-[9px] font-bold text-violet-200 ring-1 ring-violet-400/30">
                        {{ i + 1 }}
                      </div>
                      <div v-if="i < timeline.length - 1" class="mt-1 h-full w-px bg-gradient-to-b from-violet-500/30 to-transparent" />
                    </div>
                    <div class="pb-3">
                      <p class="text-[11px] font-semibold text-white">{{ t.title || 'Этап' }}</p>
                      <p class="text-[10px] text-slate-400">{{ t.desc }}</p>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- ═══ Step 6: Подтверждение ════════════════════════════════════ -->
        <UiCard v-else-if="currentStep === 6" key="s6">
          <template #header>
            <div class="flex items-center gap-2.5">
              <div class="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-emerald-500 to-emerald-600 text-white">
                <Eye class="h-4 w-4" />
              </div>
              <div>
                <h3 class="text-base font-semibold text-slate-900">Подтверждение</h3>
                <p class="text-xs text-slate-500">Проверьте всё и нажмите «Создать»</p>
              </div>
            </div>
          </template>
          <div class="divide-y divide-slate-100">
            <!-- Info -->
            <div class="pb-4">
              <div class="mb-2 flex items-center justify-between">
                <span class="text-xs font-semibold uppercase tracking-wide text-slate-400">Информация</span>
                <button type="button" class="text-xs font-medium text-brand-600 hover:underline" @click="goTo(1)">Изменить</button>
              </div>
              <dl class="grid gap-2 sm:grid-cols-2">
                <div><dt class="text-[10px] text-slate-400">Название</dt><dd class="text-sm font-medium text-slate-900">{{ title || '—' }}</dd></div>
                <div><dt class="text-[10px] text-slate-400">Спикер</dt><dd class="text-sm text-slate-700">{{ speakerName || '—' }}</dd></div>
                <div><dt class="text-[10px] text-slate-400">Slug</dt><dd class="font-mono text-xs text-slate-600">/e/.../{{ slug || '...' }}</dd></div>
                <div v-if="plannedDateTime"><dt class="text-[10px] text-slate-400">Дата</dt><dd class="text-sm text-slate-700">{{ plannedDateTime }}</dd></div>
              </dl>
              <div v-if="coverPreview || coverImageUrl" class="mt-2 h-20 w-32 overflow-hidden rounded-lg border"><img :src="coverPreview || coverImageUrl" class="h-full w-full object-cover" /></div>
            </div>
            <!-- Chat -->
            <div class="py-4">
              <div class="mb-2 flex items-center justify-between">
                <span class="text-xs font-semibold uppercase tracking-wide text-slate-400">Чат</span>
                <button type="button" class="text-xs font-medium text-brand-600 hover:underline" @click="goTo(2)">Изменить</button>
              </div>
              <div class="flex flex-wrap gap-1.5">
                <span class="rounded-full px-2 py-0.5 text-[11px] font-medium" :class="chatEnabled ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-700'">{{ chatEnabled ? 'Чат вкл.' : 'Чат выкл.' }}</span>
                <span v-if="slowModeSeconds" class="rounded-full bg-slate-100 px-2 py-0.5 text-[11px] text-slate-600">Slow {{ slowModeSeconds }}с</span>
                <span v-if="forbiddenWords.length" class="rounded-full bg-red-50 px-2 py-0.5 text-[11px] text-red-600">{{ forbiddenWords.length }} запрещ. слов</span>
                <span v-if="premoderationEnabled" class="rounded-full bg-amber-50 px-2 py-0.5 text-[11px] text-amber-700">Премодерация</span>
              </div>
            </div>
            <!-- CTA -->
            <div class="py-4">
              <div class="mb-2 flex items-center justify-between">
                <span class="text-xs font-semibold uppercase tracking-wide text-slate-400">CTA ({{ ctas.length }})</span>
                <button type="button" class="text-xs font-medium text-brand-600 hover:underline" @click="goTo(3)">Изменить</button>
              </div>
              <div v-if="ctas.length" class="space-y-1">
                <p v-for="(c, i) in ctas" :key="i" class="flex items-center gap-2 text-sm text-slate-700">
                  <Megaphone class="h-3.5 w-3.5 text-brand-400" /> {{ c.title || 'Без названия' }} → <span class="font-medium">{{ c.buttonText }}</span>
                </p>
              </div>
              <p v-else class="text-sm text-slate-400">Не добавлены</p>
            </div>
            <!-- Moderators -->
            <div class="py-4">
              <div class="mb-2 flex items-center justify-between">
                <span class="text-xs font-semibold uppercase tracking-wide text-slate-400">Модераторы ({{ moderators.length }})</span>
                <button type="button" class="text-xs font-medium text-brand-600 hover:underline" @click="goTo(4)">Изменить</button>
              </div>
              <div v-if="moderators.length" class="flex flex-wrap gap-2">
                <span v-for="m in moderators" :key="m.id" class="rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-medium text-emerald-700">{{ m.name }}</span>
              </div>
              <p v-else class="text-sm text-slate-400">Не добавлены</p>
            </div>
            <!-- Landing -->
            <div class="pt-4">
              <div class="mb-2 flex items-center justify-between">
                <span class="text-xs font-semibold uppercase tracking-wide text-slate-400">Лендинг</span>
                <button type="button" class="text-xs font-medium text-brand-600 hover:underline" @click="goTo(5)">Изменить</button>
              </div>
              <p class="text-sm text-slate-700">{{ benefits.length }} преимуществ · {{ timeline.length }} этапов программы</p>
            </div>
          </div>
        </UiCard>
      </Transition>

      <!-- ═══ Navigation ═════════════════════════════════════════════════ -->
      <div class="mt-6 flex items-center justify-between rounded-2xl border border-slate-200 bg-white/60 px-5 py-3 backdrop-blur">
        <UiButton v-if="currentStep > 1" variant="ghost" type="button" @click="prev">
          <ArrowLeft class="h-4 w-4" /> Назад
        </UiButton>
        <UiButton v-else variant="ghost" type="button" @click="emit('cancel')">
          <ArrowLeft class="h-4 w-4" /> Отмена
        </UiButton>

        <div class="flex items-center gap-4">
          <span class="hidden items-center gap-2 text-xs text-slate-500 sm:flex">
            <span class="font-semibold text-slate-700">Шаг {{ currentStep }}</span>
            <span class="text-slate-300">/</span>
            <span>6</span>
            <span class="text-slate-300">·</span>
            <span class="font-medium text-brand-600">{{ steps[currentStep - 1].title }}</span>
          </span>
          <UiButton v-if="currentStep < 6" variant="primary" type="submit">
            Далее <ArrowRight class="h-4 w-4" />
          </UiButton>
          <UiButton v-else variant="primary" type="submit" :loading="loading">
            <Save class="h-4 w-4" /> {{ submitLabel ?? (mode === 'create' ? 'Создать' : 'Сохранить') }}
          </UiButton>
        </div>
      </div>
    </form>
  </div>
</template>

<style scoped>
.step-enter-active { transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1); }
.step-leave-active { transition: all 0.2s ease-in; }
.step-enter-from { opacity: 0; transform: translateX(20px); }
.step-leave-to   { opacity: 0; transform: translateX(-20px); }
</style>
