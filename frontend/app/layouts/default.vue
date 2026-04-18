<script setup lang="ts">
const { appName } = useRuntimeConfig().public
</script>

<template>
  <div class="min-h-screen flex flex-col">
    <UiToastContainer />
    <header class="absolute inset-x-0 top-0 z-50 border-b border-white/5">
      <div class="mx-auto flex h-16 w-full max-w-6xl items-center justify-between px-4">
        <LogoFull :size="30" dark />
        <div class="flex items-center gap-2">
          <NuxtLink to="/auth/sign-in" class="inline-flex items-center justify-center gap-2 rounded-lg px-4 py-2 text-sm font-medium text-white/70 transition-colors hover:text-white">
            Войти
          </NuxtLink>
          <NuxtLink to="/auth/sign-up" class="inline-flex items-center justify-center gap-2 rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-brand-500">
            Начать бесплатно
          </NuxtLink>
        </div>
      </div>
    </header>

    <main class="flex-1">
      <slot />
    </main>

    <footer class="border-t border-slate-800 bg-slate-950">
      <div class="mx-auto w-full max-w-6xl px-4 py-8">
        <div class="flex flex-col items-center justify-between gap-4 sm:flex-row">
          <LogoFull :size="24" :animate="false" dark />
          <p class="text-xs text-slate-500">
            © {{ new Date().getFullYear() }} {{ appName }}. Казахстан, Алматы.
          </p>
        </div>
      </div>
    </footer>
  </div>
</template>
