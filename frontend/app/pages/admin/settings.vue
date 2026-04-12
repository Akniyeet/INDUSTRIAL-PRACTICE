<script setup lang="ts">
/**
 * Tenant settings at {@code /admin/settings}.
 *
 * <p>Read-only view of current workspace configuration. The backend does
 * not yet expose a PATCH endpoint for tenant settings — when it does, this
 * page will gain inline editing for displayName, locale, timezone, etc.
 */
import {
  Settings,
  Globe,
  Clock,
  DollarSign,
  Shield,
  Calendar,
} from 'lucide-vue-next'
import { format } from 'date-fns'

definePageMeta({
  layout: 'admin',
  middleware: 'auth',
})

useHead({ title: 'Баптаулар — Webizon' })

const auth = useAuthStore()

const tenantInfo = computed(() => ({
  displayName: auth.user?.tenantId ? 'Workspace' : '—',
  tenantId: auth.user?.tenantId ?? '—',
  role: auth.user?.role ?? '—',
  email: auth.user?.email ?? '—',
}))

const ROLE_LABELS: Record<string, string> = {
  TENANT_OWNER: 'Иесі',
  TENANT_ADMIN: 'Админ',
  TENANT_MODERATOR: 'Модератор',
  TENANT_PRESENTER: 'Спикер',
  TENANT_ANALYST: 'Аналитик',
}
</script>

<template>
  <div>
    <PageHeader
      title="Баптаулар"
      subtitle="Жұмыс кеңістігі баптаулары"
      :breadcrumbs="[
        { label: 'Басты бет', to: '/admin' },
        { label: 'Баптаулар' },
      ]"
    />

    <div class="mt-5 grid gap-5 lg:grid-cols-2">
      <!-- Workspace info -->
      <UiCard title="Жұмыс кеңістігі">
        <dl class="space-y-3 text-sm">
          <div class="flex items-center justify-between">
            <dt class="flex items-center gap-2 text-slate-500">
              <Shield class="h-4 w-4" />
              Tenant ID
            </dt>
            <dd class="font-mono text-xs text-slate-700">{{ tenantInfo.tenantId }}</dd>
          </div>
          <div class="flex items-center justify-between">
            <dt class="flex items-center gap-2 text-slate-500">
              <Settings class="h-4 w-4" />
              Сіздің рөліңіз
            </dt>
            <dd class="font-medium text-slate-900">
              {{ ROLE_LABELS[tenantInfo.role] || tenantInfo.role }}
            </dd>
          </div>
          <div class="flex items-center justify-between">
            <dt class="flex items-center gap-2 text-slate-500">
              <Globe class="h-4 w-4" />
              Email
            </dt>
            <dd class="text-slate-700">{{ tenantInfo.email }}</dd>
          </div>
        </dl>
      </UiCard>

      <!-- Defaults -->
      <UiCard title="Әдепкі мәндер">
        <dl class="space-y-3 text-sm">
          <div class="flex items-center justify-between">
            <dt class="flex items-center gap-2 text-slate-500">
              <Globe class="h-4 w-4" />
              Аймақ
            </dt>
            <dd class="font-medium text-slate-900">KZ (Қазақстан)</dd>
          </div>
          <div class="flex items-center justify-between">
            <dt class="flex items-center gap-2 text-slate-500">
              <DollarSign class="h-4 w-4" />
              Валюта
            </dt>
            <dd class="font-medium text-slate-900">KZT (Теңге)</dd>
          </div>
          <div class="flex items-center justify-between">
            <dt class="flex items-center gap-2 text-slate-500">
              <Clock class="h-4 w-4" />
              Уақыт белдеуі
            </dt>
            <dd class="font-medium text-slate-900">Asia/Almaty</dd>
          </div>
          <div class="flex items-center justify-between">
            <dt class="flex items-center gap-2 text-slate-500">
              <Globe class="h-4 w-4" />
              Тіл
            </dt>
            <dd class="font-medium text-slate-900">ru-KZ</dd>
          </div>
        </dl>
      </UiCard>

      <!-- Coming soon -->
      <UiCard class="lg:col-span-2">
        <div class="flex items-start gap-3">
          <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-brand-50 text-brand-600">
            <Settings class="h-5 w-5" />
          </div>
          <div>
            <h3 class="text-sm font-semibold text-slate-900">Баптаулар кеңейтілуде</h3>
            <p class="mt-1 text-sm text-slate-500">
              Жұмыс кеңістігі атауын өзгерту, брендинг, домен баптаулары, биллинг және
              интеграциялар — жақын жаңартуларда қосылады.
            </p>
          </div>
        </div>
      </UiCard>
    </div>
  </div>
</template>
