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
      <div class="mx-auto w-full max-w-6xl px-4 py-8 text-sm text-slate-500">
        © {{ new Date().getFullYear() }} {{ appName }}. Казахстан, Алматы.
      </div>
    </footer>
  </div>
</template>
