<script setup lang="ts">
/**
 * OIDC callback handler — called after Google OAuth redirect from Keycloak.
 *
 * Flow:
 *  1. Keycloak redirects here with ?code=&session_state=
 *  2. We grab `code_verifier` from sessionStorage (stored before redirect)
 *  3. POST to backend /api/v1/public/auth/callback → exchange code for tokens
 *  4. Call bootstrap to get user + memberships
 *  5. Redirect to original destination
 */
definePageMeta({ layout: false })
useHead({ title: 'Авторизация... — Webizon' })

const auth    = useAuthStore()
const route   = useRoute()
const router  = useRouter()
const errMsg  = ref<string | null>(null)

onMounted(async () => {
  const code = route.query.code as string | undefined
  if (!code) {
    errMsg.value = 'Код авторизации не получен.'
    return
  }

  const codeVerifier = sessionStorage.getItem('webizon:pkce_verifier')
  const redirectUri  = sessionStorage.getItem('webizon:pkce_redirect_uri') ?? (window.location.origin + '/auth/callback')
  const destination  = sessionStorage.getItem('webizon:auth_destination') ?? '/admin'

  sessionStorage.removeItem('webizon:pkce_verifier')
  sessionStorage.removeItem('webizon:pkce_redirect_uri')
  sessionStorage.removeItem('webizon:auth_destination')

  if (!codeVerifier) {
    errMsg.value = 'Сессия устарела. Попробуйте войти снова.'
    return
  }

  try {
    await auth.handleOAuthCallback(code, redirectUri, codeVerifier)
    await router.replace(destination)
  } catch {
    errMsg.value = 'Ошибка авторизации. Пожалуйста, попробуйте снова.'
  }
})
</script>

<template>
  <div class="flex min-h-screen items-center justify-center bg-white">
    <div class="text-center">
      <!-- Error state -->
      <template v-if="errMsg">
        <div class="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-red-100">
          <svg class="h-8 w-8 text-red-500" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
            <path d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z"/>
          </svg>
        </div>
        <h1 class="mt-4 text-lg font-semibold text-slate-900">{{ errMsg }}</h1>
        <NuxtLink to="/auth/sign-in" class="mt-4 inline-flex items-center gap-1.5 text-sm font-medium text-brand-600 hover:underline">
          ← Вернуться ко входу
        </NuxtLink>
      </template>

      <!-- Loading state -->
      <template v-else>
        <LogoLoader :size="72" text="Выполняем вход..." />
      </template>
    </div>
  </div>
</template>
