<script setup lang="ts">
definePageMeta({ layout: false })
useHead({ title: 'Регистрация — Webizon' })

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const fullName = ref('')
const email = ref('')
const password = ref('')
const confirmPassword = ref('')
const showPass = ref(false)
const loading = ref(false)
const error = ref<string | null>(null)
const alreadyExists = ref(false)   // true when 409 Conflict received
const step = ref<'form' | 'otp'>('form')

// OTP state
const otpDigits = ref(['', '', '', '', '', ''])
const otpInputs = ref<HTMLInputElement[]>([])
const otpResendTimer = ref(0)
let resendInterval: ReturnType<typeof setInterval> | null = null

const redirectTo = computed(() => (route.query.redirect as string) || '/admin')
const passwordsMatch = computed(() => !confirmPassword.value || password.value === confirmPassword.value)
const canSubmit = computed(() => fullName.value.trim() && email.value.trim() && password.value.length >= 8 && password.value === confirmPassword.value)

const passwordStrength = computed(() => {
  const p = password.value
  if (!p) return 0
  let score = 0
  if (p.length >= 8) score++
  if (p.length >= 12) score++
  if (/[A-Z]/.test(p)) score++
  if (/[0-9]/.test(p)) score++
  if (/[^A-Za-z0-9]/.test(p)) score++
  return Math.min(score, 4)
})
const strengthLabel = computed(() => ['', 'Слабый', 'Средний', 'Хороший', 'Надёжный'][passwordStrength.value])
const strengthColor = computed(() => ['', 'bg-red-400', 'bg-amber-400', 'bg-blue-400', 'bg-emerald-400'][passwordStrength.value])

// Step 1: Register → send OTP
async function submit() {
  if (password.value !== confirmPassword.value) { error.value = 'Пароли не совпадают'; return }
  error.value = null
  alreadyExists.value = false
  loading.value = true
  try {
    // Register user first
    await $fetch('/api/backend/v1/public/auth/register', {
      method: 'POST',
      body: { fullName: fullName.value.trim(), email: email.value.trim(), password: password.value },
    })
    // Send OTP for email verification
    await $fetch('/api/backend/v1/public/auth/otp/request', {
      method: 'POST',
      body: { email: email.value.trim(), password: password.value },
    })
    step.value = 'otp'
    startResendTimer()
  } catch (e: unknown) {
    const err = e as { data?: { detail?: string; status?: number }; status?: number }
    const status = (err as { status?: number }).status
    if (status === 409) {
      alreadyExists.value = true
      error.value = null
    } else {
      error.value = err?.data?.detail ?? 'Ошибка при регистрации. Проверьте данные.'
    }
  } finally { loading.value = false }
}

// Step 2: Verify OTP → login
async function verifyOtp() {
  const code = otpDigits.value.join('')
  if (code.length !== 6) return
  error.value = null
  loading.value = true
  try {
    const tokens = await $fetch<{ access_token: string; refresh_token: string; expires_in: number }>('/api/backend/v1/public/auth/otp/verify', {
      method: 'POST',
      body: { email: email.value.trim(), code },
    })
    await auth._applyTokens(tokens)
    await router.push(redirectTo.value)
  } catch (e: unknown) {
    const err = e as { data?: { detail?: string } }
    error.value = err?.data?.detail ?? 'Неверный код'
    otpDigits.value = ['', '', '', '', '', '']
    otpInputs.value[0]?.focus()
  } finally { loading.value = false }
}

function onOtpInput(index: number, e: Event) {
  const val = (e.target as HTMLInputElement).value.replace(/\D/g, '')
  otpDigits.value[index] = val.slice(-1)
  if (val && index < 5) otpInputs.value[index + 1]?.focus()
  if (otpDigits.value.every(d => d) && otpDigits.value.join('').length === 6) verifyOtp()
}

function onOtpKeydown(index: number, e: KeyboardEvent) {
  if (e.key === 'Backspace' && !otpDigits.value[index] && index > 0) otpInputs.value[index - 1]?.focus()
}

