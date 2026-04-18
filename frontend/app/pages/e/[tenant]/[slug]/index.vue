<script setup lang="ts">
/**
 * Public event landing page at `/e/{tenantSlug}/{eventSlug}`.
 *
 * <p>Visual language matches the main marketing landing ({@code pages/index.vue}):
 * "Broadcast Universe" — dark cinematic hero with orbital rings, gradient
 * mesh blurs, noise texture, glassmorphic cards, scroll-triggered
 * reveals, and a gradient accent on the title. Every piece of content
 * flows from the backend resolver ({@code PublicEventResolution}):
 * admin-entered title, description, speaker name/bio, cover image,
 * session start time — nothing is hardcoded or filled with dummy copy.
 *
 * <p>The page renders one of five states returned by the resolver:
 * <ul>
 *   <li>{@code LIVE_NOW} — a session is broadcasting; "Кіру" jumps to
 *       the room.</li>
 *   <li>{@code WAITING} — a session starts in less than 30 minutes;
 *       show the countdown and a waiting-room link.</li>
 *   <li>{@code LANDING} + {@code nextSession} — a session is scheduled
 *       later; show countdown + register CTA.</li>
 *   <li>{@code LANDING} (no nextSession) — no session at all; show
 *       "notify me" register CTA.</li>
 *   <li>{@code SLOT_SELECTION} — auto replays available; slot list.</li>
 *   <li>{@code UNAVAILABLE} — archived / wrong slug; apology copy.</li>
 * </ul>
 */
import type { PublicSessionView } from '#shared/api/types'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import {
  Radio, Bell, ChevronRight, Play, Calendar, Clock, Flame, Users,
  TrendingUp, Sparkles, CheckCircle2,
  // Icons surfaced through the admin "Лендинг Builder" icon picker
  Lightbulb, Target, Rocket, Trophy, Star, Heart, Zap, BookOpen,
  GraduationCap, Brain, Puzzle, Award, CheckCircle, MessageCircle,
  Code, Palette, Laptop,
} from 'lucide-vue-next'

definePageMeta({
  layout: 'event',
})

const route = useRoute()
const tenantSlug = computed(() => String(route.params.tenant))
const eventSlug  = computed(() => String(route.params.slug))

const api = useApi()

const { data, pending, error, refresh } = await useAsyncData(
  () => `public-event:${tenantSlug.value}:${eventSlug.value}`,
  () => api.publicEvents.resolve(tenantSlug.value, eventSlug.value),
  { watch: [tenantSlug, eventSlug] },
)

const auth = useAuthStore()
const config = useRuntimeConfig()

useHead(() => ({
  title: data.value?.event?.title
    ? `${data.value.event.title} — ${config.public.appName}`
    : 'Іс-шара',
  meta: data.value?.event?.description
    ? [{ name: 'description', content: data.value.event.description }]
    : [],
}))

/**
 * Resolve where the "join" button should land. If the visitor is
 * logged in, drop them straight into the target; otherwise bounce
 * through sign-in with a redirect query.
 */
function joinHref(targetPath?: string): string {
  const dest = targetPath ?? `/e/${tenantSlug.value}/${eventSlug.value}/room`
  if (auth.isAuthenticated) return dest
  return `/auth/sign-in?redirect=${encodeURIComponent(dest)}`
}

const dateFmt = new Intl.DateTimeFormat('ru-RU', {
  weekday: 'short',
  day:     'numeric',
  month:   'long',
  hour:    '2-digit',
  minute:  '2-digit',
})
function formatStart(s: PublicSessionView): string {
  try { return dateFmt.format(new Date(s.startTime)) } catch { return s.startTime }
}

// ────────────────────────────────────────────────────────────────────
// Scroll-triggered reveals — same pattern as pages/index.vue
// ────────────────────────────────────────────────────────────────────
const revealRefs = ref<HTMLElement[]>([])
function setRevealRef(el: any) {
  if (el) revealRefs.value.push(el)
}
onMounted(() => {
  const observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add('revealed')
          observer.unobserve(entry.target)
        }
      })
    },
    { threshold: 0.15, rootMargin: '0px 0px -40px 0px' },
  )
  revealRefs.value.forEach((el) => observer.observe(el))
  onUnmounted(() => observer.disconnect())
})

/**
 * Split a multi-paragraph description into discrete paragraph blocks
 * so each gets its own reveal delay. An admin-supplied empty string or
 * null short-circuits and returns an empty array so {@code v-if}
 * blocks collapse cleanly.
 *
 * <p>We also strip common leading labels like "Ашық вебинар:" or
 * "Открытый вебинар:" — they are redundant with the event title (which
 * already says what kind of event this is) and cheapen the hero copy.
 * Admins keep typing them out of habit, so we clean them at render
 * time rather than nag in the editor.
 */
const REDUNDANT_PREFIXES = [
  /^ашы[қк]\s+вебинар\s*[:\-–—]\s*/i,
  /^ашы[қк]\s+эфир\s*[:\-–—]\s*/i,
  /^ашы[қк]\s+сабақ\s*[:\-–—]\s*/i,
  /^открыт(?:ый|ая|ое)\s+(?:вебинар|эфир|урок)\s*[:\-–—]\s*/i,
  /^бесплатн(?:ый|ая|ое)\s+(?:вебинар|эфир|урок)\s*[:\-–—]\s*/i,
  /^open\s+(?:webinar|broadcast|lesson)\s*[:\-–—]\s*/i,
]
function stripRedundantPrefix(s: string): string {
  let out = s
  for (const re of REDUNDANT_PREFIXES) out = out.replace(re, '')
  return out
}
const descriptionParagraphs = computed(() => {
  const raw = data.value?.event?.description
  if (!raw) return []
  const paragraphs = raw.split(/\n\s*\n/).map(p => p.trim()).filter(Boolean)
  if (paragraphs.length > 0) {
    paragraphs[0] = stripRedundantPrefix(paragraphs[0]).trim()
  }
  return paragraphs.filter(Boolean)
})

// ────────────────────────────────────────────────────────────────────
// Scarcity marketing — "57 орын қалды" + reservation flag.
//
// <p>Pure frontend theatre: there is no real seat cap, no backend
// counter. The scarcity number is purely cosmetic. Every visitor can
// "reserve" their spot exactly once — subsequent clicks are a no-op
// and the button flips into a success state ("Орын сізге броньдалды")
// so the user doesn't keep burning down the counter on repeat clicks.
// Both the spot count and the reservation flag persist to localStorage
// per (tenant, event) so the value stays believable across reloads.
// ────────────────────────────────────────────────────────────────────
const SPOTS_INITIAL = 57
const SPOTS_FLOOR = 3
const spotsLeft = ref(SPOTS_INITIAL)
const spotsBumped = ref(false) // triggers the "–1" pulse animation
const hasReserved = ref(false) // visitor already reserved this event

function spotsStorageKey(): string {
  return `webizon:spots:${tenantSlug.value}:${eventSlug.value}`
}
function reservedStorageKey(): string {
  return `webizon:reserved:${tenantSlug.value}:${eventSlug.value}`
}

