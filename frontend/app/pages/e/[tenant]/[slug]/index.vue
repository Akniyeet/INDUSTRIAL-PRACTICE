<script setup lang="ts">
/**
 * Public event landing page at `/e/{tenantSlug}/{eventSlug}`.
 *
 * <p>This is the entry point a tenant shares with their audience. It is
 * deliberately reachable without a JWT — visitors should be able to read
 * the event description and decide whether to register before being asked
 * to authenticate.
 *
 * <p>The page renders one of five states returned by the backend resolver:
 * <ul>
 *   <li>{@code LIVE_NOW} — a session is broadcasting right now; show a
 *       prominent "Кіру" button that hops to the room.</li>
 *   <li>{@code WAITING} — a session is starting soon; render the countdown
 *       and let the visitor pre-register.</li>
 *   <li>{@code SLOT_SELECTION} — the live broadcast finished and the tenant
 *       has scheduled auto replays; show a slot picker.</li>
 *   <li>{@code LANDING} — the event is published but no session is currently
 *       scheduled; show informational copy + "напомнить" CTA.</li>
 *   <li>{@code UNAVAILABLE} — the event is archived or never existed;
 *       render an apology and a link back to the tenant home.</li>
 * </ul>
 *
 * <p>The "Join" action gates on auth: anonymous visitors are bounced through
 * sign-in with a return-to query so they land back on this page (or directly
 * on the room if the session is already live).
 *
 * <p>Multi-tenancy: today the tenant is encoded in the path. Once we ship
 * subdomain routing the same component can be reused with the tenant
 * resolved from the host header by Nitro middleware.
 */
import type { PublicSessionView } from '#shared/api/types'
import { computed } from 'vue'

definePageMeta({
  layout: 'event',
})

const route = useRoute()
const tenantSlug = computed(() => String(route.params.tenant))
const eventSlug  = computed(() => String(route.params.slug))

const api = useApi()

const { data, pending, error, refresh } = await useAsyncData(
  () => `public-event:${tenantSlug.value}:${eventSlug.value}`,
  () => api.publicEvents.resolve(tenantSlug.value, eventSlug.value),
  { watch: [tenantSlug, eventSlug] },
)

const auth = useAuthStore()

useHead(() => ({
  title: data.value?.event?.title
    ? `${data.value.event.title} — ${useRuntimeConfig().public.appName}`
    : 'Іс-шара',
  meta: data.value?.event?.description
    ? [{ name: 'description', content: data.value.event.description }]
    : [],
}))

/**
 * Resolve where the "join" button should land. If the visitor is logged in,
 * we drop them straight into the room; otherwise we bounce through sign-in
 * with a redirect query so they come back here after authentication.
 */
function joinHref(targetPath?: string): string {
  const dest = targetPath ?? `/e/${tenantSlug.value}/${eventSlug.value}/room`
  if (auth.isAuthenticated) return dest
  return `/auth/sign-in?redirect=${encodeURIComponent(dest)}`
}

const dateFmt = new Intl.DateTimeFormat('ru-RU', {
  weekday: 'short',
  day:     'numeric',
  month:   'long',
  hour:    '2-digit',
  minute:  '2-digit',
})
function formatStart(s: PublicSessionView): string {
  try { return dateFmt.format(new Date(s.startTime)) } catch { return s.startTime }
}
</script>