function onOtpPaste(e: ClipboardEvent) {
  const text = e.clipboardData?.getData('text')?.replace(/\D/g, '') ?? ''
  if (text.length >= 6) {
    for (let i = 0; i < 6; i++) otpDigits.value[i] = text[i] || ''
    otpInputs.value[5]?.focus()
    verifyOtp()
  }
}

function startResendTimer() {
  otpResendTimer.value = 60
  if (resendInterval) clearInterval(resendInterval)
  resendInterval = setInterval(() => {
    otpResendTimer.value--
    if (otpResendTimer.value <= 0 && resendInterval) clearInterval(resendInterval)
  }, 1000)
}

async function resendOtp() {
  if (otpResendTimer.value > 0) return
  try {
    await $fetch('/api/backend/v1/public/auth/otp/request', {
      method: 'POST',
      body: { email: email.value.trim(), password: password.value },
    })
    startResendTimer()
  } catch { error.value = 'Не удалось отправить код' }
}

function loginWithGoogle() {
  auth.startGoogleLogin(window.location.origin + '/auth/callback', redirectTo.value)
}

onBeforeUnmount(() => { if (resendInterval) clearInterval(resendInterval) })
</script>

<template>
  <div class="relative flex min-h-screen items-center justify-center bg-slate-950 px-4 py-12">
    <!-- Background -->
    <div class="pointer-events-none absolute inset-0 overflow-hidden">
      <div class="absolute -right-40 top-20 h-[500px] w-[500px] rounded-full bg-violet-600/10 blur-[120px]" />
      <div class="absolute -left-20 bottom-10 h-[400px] w-[400px] rounded-full bg-brand-600/8 blur-[100px]" />
      <div class="absolute inset-0 bg-[linear-gradient(rgba(255,255,255,.015)_1px,transparent_1px),linear-gradient(90deg,rgba(255,255,255,.015)_1px,transparent_1px)] bg-[size:48px_48px]" />
    </div>

    <div class="relative w-full max-w-md">
      <div class="mb-10 flex justify-center">
        <LogoFull :size="44" dark />
      </div>

      <Transition name="auth-step" mode="out-in">
      <div v-if="step === 'form'" key="form" class="rounded-2xl border border-white/[0.08] bg-white/[0.04] p-8 shadow-2xl backdrop-blur-md">
        <h1 class="text-center text-2xl font-bold text-white">Создать аккаунт</h1>
        <p class="mt-1.5 text-center text-sm text-slate-400">14 дней бесплатно, карта не нужна</p>

        <button
          type="button"
          class="mt-8 flex w-full items-center justify-center gap-3 rounded-xl border border-white/[0.08] bg-white/[0.04] px-4 py-3 text-sm font-medium text-white/80 transition-all duration-200 hover:border-white/[0.15] hover:bg-white/[0.08] active:scale-[.98]"
          @click="loginWithGoogle"
        >
          <svg class="h-5 w-5 flex-shrink-0" viewBox="0 0 24 24">
            <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4"/>
            <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/>
            <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l3.66-2.84z" fill="#FBBC05"/>
            <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/>
          </svg>
          Зарегистрироваться через Google
        </button>

        <div class="my-6 flex items-center gap-3">
          <div class="h-px flex-1 bg-white/[0.08]" />
          <span class="text-xs text-slate-500">или с паролем</span>
          <div class="h-px flex-1 bg-white/[0.08]" />
        </div>

        <!-- Already registered notice -->
        <div v-if="alreadyExists" class="mb-5 rounded-xl border border-amber-500/20 bg-amber-500/10 px-4 py-3 text-sm">
          <p class="font-medium text-amber-300">Этот email уже зарегистрирован</p>
          <p class="mt-1 text-amber-400/80">
            <NuxtLink :to="{ path: '/auth/sign-in', query: { ...route.query, email: email } }" class="underline underline-offset-2 hover:text-amber-300">Войти в аккаунт</NuxtLink>
            &nbsp;или&nbsp;
            <NuxtLink :to="{ path: '/auth/forgot-password', query: route.query }" class="underline underline-offset-2 hover:text-amber-300">восстановить пароль</NuxtLink>
          </p>
        </div>

        <div v-else-if="error" class="mb-5 rounded-xl border border-red-500/20 bg-red-500/10 px-4 py-3 text-sm text-red-400">
          {{ error }}
        </div>

        <form class="space-y-4" @submit.prevent="submit">
          <div>
            <label class="mb-1.5 block text-sm font-medium text-slate-300">Имя и фамилия</label>
            <input v-model="fullName" type="text" autocomplete="name" required placeholder="Иван Иванов"
              class="w-full rounded-xl border border-white/[0.08] bg-white/[0.04] px-4 py-3 text-sm text-white placeholder:text-slate-500 transition-all focus:border-brand-500/50 focus:bg-white/[0.06] focus:outline-none focus:ring-2 focus:ring-brand-500/20" />
          </div>

          <div>
            <label class="mb-1.5 block text-sm font-medium text-slate-300">Email</label>
            <input v-model="email" type="email" autocomplete="email" required placeholder="you@example.com"
              class="w-full rounded-xl border border-white/[0.08] bg-white/[0.04] px-4 py-3 text-sm text-white placeholder:text-slate-500 transition-all focus:border-brand-500/50 focus:bg-white/[0.06] focus:outline-none focus:ring-2 focus:ring-brand-500/20" />
          </div>

          <div>
            <label class="mb-1.5 block text-sm font-medium text-slate-300">Пароль</label>
            <div class="relative">
              <input v-model="password" :type="showPass ? 'text' : 'password'" autocomplete="new-password" required minlength="8" placeholder="Минимум 8 символов"
                class="w-full rounded-xl border border-white/[0.08] bg-white/[0.04] px-4 py-3 pr-11 text-sm text-white placeholder:text-slate-500 transition-all focus:border-brand-500/50 focus:bg-white/[0.06] focus:outline-none focus:ring-2 focus:ring-brand-500/20" />
              <button type="button" tabindex="-1" class="absolute right-3.5 top-1/2 -translate-y-1/2 text-slate-500 transition hover:text-slate-300" @click="showPass = !showPass">
                <svg v-if="showPass" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="1.8" viewBox="0 0 24 24"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
                <svg v-else class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="1.8" viewBox="0 0 24 24"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
              </button>
            </div>
            <div v-if="password" class="mt-2">
              <div class="flex gap-1">
                <div v-for="i in 4" :key="i" class="h-1 flex-1 rounded-full transition-all duration-300" :class="i <= passwordStrength ? strengthColor : 'bg-white/10'" />
              </div>
              <p class="mt-1 text-xs" :class="['text-slate-500', 'text-red-400', 'text-amber-400', 'text-blue-400', 'text-emerald-400'][passwordStrength]">{{ strengthLabel }}</p>
            </div>
          </div>

          <div>
            <label class="mb-1.5 block text-sm font-medium text-slate-300">Повторите пароль</label>
            <input v-model="confirmPassword" :type="showPass ? 'text' : 'password'" autocomplete="new-password" required minlength="8" placeholder="Повторите пароль"
              class="w-full rounded-xl border bg-white/[0.04] px-4 py-3 text-sm text-white placeholder:text-slate-500 transition-all focus:bg-white/[0.06] focus:outline-none focus:ring-2"
              :class="confirmPassword && !passwordsMatch ? 'border-red-500/50 focus:border-red-500/50 focus:ring-red-500/20' : 'border-white/[0.08] focus:border-brand-500/50 focus:ring-brand-500/20'" />
            <p v-if="confirmPassword && !passwordsMatch" class="mt-1 text-xs text-red-400">Пароли не совпадают</p>
          </div>

          <button type="submit" :disabled="loading || !canSubmit"
            class="mt-2 w-full rounded-xl bg-brand-600 px-4 py-3 text-sm font-semibold text-white shadow-lg shadow-brand-600/25 transition-all duration-200 hover:bg-brand-500 hover:shadow-brand-500/30 active:scale-[.98] disabled:opacity-60">
            <span v-if="!loading">Создать аккаунт</span>
            <span v-else class="flex items-center justify-center gap-2">
              <svg class="h-4 w-4 animate-spin" fill="none" viewBox="0 0 24 24"><circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/><path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"/></svg>
              Создание...
            </span>
          </button>
        </form>

        <p class="mt-6 text-center text-xs text-slate-500">
          Регистрируясь, вы соглашаетесь с условиями использования
        </p>

        <p class="mt-4 text-center text-sm text-slate-500">
          Уже есть аккаунт?
          <NuxtLink :to="{ path: '/auth/sign-in', query: route.query }" class="font-semibold text-brand-400 hover:text-brand-300">Войти</NuxtLink>
        </p>
      </div>

      <!-- OTP step -->
      <div v-else key="otp" class="rounded-2xl border border-white/[0.08] bg-white/[0.04] p-8 shadow-2xl backdrop-blur-md">
        <div class="mx-auto flex h-16 w-16 items-center justify-center rounded-2xl bg-brand-600/20">
          <svg class="h-8 w-8 text-brand-400" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
            <path d="M21.75 6.75v10.5a2.25 2.25 0 01-2.25 2.25h-15a2.25 2.25 0 01-2.25-2.25V6.75m19.5 0A2.25 2.25 0 0019.5 4.5h-15a2.25 2.25 0 00-2.25 2.25m19.5 0v.243a2.25 2.25 0 01-1.07 1.916l-7.5 4.615a2.25 2.25 0 01-2.36 0L3.32 8.91a2.25 2.25 0 01-1.07-1.916V6.75" />
          </svg>
        </div>
        <h1 class="mt-5 text-center text-2xl font-bold text-white">Подтвердите почту</h1>
        <p class="mt-2 text-center text-sm text-slate-400">
          Мы отправили 6-значный код на<br /><span class="font-medium text-white">{{ email }}</span>
        </p>

        <div v-if="error" class="mt-5 rounded-xl border border-red-500/20 bg-red-500/10 px-4 py-3 text-sm text-red-400">{{ error }}</div>

        <div class="mt-8 flex justify-center gap-2.5" @paste.prevent="onOtpPaste">
          <input
            v-for="(_, i) in 6" :key="i"
            :ref="(el) => { if (el) otpInputs[i] = el as HTMLInputElement }"
            :value="otpDigits[i]"
            type="text" inputmode="numeric" maxlength="1"
            class="h-14 w-12 rounded-xl border bg-white/[0.04] text-center text-xl font-bold text-white transition-all focus:outline-none focus:ring-2"
            :class="otpDigits[i] ? 'border-brand-500/50 focus:ring-brand-500/30' : 'border-white/[0.08] focus:ring-brand-500/20'"
            @input="onOtpInput(i, $event)" @keydown="onOtpKeydown(i, $event)"
          />
        </div>

        <div class="mt-6 text-center">
          <button v-if="otpResendTimer > 0" disabled class="text-sm text-slate-500">Повторно через {{ otpResendTimer }}с</button>
          <button v-else class="text-sm font-medium text-brand-400 hover:text-brand-300" @click="resendOtp">Отправить код повторно</button>
        </div>

        <button type="button" :disabled="loading || otpDigits.join('').length !== 6"
          class="mt-6 w-full rounded-xl bg-brand-600 px-4 py-3 text-sm font-semibold text-white shadow-lg shadow-brand-600/25 transition-all hover:bg-brand-500 active:scale-[.98] disabled:opacity-60"
          @click="verifyOtp">
          <span v-if="!loading">Подтвердить</span>
          <span v-else class="flex items-center justify-center gap-2">
            <svg class="h-4 w-4 animate-spin" fill="none" viewBox="0 0 24 24"><circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/><path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"/></svg>
            Проверка...
          </span>
        </button>
      </div>
      </Transition>

      <p class="mt-8 text-center text-xs text-slate-600">© 2026 Webizon. Казахстан.</p>
    </div>
  </div>
</template>