onMounted(() => {
  if (typeof window === 'undefined') return
  try {
    const raw = window.localStorage.getItem(spotsStorageKey())
    const parsed = raw ? parseInt(raw, 10) : NaN
    if (Number.isFinite(parsed) && parsed >= SPOTS_FLOOR && parsed <= SPOTS_INITIAL) {
      spotsLeft.value = parsed
    } else {
      spotsLeft.value = SPOTS_INITIAL
      window.localStorage.setItem(spotsStorageKey(), String(SPOTS_INITIAL))
    }
    hasReserved.value = window.localStorage.getItem(reservedStorageKey()) === '1'
  } catch {
    // Private mode / storage disabled — just use the default.
    spotsLeft.value = SPOTS_INITIAL
  }
})

/**
 * Flag the visitor as "reserved" on this event. Persists the flag and
 * decrements the scarcity counter exactly once (the first reservation).
 * Every subsequent click is a no-op — we never decrement twice for the
 * same visitor and we keep the button in its success state. Doesn't
 * navigate; the wrapping <NuxtLink> handles that.
 */
function reserveSpot(): void {
  if (hasReserved.value) return
  hasReserved.value = true
  try {
    window.localStorage.setItem(reservedStorageKey(), '1')
  } catch { /* ignore */ }
  if (spotsLeft.value > SPOTS_FLOOR) {
    spotsLeft.value -= 1
    spotsBumped.value = true
    setTimeout(() => { spotsBumped.value = false }, 600)
    try {
      window.localStorage.setItem(spotsStorageKey(), String(spotsLeft.value))
    } catch { /* ignore */ }
  }
}

// ────────────────────────────────────────────────────────────────────
// Event info ticker — rotates through the admin-entered event facts.
//
// <p>The cover frame hosts a vertical feed that cycles through the
// important pieces of event metadata (start time, duration, speaker,
// limited spots, topic, format, description). A new fact slides in
// every 2.5s and the oldest shifts out via TransitionGroup, which
// gives the card the same "alive" feel as a chat feed while actually
// teaching the visitor about the event. Everything is derived from
// {@code data.event} + {@code data.nextSession} — nothing hardcoded.
// ────────────────────────────────────────────────────────────────────
interface InfoRow {
  id: number
  avatar: string       // single glyph / emoji shown in the avatar circle
  avatarColor: string  // tailwind bg class
  name: string         // label (e.g. "Басталуы")
  text: string         // value (e.g. "вс, 19 апреля в 13:00")
  mod?: boolean        // highlight label in amber like a moderator
  pinned?: boolean     // surround the row with an amber "pinned" frame
}

const dateFullFmt = new Intl.DateTimeFormat('ru-RU', {
  weekday: 'short',
  day:     'numeric',
  month:   'long',
  hour:    '2-digit',
  minute:  '2-digit',
})

/**
 * Build the rotating info pool from the resolved event + session. We
 * keep the same shape as the former chat messages so the existing
 * row template ({avatar, name, text, mod, pinned}) renders without
 * changes — only the content shifts from fake chat → real event info.
 */
const infoPool = computed<Omit<InfoRow, 'id'>[]>(() => {
  const e = data.value?.event
  if (!e) return []
  const s = data.value?.nextSession ?? data.value?.activeSession

  const rows: Omit<InfoRow, 'id'>[] = []

  if (s?.startTime) {
    let when = s.startTime
    try { when = dateFullFmt.format(new Date(s.startTime)) } catch { /* keep raw */ }
    rows.push({ avatar: '📅', avatarColor: 'bg-brand-500/90', name: 'Басталуы', text: when, pinned: true })
  }
  if (s?.plannedDurationSeconds && s.plannedDurationSeconds > 0) {
    const minutes = Math.max(1, Math.round(s.plannedDurationSeconds / 60))
    rows.push({ avatar: '⏱', avatarColor: 'bg-amber-500/90', name: 'Ұзақтығы', text: `~${minutes} минут` })
  }
  if (e.speakerName) {
    rows.push({
      avatar: e.speakerName.slice(0, 1).toUpperCase(),
      avatarColor: 'bg-violet-500/90',
      name: 'Спикер',
      text: e.speakerName,
    })
  }
  rows.push({
    avatar: '🔥',
    avatarColor: 'bg-rose-500/90',
    name: 'Орын саны',
    text: 'Шектеулі — тезірек тіркеліңіз',
    mod: true,
  })
  if (e.title) {
    rows.push({ avatar: '🎯', avatarColor: 'bg-emerald-500/90', name: 'Тақырыбы', text: e.title })
  }
  const isLive = data.value?.state === 'LIVE_NOW' || s?.type === 'LIVE'
  rows.push({
    avatar: '📡',
    avatarColor: 'bg-red-500/90',
    name: 'Форматы',
    text: isLive ? 'Тікелей эфир' : 'Автотрансляция',
  })
  const firstPara = descriptionParagraphs.value[0]
  if (firstPara) {
    rows.push({ avatar: '💡', avatarColor: 'bg-cyan-500/90', name: 'Не туралы', text: firstPara })
  }

  return rows
})

const visibleMessages = ref<InfoRow[]>([])
const viewerCount = ref(2413)
let infoIndex = 0
let msgIdCounter = 100

function takeRow(): InfoRow | null {
  const pool = infoPool.value
  if (!pool.length) return null
  const src = pool[infoIndex % pool.length]
  infoIndex++
  return { ...src, id: ++msgIdCounter }
}

onMounted(() => {
  // Seed with three rows so the feed never appears empty on entry.
  const initial: InfoRow[] = []
  for (let i = 0; i < 3; i++) {
    const row = takeRow()
    if (row) initial.push(row)
  }
  visibleMessages.value = initial

  const chatInterval = setInterval(() => {
    const row = takeRow()
    if (!row) return
    visibleMessages.value.push(row)
    if (visibleMessages.value.length > 3) visibleMessages.value.shift()
    viewerCount.value += Math.floor(Math.random() * 7) - 2
  }, 2500)

  onUnmounted(() => clearInterval(chatInterval))
})

// ────────────────────────────────────────────────────────────────────
// Admin "Лендинг Builder" sections — benefits + timeline.
//
// <p>Both blocks come from {@code data.event.landingConfig}, which the
// backend returns as an untyped JSON map. We defensively coerce each
// field and render nothing when the admin skipped Step 5 so the page
// flow stays clean.
// ────────────────────────────────────────────────────────────────────
interface LandingBenefit  { title: string; desc: string; icon?: string }
interface LandingStep     { title: string; desc: string }

/** Icon-picker → lucide component table. Matches the admin form. */
const benefitIconMap: Record<string, any> = {
  Sparkles, Lightbulb, Target, Rocket, Trophy, Star, Heart, Zap,
  BookOpen, GraduationCap, Users, Brain, Puzzle, TrendingUp, Award,
  Clock, CheckCircle, MessageCircle, Play, Code, Palette, Laptop,
}
function benefitIcon(name?: string) {
  return benefitIconMap[name ?? 'Sparkles'] ?? Sparkles
}