<template>
  <div class="mx-auto w-full max-w-5xl px-4 py-10 md:py-16">
    <!-- Loading skeleton -->
    <div v-if="pending && !data" class="grid gap-6 md:grid-cols-2">
      <UiSkeleton h="h-64" rounded="rounded-2xl" />
      <div class="flex flex-col gap-3">
        <UiSkeleton h="h-8" rounded="rounded-lg" />
        <UiSkeleton h="h-4" rounded="rounded" />
        <UiSkeleton h="h-4" rounded="rounded" />
        <UiSkeleton h="h-24" rounded="rounded-xl" />
      </div>
    </div>

    <!-- Network error -->
    <UiCard v-else-if="error" class="text-center">
      <div class="space-y-3 py-10">
        <h2 class="text-xl font-semibold text-slate-900">Іс-шараны жүктеу мүмкін болмады</h2>
        <p class="text-sm text-slate-500">Желі ақаулығы немесе сервер жауап бермейді.</p>
        <UiButton variant="primary" @click="refresh()">Қайталау</UiButton>
      </div>
    </UiCard>

    <!-- Unavailable / archived event -->
    <UiCard v-else-if="!data?.event || data.state === 'UNAVAILABLE'" class="text-center">
      <div class="space-y-3 py-12">
        <h2 class="text-2xl font-bold text-slate-900">Іс-шара қолжетімсіз</h2>
        <p class="text-sm text-slate-500">
          Бұл іс-шара мұрағатталған, жойылған немесе сілтеме қате теріліп қалуы мүмкін.
        </p>
      </div>
    </UiCard>

    <!-- Normal render -->
    <template v-else-if="data.event">
      <EventHero :event="data.event" />

      <!-- LIVE NOW: prominent join button -->
      <UiCard v-if="data.state === 'LIVE_NOW' && data.activeSession" class="mt-10">
        <div class="flex flex-col items-start gap-4 p-2 md:flex-row md:items-center md:justify-between">
          <div>
            <div class="inline-flex items-center gap-2 rounded-full bg-danger-100 px-3 py-1 text-xs font-semibold uppercase tracking-wide text-danger-700">
              <span class="h-2 w-2 animate-pulse rounded-full bg-danger-600" />
              Эфирде
            </div>
            <h3 class="mt-3 text-xl font-semibold text-slate-900">Эфир қазір жүріп жатыр</h3>
            <p class="mt-1 text-sm text-slate-500">Бірден қосылып, сұрағыңызды чатта қалдырыңыз.</p>
          </div>
          <UiButton variant="primary" size="lg" :to="joinHref()">Кіру</UiButton>
        </div>
      </UiCard>

      <!-- WAITING: countdown + register -->
      <UiCard v-else-if="data.state === 'WAITING' && data.nextSession" class="mt-10">
        <div class="space-y-5 p-2 text-center md:text-left">
          <div>
            <div class="text-xs font-semibold uppercase tracking-wide text-brand-600">
              Эфир жақын арада
            </div>
            <h3 class="mt-1 text-xl font-semibold text-slate-900">
              {{ formatStart(data.nextSession) }}
            </h3>
          </div>
          <div class="flex justify-center md:justify-start">
            <UiCountdown :target="data.nextSession.startTime" @finished="refresh()" />
          </div>
          <div class="flex flex-col gap-2 md:flex-row md:items-center md:gap-3">
            <UiButton variant="primary" size="lg" :to="joinHref()">
              Қатысуды растау
            </UiButton>
            <p class="text-xs text-slate-400">
              Эфир басталғанда бөлме автоматты түрде ашылады.
            </p>
          </div>
        </div>
      </UiCard>

      <!-- SLOT SELECTION: pick a replay slot -->
      <UiCard
        v-else-if="data.state === 'SLOT_SELECTION' && data.autoSlots.length"
        title="Қайта көру уақытын таңдаңыз"
        subtitle="Тікелей эфир аяқталды. Төмендегі бір сессияға тіркеліп, қайта көре аласыз."
        class="mt-10"
      >
        <ul class="divide-y divide-slate-100">
          <li
            v-for="slot in data.autoSlots"
            :key="slot.id"
            class="flex items-center justify-between gap-4 px-5 py-4"
          >
            <div>
              <div class="font-medium text-slate-900">{{ formatStart(slot) }}</div>
              <div class="text-xs text-slate-500">
                ~{{ Math.round(slot.plannedDurationSeconds / 60) }} минут
              </div>
            </div>
            <UiButton
              variant="secondary"
              size="sm"
              :to="joinHref(`/e/${tenantSlug}/${eventSlug}/room?session=${slot.id}`)"
            >
              Таңдау
            </UiButton>
          </li>
        </ul>
      </UiCard>

      <!-- LANDING: published but no session yet -->
      <UiCard v-else class="mt-10">
        <div class="space-y-3 p-2 text-center md:text-left">
          <h3 class="text-xl font-semibold text-slate-900">Жақын арада эфир жоспарланбаған</h3>
          <p class="text-sm text-slate-500">
            Жаңа эфир ашылғанда хабарлама алу үшін тіркеліңіз.
          </p>
          <UiButton variant="primary" :to="joinHref(`/e/${tenantSlug}/${eventSlug}`)">
            Тіркелу
          </UiButton>
        </div>
      </UiCard>
    </template>
  </div>
</template>
