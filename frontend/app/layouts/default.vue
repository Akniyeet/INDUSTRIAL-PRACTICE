<script setup lang="ts">
const { appName } = useRuntimeConfig().public
</script>

<template>
  <div class="min-h-screen flex flex-col">
    <UiToastContainer />
    <header class="border-b border-slate-200 bg-white/80 backdrop-blur">
      <div class="mx-auto flex h-16 w-full max-w-6xl items-center justify-between px-4">
        <NuxtLink to="/" class="flex items-center gap-2 font-semibold text-brand-700">
          <span class="inline-block h-3 w-3 rounded-full bg-brand-600" />
          {{ appName }}
        </NuxtLink>
        <nav class="hidden items-center gap-6 text-sm text-slate-600 md:flex">
          <NuxtLink to="/pricing" class="hover:text-slate-900">Цены</NuxtLink>
          <NuxtLink to="/for-schools" class="hover:text-slate-900">Для школ</NuxtLink>
          <NuxtLink to="/for-coaches" class="hover:text-slate-900">Для коучей</NuxtLink>
        </nav>
        <div class="flex items-center gap-2">
          <NuxtLink to="/auth/sign-in" class="btn-ghost">Войти</NuxtLink>
          <NuxtLink to="/auth/sign-up" class="btn-primary">Начать бесплатно</NuxtLink>
        </div>
      </div>
    </header>

    <main class="flex-1">
      <slot />
    </main>

    <footer class="border-t border-slate-200 bg-white">
      <div class="mx-auto w-full max-w-6xl px-4 py-10">
        <div class="grid gap-8 sm:grid-cols-2 md:grid-cols-4">
          <!-- Brand -->
          <div>
            <NuxtLink to="/" class="flex items-center gap-2 font-semibold text-brand-700">
              <span class="inline-block h-3 w-3 rounded-full bg-brand-600" />
              {{ appName }}
            </NuxtLink>
            <p class="mt-3 text-sm text-slate-500">
              Платформа для вебинаров и образовательных эфиров.
            </p>
          </div>

          <!-- Product -->
          <div>
            <h4 class="text-xs font-semibold uppercase tracking-widest text-slate-400">Продукт</h4>
            <nav class="mt-3 flex flex-col gap-2 text-sm text-slate-600">
              <NuxtLink to="/#features" class="hover:text-slate-900">Возможности</NuxtLink>
              <NuxtLink to="/pricing" class="hover:text-slate-900">Цены</NuxtLink>
            </nav>
          </div>

          <!-- Use cases -->
          <div>
            <h4 class="text-xs font-semibold uppercase tracking-widest text-slate-400">Решения</h4>
            <nav class="mt-3 flex flex-col gap-2 text-sm text-slate-600">
              <NuxtLink to="/for-schools" class="hover:text-slate-900">Для школ</NuxtLink>
              <NuxtLink to="/for-coaches" class="hover:text-slate-900">Для коучей</NuxtLink>
            </nav>
          </div>

          <!-- Legal -->
          <div>
            <h4 class="text-xs font-semibold uppercase tracking-widest text-slate-400">Компания</h4>
            <nav class="mt-3 flex flex-col gap-2 text-sm text-slate-600">
              <NuxtLink to="/auth/sign-in" class="hover:text-slate-900">Войти</NuxtLink>
              <NuxtLink to="/auth/sign-up" class="hover:text-slate-900">Регистрация</NuxtLink>
            </nav>
          </div>
        </div>

        <div class="mt-8 border-t border-slate-100 pt-6 text-xs text-slate-400">
          © {{ new Date().getFullYear() }} {{ appName }}. Казахстан, Алматы.
        </div>
      </div>
    </footer>
  </div>
</template>