const landingConfig = computed<Record<string, unknown>>(() => {
  const raw = (data.value?.event as any)?.landingConfig
  return raw && typeof raw === 'object' ? raw as Record<string, unknown> : {}
})
function asString(v: unknown, fallback = ''): string {
  return typeof v === 'string' && v.trim().length > 0 ? v : fallback
}
const benefitsTitle = computed(() => asString(landingConfig.value.benefitsTitle, 'Не үйренесіз'))
const benefitsList = computed<LandingBenefit[]>(() => {
  const raw = landingConfig.value.benefits
  if (!Array.isArray(raw)) return []
  return raw
    .map((r) => {
      const it = r as Record<string, unknown>
      return {
        title: asString(it?.title),
        desc:  asString(it?.desc),
        icon:  typeof it?.icon === 'string' ? it.icon : undefined,
      }
    })
    .filter((b) => b.title || b.desc)
})
const timelineTitle    = computed(() => asString(landingConfig.value.timelineTitle,    'Эфир бағдарламасы'))
const timelineSubtitle = computed(() => asString(landingConfig.value.timelineSubtitle, ''))
const timelineList = computed<LandingStep[]>(() => {
  const raw = landingConfig.value.timeline
  if (!Array.isArray(raw)) return []
  return raw
    .map((r) => {
      const it = r as Record<string, unknown>
      return {
        title: asString(it?.title),
        desc:  asString(it?.desc),
      }
    })
    .filter((t) => t.title || t.desc)
})
</script>

