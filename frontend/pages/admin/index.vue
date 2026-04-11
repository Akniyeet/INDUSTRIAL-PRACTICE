<script setup lang="ts">
/**
 * Admin dashboard — placeholder for the vertical slice.
 *
 * <p>Once the events + sessions pages land, this will aggregate: upcoming
 * sessions, in-progress live rooms, recent leads, billing status. For now
 * it's a welcome card plus quick links, so the rest of the slice has
 * something to navigate back to.
 */
import { Calendar, Radio, Users, ArrowRight } from 'lucide-vue-next'

definePageMeta({
  layout: 'admin',
  middleware: 'auth',
})

const auth = useAuthStore()
const displayName = computed(() => auth.user?.fullName || auth.user?.email?.split('@')[0] || 'дос')

const shortcuts = [
  {
    to: '/admin/events',
    title: 'Ивенттер',
    description: 'Жаңа ивент жасау, бар ивенттерді редакциялау.',
    icon: Calendar,
  },
  {
    to: '/admin/sessions',
    title: 'Сессиялар',
    description: 'Жоспарланған және өтіп жатқан эфирлер.',
    icon: Radio,
  },
  {
    to: '/admin/members',
    title: 'Мүшелер',
    description: 'Командаңызды шақырыңыз және рөлдерді басқарыңыз.',
    icon: Users,
  },
]
</script>

<template>
  <div class="space-y-8">
    <header>
      <h1 class="text-2xl font-semibold text-slate-900">Сәлем, {{ displayName }} 👋</h1>
      <p class="mt-1 text-sm text-slate-500">Webizon-ға қош келдіңіз. Мынадан бастаңыз:</p>
    </header>

    <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <NuxtLink
        v-for="s in shortcuts"
        :key="s.to"
        :to="s.to"
        class="card card-hover p-5 transition-transform hover:-translate-y-0.5"
      >
        <div
          class="flex h-10 w-10 items-center justify-center rounded-lg bg-brand-50 text-brand-700"
        >
          <component :is="s.icon" class="h-5 w-5" />
        </div>
        <h3 class="mt-4 text-base font-semibold text-slate-900">{{ s.title }}</h3>
        <p class="mt-1 text-sm text-slate-500">{{ s.description }}</p>
        <div class="mt-4 inline-flex items-center gap-1 text-sm font-medium text-brand-700">
          Ашу <ArrowRight class="h-3.5 w-3.5" />
        </div>
      </NuxtLink>
    </div>

    <UiCard title="Платформа жаңалықтары" subtitle="Соңғы релиздер және ұсыныстар">
      <ul class="space-y-3 text-sm text-slate-600">
        <li class="flex items-start gap-2">
          <span class="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-success-500" />
          Shakyry және WELCOME email жіберу жүйесі қосылды.
        </li>
        <li class="flex items-start gap-2">
          <span class="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-brand-500" />
          Auto-session replay engine — тіркелмегендерге тегін көрсету мүмкіндігі.
        </li>
        <li class="flex items-start gap-2">
          <span class="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-slate-300" />
          Tenant invite flow — команданы топтап қосуға болады.
        </li>
      </ul>
    </UiCard>
  </div>
</template>
