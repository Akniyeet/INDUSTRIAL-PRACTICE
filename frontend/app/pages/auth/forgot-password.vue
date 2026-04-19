<script setup lang="ts">
definePageMeta({ layout: false })
useHead({ title: 'Восстановление пароля — Webizon' })

const router = useRouter()
const route = useRoute()

// Steps: email → otp → newpass → done
const step = ref<'email' | 'otp' | 'newpass' | 'done'>('email')

// Email step
const email = ref('')
const emailLoading = ref(false)
const emailError = ref<string | null>(null)
const emailNotRegistered = ref(false)  // true when backend returns 404

// OTP step
const otpDigits = ref(['', '', '', '', '', ''])
const otpInputs = ref<HTMLInputElement[]>([])
const otpLoading = ref(false)
const otpError = ref<string | null>(null)
const otpResendTimer = ref(0)
let resendInterval: ReturnType<typeof setInterval> | null = null

// New password step
const newPassword = ref('')
const confirmPassword = ref('')
const showPass = ref(false)
const passLoading = ref(false)
const passError = ref<string | null>(null)

const passwordsMatch = computed(() => !confirmPassword.value || newPassword.value === confirmPassword.value)
const canSubmitPass = computed(
  () => newPassword.value.length >= 8 && newPassword.value === confirmPassword.value,
)
const passwordStrength = computed(() => {
  const p = newPassword.value
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

// ------------------------------------------------------------------ steps --

async function submitEmail() {
  emailError.value = null
  emailNotRegistered.value = false
  emailLoading.value = true
  try {
    await $fetch('/api/backend/v1/public/auth/password-reset/request', {
      method: 'POST',
      body: { email: email.value.trim() },
    })
    step.value = 'otp'
    startResendTimer()
  } catch (e: unknown) {
    const err = e as { data?: { detail?: string }; status?: number }
    if (err?.status === 404) {
      emailNotRegistered.value = true
    } else {
      emailError.value = err?.data?.detail ?? 'Не удалось отправить код. Попробуйте позже.'
    }
  } finally {
    emailLoading.value = false
  }
}

async function verifyOtp() {
  const code = otpDigits.value.join('')
  if (code.length !== 6) return
  otpError.value = null
  otpLoading.value = true
  try {
    // We just move to the next step — actual OTP verification happens
    // together with the password change in the confirm endpoint
    otpLoading.value = false
    step.value = 'newpass'
  } catch {
    otpError.value = 'Неверный код. Проверьте почту.'
    otpDigits.value = ['', '', '', '', '', '']
    otpInputs.value[0]?.focus()
    otpLoading.value = false
  }
}

async function submitNewPassword() {
  if (newPassword.value !== confirmPassword.value) {
    passError.value = 'Пароли не совпадают'
    return
  }
  passError.value = null
  passLoading.value = true
  try {
    const code = otpDigits.value.join('')
    const tokens = await $fetch<{
      access_token: string
      refresh_token: string
      expires_in: number
      token_type: string
    }>('/api/backend/v1/public/auth/password-reset/confirm', {
      method: 'POST',
      body: {
        email: email.value.trim(),
        code,
        newPassword: newPassword.value,
      },
    })
    // Apply tokens (log user in)
    const auth = useAuthStore()
    await auth._applyTokens(tokens)
    step.value = 'done'
  } catch (e: unknown) {
    const err = e as { data?: { detail?: string }; status?: number }
    if (err?.status === 400 || err?.data?.detail?.includes('Неверный код') || err?.data?.detail?.includes('истёк')) {
      // OTP problem — go back to OTP step
      passError.value = err?.data?.detail ?? 'Неверный или просроченный код. Запросите новый.'
      step.value = 'otp'
      otpDigits.value = ['', '', '', '', '', '']
    } else {
      passError.value = err?.data?.detail ?? 'Не удалось изменить пароль. Попробуйте снова.'
    }
  } finally {
    passLoading.value = false
  }
}

// ----------------------------------------------------------------- OTP UI --

function onOtpInput(index: number, e: Event) {
  const val = (e.target as HTMLInputElement).value.replace(/\D/g, '')
  otpDigits.value[index] = val.slice(-1)
  if (val && index < 5) otpInputs.value[index + 1]?.focus()
  if (otpDigits.value.every((d) => d) && otpDigits.value.join('').length === 6) verifyOtp()
}

function onOtpKeydown(index: number, e: KeyboardEvent) {
  if (e.key === 'Backspace' && !otpDigits.value[index] && index > 0)
    otpInputs.value[index - 1]?.focus()
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
  otpError.value = null
  try {
    await $fetch('/api/backend/v1/public/auth/password-reset/request', {
      method: 'POST',
      body: { email: email.value.trim() },
    })
    startResendTimer()
  } catch {
    otpError.value = 'Не удалось отправить код'
  }
}

onBeforeUnmount(() => { if (resendInterval) clearInterval(resendInterval) })
</script>

<template>
  <div class="relative flex min-h-screen items-center justify-center bg-slate-950 px-4 py-12">
    <!-- Background -->
    <div class="pointer-events-none absolute inset-0 overflow-hidden">
      <div class="absolute -left-40 top-20 h-[500px] w-[500px] rounded-full bg-rose-600/8 blur-[120px]" />
      <div class="absolute -right-20 bottom-10 h-[400px] w-[400px] rounded-full bg-brand-600/6 blur-[100px]" />
      <div class="absolute inset-0 bg-[linear-gradient(rgba(255,255,255,.015)_1px,transparent_1px),linear-gradient(90deg,rgba(255,255,255,.015)_1px,transparent_1px)] bg-[size:48px_48px]" />
    </div>

    <div class="relative w-full max-w-md">
      <div class="mb-10 flex justify-center">
        <LogoFull :size="44" dark />
      </div>

      <Transition name="auth-step" mode="out-in">

        <!-- ── Step 1: Email ─────────────────────────────────────────────── -->
        <div v-if="step === 'email'" key="email"
          class="rounded-2xl border border-white/[0.08] bg-white/[0.04] p-8 shadow-2xl backdrop-blur-md">
          <div class="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-rose-600/20">
            <svg class="h-7 w-7 text-rose-400" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="M16.5 10.5V6.75a4.5 4.5 0 10-9 0v3.75m-.75 11.25h10.5a2.25 2.25 0 002.25-2.25v-6.75a2.25 2.25 0 00-2.25-2.25H6.75a2.25 2.25 0 00-2.25 2.25v6.75a2.25 2.25 0 002.25 2.25z" />
            </svg>
          </div>
          <h1 class="mt-5 text-center text-2xl font-bold text-white">Забыли пароль?</h1>
          <p class="mt-2 text-center text-sm text-slate-400">
            Введите email — мы отправим код для сброса пароля
          </p>

          <!-- Not registered notice -->
          <div v-if="emailNotRegistered" class="mt-5 rounded-xl border border-amber-500/20 bg-amber-500/10 px-4 py-3.5">
            <p class="text-sm font-medium text-amber-300">Этот email не зарегистрирован</p>
            <p class="mt-1 text-xs text-amber-400/80">Хотите создать новый аккаунт?</p>
            <NuxtLink
              :to="{ path: '/auth/sign-up', query: route.query }"
              class="mt-3 flex items-center justify-center gap-2 rounded-xl bg-amber-500/20 px-4 py-2.5 text-sm font-semibold text-amber-300 transition hover:bg-amber-500/30 active:scale-[.98]"
            >
              <svg class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M18 7.5v3m0 0v3m0-3h3m-3 0h-3M13.5 19.5H6.75a2.25 2.25 0 01-2.25-2.25V6.75A2.25 2.25 0 016.75 4.5h10.5a2.25 2.25 0 012.25 2.25v3.75" />
              </svg>
              Зарегистрироваться бесплатно
            </NuxtLink>
          </div>

          <div v-else-if="emailError" class="mt-5 rounded-xl border border-red-500/20 bg-red-500/10 px-4 py-3 text-sm text-red-400">
            {{ emailError }}
          </div>

          <form class="mt-7 space-y-4" @submit.prevent="submitEmail">
            <div>
              <label class="mb-1.5 block text-sm font-medium text-slate-300">Email</label>
              <input v-model="email" type="email" autocomplete="email" required placeholder="you@example.com"
                class="w-full rounded-xl border bg-white/[0.04] px-4 py-3 text-sm text-white placeholder:text-slate-500 transition-all focus:bg-white/[0.06] focus:outline-none focus:ring-2"
                :class="emailNotRegistered
                  ? 'border-amber-500/50 focus:border-amber-500/50 focus:ring-amber-500/20'
                  : 'border-white/[0.08] focus:border-brand-500/50 focus:ring-brand-500/20'"
                @input="emailNotRegistered = false; emailError = null" />
            </div>

            <button type="submit" :disabled="emailLoading || !email.trim()"
              class="w-full rounded-xl bg-brand-600 px-4 py-3 text-sm font-semibold text-white shadow-lg shadow-brand-600/25 transition-all hover:bg-brand-500 active:scale-[.98] disabled:opacity-60">
              <span v-if="!emailLoading">Отправить код</span>
              <span v-else class="flex items-center justify-center gap-2">
                <svg class="h-4 w-4 animate-spin" fill="none" viewBox="0 0 24 24"><circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/><path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"/></svg>
                Отправка...
              </span>
            </button>
          </form>

          <p class="mt-6 text-center text-sm text-slate-500">
            Вспомнили пароль?
            <NuxtLink :to="{ path: '/auth/sign-in', query: route.query }" class="font-semibold text-brand-400 hover:text-brand-300">Войти</NuxtLink>
          </p>
        </div>

        <!-- ── Step 2: OTP code ──────────────────────────────────────────── -->
        <div v-else-if="step === 'otp'" key="otp"
          class="rounded-2xl border border-white/[0.08] bg-white/[0.04] p-8 shadow-2xl backdrop-blur-md">
          <div class="mx-auto flex h-16 w-16 items-center justify-center rounded-2xl bg-brand-600/20">
            <svg class="h-8 w-8 text-brand-400" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="M21.75 6.75v10.5a2.25 2.25 0 01-2.25 2.25h-15a2.25 2.25 0 01-2.25-2.25V6.75m19.5 0A2.25 2.25 0 0019.5 4.5h-15a2.25 2.25 0 00-2.25 2.25m19.5 0v.243a2.25 2.25 0 01-1.07 1.916l-7.5 4.615a2.25 2.25 0 01-2.36 0L3.32 8.91a2.25 2.25 0 01-1.07-1.916V6.75" />
            </svg>
          </div>
          <h1 class="mt-5 text-center text-2xl font-bold text-white">Введите код</h1>
          <p class="mt-2 text-center text-sm text-slate-400">
            Мы отправили 6-значный код на<br />
            <span class="font-medium text-white">{{ email }}</span>
          </p>

          <div v-if="otpError" class="mt-5 rounded-xl border border-red-500/20 bg-red-500/10 px-4 py-3 text-sm text-red-400">
            {{ otpError }}
          </div>

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
            <button v-if="otpResendTimer > 0" disabled class="text-sm text-slate-500">
              Повторно через {{ otpResendTimer }}с
            </button>
            <button v-else class="text-sm font-medium text-brand-400 hover:text-brand-300" @click="resendOtp">
              Отправить код повторно
            </button>
          </div>

          <button type="button" :disabled="otpLoading || otpDigits.join('').length !== 6"
            class="mt-6 w-full rounded-xl bg-brand-600 px-4 py-3 text-sm font-semibold text-white shadow-lg shadow-brand-600/25 transition-all hover:bg-brand-500 active:scale-[.98] disabled:opacity-60"
            @click="verifyOtp">
            <span v-if="!otpLoading">Продолжить</span>
            <span v-else class="flex items-center justify-center gap-2">
              <svg class="h-4 w-4 animate-spin" fill="none" viewBox="0 0 24 24"><circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/><path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"/></svg>
              Проверка...
            </span>
          </button>

          <button type="button" class="mt-4 w-full text-center text-sm text-slate-500 hover:text-slate-300 transition-colors" @click="step = 'email'">
            ← Другой email
          </button>
        </div>

        <!-- ── Step 3: New password ──────────────────────────────────────── -->
        <div v-else-if="step === 'newpass'" key="newpass"
          class="rounded-2xl border border-white/[0.08] bg-white/[0.04] p-8 shadow-2xl backdrop-blur-md">
          <div class="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-emerald-600/20">
            <svg class="h-7 w-7 text-emerald-400" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="M15.75 5.25a3 3 0 013 3m3 0a6 6 0 01-7.029 5.912c-.563-.097-1.159.026-1.563.43L10.5 17.25H8.25v2.25H6v2.25H2.25v-2.818c0-.597.237-1.17.659-1.591l6.499-6.499c.404-.404.527-1 .43-1.563A6 6 0 1121.75 8.25z" />
            </svg>
          </div>
          <h1 class="mt-5 text-center text-2xl font-bold text-white">Новый пароль</h1>
          <p class="mt-2 text-center text-sm text-slate-400">Придумайте надёжный пароль</p>

          <div v-if="passError" class="mt-5 rounded-xl border border-red-500/20 bg-red-500/10 px-4 py-3 text-sm text-red-400">
            {{ passError }}
          </div>

          <form class="mt-7 space-y-4" @submit.prevent="submitNewPassword">
            <div>
              <label class="mb-1.5 block text-sm font-medium text-slate-300">Новый пароль</label>
              <div class="relative">
                <input v-model="newPassword" :type="showPass ? 'text' : 'password'" autocomplete="new-password"
                  required minlength="8" placeholder="Минимум 8 символов"
                  class="w-full rounded-xl border border-white/[0.08] bg-white/[0.04] px-4 py-3 pr-11 text-sm text-white placeholder:text-slate-500 transition-all focus:border-brand-500/50 focus:bg-white/[0.06] focus:outline-none focus:ring-2 focus:ring-brand-500/20" />
                <button type="button" tabindex="-1"
                  class="absolute right-3.5 top-1/2 -translate-y-1/2 text-slate-500 transition hover:text-slate-300"
                  @click="showPass = !showPass">
                  <svg v-if="showPass" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="1.8" viewBox="0 0 24 24"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
                  <svg v-else class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="1.8" viewBox="0 0 24 24"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                </button>
              </div>
              <div v-if="newPassword" class="mt-2">
                <div class="flex gap-1">
                  <div v-for="i in 4" :key="i" class="h-1 flex-1 rounded-full transition-all duration-300"
                    :class="i <= passwordStrength ? strengthColor : 'bg-white/10'" />
                </div>
                <p class="mt-1 text-xs"
                  :class="['text-slate-500','text-red-400','text-amber-400','text-blue-400','text-emerald-400'][passwordStrength]">
                  {{ strengthLabel }}
                </p>
              </div>
            </div>

            <div>
              <label class="mb-1.5 block text-sm font-medium text-slate-300">Повторите пароль</label>
              <input v-model="confirmPassword" :type="showPass ? 'text' : 'password'" autocomplete="new-password"
                required minlength="8" placeholder="Повторите пароль"
                class="w-full rounded-xl border bg-white/[0.04] px-4 py-3 text-sm text-white placeholder:text-slate-500 transition-all focus:bg-white/[0.06] focus:outline-none focus:ring-2"
                :class="confirmPassword && !passwordsMatch
                  ? 'border-red-500/50 focus:border-red-500/50 focus:ring-red-500/20'
                  : 'border-white/[0.08] focus:border-brand-500/50 focus:ring-brand-500/20'" />
              <p v-if="confirmPassword && !passwordsMatch" class="mt-1 text-xs text-red-400">Пароли не совпадают</p>
            </div>

            <button type="submit" :disabled="passLoading || !canSubmitPass"
              class="w-full rounded-xl bg-brand-600 px-4 py-3 text-sm font-semibold text-white shadow-lg shadow-brand-600/25 transition-all hover:bg-brand-500 active:scale-[.98] disabled:opacity-60">
              <span v-if="!passLoading">Изменить пароль</span>
              <span v-else class="flex items-center justify-center gap-2">
                <svg class="h-4 w-4 animate-spin" fill="none" viewBox="0 0 24 24"><circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/><path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"/></svg>
                Сохранение...
              </span>
            </button>
          </form>
        </div>

        <!-- ── Step 4: Done ──────────────────────────────────────────────── -->
        <div v-else key="done"
          class="rounded-2xl border border-white/[0.08] bg-white/[0.04] p-8 text-center shadow-2xl backdrop-blur-md">
          <div class="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-emerald-500/20">
            <svg class="h-8 w-8 text-emerald-400" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="M4.5 12.75l6 6 9-13.5" />
            </svg>
          </div>
          <h1 class="mt-6 text-2xl font-bold text-white">Пароль изменён!</h1>
          <p class="mt-2 text-sm text-slate-400">Вы уже вошли в аккаунт. Добро пожаловать!</p>
          <NuxtLink to="/admin"
            class="mt-8 inline-flex w-full items-center justify-center rounded-xl bg-brand-600 px-4 py-3 text-sm font-semibold text-white shadow-lg shadow-brand-600/25 transition-all hover:bg-brand-500 active:scale-[.98]">
            Перейти в кабинет
          </NuxtLink>
        </div>

      </Transition>

      <p class="mt-8 text-center text-xs text-slate-600">© 2026 Webizon. Казахстан.</p>
    </div>
  </div>
</template>