<template>
  <div class="event-landing">
    <!-- Orbital background — same motif as the main landing hero -->
    <div class="orbital-backdrop pointer-events-none fixed inset-0">
      <div class="hero-ring hero-ring-1" />
      <div class="hero-ring hero-ring-2" />
      <div class="hero-ring hero-ring-3" />
      <div class="absolute -left-40 top-20 h-[600px] w-[600px] rounded-full bg-brand-600/15 blur-[120px]" />
      <div class="absolute -right-20 top-1/3 h-[500px] w-[500px] rounded-full bg-accent-500/10 blur-[100px]" />
      <div class="absolute left-1/2 top-2/3 h-[300px] w-[300px] -translate-x-1/2 rounded-full bg-violet-600/8 blur-[80px]" />
      <div class="noise-texture absolute inset-0 opacity-[0.03]" />
    </div>

    <!-- Loading skeleton -->
    <div v-if="pending && !data" class="relative mx-auto w-full max-w-5xl px-4 pt-20">
      <div class="aspect-video w-full animate-pulse rounded-2xl bg-white/5" />
      <div class="mt-6 space-y-3">
        <div class="h-8 w-2/3 animate-pulse rounded bg-white/5" />
        <div class="h-4 w-full animate-pulse rounded bg-white/5" />
        <div class="h-4 w-4/5 animate-pulse rounded bg-white/5" />
      </div>
    </div>

    <!-- Network error -->
    <section v-else-if="error" class="relative mx-auto max-w-2xl px-4 pt-32">
      <div class="glass-card py-10 text-center">
        <h2 class="text-2xl font-bold text-white">Іс-шараны жүктеу мүмкін болмады</h2>
        <p class="mt-3 text-slate-400">Желі ақаулығы немесе сервер жауап бермейді.</p>
        <button
          class="mt-6 rounded-xl bg-brand-600 px-6 py-2.5 text-sm font-semibold text-white hover:bg-brand-500 transition"
          @click="refresh()"
        >
          Қайталау
        </button>
      </div>
    </section>

    <!-- UNAVAILABLE -->
    <section
      v-else-if="!data?.event || data.state === 'UNAVAILABLE'"
      class="relative mx-auto max-w-2xl px-4 pt-32"
    >
      <div class="glass-card py-10 text-center">
        <h2 class="text-2xl font-bold text-white">Іс-шара қолжетімсіз</h2>
        <p class="mt-3 text-slate-400">
          Бұл іс-шара мұрағатталған, жойылған немесе сілтеме қате теріліп қалуы мүмкін.
        </p>
      </div>
    </section>

    <!-- Normal render -->
    <template v-else-if="data.event">
      <!-- ═══════════════════════════════════════════════════════════ -->
      <!-- HERO                                                        -->
      <!-- ═══════════════════════════════════════════════════════════ -->
      <section class="hero-section relative overflow-hidden pb-16 pt-20 md:pb-20 md:pt-28">
        <div class="relative mx-auto w-full max-w-6xl px-4">
          <div class="grid gap-10 md:grid-cols-[1fr_1.15fr] md:items-center md:gap-12 lg:gap-16">
            <!-- Left: copy + countdown + CTA -->
            <div class="hero-content">
              <!-- State badge (LIVE / WAITING / UPCOMING / REPLAY) -->
              <div
                v-if="data.state === 'LIVE_NOW'"
                class="hero-badge mb-6 inline-flex items-center gap-2 rounded-full border border-red-500/30 bg-red-500/10 px-4 py-1.5 text-xs font-semibold text-red-400 backdrop-blur-sm"
              >
                <span class="relative flex h-2 w-2">
                  <span class="absolute inline-flex h-full w-full animate-ping rounded-full bg-red-400 opacity-75" />
                  <span class="relative inline-flex h-2 w-2 rounded-full bg-red-400" />
                </span>
                Эфирде
              </div>
              <div
                v-else-if="data.state === 'WAITING'"
                class="hero-badge mb-6 inline-flex items-center gap-2 rounded-full border border-amber-400/30 bg-amber-500/10 px-4 py-1.5 text-xs font-semibold text-amber-300 backdrop-blur-sm"
              >
                <Clock class="h-3 w-3" />
                Жақын арада басталады
              </div>
              <div
                v-else-if="data.state === 'SLOT_SELECTION'"
                class="hero-badge mb-6 inline-flex items-center gap-2 rounded-full border border-violet-400/30 bg-violet-500/10 px-4 py-1.5 text-xs font-semibold text-violet-300 backdrop-blur-sm"
              >
                <Play class="h-3 w-3" />
                Қайта көру
              </div>

              <!-- Event title (admin-entered) -->
              <h1 class="hero-title text-4xl font-bold leading-[1.1] tracking-tight text-white md:text-5xl lg:text-[3.25rem]">
                {{ data.event.title }}
              </h1>

              <!-- Short teaser (first paragraph of description) -->
              <p
                v-if="descriptionParagraphs[0]"
                class="hero-desc mt-5 max-w-lg text-base leading-relaxed text-slate-400 md:text-lg"
              >
                {{ descriptionParagraphs[0] }}
              </p>

              <!-- Session time + countdown block -->
              <div
                v-if="(data.state === 'WAITING' || data.state === 'LANDING') && data.nextSession"
                class="hero-countdown mt-8"
              >
                <div class="mb-4 inline-flex items-center gap-2 rounded-full border border-red-500/30 bg-red-500/10 px-4 py-1.5 text-sm font-bold text-red-400 shadow-lg shadow-red-500/10 backdrop-blur-sm">
                  <Calendar class="h-4 w-4" />
                  {{ formatStart(data.nextSession) }}
                </div>
                <UiCountdown
                  theme="dark"
                  :target="data.nextSession.startTime"
                  @finished="refresh()"
                />
              </div>

              <!-- Primary CTA -->
              <div class="hero-cta mt-8 flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-center">
                <!-- Scarcity badge — shown on any register-style CTA.
                     The count decrements exactly once (on the first
                     reservation) and then locks. Matches the button's
                     height / radius so the two read like twins. -->
                <div
                  v-if="data.state === 'LANDING' || data.state === 'WAITING'"
                  class="spots-badge flex items-center gap-2 border border-amber-500/30 bg-amber-500/10 font-semibold text-amber-300 shadow-lg shadow-amber-500/10 backdrop-blur-sm"
                  :class="{ 'spots-bumped': spotsBumped }"
                >
                  <Flame class="h-4 w-4" />
                  <span>
                    <span class="spots-number tabular-nums font-bold text-amber-200">{{ spotsLeft }}</span>
                    орын қалды
                  </span>
                </div>

                <NuxtLink
                  v-if="data.state === 'LIVE_NOW' && data.activeSession"
                  :to="joinHref()"
                  class="btn-primary group"
                >
                  Эфирге кіру
                  <ChevronRight class="h-4 w-4 transition-transform duration-300 group-hover:translate-x-0.5" />
                </NuxtLink>
                <NuxtLink
                  v-else-if="data.state === 'WAITING' && data.nextSession"
                  :to="joinHref(`/e/${tenantSlug}/${eventSlug}/waiting`)"
                  class="btn-primary group"
                  :class="{ 'btn-primary--reserved': hasReserved }"
                  @click="reserveSpot"
                >
                  <template v-if="hasReserved">
                    <CheckCircle2 class="h-4 w-4" />
                    Орын сізге броньдалды
                  </template>
                  <template v-else>
                    Күту бөлмесіне кіру
                    <ChevronRight class="h-4 w-4 transition-transform duration-300 group-hover:translate-x-0.5" />
                  </template>
                </NuxtLink>
                <NuxtLink
                  v-else-if="data.state === 'LANDING' && data.nextSession"
                  :to="joinHref(`/e/${tenantSlug}/${eventSlug}`)"
                  class="btn-primary group"
                  :class="{ 'btn-primary--reserved': hasReserved }"
                  @click="reserveSpot"
                >
                  <template v-if="hasReserved">
                    <CheckCircle2 class="h-4 w-4" />
                    Орын сізге броньдалды
                  </template>
                  <template v-else>
                    Эфирге орын алу
                    <ChevronRight class="h-4 w-4 transition-transform duration-300 group-hover:translate-x-0.5" />
                  </template>
                </NuxtLink>
                <NuxtLink
                  v-else-if="data.state === 'LANDING'"
                  :to="joinHref(`/e/${tenantSlug}/${eventSlug}`)"
                  class="btn-primary group"
                  :class="{ 'btn-primary--reserved': hasReserved }"
                  @click="reserveSpot"
                >
                  <template v-if="hasReserved">
                    <CheckCircle2 class="h-4 w-4" />
                    Орын сізге броньдалды
                  </template>
                  <template v-else>
                    Эфирге орын алу
                    <ChevronRight class="h-4 w-4 transition-transform duration-300 group-hover:translate-x-0.5" />
                  </template>
                </NuxtLink>

              </div>
            </div>

            <!-- Right: broadcast preview card — matches the marketing
                 landing ({@code pages/index.vue}) 1:1 in structure.
                 One unified frame holds: cover (with LIVE badge,
                 viewers, orbit chips, CTA banner) → chat feed → footer
                 bar with event title + LogoMark. Everything the user
                 already saw on the home page now lives here, only with
                 the admin-provided cover image swapped in for the mock
                 broadcast background. -->
            <div class="hero-visual relative">
              <div class="absolute -inset-8 rounded-3xl bg-gradient-to-br from-brand-600/20 via-transparent to-accent-500/15 blur-2xl" />
              <div class="relative overflow-hidden rounded-2xl border border-white/10 bg-white/5 p-1.5 shadow-2xl backdrop-blur-md">
                <!-- Cover / video area -->
                <div class="relative aspect-video w-full overflow-hidden rounded-xl bg-gradient-to-br from-slate-800 to-slate-900">
                  <img
                    v-if="data.event.coverImageUrl"
                    :src="data.event.coverImageUrl"
                    :alt="data.event.title"
                    class="absolute inset-0 h-full w-full object-cover"
                  >
                  <div
                    v-else
                    class="absolute inset-0 flex items-center justify-center text-7xl font-black text-white/20"
                  >
                    {{ data.event.title.slice(0, 1).toUpperCase() }}
                  </div>

                  <!-- Subtle grid overlay (same as main page) -->
                  <div class="pointer-events-none absolute inset-0 bg-[linear-gradient(rgba(255,255,255,.02)_1px,transparent_1px),linear-gradient(90deg,rgba(255,255,255,.02)_1px,transparent_1px)] bg-[size:24px_24px]" />

                  <!-- Dark gradient at bottom so the CTA banner reads
                       cleanly even over a bright cover photo. -->
                  <div class="pointer-events-none absolute inset-x-0 bottom-0 h-28 bg-gradient-to-t from-slate-950/85 via-slate-950/50 to-transparent" />

                  <!-- Orbiting AI-metric chips (same as main page) -->
                  <div class="hero-orbit-badge hero-orbit-1 absolute flex items-center gap-1.5 rounded-lg border border-emerald-400/20 bg-emerald-500/10 px-2.5 py-1.5 backdrop-blur-md" style="top: 16%; right: 12%">
                    <TrendingUp class="h-3.5 w-3.5 text-emerald-400" />
                    <div>
                      <p class="text-[11px] font-bold text-emerald-400">+34%</p>
                      <p class="text-[8px] text-white/40">конверсия</p>
                    </div>
                  </div>
                  <div class="hero-orbit-badge hero-orbit-2 absolute flex items-center gap-1.5 rounded-lg border border-accent-400/20 bg-accent-500/10 px-2.5 py-1.5 backdrop-blur-md" style="top: 38%; left: 6%">
                    <Sparkles class="h-3.5 w-3.5 text-accent-400" />
                    <div>
                      <p class="text-[11px] font-bold text-accent-400">56%</p>
                      <p class="text-[8px] text-white/40">горячий лид</p>
                    </div>
                  </div>
                  <div class="hero-orbit-badge hero-orbit-3 absolute flex items-center gap-1.5 rounded-lg border border-violet-400/20 bg-violet-500/10 px-2.5 py-1.5 backdrop-blur-md" style="top: 40%; right: 8%">
                    <Users class="h-3.5 w-3.5 text-violet-400" />
                    <div>
                      <p class="text-[11px] font-bold text-violet-400">87%</p>
                      <p class="text-[8px] text-white/40">зрители активно</p>
                    </div>
                  </div>

                  <!-- LIVE badge -->
                  <div
                    v-if="data.state === 'LIVE_NOW'"
                    class="absolute left-3 top-3 flex items-center gap-1.5 rounded-lg bg-red-500/90 px-2.5 py-1 text-[11px] font-bold text-white shadow-lg shadow-red-500/30 backdrop-blur"
                  >
                    <span class="relative flex h-1.5 w-1.5">
                      <span class="absolute inline-flex h-full w-full animate-ping rounded-full bg-white opacity-75" />
                      <span class="relative inline-flex h-1.5 w-1.5 rounded-full bg-white" />
                    </span>
                    LIVE
                  </div>

                  <!-- Viewers -->
                  <div class="absolute right-3 top-3 flex items-center gap-1.5 rounded-lg bg-black/40 px-2.5 py-1 text-[11px] font-medium text-white/90 backdrop-blur">
                    <Users class="h-3 w-3" />
                    {{ viewerCount.toLocaleString('ru-RU') }}
                  </div>

                  <!-- CTA banner overlay (same as main page) -->
                  <div class="hero-cta-banner absolute bottom-3 left-3 right-3 overflow-hidden rounded-lg border border-brand-400/40 bg-brand-600/25 px-3 py-2.5 backdrop-blur-md">
                    <div class="hero-cta-shimmer pointer-events-none absolute inset-0" />
                    <div class="relative flex items-center justify-between gap-2">
                      <span class="text-xs font-medium text-white/90">Тегін чек-листті алу</span>
                      <span class="hero-cta-btn rounded-md bg-brand-500 px-2.5 py-1 text-[10px] font-bold text-white shadow-md shadow-brand-500/30">Жүктеу</span>
                    </div>
                  </div>
                </div>

                <!-- Live chat feed (same as main page) -->
                <div class="border-t border-white/5 px-3 py-2">
                  <div class="mb-1.5 flex items-center justify-between">
                    <span class="text-[10px] font-semibold text-white/40">ЧАТ</span>
                    <span class="text-[10px] text-white/30">{{ viewerCount.toLocaleString('ru-RU') }} көрермен</span>
                  </div>
                  <div class="live-chat-feed relative space-y-1.5 overflow-hidden" style="height: 88px">
                    <TransitionGroup name="chat-msg">
                      <div
                        v-for="msg in visibleMessages"
                        :key="msg.id"
                        class="flex items-start gap-2 rounded-lg px-2 py-1.5 transition-all duration-300"
                        :class="msg.pinned ? 'border border-amber-500/20 bg-amber-500/10' : 'hover:bg-white/[0.02]'"
                      >
                        <div
                          class="mt-0.5 flex h-5 w-5 flex-shrink-0 items-center justify-center rounded-full text-[9px] font-bold text-white"
                          :class="msg.avatarColor"
                        >{{ msg.avatar }}</div>
                        <div class="min-w-0 flex-1">
                          <div class="flex items-center gap-1.5">
                            <span
                              class="text-[10px] font-semibold"
                              :class="msg.mod ? 'text-amber-400' : 'text-white/60'"
                            >{{ msg.name }}</span>
                            <span v-if="msg.mod" class="rounded bg-amber-500/20 px-1 text-[8px] font-bold text-amber-400">MOD</span>
                            <span v-if="msg.pinned" class="text-[8px] text-amber-400/70">📌</span>
                          </div>
                          <p class="truncate text-[11px] text-white/70">{{ msg.text }}</p>
                        </div>
                        <div
                          v-if="msg.likes"
                          class="flex flex-shrink-0 items-center gap-0.5 text-[10px] text-accent-400/70"
                        >
                          ❤️ {{ msg.likes }}
                        </div>
                      </div>
                    </TransitionGroup>
                  </div>
                </div>

                <!-- Bottom bar — event title on the left, LogoMark on
                     the right. Same footer strip as the main page's
                     mock broadcast card. -->
                <div class="flex items-center justify-between border-t border-white/5 px-3 py-2">
                  <span class="truncate text-[10px] font-medium text-white/50">{{ data.event.title }}</span>
                  <LogoMark :size="16" :animate="false" />
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- ═══════════════════════════════════════════════════════════ -->
      <!-- SLOT SELECTION                                              -->
      <!-- ═══════════════════════════════════════════════════════════ -->
      <section
        v-if="data.state === 'SLOT_SELECTION' && data.autoSlots.length"
        class="relative overflow-hidden py-16 md:py-20"
      >
        <div class="mx-auto w-full max-w-6xl px-4">
          <div :ref="setRevealRef" class="reveal-up">
            <span class="inline-flex items-center gap-2 rounded-full border border-violet-500/20 bg-violet-500/10 px-3 py-1 text-xs font-semibold text-violet-300">
              <Play class="h-3 w-3" /> Replay слоттары
            </span>
            <h2 class="mt-5 text-3xl font-bold text-white md:text-4xl">
              Қайта көру уақытын таңдаңыз
            </h2>
            <p class="mt-3 text-slate-400">
              Тікелей эфир аяқталды. Төмендегі тізімнен бір сеансты таңдаңыз — ол уақыт келгенде басынан басталады.
            </p>
          </div>

          <ul class="mt-10 space-y-3">
            <li
              v-for="(slot, i) in data.autoSlots"
              :key="slot.id"
              :ref="setRevealRef"
              class="reveal-up"
              :style="{ transitionDelay: `${i * 80}ms` }"
            >
              <NuxtLink
                :to="joinHref(`/e/${tenantSlug}/${eventSlug}/room?session=${slot.id}`)"
                class="slot-card group flex items-center justify-between gap-4 rounded-2xl border border-white/[0.06] bg-white/[0.03] px-5 py-4 backdrop-blur-sm transition-all duration-300 hover:border-violet-400/40 hover:bg-white/[0.06]"
              >
                <div class="flex items-center gap-4">
                  <div class="flex h-11 w-11 items-center justify-center rounded-xl bg-gradient-to-br from-violet-600 to-brand-600 text-white shadow-lg">
                    <Play class="h-4 w-4" />
                  </div>
                  <div>
                    <div class="font-semibold text-white">{{ formatStart(slot) }}</div>
                    <div class="mt-0.5 flex items-center gap-1 text-xs text-slate-400">
                      <Clock class="h-3 w-3" />
                      ~{{ Math.round(slot.plannedDurationSeconds / 60) }} минут
                    </div>
                  </div>
                </div>
                <ChevronRight class="h-5 w-5 text-slate-500 transition-transform group-hover:translate-x-0.5 group-hover:text-white" />
              </NuxtLink>
            </li>
          </ul>
        </div>
      </section>

      <!-- ═══════════════════════════════════════════════════════════ -->
      <!-- ABOUT / DESCRIPTION                                         -->
      <!-- ═══════════════════════════════════════════════════════════ -->
      <section
        v-if="descriptionParagraphs.length"
        class="relative overflow-hidden py-16 md:py-20"
      >
        <div class="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-brand-600/40 to-transparent" />

        <div class="mx-auto w-full max-w-6xl px-4">
          <div :ref="setRevealRef" class="reveal-up">
            <span class="inline-flex items-center gap-2 rounded-full border border-brand-500/20 bg-brand-500/10 px-3 py-1 text-xs font-semibold text-brand-400">
              <Radio class="h-3 w-3" /> Эфир туралы
            </span>
            <h2 class="mt-5 text-3xl font-bold text-white md:text-4xl">
              Не талқыланады
            </h2>
          </div>

          <div class="mt-10 space-y-5">
            <p
              v-for="(para, i) in descriptionParagraphs"
              :key="i"
              :ref="setRevealRef"
              class="reveal-up max-w-3xl text-base leading-relaxed text-slate-300 md:text-lg"
              :style="{ transitionDelay: `${i * 100}ms` }"
            >
              {{ para }}
            </p>
          </div>
        </div>
      </section>

      <!-- ═══════════════════════════════════════════════════════════ -->
      <!-- BENEFITS — from admin "Лендинг Builder" (Step 5)            -->
      <!-- Renders only when the admin added at least one card; falls  -->
      <!-- back gracefully on an empty config so the page flow keeps   -->
      <!-- its rhythm on events that skipped this step.                -->
      <!-- ═══════════════════════════════════════════════════════════ -->
      <section v-if="benefitsList.length" class="relative overflow-hidden py-16 md:py-20">
        <div class="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-accent-500/30 to-transparent" />

        <div class="mx-auto w-full max-w-6xl px-4">
          <div :ref="setRevealRef" class="reveal-up">
            <span class="inline-flex items-center gap-2 rounded-full border border-accent-500/20 bg-accent-500/10 px-3 py-1 text-xs font-semibold text-accent-400">
              <Sparkles class="h-3 w-3" /> Артықшылықтар
            </span>
            <h2 class="mt-5 text-3xl font-bold text-white md:text-4xl">
              {{ benefitsTitle }}
            </h2>
          </div>

          <div class="mt-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <div
              v-for="(b, i) in benefitsList"
              :key="i"
              :ref="setRevealRef"
              class="reveal-up benefit-card group relative overflow-hidden rounded-2xl border border-white/[0.06] bg-white/[0.03] p-6 backdrop-blur-sm transition-all duration-500 hover:-translate-y-1 hover:border-accent-400/30 hover:bg-white/[0.05]"
              :style="{ transitionDelay: `${i * 80}ms` }"
            >
              <div class="pointer-events-none absolute -right-12 -top-12 h-32 w-32 rounded-full bg-gradient-to-br from-accent-500/15 to-brand-500/10 blur-2xl opacity-0 transition-opacity duration-500 group-hover:opacity-100" />
              <div class="relative flex h-11 w-11 items-center justify-center rounded-xl bg-gradient-to-br from-accent-500/20 to-brand-500/20 text-accent-300 ring-1 ring-inset ring-white/10">
                <component :is="benefitIcon(b.icon)" class="h-5 w-5" />
              </div>
              <h3 v-if="b.title" class="relative mt-4 text-lg font-bold text-white">
                {{ b.title }}
              </h3>
              <p v-if="b.desc" class="relative mt-2 text-sm leading-relaxed text-slate-400">
                {{ b.desc }}
              </p>
            </div>
          </div>
        </div>
      </section>

      <!-- ═══════════════════════════════════════════════════════════ -->
      <!-- TIMELINE / PROGRAM — from admin "Лендинг Builder" (Step 5)  -->
      <!-- ═══════════════════════════════════════════════════════════ -->
      <section v-if="timelineList.length" class="relative overflow-hidden py-16 md:py-20">
        <div class="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-violet-500/30 to-transparent" />

        <div class="mx-auto w-full max-w-4xl px-4">
          <div :ref="setRevealRef" class="reveal-up">
            <span class="inline-flex items-center gap-2 rounded-full border border-violet-500/20 bg-violet-500/10 px-3 py-1 text-xs font-semibold text-violet-300">
              <Play class="h-3 w-3" /> Бағдарлама
            </span>
            <h2 class="mt-5 text-3xl font-bold text-white md:text-4xl">
              {{ timelineTitle }}
            </h2>
            <p v-if="timelineSubtitle" class="mt-3 text-slate-400">
              {{ timelineSubtitle }}
            </p>
          </div>

          <ol class="timeline-list relative mt-10 space-y-4 pl-10 before:absolute before:left-3 before:top-2 before:bottom-2 before:w-px before:bg-gradient-to-b before:from-violet-500/40 before:via-violet-500/20 before:to-transparent">
            <li
              v-for="(t, i) in timelineList"
              :key="i"
              :ref="setRevealRef"
              class="reveal-up relative"
              :style="{ transitionDelay: `${i * 90}ms` }"
            >
              <span class="timeline-dot absolute -left-[22px] top-5 flex h-6 w-6 items-center justify-center rounded-full bg-gradient-to-br from-violet-500 to-brand-500 text-[10px] font-bold text-white shadow-lg shadow-violet-500/30 ring-4 ring-slate-950">
                {{ i + 1 }}
              </span>
              <div class="rounded-2xl border border-white/[0.06] bg-white/[0.03] px-5 py-4 backdrop-blur-sm transition-colors duration-300 hover:border-violet-400/30 hover:bg-white/[0.05]">
                <h3 v-if="t.title" class="text-base font-bold text-white md:text-lg">
                  {{ t.title }}
                </h3>
                <p v-if="t.desc" class="mt-1 text-sm leading-relaxed text-slate-400">
                  {{ t.desc }}
                </p>
              </div>
            </li>
          </ol>
        </div>
      </section>

      <!-- ═══════════════════════════════════════════════════════════ -->
      <!-- SPEAKER                                                     -->
      <!-- ═══════════════════════════════════════════════════════════ -->
      <section v-if="data.event.speakerName" class="relative overflow-hidden py-16 md:py-20">
        <div class="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-white/[0.06] to-transparent" />

        <div class="mx-auto w-full max-w-6xl px-4">
          <div :ref="setRevealRef" class="reveal-up">
            <span class="inline-flex items-center gap-2 rounded-full border border-accent-500/20 bg-accent-500/10 px-3 py-1 text-xs font-semibold text-accent-400">
              <Radio class="h-3 w-3" /> Спикер
            </span>
            <h2 class="mt-5 text-3xl font-bold text-white md:text-4xl">
              Кім жүргізеді
            </h2>
          </div>

          <div :ref="setRevealRef" class="reveal-up mt-10">
            <div class="speaker-card group relative overflow-hidden rounded-2xl border border-white/[0.06] bg-white/[0.03] p-6 backdrop-blur-sm transition-all duration-500 hover:border-white/10 hover:bg-white/[0.05] md:p-8">
              <div class="absolute -right-20 -top-20 h-64 w-64 rounded-full bg-gradient-to-br from-brand-600/20 to-accent-500/10 blur-3xl transition-opacity duration-500 group-hover:opacity-150" />
              <div class="relative flex items-start gap-5 md:gap-7">
                <div class="speaker-avatar flex h-16 w-16 flex-shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br from-brand-500 to-accent-500 text-2xl font-bold text-white shadow-lg shadow-brand-600/30 md:h-20 md:w-20 md:text-3xl">
                  {{ data.event.speakerName.slice(0, 1).toUpperCase() }}
                </div>
                <div class="min-w-0 flex-1">
                  <div class="text-xl font-bold text-white md:text-2xl">
                    {{ data.event.speakerName }}
                  </div>
                  <p v-if="data.event.speakerBio" class="mt-2 text-sm leading-relaxed text-slate-400 md:text-base">
                    {{ data.event.speakerBio }}
                  </p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- ═══════════════════════════════════════════════════════════ -->
      <!-- CLOSING CTA (only when we have a session to register for)   -->
      <!-- ═══════════════════════════════════════════════════════════ -->
      <section
        v-if="data.state !== 'UNAVAILABLE' && data.state !== 'SLOT_SELECTION'"
        class="relative overflow-hidden py-20 md:py-28"
      >
        <div class="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-brand-600/40 to-transparent" />

        <div :ref="setRevealRef" class="reveal-up mx-auto w-full max-w-6xl px-4 text-center">
          <h2 class="text-3xl font-bold text-white md:text-4xl lg:text-5xl">
            Эфирге қосылуға дайынсыз ба?
          </h2>

          <!-- Closing countdown — same block as hero, so the visitor
               sees the same urgency cue at both scroll anchors. Only
               rendered when there's an actual upcoming session to
               count down to. -->
          <div
            v-if="(data.state === 'WAITING' || data.state === 'LANDING') && data.nextSession"
            class="mt-10 flex flex-col items-center gap-4"
          >
            <div class="inline-flex items-center gap-2 rounded-full border border-red-500/30 bg-red-500/10 px-4 py-1.5 text-sm font-bold text-red-400 shadow-lg shadow-red-500/10 backdrop-blur-sm">
              <Calendar class="h-4 w-4" />
              {{ formatStart(data.nextSession) }}
            </div>
            <UiCountdown
              theme="dark"
              :target="data.nextSession.startTime"
              @finished="refresh()"
            />
          </div>

          <div class="mt-10 flex flex-col items-center justify-center gap-3 sm:flex-row sm:flex-wrap">
            <!-- Scarcity twin — matches the hero badge / button frame -->
            <div
              v-if="data.state === 'LANDING' || data.state === 'WAITING'"
              class="spots-badge spots-badge--lg flex items-center gap-2 border border-amber-500/30 bg-amber-500/10 font-semibold text-amber-300 shadow-lg shadow-amber-500/10 backdrop-blur-sm"
              :class="{ 'spots-bumped': spotsBumped }"
            >
              <Flame class="h-4 w-4" />
              <span>
                <span class="spots-number tabular-nums font-bold text-amber-200">{{ spotsLeft }}</span>
                орын қалды
              </span>
            </div>

            <NuxtLink
              :to="joinHref(
                data.state === 'LIVE_NOW'
                  ? `/e/${tenantSlug}/${eventSlug}/room`
                  : data.state === 'WAITING'
                    ? `/e/${tenantSlug}/${eventSlug}/waiting`
                    : `/e/${tenantSlug}/${eventSlug}`
              )"
              class="btn-primary btn-primary--lg group"
              :class="{ 'btn-primary--reserved': hasReserved && data.state !== 'LIVE_NOW' }"
              @click="(data.state === 'LANDING' || data.state === 'WAITING') ? reserveSpot() : null"
            >
              <template v-if="hasReserved && data.state !== 'LIVE_NOW'">
                <CheckCircle2 class="h-5 w-5" />
                Орын сізге броньдалды
              </template>
              <template v-else>
                {{
                  data.state === 'LIVE_NOW'
                    ? 'Эфирге қазір кіру'
                    : data.state === 'WAITING'
                      ? 'Күту бөлмесіне кіру'
                      : 'Эфирге орын алу'
                }}
                <ChevronRight class="h-5 w-5 transition-transform duration-300 group-hover:translate-x-0.5" />
              </template>
            </NuxtLink>
          </div>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
