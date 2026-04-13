<script setup lang="ts">
/**
 * Tenant settings at {@code /admin/settings}.
 */
import {
  Settings,
  Globe,
  Clock,
  DollarSign,
  Shield,
  Building2,
  Link,
} from 'lucide-vue-next'

definePageMeta({
  layout: 'admin',
  middleware: 'auth',
})

useHead({ title: 'Настройки — Webizon' })

const api = useApi()
const auth = useAuthStore()

const { data: tenant, error } = await useAsyncData('tenant-me', () => api.tenants.me())

const ROLE_LABELS: Record<string, string> = {
  TENANT_OWNER: 'Владелец',
  TENANT_ADMIN: 'Админ',
  TENANT_MODERATOR: 'Модератор',
  TENANT_PRESENTER: 'Спикер',
  TENANT_ANALYST: 'Аналитик',
}

const STATUS_LABELS: Record<string, string> = {
  ACTIVE: 'Активен',
  TRIAL: 'Пробный период',
  SUSPENDED: 'Приостановлен',
}
</script>

<template>
  <div>
    <PageHeader
      title="Настройки"
      subtitle="Настройки рабочего пространства"
      :breadcrumbs="[
        { label: 'Главная', to: '/admin' },
        { label: 'Настройки' },
      ]"
    />

    <div v-if="error" class="mt-5">
      <UiCard class="border-danger-200 bg-danger-50/60">
        <p class="text-sm text-danger-700">Настройки загрузить не удалось. Обновите страницу.</p>
      </UiCard>
    </div>

    <div v-else class="mt-5 grid gap-5 lg:grid-cols-2">
      <!-- Workspace info -->
      <UiCard title="Рабочее пространство">
        <dl class="space-y-3 text-sm">
          <div class="flex items-center justify-between">
            <dt class="flex items-center gap-2 text-slate-500">
              <Building2 class="h-4 w-4" />
              Название
            </dt>
            <dd class="font-medium text-slate-900">
              <UiSkeleton v-if="!tenant" class="h-4 w-32" />
              <span v-else>{{ tenant.displayName }}</span>
            </dd>
          </div>
          <div class="flex items-center justify-between">
            <dt class="flex items-center gap-2 text-slate-500">
              <Link class="h-4 w-4" />
              Slug
            </dt>
            <dd class="font-mono text-xs text-slate-700">
              <UiSkeleton v-if="!tenant" class="h-4 w-24" />
              <span v-else>{{ tenant.slug }}</span>
            </dd>
          </div>
          <div class="flex items-center justify-between">
            <dt class="flex items-center gap-2 text-slate-500">
              <Shield class="h-4 w-4" />
              Tenant ID
            </dt>
            <dd class="font-mono text-xs text-slate-700">
              <UiSkeleton v-if="!tenant" class="h-4 w-40" />
              <span v-else>{{ tenant.id }}</span>
            </dd>
          </div>
          <div class="flex items-center justify-between">
            <dt class="flex items-center gap-2 text-slate-500">
              <Settings class="h-4 w-4" />
              Статус
            </dt>
            <dd>
              <UiSkeleton v-if="!tenant" class="h-5 w-20" />
              <UiBadge
                v-else
                :variant="tenant.status === 'ACTIVE' ? 'success' : tenant.status === 'TRIAL' ? 'warning' : 'danger'"
                size="sm"
              >
                {{ STATUS_LABELS[tenant.status] ?? tenant.status }}
              </UiBadge>
            </dd>
          </div>
          <div class="flex items-center justify-between">
            <dt class="flex items-center gap-2 text-slate-500">
              <Settings class="h-4 w-4" />
              Ваша роль
            </dt>
            <dd class="font-medium text-slate-900">
              {{ ROLE_LABELS[auth.user?.role ?? ''] || auth.user?.role || '—' }}
            </dd>
          </div>
          <div class="flex items-center justify-between">
            <dt class="flex items-center gap-2 text-slate-500">
              <Globe class="h-4 w-4" />
              Email
            </dt>
            <dd class="text-slate-700">{{ auth.user?.email ?? '—' }}</dd>
          </div>
        </dl>
      </UiCard>

      <!-- Defaults from tenant -->
      <UiCard title="Региональные настройки">
        <dl class="space-y-3 text-sm">
          <div class="flex items-center justify-between">
            <dt class="flex items-center gap-2 text-slate-500">
              <Globe class="h-4 w-4" />
              Регион
            </dt>
            <dd class="font-medium text-slate-900">
              <UiSkeleton v-if="!tenant" class="h-4 w-16" />
              <span v-else>{{ tenant.countryCode }}</span>
            </dd>
          </div>
          <div class="flex items-center justify-between">
            <dt class="flex items-center gap-2 text-slate-500">
              <DollarSign class="h-4 w-4" />
              Валюта
            </dt>
            <dd class="font-medium text-slate-900">
              <UiSkeleton v-if="!tenant" class="h-4 w-16" />
              <span v-else>{{ tenant.defaultCurrency }}</span>
            </dd>
          </div>
          <div class="flex items-center justify-between">
            <dt class="flex items-center gap-2 text-slate-500">
              <Clock class="h-4 w-4" />
              Часовой пояс
            </dt>
            <dd class="font-medium text-slate-900">
              <UiSkeleton v-if="!tenant" class="h-4 w-28" />
              <span v-else>{{ tenant.defaultTimezone }}</span>
            </dd>
          </div>
          <div class="flex items-center justify-between">
            <dt class="flex items-center gap-2 text-slate-500">
              <Globe class="h-4 w-4" />
              Локаль
            </dt>
            <dd class="font-medium text-slate-900">
              <UiSkeleton v-if="!tenant" class="h-4 w-16" />
              <span v-else>{{ tenant.defaultLocale }}</span>
            </dd>
          </div>
          <div v-if="tenant?.trialEndsAt" class="flex items-center justify-between">
            <dt class="flex items-center gap-2 text-slate-500">
              <Clock class="h-4 w-4" />
              Пробный период до
            </dt>
            <dd class="font-medium text-slate-900">
              {{ new Date(tenant.trialEndsAt).toLocaleDateString('ru-KZ') }}
            </dd>
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
            <h3 class="text-sm font-semibold text-slate-900">Настройки расширяются</h3>
            <p class="mt-1 text-sm text-slate-500">
              Изменение названия, брендинг, домен, биллинг и интеграции — будут добавлены в ближайших обновлениях.
            </p>
          </div>
        </div>
      </UiCard>
    </div>
  </div>
</template>
