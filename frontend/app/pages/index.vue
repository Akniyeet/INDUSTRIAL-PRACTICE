<script setup lang="ts">
/**
 * Webizon — cinematic marketing landing page.
 *
 * Aesthetic: "Broadcast Universe" — dark hero with orbital motifs from the logo,
 * gradient mesh backgrounds, scroll-triggered reveals, glassmorphic cards.
 * Targets online schools, coaches, and marketing teams in Kazakhstan.
 */
import {
  Zap,
  Users,
  BarChart3,
  Shield,
  RefreshCw,
  MousePointerClick,
  PlayCircle,
  MessageSquare,
  Megaphone,
  CheckCircle2,
  ArrowRight,
  Radio,
  GraduationCap,
  Briefcase,
  Sparkles,
  Globe,
  Clock,
  TrendingUp,
} from 'lucide-vue-next'

useHead({
  title: 'Webizon — вебинары без лимитов',
  meta: [
    { name: 'description', content: 'Платформа для вебинаров с поддержкой 60 000+ одновременных зрителей. Живой чат, авто-повторы, CTA-движок, аналитика и модерация.' },
  ],
})

/* ------------------------------------------------------------------ */
/* Scroll-triggered reveal via IntersectionObserver                     */
/* ------------------------------------------------------------------ */
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

/* ------------------------------------------------------------------ */
/* Data                                                                */
/* ------------------------------------------------------------------ */
const features = [
  { icon: MessageSquare, title: 'Живой чат', desc: 'Кастомный чат с модерацией, slow mode, ответами и ролями. Полный контроль за вами.', color: 'from-blue-500 to-cyan-400' },
  { icon: RefreshCw, title: 'Авто-повторы', desc: 'Проведите эфир один раз — система покажет его снова по расписанию. Без вашего участия.', color: 'from-violet-500 to-purple-400' },
  { icon: MousePointerClick, title: 'CTA-движок', desc: 'Кнопки и формы появляются в эфире точно когда нужно. Связь с аудиторией в один клик.', color: 'from-accent-500 to-rose-400' },
  { icon: BarChart3, title: 'Аналитика', desc: 'Retention, конверсии CTA, пиковые зрители и поведенческие сигналы для CRM.', color: 'from-emerald-500 to-teal-400' },
  { icon: Shield, title: 'Модерация', desc: 'Предупреждение, мут, бан. Аудит-лог, фильтры нецензурной лексики и спама.', color: 'from-amber-500 to-orange-400' },
  { icon: Zap, title: 'Удобная оплата', desc: 'Любая карта, любая страна. Платите только за реальных зрителей.', color: 'from-brand-500 to-blue-400' },
]

const steps = [
  { num: '01', title: 'Создайте событие', desc: 'Тема, спикер, обложка и расписание. Публичная ссылка готова.', icon: Sparkles },
  { num: '02', title: 'Проведите эфир', desc: 'OBS → YouTube Live. Чат, CTA и модерация — из вашей админки.', icon: Radio },
  { num: '03', title: 'Настройте повторы', desc: 'Авто-сессии по расписанию. Чат и CTA воспроизводятся автоматически.', icon: RefreshCw },
  { num: '04', title: 'Анализируйте', desc: 'Retention, конверсии, поведение. CRM-сигналы генерируются сами.', icon: TrendingUp },
]

const audiences = [
  { title: 'Онлайн-школы и академии', tag: 'Образование', desc: 'Открытые уроки и марафоны на тысячи зрителей. Авто-повторы для разных часовых поясов.', dot: 'bg-emerald-400 shadow-emerald-400/50' },
  { title: 'Коучи и эксперты', tag: 'Коучинг', desc: 'Мастер-классы с конверсионными кнопками. Аналитика покажет, кто готов к покупке.', dot: 'bg-violet-400 shadow-violet-400/50' },
  { title: 'Блогеры и авторы', tag: 'Контент', desc: 'Монетизируйте аудиторию через живые эфиры. CTA и чат помогут продавать.', dot: 'bg-amber-400 shadow-amber-400/50' },
  { title: 'Образовательные учреждения', tag: 'Учреждения', desc: 'Лекции и семинары в онлайн-формате. Полный контроль и модерация.', dot: 'bg-rose-400 shadow-rose-400/50' },
  { title: 'Бизнес и корпорации', tag: 'Бизнес', desc: 'Обучение сотрудников и партнёров без отрыва от работы. Записи по расписанию.', dot: 'bg-sky-400 shadow-sky-400/50' },
  { title: 'Маркетинг-команды', tag: 'Маркетинг', desc: 'Лидогенерация через образовательные эфиры. Поведенческие сигналы прямо в CRM.', dot: 'bg-teal-400 shadow-teal-400/50' },
]

const activeAudience = ref(0)

onMounted(() => {
  const audienceInterval = setInterval(() => {
    activeAudience.value = (activeAudience.value + 1) % audiences.length
  }, 3000)
  onUnmounted(() => clearInterval(audienceInterval))
})

function setAudience(i: number) {
  activeAudience.value = i
}