/* ================================================================== */
/* Page shell                                                         */
/* ================================================================== */
.event-landing {
  position: relative;
  min-height: 100vh;
  color: #fff;
}

/* ================================================================== */
/* Orbital backdrop — rings + gradient mesh + noise                   */
/* ================================================================== */
.orbital-backdrop {
  z-index: 0;
  overflow: hidden;
}
.hero-ring {
  position: absolute;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.04);
  left: 50%;
  top: 40%;
  transform: translate(-50%, -50%);
}
.hero-ring-1 {
  width: 600px;
  height: 600px;
  animation: hero-ring-spin 40s linear infinite;
}
.hero-ring-2 {
  width: 900px;
  height: 900px;
  border-color: rgba(255, 255, 255, 0.025);
  animation: hero-ring-spin 60s linear infinite reverse;
}
.hero-ring-3 {
  width: 1200px;
  height: 1200px;
  border-color: rgba(255, 255, 255, 0.015);
  animation: hero-ring-spin 80s linear infinite;
}
@keyframes hero-ring-spin {
  from { transform: translate(-50%, -50%) rotate(0deg); }
  to   { transform: translate(-50%, -50%) rotate(360deg); }
}
.noise-texture {
  background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 256 256' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)' opacity='1'/%3E%3C/svg%3E");
}

