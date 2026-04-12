<script setup lang="ts">
/**
 * Webizon — main marketing landing page.
 *
 * <p>Targets two buyer personas: online schools running live/auto webinars at
 * scale, and individual coaches/tutors who need a no-fuss broadcast tool with
 * built-in CRM signals. The page follows a classic SaaS flow:
 * Hero → Social proof → Features → How it works → Use cases → Pricing teaser → CTA.
 *
 * <p>All content is static — no API calls. The page inherits the {@code default}
 * layout which provides the global header/footer chrome.
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
  Timer,
  GraduationCap,
  Briefcase,
} from 'lucide-vue-next'

useHead({
  title: 'Webizon — вебинары без лимитов',
  meta: [
    { name: 'description', content: 'Платформа для вебинаров с поддержкой 60 000+ одновременных зрителей. Живой чат, авто-повторы, CTA-движок, аналитика и модерация — всё в одном месте.' },
  ],
})

const features = [
  {
    icon: MessageSquare,
    title: 'Живой чат',
    desc: 'Кастомный чат с модерацией, slow mode, ответами и ролями. YouTube чат не используется — полный контроль за вами.',
  },
  {
    icon: RefreshCw,
    title: 'Авто-повторы',
    desc: 'Запишите один эфир — показывайте его по расписанию. Исторический чат воспроизводится по таймлайну, создавая эффект присутствия.',
  },
  {
    icon: MousePointerClick,
    title: 'CTA-движок',
    desc: 'Кнопки, файлы, формы и ссылки — появляются точно в нужный момент по таймлайну. Приоритеты, стекинг и аналитика кликов.',
  },
  {
    icon: BarChart3,
    title: 'Аналитика',
    desc: 'Retention-графики, пиковые зрители, время просмотра, конверсии CTA и поведенческие сигналы для CRM.',
  },
  {
    icon: Shield,
    title: 'Модерация',
    desc: 'Лестница: предупреждение → мут → чат-бан → удаление. Журнал аудита, фильтры и блокировки в реальном времени.',
  },
  {
    icon: Zap,
    title: '60K+ зрителей',
    desc: 'Centrifugo + Redis обеспечивают доставку чата за < 150 мс. Видео через YouTube CDN — без нагрузки на ваш сервер.',
  },
]

const steps = [
  { num: '01', title: 'Создайте событие', desc: 'Укажите тему, спикера, обложку и расписание. Получите публичную ссылку.' },
  { num: '02', title: 'Проведите эфир', desc: 'Транслируйте через OBS → YouTube Live. Управляйте чатом, CTA и модерацией из админки.' },
  { num: '03', title: 'Настройте повторы', desc: 'Создайте авто-сессии по расписанию. Чат и CTA воспроизводятся по таймлайну автоматически.' },
  { num: '04', title: 'Анализируйте', desc: 'Отчёты по retention, конверсиям и поведению. Сигналы для CRM генерируются автоматически.' },
]

const useCases = [
  {
    icon: GraduationCap,
    title: 'Онлайн-школы',
    desc: 'Бесплатные уроки, марафоны и открытые занятия на 10–60 000 человек. Авто-повторы для разных часовых поясов.',
    link: '/for-schools',
    linkText: 'Подробнее для школ',
  },
  {
    icon: Briefcase,
    title: 'Коучи и эксперты',
    desc: 'Мастер-классы и вебинары с конверсионными CTA. Аналитика помогает понять, кто готов к покупке.',
    link: '/for-coaches',
    linkText: 'Подробнее для коучей',
  },
  {
    icon: Megaphone,
    title: 'Маркетинг-команды',
    desc: 'Лидогенерация через образовательные эфиры. Поведенческие сигналы передаются в CRM для квалификации.',
    link: '#features',
    linkText: 'Смотреть возможности',
  },
]
</script>

<template>
  <div>
    <!-- ═══════════════════════════════════════════════════════════════════ -->
    <!-- HERO                                                              -->
    <!-- ═══════════════════════════════════════════════════════════════════ -->
    <section class="relative overflow-hidden">
      <!-- Subtle gradient background -->
      <div class="absolute inset-0 -z-10 bg-gradient-to-b from-brand-50/60 via-white to-white" />
      <div class="absolute -right-40 -top-40 -z-10 h-[500px] w-[500px] rounded-full bg-brand-100/40 blur-3xl" />

      <div class="mx-auto w-full max-w-6xl px-4 pb-16 pt-20 md:pb-24 md:pt-28">
        <div class="grid gap-12 md:grid-cols-2 md:items-center md:gap-16">
          <div>
            <p class="mb-4 inline-flex items-center gap-2 rounded-full bg-brand-50 px-3 py-1 text-xs font-medium text-brand-700">
              <span class="h-1.5 w-1.5 animate-pulse rounded-full bg-brand-500" />
              14 дней бесплатно
            </p>
            <h1 class="text-4xl font-bold leading-[1.15] tracking-tight text-slate-900 md:text-5xl lg:text-[3.5rem]">
              Проводите вебинары<br />
              <span class="text-brand-600">без лимита зрителей.</span>
            </h1>
            <p class="mt-6 max-w-xl text-lg leading-relaxed text-slate-600">
              Webizon выдерживает 60&nbsp;000 одновременных подключений в одной сессии.
              Живой чат, авто-повторы, CTA-движок и аналитика — всё в одной платформе.
              Платите только за факт.
            </p>
            <div class="mt-8 flex flex-col gap-3 sm:flex-row">
              <NuxtLink to="/auth/sign-up" class="btn-primary !px-6 !py-3 !text-base">
                Начать бесплатно
                <ArrowRight class="h-4 w-4" />
              </NuxtLink>
              <NuxtLink to="/pricing" class="btn-ghost !px-6 !py-3 !text-base">
                Посмотреть цены
              </NuxtLink>
            </div>
          </div>

          <!-- Hero visual: mock room card -->
          <div class="card p-5 md:p-6">
            <div class="relative aspect-video w-full overflow-hidden rounded-xl bg-gradient-to-br from-slate-800 to-slate-900">
              <div class="absolute inset-0 flex items-center justify-center">
                <PlayCircle class="h-16 w-16 text-white/30" />
              </div>
              <!-- Live badge -->
              <div class="absolute left-3 top-3 flex items-center gap-1.5 rounded-md bg-red-600/90 px-2 py-1 text-[11px] font-semibold text-white backdrop-blur">
                <Radio class="h-3 w-3" />
                LIVE
              </div>
              <!-- Viewer count -->
              <div class="absolute right-3 top-3 flex items-center gap-1.5 rounded-md bg-black/50 px-2 py-1 text-[11px] font-medium text-white backdrop-blur">
                <Users class="h-3 w-3" />
                2 413
              </div>
            </div>
            <div class="mt-4 flex items-center justify-between">
              <span class="text-sm font-medium text-slate-700">Открытый урок: Английский для IT</span>
              <span class="rounded-full bg-brand-50 px-2 py-0.5 text-[10px] font-semibold text-brand-600">
                Webizon
              </span>
            </div>
          </div>
        </div>

        <!-- Stats strip -->
        <dl class="mt-16 grid grid-cols-2 gap-6 border-t border-slate-200 pt-10 sm:grid-cols-4">
          <div>
            <dd class="text-2xl font-bold text-slate-900 md:text-3xl">60K+</dd>
            <dt class="mt-1 text-sm text-slate-500">одновременных зрителей</dt>
          </div>
          <div>
            <dd class="text-2xl font-bold text-slate-900 md:text-3xl">&lt; 150 мс</dd>
            <dt class="mt-1 text-sm text-slate-500">латентность чата</dt>
          </div>
          <div>
            <dd class="text-2xl font-bold text-slate-900 md:text-3xl">99.9%</dd>
            <dt class="mt-1 text-sm text-slate-500">uptime SLA</dt>
          </div>
          <div>
            <dd class="text-2xl font-bold text-slate-900 md:text-3xl">15 ₸</dd>
            <dt class="mt-1 text-sm text-slate-500">за реального зрителя</dt>
          </div>
        </dl>
      </div>
    </section>

    <!-- ═══════════════════════════════════════════════════════════════════ -->
    <!-- FEATURES                                                          -->
    <!-- ═══════════════════════════════════════════════════════════════════ -->
    <section id="features" class="bg-white py-20 md:py-28">
      <div class="mx-auto w-full max-w-6xl px-4">
        <div class="max-w-2xl">
          <p class="text-sm font-semibold uppercase tracking-widest text-brand-600">Возможности</p>
          <h2 class="mt-3 text-3xl font-bold text-slate-900 md:text-4xl">
            Всё для проведения эфиров на любом масштабе
          </h2>
          <p class="mt-4 text-lg text-slate-600">
            Видео идёт через YouTube CDN. Чат, модерация, CTA и аналитика — полностью ваши.
          </p>
        </div>

        <div class="mt-14 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          <article
            v-for="f in features"
            :key="f.title"
            class="card card-hover p-6"
          >
            <div class="flex h-10 w-10 items-center justify-center rounded-xl bg-brand-50 text-brand-600">
              <component :is="f.icon" class="h-5 w-5" />
            </div>
            <h3 class="mt-4 text-base font-semibold text-slate-900">{{ f.title }}</h3>
            <p class="mt-2 text-sm leading-relaxed text-slate-600">{{ f.desc }}</p>
          </article>
        </div>
      </div>
    </section>

    <!-- ═══════════════════════════════════════════════════════════════════ -->
    <!-- HOW IT WORKS                                                      -->
    <!-- ═══════════════════════════════════════════════════════════════════ -->
    <section class="py-20 md:py-28">
      <div class="mx-auto w-full max-w-6xl px-4">
        <div class="max-w-2xl">
          <p class="text-sm font-semibold uppercase tracking-widest text-brand-600">Как это работает</p>
          <h2 class="mt-3 text-3xl font-bold text-slate-900 md:text-4xl">
            От первого эфира до аналитики — за 4 шага
          </h2>
        </div>

        <div class="mt-14 grid gap-8 sm:grid-cols-2 lg:grid-cols-4">
          <div v-for="s in steps" :key="s.num" class="relative">
            <span class="text-5xl font-black text-brand-100">{{ s.num }}</span>
            <h3 class="mt-2 text-base font-semibold text-slate-900">{{ s.title }}</h3>
            <p class="mt-2 text-sm leading-relaxed text-slate-600">{{ s.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- ═══════════════════════════════════════════════════════════════════ -->
    <!-- USE CASES                                                         -->
    <!-- ═══════════════════════════════════════════════════════════════════ -->
    <section class="bg-white py-20 md:py-28">
      <div class="mx-auto w-full max-w-6xl px-4">
        <div class="max-w-2xl">
          <p class="text-sm font-semibold uppercase tracking-widest text-brand-600">Для кого</p>
          <h2 class="mt-3 text-3xl font-bold text-slate-900 md:text-4xl">
            Платформа для тех, кто учит и продаёт через эфиры
          </h2>
        </div>

        <div class="mt-14 grid gap-6 md:grid-cols-3">
          <article
            v-for="uc in useCases"
            :key="uc.title"
            class="card card-hover flex flex-col p-6"
          >
            <div class="flex h-10 w-10 items-center justify-center rounded-xl bg-brand-50 text-brand-600">
              <component :is="uc.icon" class="h-5 w-5" />
            </div>
            <h3 class="mt-4 text-base font-semibold text-slate-900">{{ uc.title }}</h3>
            <p class="mt-2 flex-1 text-sm leading-relaxed text-slate-600">{{ uc.desc }}</p>
            <NuxtLink
              :to="uc.link"
              class="mt-4 inline-flex items-center gap-1 text-sm font-medium text-brand-600 hover:text-brand-700"
            >
              {{ uc.linkText }}
              <ArrowRight class="h-3.5 w-3.5" />
            </NuxtLink>
          </article>
        </div>
      </div>
    </section>

    <!-- ═══════════════════════════════════════════════════════════════════ -->
    <!-- PRICING TEASER                                                    -->
    <!-- ═══════════════════════════════════════════════════════════════════ -->
    <section class="py-20 md:py-28">
      <div class="mx-auto w-full max-w-5xl px-4">
        <div class="card overflow-hidden md:flex">
          <div class="flex-1 p-8 md:p-10">
            <p class="text-sm font-semibold uppercase tracking-widest text-brand-600">Честная оплата</p>
            <h2 class="mt-3 text-2xl font-bold text-slate-900 md:text-3xl">
              Платите только за реальных зрителей
            </h2>
            <p class="mt-4 text-slate-600">
              Без пакетов, без абонентской платы, без скрытых лимитов.
              Первые 14 дней — полностью бесплатно.
            </p>
            <ul class="mt-6 space-y-2">
              <li class="flex items-center gap-2 text-sm text-slate-700">
                <CheckCircle2 class="h-4 w-4 flex-shrink-0 text-success-500" />
                Live-зритель — 15 ₸
              </li>
              <li class="flex items-center gap-2 text-sm text-slate-700">
                <CheckCircle2 class="h-4 w-4 flex-shrink-0 text-success-500" />
                Авто-повтор — 10 ₸
              </li>
              <li class="flex items-center gap-2 text-sm text-slate-700">
                <CheckCircle2 class="h-4 w-4 flex-shrink-0 text-success-500" />
                Хранение — 200 ₸ / ГБ в месяц
              </li>
            </ul>
            <NuxtLink to="/pricing" class="btn-primary mt-8">
              Подробнее о ценах
              <ArrowRight class="h-4 w-4" />
            </NuxtLink>
          </div>
          <div class="hidden w-80 bg-gradient-to-br from-brand-500 to-brand-700 md:block">
            <div class="flex h-full items-center justify-center">
              <div class="text-center text-white">
                <Timer class="mx-auto h-12 w-12 opacity-60" />
                <p class="mt-4 text-3xl font-bold">14</p>
                <p class="text-sm opacity-80">дней бесплатно</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ═══════════════════════════════════════════════════════════════════ -->
    <!-- FINAL CTA                                                         -->
    <!-- ═══════════════════════════════════════════════════════════════════ -->
    <section class="bg-slate-900 py-20 md:py-28">
      <div class="mx-auto w-full max-w-3xl px-4 text-center">
        <h2 class="text-3xl font-bold text-white md:text-4xl">
          Готовы запустить первый эфир?
        </h2>
        <p class="mt-4 text-lg text-slate-400">
          Создайте аккаунт за 30 секунд. Настройте событие, добавьте YouTube-ссылку
          и выходите в эфир.
        </p>
        <div class="mt-8 flex flex-col items-center justify-center gap-3 sm:flex-row">
          <NuxtLink to="/auth/sign-up" class="btn bg-white text-slate-900 hover:bg-slate-100 !px-6 !py-3 !text-base">
            Начать бесплатно
            <ArrowRight class="h-4 w-4" />
          </NuxtLink>
          <NuxtLink to="/pricing" class="btn text-slate-300 hover:text-white !px-6 !py-3 !text-base">
            Посмотреть цены
          </NuxtLink>
        </div>
      </div>
    </section>
  </div>
</template>
