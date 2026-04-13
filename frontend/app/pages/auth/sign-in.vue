<script setup lang="ts">
definePageMeta({ layout: false })
useHead({ title: 'Вход — Webizon' })

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const email    = ref('')
const password = ref('')
const showPass = ref(false)
const loading  = ref(false)
const error    = ref<string | null>(null)

const redirectTo = computed(() => (route.query.redirect as string) || '/admin')

// Direct login — no OTP for sign-in
async function submit() {
  error.value = null
  loading.value = true
  try {
    await auth.login(email.value.trim(), password.value)
    await router.push(redirectTo.value)
  } catch (e: unknown) {
    const err = e as { data?: { detail?: string } }
    error.value = err?.data?.detail ?? 'Неверный email или пароль'
  } finally {
    loading.value = false
  }
}

function loginWithGoogle() {
  auth.startGoogleLogin(window.location.origin + '/auth/callback', redirectTo.value)
}
</script>

<template>
  <div class="relative flex min-h-screen items-center justify-center bg-slate-950 px-4 py-12">
    <!-- Background -->
    <div class="pointer-events-none absolute inset-0 overflow-hidden">
      <div class="absolute -left-40 top-20 h-[500px] w-[500px] rounded-full bg-brand-600/10 blur-[120px]" />
      <div class="absolute -right-20 bottom-10 h-[400px] w-[400px] rounded-full bg-accent-500/8 blur-[100px]" />
      <div class="absolute left-1/2 top-1/3 h-[300px] w-[300px] -translate-x-1/2 rounded-full bg-violet-600/5 blur-[80px]" />
      <div class="absolute inset-0 bg-[linear-gradient(rgba(255,255,255,.015)_1px,transparent_1px),linear-gradient(90deg,rgba(255,255,255,.015)_1px,transparent_1px)] bg-[size:48px_48px]" />
    </div>

    <div class="relative w-full max-w-md">
      <div class="mb-10 flex justify-center">
        <LogoFull :size="44" dark />
      </div>

      <div class="rounded-2xl border border-white/[0.08] bg-white/[0.04] p-8 shadow-2xl backdrop-blur-md">
          <h1 class="text-center text-2xl font-bold text-white">Вход в аккаунт</h1>
          <p class="mt-1.5 text-center text-sm text-slate-400">Рады видеть вас снова</p>

          <!-- Google -->
          <button type="button" class="mt-8 flex w-full items-center justify-center gap-3 rounded-xl border border-white/[0.08] bg-white/[0.04] px-4 py-3 text-sm font-medium text-white/80 transition-all duration-200 hover:border-white/[0.15] hover:bg-white/[0.08] active:scale-[.98]" @click="loginWithGoogle">
            <svg class="h-5 w-5 flex-shrink-0" viewBox="0 0 24 24">
              <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4"/>
              <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/>
              <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l3.66-2.84z" fill="#FBBC05"/>
              <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/>
            </svg>
            Продолжить с Google
          </button>

          <div class="my-6 flex items-center gap-3">
            <div class="h-px flex-1 bg-white/[0.08]" /><span class="text-xs text-slate-500">или войдите с паролем</span><div class="h-px flex-1 bg-white/[0.08]" />
          </div>

          <div v-if="error" class="mb-5 rounded-xl border border-red-500/20 bg-red-500/10 px-4 py-3 text-sm text-red-400">{{ error }}</div>

          <form class="space-y-4" @submit.prevent="submit">
            <div>
              <label class="mb-1.5 block text-sm font-medium text-slate-300">Email</label>
              <input v-model="email" type="email" autocomplete="email" required placeholder="you@example.com"
                class="w-full rounded-xl border border-white/[0.08] bg-white/[0.04] px-4 py-3 text-sm text-white placeholder:text-slate-500 transition-all focus:border-brand-500/50 focus:bg-white/[0.06] focus:outline-none focus:ring-2 focus:ring-brand-500/20" />
            </div>
            <div>
              <label class="mb-1.5 block text-sm font-medium text-slate-300">Пароль</label>
              <div class="relative">
                <input v-model="password" :type="showPass ? 'text' : 'password'" autocomplete="current-password" required placeholder="••••••••"
                  class="w-full rounded-xl border border-white/[0.08] bg-white/[0.04] px-4 py-3 pr-11 text-sm text-white placeholder:text-slate-500 transition-all focus:border-brand-500/50 focus:bg-white/[0.06] focus:outline-none focus:ring-2 focus:ring-brand-500/20" />
                <button type="button" tabindex="-1" class="absolute right-3.5 top-1/2 -translate-y-1/2 text-slate-500 transition hover:text-slate-300" @click="showPass = !showPass">
                  <svg v-if="showPass" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="1.8" viewBox="0 0 24 24"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
                  <svg v-else class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="1.8" viewBox="0 0 24 24"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                </button>
              </div>
            </div>
            <button type="submit" :disabled="loading"
              class="mt-2 w-full rounded-xl bg-brand-600 px-4 py-3 text-sm font-semibold text-white shadow-lg shadow-brand-600/25 transition-all duration-200 hover:bg-brand-500 hover:shadow-brand-500/30 active:scale-[.98] disabled:opacity-60">
              <span v-if="!loading">Войти</span>
              <span v-else class="flex items-center justify-center gap-2">
                <svg class="h-4 w-4 animate-spin" fill="none" viewBox="0 0 24 24"><circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/><path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"/></svg>
                Вход...
              </span>
            </button>
          </form>

          <p class="mt-6 text-center text-sm text-slate-500">
            Нет аккаунта?
            <NuxtLink :to="{ path: '/auth/sign-up', query: route.query }" class="font-semibold text-brand-400 hover:text-brand-300">Зарегистрироваться бесплатно</NuxtLink>
          </p>
        </div>

      <p class="mt-8 text-center text-xs text-slate-600">© 2026 Webizon. Казахстан.</p>
    </div>
  </div>
</template>