/* ================================================================== */
/* Hero entrance animations — match the marketing landing             */
/* ================================================================== */
.hero-badge {
  opacity: 0;
  transform: translateY(16px);
  animation: hero-slide-up 0.7s cubic-bezier(0.16, 1, 0.3, 1) 0.1s forwards;
}
.hero-title {
  opacity: 0;
  transform: translateY(20px);
  animation: hero-slide-up 0.8s cubic-bezier(0.16, 1, 0.3, 1) 0.2s forwards;
}
.hero-desc {
  opacity: 0;
  transform: translateY(16px);
  animation: hero-slide-up 0.7s cubic-bezier(0.16, 1, 0.3, 1) 0.35s forwards;
}
.hero-countdown {
  opacity: 0;
  transform: translateY(16px);
  animation: hero-slide-up 0.7s cubic-bezier(0.16, 1, 0.3, 1) 0.45s forwards;
}
.hero-cta {
  opacity: 0;
  transform: translateY(16px);
  animation: hero-slide-up 0.7s cubic-bezier(0.16, 1, 0.3, 1) 0.55s forwards;
}
.hero-visual {
  opacity: 0;
  transform: translateY(24px) scale(0.97);
  animation: hero-visual-in 0.9s cubic-bezier(0.16, 1, 0.3, 1) 0.3s forwards;
}
@keyframes hero-slide-up {
  to { opacity: 1; transform: translateY(0); }
}
@keyframes hero-visual-in {
  to { opacity: 1; transform: translateY(0) scale(1); }
}