/* ------------------------------------------------------------------ */
/* Live chat simulation for hero                                       */
/* ------------------------------------------------------------------ */
interface ChatMsg {
  id: number
  avatar: string
  avatarColor: string
  name: string
  text: string
  mod?: boolean
  pinned?: boolean
  likes?: number
}

const chatPool: ChatMsg[] = [
  { id: 1, avatar: 'А', avatarColor: 'bg-emerald-500', name: 'Алия', text: 'Очень полезно, спасибо! 🔥' },
  { id: 2, avatar: 'Д', avatarColor: 'bg-violet-500', name: 'Дамир', text: 'А будет запись?' },
  { id: 3, avatar: 'М', avatarColor: 'bg-amber-500', name: 'Модератор', text: 'Ссылка на материалы закреплена ⬆️', mod: true, pinned: true },
  { id: 4, avatar: 'С', avatarColor: 'bg-pink-500', name: 'Сания', text: 'Подскажите, это подходит для начинающих?', likes: 3 },
  { id: 5, avatar: 'Т', avatarColor: 'bg-blue-500', name: 'Тимур', text: 'Скачал чек-лист, огонь! 👍', likes: 7 },
  { id: 6, avatar: 'Н', avatarColor: 'bg-rose-500', name: 'Нұрай', text: 'Рахмет! Очень понятно объясняете', likes: 12 },
  { id: 7, avatar: 'М', avatarColor: 'bg-amber-500', name: 'Модератор', text: 'Вопросы пишите в чат, ответим в конце', mod: true },
  { id: 8, avatar: 'Е', avatarColor: 'bg-teal-500', name: 'Ерлан', text: 'Уже третий вебинар смотрю, класс!' },
  { id: 9, avatar: 'К', avatarColor: 'bg-indigo-500', name: 'Карина', text: 'А можно получить сертификат?', likes: 5 },
  { id: 10, avatar: 'Б', avatarColor: 'bg-orange-500', name: 'Бекзат', text: 'Добрый вечер всем! 👋' },
  { id: 11, avatar: 'М', avatarColor: 'bg-amber-500', name: 'Модератор', text: '📌 Записи будут доступны участникам', mod: true, pinned: true },
  { id: 12, avatar: 'Г', avatarColor: 'bg-cyan-500', name: 'Гүлнұр', text: 'Лайк! Самое полезное что видела 🙌', likes: 18 },
  { id: 13, avatar: 'А', avatarColor: 'bg-red-500', name: 'Арман', text: 'Сколько стоит полный курс?' },
  { id: 14, avatar: 'Ж', avatarColor: 'bg-lime-500', name: 'Жанна', text: 'Здравствуйте из Астаны! 🏙️' },
  { id: 15, avatar: 'И', avatarColor: 'bg-purple-500', name: 'Ислам', text: 'Подписался, жду следующий эфир' },
]

const visibleMessages = ref<ChatMsg[]>([])
const viewerCount = ref(2413)
let chatIndex = 0
let msgIdCounter = 100

onMounted(() => {
  // Start with 3 messages
  visibleMessages.value = chatPool.slice(0, 3).map(m => ({ ...m, id: ++msgIdCounter }))
  chatIndex = 3

  // Add new message every 2.5 seconds
  const chatInterval = setInterval(() => {
    const msg = { ...chatPool[chatIndex % chatPool.length], id: ++msgIdCounter }
    visibleMessages.value.push(msg)
    if (visibleMessages.value.length > 3) {
      visibleMessages.value.shift()
    }
    chatIndex++
    // Fluctuate viewer count
    viewerCount.value += Math.floor(Math.random() * 7) - 2
  }, 2500)

  onUnmounted(() => clearInterval(chatInterval))
})

const stats = [
  { value: '60K+', label: 'зрителей одновременно', icon: Users },
  { value: '<150мс', label: 'скорость доставки чата', icon: Zap },
  { value: '99.9%', label: 'гарантия доступности', icon: Globe },
  { value: '∞', label: 'повторов одного эфира', icon: RefreshCw },
]
</script>

