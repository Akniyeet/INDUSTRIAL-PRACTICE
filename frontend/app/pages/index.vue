<script setup lang="ts">
useHead({ title: 'Webizon — вебинары без лимитов' })
</script>

<template>
  <section class="mx-auto w-full max-w-6xl px-4 py-20">
    <div class="grid gap-16 md:grid-cols-2 md:items-center">
      <div>
        <p class="mb-4 inline-flex rounded-full bg-brand-50 px-3 py-1 text-xs font-medium text-brand-700">
          14 дней бесплатно · Оплата за факт
        </p>
        <h1 class="text-4xl font-bold leading-tight text-slate-900 md:text-5xl">
          Проводите вебинары<br />
          <span class="text-brand-600">без лимита зрителей.</span>
        </h1>
        <p class="mt-6 max-w-xl text-lg text-slate-600">
          Webizon выдерживает 60&nbsp;000 одновременных подключений в одной сессии.
          Живой чат, авто-повторы, CTA и аналитика — всё в одной платформе.
          Платите только за то, что реально использовали.
        </p>
        <div class="mt-8 flex flex-col gap-3 sm:flex-row">
          <NuxtLink to="/auth/sign-up" class="btn-primary">Начать бесплатно</NuxtLink>
          <NuxtLink to="/pricing" class="btn-ghost">Посмотреть цены</NuxtLink>
        </div>
        <dl class="mt-10 grid max-w-md grid-cols-3 gap-6 text-sm">
          <div>
            <dt class="text-slate-500">Живых зрителей</dt>
            <dd class="mt-1 text-xl font-semibold text-slate-900">60K+</dd>
          </div>
          <div>
            <dt class="text-slate-500">Латентность чата</dt>
            <dd class="mt-1 text-xl font-semibold text-slate-900">&lt; 150&nbsp;мс</dd>
          </div>
          <div>
            <dt class="text-slate-500">Uptime SLA</dt>
            <dd class="mt-1 text-xl font-semibold text-slate-900">99.9%</dd>
          </div>
        </dl>
      </div>

      <div class="card p-6">
        <div class="aspect-video w-full rounded-xl bg-gradient-to-br from-brand-500 to-brand-800" />
        <div class="mt-4 flex items-center justify-between text-sm text-slate-600">
          <span>«Открытый урок: Английский для IT»</span>
          <span class="rounded-full bg-red-50 px-2 py-0.5 text-xs font-medium text-red-600">
            LIVE · 2 413 зрителей
          </span>
        </div>
      </div>
    </div>
  </section>
</template>