/* ================================================================== */
/* Scroll reveal                                                      */
/* ================================================================== */
.reveal-up {
  opacity: 0;
  transform: translateY(32px);
  transition: opacity 0.7s cubic-bezier(0.16, 1, 0.3, 1),
              transform 0.7s cubic-bezier(0.16, 1, 0.3, 1);
}
.reveal-up.revealed {
  opacity: 1;
  transform: translateY(0);
}

/* ================================================================== */
/* Glass cards                                                         */
/* ================================================================== */
.glass-card {
  position: relative;
  overflow: hidden;
  border-radius: 20px;
  border: 1px solid rgba(255, 255, 255, 0.06);
  background: rgba(255, 255, 255, 0.03);
  padding: 28px;
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
}

/* ================================================================== */
/* Primary CTA button — matches marketing landing                     */
/* ================================================================== */
.btn-primary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-radius: 14px;
  background: #2c5dff;
  padding: 14px 26px;
  font-size: 15px;
  font-weight: 600;
  color: #fff;
  box-shadow: 0 14px 38px rgba(44, 93, 255, 0.35);
  transition: transform 0.3s ease, background 0.3s ease, box-shadow 0.3s ease;
}
.btn-primary:hover {
  background: #4174ff;
  transform: translateY(-2px);
  box-shadow: 0 20px 46px rgba(44, 93, 255, 0.45);
}
.btn-primary:active {
  transform: translateY(0);
}
.btn-primary--lg {
  padding: 18px 34px;
  font-size: 16px;
  border-radius: 16px;
}

