<script setup lang="ts">
/**
 * Landing page for the "Online Schools" buyer persona.
 *
 * <p>Highlights auto-replay for timezone coverage, moderation at scale,
 * and CRM lead signals — the three things that matter most to EdTech
 * marketing teams running free-lesson funnels.
 */
import {
  ArrowRight,
  CheckCircle2,
  GraduationCap,
  RefreshCw,
  Shield,
  BarChart3,
  Users,
  Zap,
} from 'lucide-vue-next'

useHead({
  title: 'Webizon для онлайн-школ',
  meta: [
    { name: 'description', content: 'Платформа для бесплатных уроков, марафонов и открытых занятий. Авто-повторы для разных часовых поясов, модерация на 60K+ зрителей и лид-сигналы для CRM.' },
  ],
})

const benefits = [
  {
    icon: RefreshCw,
    title: 'Авто-повторы по расписанию',
    desc: 'Проведите один live-урок. Покажите его снова в удобных слотах для разных городов. Чат и CTA воспроизводятся по таймлайну — зрители чувствуют себя на живом эфире.',
  },
  {
    icon: Shield,
    title: 'Модерация на масштабе',
    desc: 'Предупреждение, мут, чат-бан, удаление сообщений — с журналом аудита и быстрыми действиями. Slow mode снижает нагрузку модераторов при пиковых 60K+ участников.',
  },
  {
    icon: BarChart3,
    title: 'Лид-сигналы для CRM',
    desc: 'Кто смотрел больше 30 минут? Кто кликнул CTA? Кто вернулся на повтор? Поведенческие сигналы генерируются автоматически и передаются в вашу воронку.',
  },
  {
    icon: Users,
    title: 'Единая идентичность',
    desc: 'Зрители авторизуются через вашу систему. Один profileId на все модули: чат, аналитика, CRM и академический прогресс. Никаких гостей.',
  },
]

const checklist = [
  'YouTube CDN для видео — нет нагрузки на ваш сервер',
  'Кастомный чат с ролями, ответами и историей',
  'CTA-движок: файлы, ссылки, формы — по таймлайну',
  'Retention-графики и пиковые зрители',
  'Мультитенантность — каждая школа в своём пространстве',
  'Оплата за факт: 15 ₸ за live-зрителя, 10 ₸ за авто',
]
</script>

<template>
  <div>
    <!-- Hero -->
    <section class="relative overflow-hidden">
      <div class="absolute inset-0 -z-10 bg-gradient-to-b from-brand-50/50 via-white to-white" />
      <div class="mx-auto w-full max-w-6xl px-4 pb-16 pt-20 md:pb-24 md:pt-28">
        <div class="max-w-3xl">
          <div class="mb-4 inline-flex items-center gap-2 rounded-full bg-brand-50 px-3 py-1 text-xs font-medium text-brand-700">
            <GraduationCap class="h-3.5 w-3.5" />
            Для онлайн-школ
          </div>
          <h1 class="text-4xl font-bold leading-[1.15] tracking-tight text-slate-900 md:text-5xl">
            Бесплатные уроки и марафоны<br />
            <span class="text-brand-600">на тысячи зрителей.</span>
          </h1>
          <p class="mt-6 max-w-2xl text-lg leading-relaxed text-slate-600">
            Webizon создан для EdTech-воронок. Проведите один live-урок, настройте авто-повторы
            для разных часовых поясов и получите поведенческие сигналы для квалификации лидов.
          </p>
          <div class="mt-8 flex flex-col gap-3 sm:flex-row">
            <NuxtLink to="/auth/sign-up" class="btn-primary !px-6 !py-3 !text-base">
              Попробовать бесплатно
              <ArrowRight class="h-4 w-4" />
            </NuxtLink>
            <NuxtLink to="/" class="btn-ghost !px-6 !py-3 !text-base">
              Все возможности
            </NuxtLink>
          </div>
        </div>
      </div>
    </section>

    <!-- Benefits -->
    <section class="bg-white py-20 md:py-28">
      <div class="mx-auto w-full max-w-6xl px-4">
        <div class="grid gap-8 md:grid-cols-2">
          <article
            v-for="b in benefits"
            :key="b.title"
            class="card card-hover p-6"
          >
            <div class="flex h-10 w-10 items-center justify-center rounded-xl bg-brand-50 text-brand-600">
              <component :is="b.icon" class="h-5 w-5" />
            </div>
            <h3 class="mt-4 text-lg font-semibold text-slate-900">{{ b.title }}</h3>
            <p class="mt-2 text-sm leading-relaxed text-slate-600">{{ b.desc }}</p>
          </article>
        </div>
      </div>
    </section>

    <!-- Checklist -->
    <section class="py-20 md:py-28">
      <div class="mx-auto w-full max-w-4xl px-4">
        <h2 class="text-center text-2xl font-bold text-slate-900 md:text-3xl">
          Что вы получаете
        </h2>
        <ul class="mt-10 grid gap-4 sm:grid-cols-2">
          <li
            v-for="item in checklist"
            :key="item"
            class="flex items-start gap-3 rounded-xl border border-slate-200 bg-white p-4 shadow-soft"
          >
            <CheckCircle2 class="mt-0.5 h-5 w-5 flex-shrink-0 text-success-500" />
            <span class="text-sm text-slate-700">{{ item }}</span>
          </li>
        </ul>
      </div>
    </section>

    <!-- CTA -->
    <section class="bg-brand-600 py-16 md:py-20">
      <div class="mx-auto w-full max-w-3xl px-4 text-center">
        <h2 class="text-2xl font-bold text-white md:text-3xl">
          Запустите бесплатный урок уже сегодня
        </h2>
        <p class="mt-3 text-brand-100">
          14 дней бесплатно. Без карты. Без обязательств.
        </p>
        <NuxtLink to="/auth/sign-up" class="btn mt-8 bg-white text-brand-700 hover:bg-brand-50 !px-6 !py-3 !text-base">
          Создать аккаунт
          <ArrowRight class="h-4 w-4" />
        </NuxtLink>
      </div>
    </section>
  </div>
</template>