<template>
  <div class="landing-page">
    <!-- ═══════════════════════════════════════════════════════════════════ -->
    <!-- HERO — Dark cinematic with orbital background                     -->
    <!-- ═══════════════════════════════════════════════════════════════════ -->
    <section class="hero-section relative overflow-hidden bg-slate-950 pb-24 pt-28 md:pb-32 md:pt-36">
      <!-- Orbital background -->
      <div class="hero-orbitals pointer-events-none absolute inset-0">
        <div class="hero-ring hero-ring-1" />
        <div class="hero-ring hero-ring-2" />
        <div class="hero-ring hero-ring-3" />
        <!-- Gradient mesh -->
        <div class="absolute -left-40 top-20 h-[600px] w-[600px] rounded-full bg-brand-600/15 blur-[120px]" />
        <div class="absolute -right-20 bottom-0 h-[500px] w-[500px] rounded-full bg-accent-500/10 blur-[100px]" />
        <div class="absolute left-1/2 top-1/3 h-[300px] w-[300px] -translate-x-1/2 rounded-full bg-violet-600/8 blur-[80px]" />
      </div>
      <!-- Noise texture overlay -->
      <div class="pointer-events-none absolute inset-0 opacity-[0.03]" style="background-image: url('data:image/svg+xml,%3Csvg viewBox=%220 0 256 256%22 xmlns=%22http://www.w3.org/2000/svg%22%3E%3Cfilter id=%22n%22%3E%3CfeTurbulence type=%22fractalNoise%22 baseFrequency=%220.9%22 numOctaves=%224%22 stitchTiles=%22stitch%22/%3E%3C/filter%3E%3Crect width=%22100%25%22 height=%22100%25%22 filter=%22url(%23n)%22 opacity=%221%22/%3E%3C/svg%3E')" />

      <div class="relative mx-auto w-full max-w-6xl px-4">
        <div class="grid gap-12 md:grid-cols-2 md:items-center md:gap-16 lg:gap-20">
          <!-- Left: copy -->
          <div class="hero-content">
            <div class="hero-badge mb-6 inline-flex items-center gap-2 rounded-full border border-brand-500/30 bg-brand-500/10 px-4 py-1.5 text-xs font-semibold text-brand-300 backdrop-blur-sm">
              <span class="relative flex h-2 w-2">
                <span class="absolute inline-flex h-full w-full animate-ping rounded-full bg-brand-400 opacity-75" />
                <span class="relative inline-flex h-2 w-2 rounded-full bg-brand-400" />
              </span>
              14 дней бесплатно
            </div>

            <h1 class="hero-title text-4xl font-bold leading-[1.08] tracking-tight text-white md:text-5xl lg:text-[3.75rem]">
              Вебинары нового<br />
              <span class="hero-gradient-text bg-gradient-to-r from-brand-400 via-violet-400 to-accent-400 bg-clip-text text-transparent">поколения</span>
            </h1>

            <p class="hero-desc mt-6 max-w-lg text-base leading-relaxed text-slate-400 md:text-lg">
              Проводите живые эфиры, автоматические повторы и образовательные трансляции на любую аудиторию. Чат, модерация, CTA и аналитика — всё в одной платформе.
            </p>

            <div class="hero-cta mt-10">
              <NuxtLink
                to="/auth/sign-up"
                class="group inline-flex items-center justify-center gap-2 rounded-xl bg-brand-600 px-7 py-3.5 text-sm font-semibold text-white shadow-lg shadow-brand-600/30 transition-all duration-300 hover:bg-brand-500 hover:shadow-brand-500/40 hover:-translate-y-0.5 active:translate-y-0"
              >
                Начать бесплатно
                <ArrowRight class="h-4 w-4 transition-transform duration-300 group-hover:translate-x-0.5" />
              </NuxtLink>
            </div>
          </div>

          <!-- Right: mock broadcast card -->
          <div class="hero-visual relative">
            <!-- Glow behind card -->
            <div class="absolute -inset-8 rounded-3xl bg-gradient-to-br from-brand-600/20 via-transparent to-accent-500/15 blur-2xl" />

            <div class="relative overflow-hidden rounded-2xl border border-white/10 bg-white/5 p-1.5 shadow-2xl backdrop-blur-md">
              <!-- Video area -->
              <div class="relative aspect-video w-full overflow-hidden rounded-xl bg-gradient-to-br from-slate-800 to-slate-900">
                <!-- Grid overlay -->
                <div class="absolute inset-0 bg-[linear-gradient(rgba(255,255,255,.02)_1px,transparent_1px),linear-gradient(90deg,rgba(255,255,255,.02)_1px,transparent_1px)] bg-[size:24px_24px]" />
                <div class="absolute inset-0 flex items-center justify-center">
                  <LogoMark :size="64" :animate="true" :pulse="true" />
                </div>
                <!-- LIVE badge -->
                <div class="absolute left-3 top-3 flex items-center gap-1.5 rounded-lg bg-red-500/90 px-2.5 py-1 text-[11px] font-bold text-white shadow-lg shadow-red-500/30 backdrop-blur">
                  <span class="relative flex h-1.5 w-1.5">
                    <span class="absolute inline-flex h-full w-full animate-ping rounded-full bg-white opacity-75" />
                    <span class="relative inline-flex h-1.5 w-1.5 rounded-full bg-white" />
                  </span>
                  LIVE
                </div>
                <!-- Viewers -->
                <div class="absolute right-3 top-3 flex items-center gap-1.5 rounded-lg bg-black/40 px-2.5 py-1 text-[11px] font-medium text-white/90 backdrop-blur">
                  <Users class="h-3 w-3" />
                  2 413
                </div>
                <!-- CTA overlay — animated attention grabber -->
                <div class="hero-cta-banner absolute bottom-3 left-3 right-3 overflow-hidden rounded-lg border border-brand-400/40 bg-brand-600/25 px-3 py-2.5 backdrop-blur-md">
                  <!-- Shimmer sweep -->
                  <div class="hero-cta-shimmer pointer-events-none absolute inset-0" />
                  <div class="relative flex items-center justify-between gap-2">
                    <span class="text-xs font-medium text-white/90">Получить чек-лист бесплатно</span>
                    <span class="hero-cta-btn rounded-md bg-brand-500 px-2.5 py-1 text-[10px] font-bold text-white shadow-md shadow-brand-500/30">Скачать</span>
                  </div>
                </div>
              </div>
              <!-- Live chat feed -->
              <div class="border-t border-white/5 px-3 py-2">
                <div class="mb-1.5 flex items-center justify-between">
                  <span class="text-[10px] font-semibold text-white/40">ЧАТ</span>
                  <span class="text-[10px] text-white/30">{{ viewerCount }} зрителей</span>
                </div>
                <div class="live-chat-feed space-y-1.5 overflow-hidden" style="height: 88px">
                  <TransitionGroup name="chat-msg">
                    <div
                      v-for="msg in visibleMessages"
                      :key="msg.id"
                      class="flex items-start gap-2 rounded-lg px-2 py-1.5 transition-all duration-300"
                      :class="msg.pinned ? 'bg-amber-500/10 border border-amber-500/20' : 'hover:bg-white/[0.02]'"
                    >
                      <div
                        class="mt-0.5 flex h-5 w-5 flex-shrink-0 items-center justify-center rounded-full text-[9px] font-bold text-white"
                        :class="msg.avatarColor"
                      >{{ msg.avatar }}</div>
                      <div class="min-w-0 flex-1">
                        <div class="flex items-center gap-1.5">
                          <span class="text-[10px] font-semibold" :class="msg.mod ? 'text-amber-400' : 'text-white/60'">{{ msg.name }}</span>
                          <span v-if="msg.mod" class="rounded bg-amber-500/20 px-1 text-[8px] font-bold text-amber-400">MOD</span>
                          <span v-if="msg.pinned" class="text-[8px] text-amber-400/70">📌</span>
                        </div>
                        <p class="truncate text-[11px] text-white/70">{{ msg.text }}</p>
                      </div>
                      <div v-if="msg.likes" class="flex flex-shrink-0 items-center gap-0.5 text-[10px] text-accent-400/70">
                        ❤️ {{ msg.likes }}
                      </div>
                    </div>
                  </TransitionGroup>
                </div>
              </div>
              <!-- Bottom bar -->
              <div class="flex items-center justify-between border-t border-white/5 px-3 py-2">
                <span class="text-[10px] font-medium text-white/50">Открытый урок: Английский для IT</span>
                <LogoMark :size="16" :animate="false" />
              </div>
            </div>

            <!-- Floating analytics badge -->
            <div class="hero-float-delayed absolute -right-2 -top-6 z-10 rounded-xl border border-white/10 bg-white/5 px-3 py-2 shadow-xl backdrop-blur-md md:-right-4 md:-top-8">
              <div class="flex items-center gap-2">
                <TrendingUp class="h-4 w-4 text-emerald-400" />
                <div>
                  <p class="text-xs font-semibold text-emerald-400">+34%</p>
                  <p class="text-[10px] text-white/50">конверсия</p>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Stats strip -->
        <div :ref="setRevealRef" class="reveal-up mt-20 grid grid-cols-2 gap-4 md:mt-24 lg:grid-cols-4 lg:gap-6">
          <div
            v-for="(s, i) in stats"
            :key="s.label"
            class="group rounded-2xl border border-white/[0.06] bg-white/[0.03] px-5 py-5 backdrop-blur-sm transition-all duration-300 hover:border-white/10 hover:bg-white/[0.06]"
            :style="{ transitionDelay: `${i * 80}ms` }"
          >
            <component :is="s.icon" class="mb-3 h-5 w-5 text-brand-400 transition-colors group-hover:text-brand-300" />
            <dd class="text-2xl font-bold tracking-tight text-white md:text-3xl">{{ s.value }}</dd>
            <dt class="mt-1 text-xs text-slate-500">{{ s.label }}</dt>
          </div>
        </div>
      </div>
    </section>

    <!-- ═══════════════════════════════════════════════════════════════════ -->
    <!-- FEATURES                                                          -->
    <!-- ═══════════════════════════════════════════════════════════════════ -->
    <section id="features" class="relative overflow-hidden bg-slate-950 py-24 md:py-32">
      <!-- Divider gradient -->
      <div class="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-brand-600/40 to-transparent" />

      <div class="mx-auto w-full max-w-6xl px-4">
        <div :ref="setRevealRef" class="reveal-up max-w-2xl">
          <span class="inline-flex items-center gap-2 rounded-full border border-brand-500/20 bg-brand-500/10 px-3 py-1 text-xs font-semibold text-brand-400">
            <Zap class="h-3 w-3" /> Возможности
          </span>
          <h2 class="mt-5 whitespace-nowrap text-3xl font-bold text-white md:text-4xl lg:text-[2.75rem]">
            Всё для эфиров <span class="bg-gradient-to-r from-brand-400 to-accent-400 bg-clip-text text-transparent">на любом масштабе</span>
          </h2>
        </div>

        <div class="mt-16 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <article
            v-for="(f, i) in features"
            :key="f.title"
            :ref="setRevealRef"
            class="reveal-up feature-card group relative overflow-hidden rounded-2xl border border-white/[0.06] bg-white/[0.02] p-6 transition-all duration-500 hover:border-white/10 hover:bg-white/[0.05]"
            :style="{ transitionDelay: `${i * 100}ms` }"
          >
            <!-- Gradient glow on hover -->
            <div
              class="absolute -right-12 -top-12 h-32 w-32 rounded-full opacity-0 blur-3xl transition-opacity duration-500 group-hover:opacity-100"
              :class="`bg-gradient-to-br ${f.color}`"
            />

            <div
              class="relative flex h-11 w-11 items-center justify-center rounded-xl bg-gradient-to-br text-white shadow-lg"
              :class="f.color"
            >
              <component :is="f.icon" class="h-5 w-5" />
            </div>
            <h3 class="relative mt-5 text-base font-semibold text-white">{{ f.title }}</h3>
            <p class="relative mt-2 text-sm leading-relaxed text-slate-400">{{ f.desc }}</p>
          </article>
        </div>
      </div>
    </section>

    <!-- ═══════════════════════════════════════════════════════════════════ -->
    <!-- HOW IT WORKS                                                      -->
    <!-- ═══════════════════════════════════════════════════════════════════ -->
    <section class="relative overflow-hidden bg-slate-950 py-24 md:py-32">
      <div class="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-white/[0.06] to-transparent" />
      <div class="mx-auto w-full max-w-6xl px-4">
        <div :ref="setRevealRef" class="reveal-up max-w-2xl">
          <span class="inline-flex items-center gap-2 rounded-full border border-brand-500/20 bg-brand-500/10 px-3 py-1 text-xs font-semibold text-brand-400">
            <Sparkles class="h-3 w-3" /> Как это работает
          </span>
          <h2 class="mt-5 text-3xl font-bold text-white md:text-4xl lg:text-5xl">
            4 шага до первого эфира
          </h2>
        </div>

        <div class="mt-16 grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
          <div
            v-for="(s, i) in steps"
            :key="s.num"
            :ref="setRevealRef"
            class="reveal-up group relative"
            :style="{ transitionDelay: `${i * 120}ms` }"
          >
            <!-- Connector line -->
            <div
              v-if="i < steps.length - 1"
              class="absolute left-[calc(50%+28px)] top-7 hidden h-px w-[calc(100%-56px)] bg-gradient-to-r from-white/10 to-white/5 lg:block"
            />

            <div class="relative mb-5 flex h-14 w-14 items-center justify-center rounded-2xl border border-white/[0.08] bg-white/[0.04] text-brand-400 transition-all duration-300 group-hover:border-white/[0.15] group-hover:bg-white/[0.08]">
              <component :is="s.icon" class="h-6 w-6" />
              <span class="absolute -right-1 -top-1 flex h-5 w-5 items-center justify-center rounded-full bg-brand-600 text-[10px] font-bold text-white">
                {{ i + 1 }}
              </span>
            </div>
            <h3 class="text-base font-semibold text-white">{{ s.title }}</h3>
            <p class="mt-2 text-sm leading-relaxed text-slate-400">{{ s.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- ═══════════════════════════════════════════════════════════════════ -->
    <!-- USE CASES — Auto-rotating cards                                   -->
    <!-- ═══════════════════════════════════════════════════════════════════ -->
    <section class="relative overflow-hidden bg-slate-950 py-24 md:py-32">
      <div class="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-brand-600/40 to-transparent" />
      <!-- Background orbs -->
      <div class="pointer-events-none absolute -right-40 top-20 h-[500px] w-[500px] rounded-full bg-accent-500/5 blur-[120px]" />
      <div class="pointer-events-none absolute -left-20 bottom-0 h-[400px] w-[400px] rounded-full bg-brand-600/8 blur-[100px]" />

      <div class="mx-auto w-full max-w-6xl px-4">
        <div :ref="setRevealRef" class="reveal-up text-center">
          <span class="inline-flex items-center gap-2 rounded-full border border-brand-500/20 bg-brand-500/10 px-3 py-1 text-xs font-semibold text-brand-400">
            <Users class="h-3 w-3" /> Для кого
          </span>
          <h2 class="mt-5 text-3xl font-bold text-white md:text-4xl lg:text-5xl">
            Webizon подходит <span class="bg-gradient-to-r from-brand-400 to-accent-400 bg-clip-text text-transparent">для каждого</span>
          </h2>
        </div>

        <!-- Cards grid with active highlight -->
        <div class="mt-14 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <article
            v-for="(a, i) in audiences"
            :key="i"
            class="audience-card group cursor-pointer rounded-2xl p-7 transition-all duration-500"
            :class="activeAudience === i
              ? 'bg-white/[0.08] border border-white/[0.15] shadow-lg shadow-brand-500/5 scale-[1.02]'
              : 'bg-white/[0.02] border border-white/[0.05] hover:bg-white/[0.05]'"
            @mouseenter="setAudience(i)"
          >
            <!-- Colored accent dot -->
            <div class="mb-4 flex items-center gap-3">
              <div
                class="h-2.5 w-2.5 rounded-full transition-all duration-500"
                :class="activeAudience === i ? a.dot + ' scale-125 shadow-lg' : 'bg-white/20'"
              />
              <span class="text-[10px] font-semibold uppercase tracking-widest transition-colors duration-300"
                :class="activeAudience === i ? 'text-white/60' : 'text-white/30'"
              >{{ a.tag }}</span>
            </div>
            <h3
              class="text-lg font-bold leading-tight transition-colors duration-300 md:text-xl"
              :class="activeAudience === i ? 'text-white' : 'text-white/60'"
            >{{ a.title }}</h3>
            <p
              class="mt-3 text-sm leading-relaxed transition-colors duration-300"
              :class="activeAudience === i ? 'text-slate-300' : 'text-slate-500'"
            >{{ a.desc }}</p>
          </article>
        </div>

        <!-- Progress dots -->
        <div class="mt-10 flex items-center justify-center gap-2">
          <button
            v-for="(_, i) in audiences"
            :key="i"
            class="h-1.5 rounded-full transition-all duration-500"
            :class="activeAudience === i ? 'w-8 bg-brand-500' : 'w-1.5 bg-white/20 hover:bg-white/40'"
            @click="setAudience(i)"
          />
        </div>
      </div>
    </section>

    <!-- ═══════════════════════════════════════════════════════════════════ -->
    <!-- PRICING — Дружелюбная система оплаты                              -->
    <!-- ═══════════════════════════════════════════════════════════════════ -->
    <section class="relative overflow-hidden bg-slate-950 py-24 md:py-32">
      <div class="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-white/[0.06] to-transparent" />
      <div class="pointer-events-none absolute -left-40 top-20 h-[500px] w-[500px] rounded-full bg-brand-600/8 blur-[120px]" />
      <div class="pointer-events-none absolute -right-20 bottom-0 h-[400px] w-[400px] rounded-full bg-accent-500/5 blur-[100px]" />

      <div class="relative mx-auto w-full max-w-6xl px-4">
        <div :ref="setRevealRef" class="reveal-up text-center">
          <span class="inline-flex items-center gap-2 rounded-full border border-emerald-500/20 bg-emerald-500/10 px-3 py-1 text-xs font-semibold text-emerald-400">
            <CheckCircle2 class="h-3 w-3" /> Честная оплата
          </span>
          <h2 class="mt-5 text-3xl font-bold text-white md:text-4xl lg:text-5xl">
            Дружелюбная система оплаты
          </h2>
          <p class="mx-auto mt-4 max-w-xl text-base text-slate-400">
            Гибкая тарифная сетка, подходящая как новичкам, так и профессионалам. Платите только за результат.
          </p>
        </div>

        <div class="mt-14 grid gap-5 md:grid-cols-3">
          <!-- Card 1: Пробный период -->
          <div
            :ref="setRevealRef"
            class="reveal-up group overflow-hidden rounded-2xl border border-white/[0.06] bg-white/[0.03] p-7 transition-all duration-500 hover:border-white/[0.12] hover:bg-white/[0.06]"
          >
            <h3 class="text-xl font-bold text-white">Пробный период<br />на 14 дней</h3>

            <ul class="mt-6 space-y-3">
              <li class="flex items-start gap-2.5 text-sm text-slate-400">
                <CheckCircle2 class="mt-0.5 h-4 w-4 flex-shrink-0 text-brand-400" />
                Вебинары с бесплатным участием до 10 зрителей
              </li>
              <li class="flex items-start gap-2.5 text-sm text-slate-400">
                <CheckCircle2 class="mt-0.5 h-4 w-4 flex-shrink-0 text-brand-400" />
                Все функции без ограничений
              </li>
              <li class="flex items-start gap-2.5 text-sm text-slate-400">
                <CheckCircle2 class="mt-0.5 h-4 w-4 flex-shrink-0 text-brand-400" />
                Чат, модерация, CTA и аналитика
              </li>
              <li class="flex items-start gap-2.5 text-sm text-slate-400">
                <CheckCircle2 class="mt-0.5 h-4 w-4 flex-shrink-0 text-brand-400" />
                Без привязки карты
              </li>
            </ul>

            <div class="mt-8">
              <p class="text-4xl font-black text-white">0 ₸</p>
            </div>

            <NuxtLink
              to="/auth/sign-up"
              class="mt-6 flex w-full items-center justify-center rounded-xl border-2 border-brand-500 py-3 text-sm font-semibold text-brand-400 transition-all duration-200 hover:bg-brand-600 hover:border-brand-600 hover:text-white"
            >
              Попробовать
            </NuxtLink>
          </div>

          <!-- Card 2: Гибкий тариф (highlighted with rotating border) -->
          <div
            :ref="setRevealRef"
            class="reveal-up glow-border group relative overflow-hidden rounded-2xl bg-slate-900 p-7 transition-all duration-500"
            style="transition-delay: 100ms"
          >
            <div class="flex items-center gap-3">
              <h3 class="text-xl font-bold text-white">Гибкий тариф</h3>
              <span class="rounded-full bg-accent-500 px-2.5 py-0.5 text-[10px] font-bold uppercase tracking-wider text-white">выгодно</span>
            </div>

            <p class="mt-3 text-sm text-slate-400">
              Платите пропорционально объёму зрителей. Без переплат за нерабочее время.
            </p>

            <div class="mt-6 space-y-4">
              <div>
                <div class="flex items-center gap-2 text-sm text-slate-400">
                  <CheckCircle2 class="h-4 w-4 text-emerald-400" />
                  Live-эфир
                </div>
                <p class="ml-6 text-2xl font-black text-white">15 ₸ <span class="text-sm font-normal text-slate-500">/ зритель</span></p>
              </div>
              <div>
                <div class="flex items-center gap-2 text-sm text-slate-400">
                  <CheckCircle2 class="h-4 w-4 text-emerald-400" />
                  Авто-повтор
                </div>
                <p class="ml-6 text-2xl font-black text-white">10 ₸ <span class="text-sm font-normal text-slate-500">/ зритель</span></p>
              </div>
              <div>
                <div class="flex items-center gap-2 text-sm text-slate-400">
                  <CheckCircle2 class="h-4 w-4 text-emerald-400" />
                  Нет зрителей
                </div>
                <p class="ml-6 text-2xl font-black text-white">0 ₸</p>
              </div>
            </div>

            <NuxtLink
              to="/auth/sign-up"
              class="group/btn mt-6 flex w-full items-center justify-center gap-2 rounded-xl bg-brand-600 py-3 text-sm font-semibold text-white shadow-lg shadow-brand-600/25 transition-all duration-200 hover:bg-brand-500"
            >
              Начать бесплатно
              <ArrowRight class="h-4 w-4 transition-transform group-hover/btn:translate-x-0.5" />
            </NuxtLink>
          </div>

          <!-- Card 3: Info cards stacked -->
          <div class="flex flex-col gap-5" style="transition-delay: 200ms">
            <div
              :ref="setRevealRef"
              class="reveal-up flex-1 rounded-2xl border border-white/[0.06] bg-white/[0.03] p-7 transition-all duration-500 hover:border-white/[0.12] hover:bg-white/[0.06]"
            >
              <h3 class="text-lg font-bold text-white">Любая карта, любая страна</h3>
              <p class="mt-2 text-sm text-slate-400">
                Visa, Mastercard, Kaspi. Оплата из любой точки мира. Счёт для юрлиц.
              </p>
              <div class="mt-4 flex items-center gap-2">
                <div class="flex h-7 w-10 items-center justify-center rounded bg-blue-500/10 text-[10px] font-bold text-blue-400">VISA</div>
                <div class="flex h-7 w-10 items-center justify-center rounded bg-orange-500/10 text-[10px] font-bold text-orange-400">MC</div>
                <div class="flex h-7 w-12 items-center justify-center rounded bg-green-500/10 text-[10px] font-bold text-green-400">Kaspi</div>
              </div>
            </div>

            <div
              :ref="setRevealRef"
              class="reveal-up flex-1 rounded-2xl border border-white/[0.06] bg-white/[0.03] p-7 transition-all duration-500 hover:border-white/[0.12] hover:bg-white/[0.06]"
            >
              <h3 class="text-lg font-bold text-white">Прозрачный расчёт</h3>
              <p class="mt-2 text-sm text-slate-400">
                Детальная статистика расходов по каждому эфиру. Никаких сюрпризов в счёте.
              </p>
              <div class="mt-3 flex items-center gap-1.5 text-sm font-medium text-brand-400">
                <BarChart3 class="h-4 w-4" />
                Аналитика в реальном времени
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ═══════════════════════════════════════════════════════════════════ -->
    <!-- FINAL CTA                                                         -->
    <!-- ═══════════════════════════════════════════════════════════════════ -->
    <section class="relative overflow-hidden bg-slate-950 py-24 md:py-32">
      <!-- Orbital rings background -->
      <div class="pointer-events-none absolute inset-0">
        <div class="absolute left-1/2 top-1/2 h-[500px] w-[500px] -translate-x-1/2 -translate-y-1/2 rounded-full border border-white/[0.03]" />
        <div class="absolute left-1/2 top-1/2 h-[700px] w-[700px] -translate-x-1/2 -translate-y-1/2 rounded-full border border-white/[0.02]" />
        <div class="absolute left-1/2 top-1/2 h-[400px] w-[400px] -translate-x-1/2 -translate-y-1/2 rounded-full bg-brand-600/10 blur-[80px]" />
      </div>

      <div :ref="setRevealRef" class="reveal-up relative mx-auto w-full max-w-3xl px-4 text-center">
        <LogoMark :size="56" :animate="false" :pulse="true" class="mx-auto mb-8" />
        <h2 class="text-3xl font-bold text-white md:text-4xl lg:text-5xl">
          Запустите эфир<br />и платите только за результат
        </h2>
        <p class="mx-auto mt-5 max-w-lg text-base text-slate-400 md:text-lg">
          30 секунд на регистрацию. 14 дней бесплатно. Ноль расходов, пока нет зрителей.
        </p>
        <div class="mt-10 flex flex-col items-center justify-center gap-3 sm:flex-row">
          <NuxtLink
            to="/auth/sign-up"
            class="group inline-flex items-center justify-center gap-2 rounded-xl bg-white px-8 py-4 text-sm font-semibold text-slate-900 shadow-xl transition-all duration-300 hover:shadow-white/20 hover:-translate-y-0.5"
          >
            Начать бесплатно
            <ArrowRight class="h-4 w-4 transition-transform duration-300 group-hover:translate-x-0.5" />
          </NuxtLink>
        </div>
      </div>
    </section>
  </div>
</template>

<style>
/* ================================================================== */
/* Hero orbital rings — echoing the logo's ring motif                  */
/* ================================================================== */
.hero-ring {
  position: absolute;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.04);
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
}
.hero-ring-1 {
  width: 600px; height: 600px;
  animation: hero-ring-spin 40s linear infinite;
}
.hero-ring-2 {
  width: 900px; height: 900px;
  border-color: rgba(255, 255, 255, 0.025);
  animation: hero-ring-spin 60s linear infinite reverse;
}
.hero-ring-3 {
  width: 1200px; height: 1200px;
  border-color: rgba(255, 255, 255, 0.015);
  animation: hero-ring-spin 80s linear infinite;
}

@keyframes hero-ring-spin {
  from { transform: translate(-50%, -50%) rotate(0deg); }
  to   { transform: translate(-50%, -50%) rotate(360deg); }
}

/* ================================================================== */
/* Hero entrance animations                                            */
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
.hero-cta {
  opacity: 0;
  transform: translateY(16px);
  animation: hero-slide-up 0.7s cubic-bezier(0.16, 1, 0.3, 1) 0.5s forwards;
}
.hero-visual {
  opacity: 0;
  transform: translateY(24px) scale(0.97);
  animation: hero-visual-in 0.9s cubic-bezier(0.16, 1, 0.3, 1) 0.3s forwards;
}
.hero-float {
  opacity: 0;
  transform: translateY(12px);
  animation: hero-slide-up 0.6s cubic-bezier(0.16, 1, 0.3, 1) 0.8s forwards;
}
.hero-float-delayed {
  opacity: 0;
  transform: translateY(12px);
  animation: hero-slide-up 0.6s cubic-bezier(0.16, 1, 0.3, 1) 0.95s forwards;
}

@keyframes hero-slide-up {
  to { opacity: 1; transform: translateY(0); }
}
@keyframes hero-visual-in {
  to { opacity: 1; transform: translateY(0) scale(1); }
}

/* ================================================================== */
/* Scroll-triggered reveal                                             */
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

/* Stagger children inside revealed containers */
.reveal-up.revealed .feature-card {
  opacity: 1;
  transform: translateY(0);
}

/* ================================================================== */
/* Gradient text animation                                             */
/* ================================================================== */
.hero-gradient-text {
  background-size: 200% 200%;
  animation: gradient-shift 4s ease-in-out infinite;
}
@keyframes gradient-shift {
  0%, 100% { background-position: 0% 50%; }
  50%      { background-position: 100% 50%; }
}

/* ================================================================== */
/* Rotating glow border for pricing card                               */
/* ================================================================== */
.glow-border {
  position: relative;
  z-index: 0;
}
.glow-border::before {
  content: '';
  position: absolute;
  inset: -2px;
  border-radius: 1.125rem;
  padding: 2px;
  background: conic-gradient(
    from var(--glow-angle, 0deg),
    transparent 0%,
    #215CFF 15%,
    #FF428D 30%,
    #215CFF 45%,
    transparent 60%
  );
  -webkit-mask: linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0);
  mask: linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0);
  -webkit-mask-composite: xor;
  mask-composite: exclude;
  animation: glow-rotate 4s linear infinite;
  z-index: -1;
}
.glow-border::after {
  content: '';
  position: absolute;
  inset: -8px;
  border-radius: 1.5rem;
  background: conic-gradient(
    from var(--glow-angle, 0deg),
    transparent 0%,
    rgba(33,92,255,0.15) 15%,
    rgba(255,66,141,0.1) 30%,
    rgba(33,92,255,0.15) 45%,
    transparent 60%
  );
  filter: blur(12px);
  animation: glow-rotate 4s linear infinite;
  z-index: -2;
}

@property --glow-angle {
  syntax: '<angle>';
  initial-value: 0deg;
  inherits: false;
}
@keyframes glow-rotate {
  to { --glow-angle: 360deg; }
}

/* ================================================================== */
/* Hero CTA banner — gentle float + shimmer                            */
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
/* Live chat message transitions                                       */
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
</style>