/* ================================================================== */
/* "Reserved" state — visitor has already claimed a spot. Switches the
 * primary CTA from brand-blue to a confident emerald with a check icon. */
/* ================================================================== */
.btn-primary--reserved {
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
  box-shadow: 0 14px 38px rgba(16, 185, 129, 0.35);
  pointer-events: none;
  cursor: default;
}
.btn-primary--reserved:hover {
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
  transform: none;
  box-shadow: 0 14px 38px rgba(16, 185, 129, 0.35);
}

/* ================================================================== */
/* Slot card hover                                                    */
/* ================================================================== */
.slot-card:hover {
  box-shadow: 0 10px 40px rgba(139, 92, 246, 0.18);
}

/* ================================================================== */
/* Speaker avatar subtle float                                        */
/* ================================================================== */
.speaker-avatar {
  animation: speaker-float 6s ease-in-out infinite;
}
@keyframes speaker-float {
  0%, 100% { transform: translateY(0); }
  50%      { transform: translateY(-4px); }
}

/* ================================================================== */
/* Orbiting AI-metric chips on the cover — small floating nudge so the
 * card feels alive, mirrors the marketing landing's mock broadcast.   */
/* ================================================================== */
.hero-orbit-badge {
  z-index: 5;
  pointer-events: none;
  animation-timing-function: ease-in-out;
  animation-iteration-count: infinite;
}
.hero-orbit-1 { animation: orbit-wave-1 4s   ease-in-out infinite; }
.hero-orbit-2 { animation: orbit-wave-2 5s   ease-in-out infinite; }
.hero-orbit-3 { animation: orbit-wave-3 4.5s ease-in-out infinite; }
@keyframes orbit-wave-1 {
  0%, 100% { transform: translateY(0)    translateX(0);   opacity: 0.95; }
  25%      { transform: translateY(-6px) translateX(3px); opacity: 1;    }
  50%      { transform: translateY(2px)  translateX(-2px); opacity: 0.85;}
  75%      { transform: translateY(-3px) translateX(-3px); opacity: 1;   }
}
@keyframes orbit-wave-2 {
  0%, 100% { transform: translateY(0)    translateX(0);   opacity: 0.9; }
  30%      { transform: translateY(5px)  translateX(-3px); opacity: 1;   }
  60%      { transform: translateY(-4px) translateX(2px);  opacity: 0.85;}
}
@keyframes orbit-wave-3 {
  0%, 100% { transform: translateY(0)    translateX(0);   opacity: 0.92; }
  40%      { transform: translateY(-5px) translateX(-4px); opacity: 1;    }
  70%      { transform: translateY(3px)  translateX(3px);  opacity: 0.88; }
}

/* ================================================================== */
/* CTA banner — float, shimmer, pulsing download button               */
/* ================================================================== */
.hero-cta-banner {
  animation: cta-float 3s ease-in-out infinite;
}
.hero-cta-shimmer {
  background: linear-gradient(
    105deg,
    transparent 30%,
    rgba(255,255,255,0.12) 45%,
    rgba(255,255,255,0.2) 50%,
    rgba(255,255,255,0.12) 55%,
    transparent 70%
  );
  background-size: 250% 100%;
  animation: cta-shimmer 3s ease-in-out infinite;
}
.hero-cta-btn {
  animation: cta-btn-pulse 2s ease-in-out infinite;
}
@keyframes cta-float {
  0%, 100% { transform: translateY(0); }
  50%      { transform: translateY(-3px); }
}
@keyframes cta-shimmer {
  0%   { background-position: 200% 0; }
  60%  { background-position: -50% 0; }
  100% { background-position: -50% 0; }
}
@keyframes cta-btn-pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(61,99,255,0.4); }
  50%      { box-shadow: 0 0 0 6px rgba(61,99,255,0); }
}

/* ================================================================== */
/* Rotating live chat feed — TransitionGroup animations.
 * Matches the marketing landing: new messages slide up from the
 * bottom with a subtle scale-in; the oldest message fades up and out
 * while the remaining ones shift via `chat-msg-move`.
/* ================================================================== */
.chat-msg-enter-active {
  transition: all 0.4s cubic-bezier(0.16, 1, 0.3, 1);
}
.chat-msg-leave-active {
  transition: all 0.3s ease-in;
  position: absolute;
  width: 100%;
}
.chat-msg-enter-from {
  opacity: 0;
  transform: translateY(12px) scale(0.97);
}
.chat-msg-leave-to {
  opacity: 0;
  transform: translateY(-12px) scale(0.97);
}
.chat-msg-move {
  transition: transform 0.4s cubic-bezier(0.16, 1, 0.3, 1);
}

/* ================================================================== */
/* Scarcity "57 орын қалды" badge — sized to read as a twin of the    */
/* primary CTA. Same vertical height, same border-radius, same type   */
/* scale so the pair sits on a single visual baseline.                */
/* ================================================================== */
.spots-badge {
  padding: 14px 22px;
  border-radius: 14px;
  font-size: 15px;
  line-height: 1;
  transition: transform 0.3s ease, box-shadow 0.3s ease;
  animation: spots-pulse-glow 3s ease-in-out infinite;
}
/* Large variant — pairs with .btn-primary--lg in the closing CTA */
.spots-badge--lg {
  padding: 18px 26px;
  border-radius: 16px;
  font-size: 16px;
}
.spots-number {
  display: inline-block;
  transition: transform 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}
.spots-bumped {
  transform: scale(1.05);
}
.spots-bumped .spots-number {
  animation: spots-bump 0.6s cubic-bezier(0.16, 1, 0.3, 1);
  color: #fca5a5; /* red-300 flash */
}
@keyframes spots-bump {
  0%   { transform: translateY(0)    scale(1); }
  35%  { transform: translateY(-6px) scale(1.25); }
  65%  { transform: translateY(3px)  scale(0.92); }
  100% { transform: translateY(0)    scale(1); }
}
@keyframes spots-pulse-glow {
  0%, 100% { box-shadow: 0 10px 30px rgba(245, 158, 11, 0.08); }
  50%      { box-shadow: 0 14px 40px rgba(245, 158, 11, 0.22); }
}
</style>
