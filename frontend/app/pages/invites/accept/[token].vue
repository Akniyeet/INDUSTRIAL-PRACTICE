<script setup lang="ts">
/**
 * Invite accept page at {@code /invites/accept/{token}}.
 *
 * <p>Recipients land here after clicking the invite link in their email.
 * The page requires authentication — if the user is not logged in, the
 * auth middleware redirects to sign-in with a redirect back here.
 *
 * <p>On mount, the token is submitted to the backend. On success, the
 * user is redirected to the admin dashboard of their new workspace.
 */
import { CheckCircle, XCircle, Loader2 } from 'lucide-vue-next'
import { ref } from 'vue'

definePageMeta({
  middleware: 'auth',
})

const route = useRoute()
const router = useRouter()
const api = useApi()

const token = computed(() => String(route.params.token))

const status = ref<'loading' | 'success' | 'error'>('loading')
const errorMessage = ref('')
const tenantName = ref('')
const roleName = ref('')

const ROLE_LABELS: Record<string, string> = {
  TENANT_OWNER: 'Иесі',
  TENANT_ADMIN: 'Админ',
  TENANT_MODERATOR: 'Модератор',
  TENANT_PRESENTER: 'Спикер',
  TENANT_ANALYST: 'Аналитик',
}

async function acceptInvite() {
  try {
    const result = await api.auth.acceptInvite(token.value)
    tenantName.value = result.tenantDisplayName
    roleName.value = ROLE_LABELS[result.role] || result.role
    status.value = 'success'
    // Redirect to admin after a short delay
    setTimeout(() => {
      router.push('/admin')
    }, 3000)
  } catch (err) {
    const apiErr = err as { detail?: string; title?: string; status?: number }
    if (apiErr.status === 410) {
      errorMessage.value = 'Бұл шақыру сілтемесі қолданылған немесе мерзімі өткен.'
    } else if (apiErr.status === 403) {
      errorMessage.value = 'Шақыру email-іңізге сәйкес келмейді.'
    } else {
      errorMessage.value = apiErr.detail ?? apiErr.title ?? 'Шақыруды қабылдау қатесі.'
    }
    status.value = 'error'
  }
}

onMounted(acceptInvite)

useHead({ title: 'Шақыруды қабылдау — Webizon' })
</script>

<template>
  <div class="flex min-h-screen items-center justify-center bg-slate-50 px-4">
    <div class="w-full max-w-md">
      <!-- Loading -->
      <div v-if="status === 'loading'" class="text-center">
        <Loader2 class="mx-auto h-10 w-10 animate-spin text-brand-600" />
        <h1 class="mt-4 text-lg font-semibold text-slate-900">Шақыру қабылдануда...</h1>
        <p class="mt-2 text-sm text-slate-500">Бір сәт күтіңіз.</p>
      </div>

      <!-- Success -->
      <UiCard v-else-if="status === 'success'" class="text-center">
        <div class="py-4">
          <CheckCircle class="mx-auto h-12 w-12 text-success-500" />
          <h1 class="mt-4 text-lg font-semibold text-slate-900">Қош келдіңіз!</h1>
          <p class="mt-2 text-sm text-slate-600">
            Сіз <strong>{{ tenantName }}</strong> жұмыс кеңістігіне
            <strong>{{ roleName }}</strong> ретінде қосылдыңыз.
          </p>
          <p class="mt-4 text-xs text-slate-400">
            Автоматты түрде басты бетке бағытталасыз...
          </p>
          <UiButton variant="primary" class="mt-4" to="/admin">
            Басты бетке өту
          </UiButton>
        </div>
      </UiCard>

      <!-- Error -->
      <UiCard v-else class="text-center">
        <div class="py-4">
          <XCircle class="mx-auto h-12 w-12 text-danger-500" />
          <h1 class="mt-4 text-lg font-semibold text-slate-900">Қате</h1>
          <p class="mt-2 text-sm text-slate-600">{{ errorMessage }}</p>
          <div class="mt-6 flex justify-center gap-3">
            <UiButton variant="outline" to="/">Басты бетке</UiButton>
            <UiButton variant="primary" to="/auth/sign-in">Кіру</UiButton>
          </div>
        </div>
      </UiCard>
    </div>
  </div>
</template>
