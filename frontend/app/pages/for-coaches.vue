<script setup lang="ts">
/**
 * Landing page for the "Coaches & Experts" buyer persona.
 *
 * <p>Emphasises conversion-oriented webinars: CTA timing, watch-time analytics,
 * and the ability to rerun the same presentation multiple times without
 * re-recording. Simpler messaging than the schools page — individual coaches
 * care about ease-of-use and conversion rate, not multi-tenant architecture.
 */
import {
  ArrowRight,
  CheckCircle2,
  Briefcase,
  MousePointerClick,
  BarChart3,
  RefreshCw,
  Timer,
  MessageSquare,
} from 'lucide-vue-next'

useHead({
  title: 'Webizon для коучей и экспертов',
  meta: [
    { name: 'description', content: 'Проводите мастер-классы и вебинары с конверсионными CTA. Аналитика показывает, кто готов к покупке. Авто-повторы — без повторной записи.' },
  ],
})

const benefits = [
  {
    icon: MousePointerClick,
    title: 'CTA в нужный момент',
    desc: 'Кнопки, файлы и ссылки появляются по таймлайну: чек-лист на 5-й минуте, консультация на 25-й, покупка курса на 40-й. Мягкий → тёплый → горячий CTA.',
  },
  {
    icon: BarChart3,
    title: 'Кто готов к покупке?',
    desc: 'Смотрите, кто досмотрел до конца, кто кликнул CTA, кто задал вопрос. Retention-графики помогают оптимизировать контент и размещение CTA.',
  },
  {
    icon: RefreshCw,
    title: 'Один эфир — много показов',
    desc: 'Запишите мастер-класс один раз. Webizon покажет его снова в выбранных слотах с историческим чатом и CTA — как настоящий live.',
  },
  {
    icon: MessageSquare,
    title: 'Живой чат без хаоса',
    desc: 'Slow mode, модерация, ответы на сообщения. Чат работает и в live, и в авто-сессиях. YouTube-чат не используется — полный контроль за вами.',
  },
]

const workflow = [
  { step: '1', text: 'Создайте событие: тема, обложка, дата эфира' },
  { step: '2', text: 'Проведите эфир через OBS + YouTube Live' },
  { step: '3', text: 'Добавьте CTA по таймлайну: файл, ссылка, форма' },
  { step: '4', text: 'Настройте авто-повторы для тех, кто пропустил' },
  { step: '5', text: 'Смотрите аналитику: retention, клики CTA, лид-сигналы' },
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
            <Briefcase class="h-3.5 w-3.5" />
            Для коучей и экспертов
          </div>
          <h1 class="text-4xl font-bold leading-[1.15] tracking-tight text-slate-900 md:text-5xl">
            Вебинары, которые<br />
            <span class="text-brand-600">продают за вас.</span>
          </h1>
          <p class="mt-6 max-w-2xl text-lg leading-relaxed text-slate-600">
            Проведите мастер-класс с конверсионными CTA, аналитикой просмотров
            и авто-повторами. Webizon покажет, кто из зрителей готов к покупке.
          </p>
          <div class="mt-8 flex flex-col gap-3 sm:flex-row">
            <NuxtLink to="/auth/sign-up" class="btn-primary !px-6 !py-3 !text-base">
              Начать бесплатно
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

    <!-- Workflow -->
    <section class="py-20 md:py-28">
      <div class="mx-auto w-full max-w-3xl px-4">
        <h2 class="text-center text-2xl font-bold text-slate-900 md:text-3xl">
          5 шагов — от идеи до конверсии
        </h2>
        <ol class="mt-10 space-y-4">
          <li
            v-for="w in workflow"
            :key="w.step"
            class="flex items-start gap-4 rounded-xl border border-slate-200 bg-white p-4 shadow-soft"
          >
            <span class="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-lg bg-brand-600 text-sm font-bold text-white">
              {{ w.step }}
            </span>
            <span class="text-sm leading-relaxed text-slate-700">{{ w.text }}</span>
          </li>
        </ol>
      </div>
    </section>

    <!-- Pricing note -->
    <section class="bg-white py-16 md:py-20">
      <div class="mx-auto w-full max-w-4xl px-4">
        <div class="card flex flex-col items-center p-8 text-center md:p-10">
          <Timer class="h-10 w-10 text-brand-600" />
          <h2 class="mt-4 text-2xl font-bold text-slate-900">14 дней бесплатно</h2>
          <p class="mt-3 max-w-lg text-slate-600">
            Без привязки карты. Платите только за реальных зрителей после пробного периода.
            Live — 15 ₸, авто — 10 ₸ за просмотр.
          </p>
          <div class="mt-6 flex flex-col gap-3 sm:flex-row">
            <NuxtLink to="/auth/sign-up" class="btn-primary">
              Создать аккаунт
              <ArrowRight class="h-4 w-4" />
            </NuxtLink>
            <NuxtLink to="/pricing" class="btn-ghost">
              Подробнее о ценах
            </NuxtLink>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>
