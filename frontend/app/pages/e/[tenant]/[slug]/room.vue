<script setup lang="ts">
/**
 * Live participant room at `/e/{tenantSlug}/{eventSlug}/room`.
 *
 * <p>Auth-required: anonymous visitors are bounced to {@code /auth/sign-in}
 * by the {@code auth} middleware. Once authenticated, the page resolves the
 * target session via one of two paths:
 * <ol>
 *   <li>If a {@code ?session={uuid}} query is present (set by the slot
 *       picker on the public landing), use it directly.</li>
 *   <li>Otherwise call {@code publicEvents.resolve(tenant, slug)} and pick
 *       the {@code activeSession} or {@code nextSession} — whichever the
 *       state-machine returns. If neither exists we redirect back to the
 *       public landing.</li>
 * </ol>
 *
 * <p>Once we have a {@code sessionId}, we hit
 * {@code GET /api/v1/sessions/{id}/room} which returns the bootstrap blob
 * (event, session, chat history, active CTAs, channel names, capabilities).
 * The bootstrap is the only synchronous data fetch on first paint.
 *
 * <p>Real-time wiring (browser only). The bootstrap ships four channel names
 * from {@code RoomChannelBundleView}: {@code chat}, {@code cta},
 * {@code presence}, {@code state}. Their canonical form is
 * {@code tenant.{tenantId}.session.{sessionId}.{kind}}.
 * <ul>
 *   <li>{@code channels.chat} → push new {@code RoomChatMessageView} into the
 *       local messages reactive list.</li>
 *   <li>{@code channels.cta} → react to {@code CTA_SHOW} / {@code CTA_HIDE}
 *       by mutating {@code activeCtas}.</li>
 *   <li>{@code channels.state} → moderator-visible capability changes (a
 *       participant getting chat-banned, slow mode being toggled, etc.). We
 *       re-read {@code canSendChat} / {@code bypassSlowMode} from the
 *       publication payload.</li>
 * </ul>
 * <p>The admin-only {@code control} channel is not in this bundle (§55) —
 * participants never subscribe to it.
 *
 * <p>The send path goes through {@code api.chat.send(sessionId, ...)} which
 * returns {@code 202 Accepted}. Per CLAUDE.md §9 we do NOT optimistically
 * append — the user's own message lands via the same Centrifugo broadcast as
 * everyone else's, so the order is canonical.
 *
 * <p>Layout: 2-column on >= md (video on the left ~7/12, chat + CTA stack on
 * the right ~5/12). On mobile we collapse to a single column with the video
 * on top, then a tab switcher (Чат | Ұсыныстар) below it.
 */
import type {
  CtaResponse,
  RoomBootstrapResponse,
  RoomChatMessageView,
} from '#shared/api/types'
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'

definePageMeta({
  layout: 'event',
  middleware: 'auth',
})

const route = useRoute()
const router = useRouter()
const tenantSlug = computed(() => String(route.params.tenant))
const eventSlug  = computed(() => String(route.params.slug))

const api = useApi()

// ---------------------------------------------------------------------------
// Step 1 — resolve target session id
// ---------------------------------------------------------------------------
const sessionId = ref<string | null>(
  typeof route.query.session === 'string' ? route.query.session : null,
)

if (!sessionId.value) {
  // Need to resolve via the public landing endpoint to find the active or
  // next session. Done synchronously inside `useAsyncData` so SSR has a
  // deterministic answer for the initial render.
  const { data: resolution } = await useAsyncData(
    () => `room-resolve:${tenantSlug.value}:${eventSlug.value}`,
    () => api.publicEvents.resolve(tenantSlug.value, eventSlug.value),
  )
  sessionId.value =
    resolution.value?.activeSession?.id ??
    resolution.value?.nextSession?.id ??
    null
}

// If we still have nothing, bounce back to the landing page.
if (!sessionId.value) {
  await router.replace(`/e/${tenantSlug.value}/${eventSlug.value}`)
}

// ---------------------------------------------------------------------------
// Step 2 — load the room bootstrap
// ---------------------------------------------------------------------------
const { data: bootstrap, error: bootstrapError } = await useAsyncData(
  () => `room-boot:${sessionId.value}`,
  () => api.room.bootstrap(sessionId.value as string),
  { watch: [sessionId] },
)

useHead(() => ({
  title: bootstrap.value?.event?.title
    ? `${bootstrap.value.event.title} — эфир`
    : 'Комната трансляции',
}))

// ---------------------------------------------------------------------------
// Step 3 — local reactive room state
// ---------------------------------------------------------------------------
/** Live chat tail. Seeded from bootstrap, then appended by the WS subscription. */
const messages = ref<RoomChatMessageView[]>([])
/** Active CTAs. Seeded from bootstrap, then mutated by timeline events. */
const activeCtas = ref<CtaResponse[]>([])
/** Capabilities — copied locally so moderation events can flip them. */
const capabilities = shallowRef<RoomBootstrapResponse['capabilities'] | null>(null)
/** Private warnings from moderators — shown only to the target user. */
const warnings = ref<Array<{ reason: string; receivedAt: number }>>([])

watch(
  bootstrap,
  (b) => {
    if (!b) return
    messages.value    = [...b.recentChat]
    activeCtas.value  = [...b.activeCtas]
    capabilities.value = { ...b.capabilities }
  },
  { immediate: true },
)

// ---------------------------------------------------------------------------
// Step 4 — Centrifuge subscriptions (browser only)
// ---------------------------------------------------------------------------
type ChatPub  = { message: RoomChatMessageView } | RoomChatMessageView
type CtaPub = {
  type: 'CTA_SHOW' | 'CTA_HIDE'
  cta?: CtaResponse
  ctaId?: string
}
/**
 * State-channel publications currently carry capability deltas — when a
 * moderator chat-bans a user, flips slow mode, or adjusts any other
 * per-participant permission. Shape matches {@code RoomCapabilitiesView} so
 * we can splice in whatever fields the backend chose to send.
 *
 * Also carries WARNING events targeted at the current user so they can
 * be displayed privately as per CLAUDE.md §18.
 */
type StatePub = {
  type: 'CapabilitiesChanged' | 'WARNING'
  capabilities?: Partial<RoomBootstrapResponse['capabilities']>
  reason?: string
  targetUserId?: string
}

const subs: Array<{ unsubscribe: () => void }> = []

onMounted(() => {
  if (!bootstrap.value) return
  const { channels } = bootstrap.value
  const { subscribe } = useCentrifuge()

  subs.push(
    subscribe<ChatPub>(channels.chat, {
      onPublication: (data) => {
        // Backend may wrap the message in `{message: ...}` or send it bare —
        // accept both shapes so a future schema tweak doesn't break the UI.
        const msg = (data as { message?: RoomChatMessageView }).message ?? (data as RoomChatMessageView)
        if (!msg?.id) return
        messages.value.push(msg)
      },
    }),
  )

  subs.push(
    subscribe<CtaPub>(channels.cta, {
      onPublication: (evt) => {
        if (evt.type === 'CTA_SHOW' && evt.cta) {
          // Replace if present (priority/wording may have changed), else add.
          const idx = activeCtas.value.findIndex((c) => c.id === evt.cta!.id)
          if (idx >= 0) activeCtas.value.splice(idx, 1, evt.cta)
          else activeCtas.value.push(evt.cta)
        } else if (evt.type === 'CTA_HIDE') {
          const targetId = evt.ctaId ?? evt.cta?.id
          if (targetId) {
            activeCtas.value = activeCtas.value.filter((c) => c.id !== targetId)
          }
        }
      },
    }),
  )

  subs.push(
    subscribe<StatePub>(channels.state, {
      onPublication: (evt) => {
        if (evt.type === 'CapabilitiesChanged' && evt.capabilities && capabilities.value) {
          capabilities.value = { ...capabilities.value, ...evt.capabilities }
        }
        // Private warning targeted at the current user
        if (evt.type === 'WARNING' && evt.reason !== undefined) {
          const auth = useAuthStore()
          // Show only if targeted at current user (or broadcast to all)
          if (!evt.targetUserId || evt.targetUserId === auth.user?.id) {
            warnings.value.push({ reason: evt.reason || '', receivedAt: Date.now() })
          }
        }
      },
    }),
  )
})

onBeforeUnmount(() => {
  for (const s of subs) {
    try { s.unsubscribe() } catch { /* swallow */ }
  }
})

// ---------------------------------------------------------------------------
// Step 5 — send handler
// ---------------------------------------------------------------------------
async function sendMessage(text: string, replyToMessageId?: string) {
  if (!sessionId.value) return
  await api.chat.send(sessionId.value, { text, replyToMessageId })
  // Per §9 we do NOT optimistically append. The Centrifugo broadcast lands
  // a few hundred ms later and pushes the row into `messages`.
}

function handleCtaClick(cta: CtaResponse) {
  // TODO(F6): record CTA_CLICK analytics. For now we just open the URL.
  const url = cta.actionUrl ?? cta.fileUrl
  if (url) window.open(url, '_blank', 'noopener,noreferrer')
}

// ---------------------------------------------------------------------------
// Mobile tab switcher (chat | cta)
// ---------------------------------------------------------------------------
const mobileTab = ref<'chat' | 'cta'>('chat')
</script>

<template>
  <div class="mx-auto w-full max-w-7xl px-3 py-4 md:px-6 md:py-8">
    <!-- Bootstrap error -->
    <UiCard v-if="bootstrapError" class="text-center">
      <div class="space-y-3 py-12">
        <h2 class="text-xl font-semibold text-slate-900">Не удалось войти в комнату</h2>
        <p class="text-sm text-slate-500">Сессия недоступна или у вас нет прав доступа.</p>
      </div>
    </UiCard>

    <template v-else-if="bootstrap && capabilities">
      <!-- Header -->
      <div class="mb-4 flex items-center justify-between md:mb-6">
        <div class="min-w-0">
          <h1 class="truncate text-lg font-semibold text-slate-900 md:text-xl">
            {{ bootstrap.event.title }}
          </h1>
          <p
            v-if="bootstrap.session.status === 'LIVE' || bootstrap.session.status === 'AUTO_LIVE'"
            class="mt-0.5 inline-flex items-center gap-1.5 text-xs font-medium text-danger-700"
          >
            <span class="h-1.5 w-1.5 animate-pulse rounded-full bg-danger-600" />
            В эфире
          </p>
        </div>
        <NuxtLink
          :to="`/e/${tenantSlug}/${eventSlug}`"
          class="text-xs text-slate-500 hover:text-slate-900"
        >
          ← Вернуться
        </NuxtLink>
      </div>

      <!-- Desktop: 2-col grid. Mobile: stacked. -->
      <div class="grid gap-4 md:grid-cols-12 md:gap-6">
        <!-- Video column -->
        <div class="md:col-span-7">
          <div class="aspect-video w-full overflow-hidden rounded-2xl">
            <YoutubePlayer
              :embed-url="bootstrap.session.youtubeEmbedUrl"
              :title="bootstrap.event.title"
            />
          </div>
        </div>

        <!-- Sidebar column (desktop only) -->
        <div class="hidden md:col-span-5 md:flex md:flex-col md:gap-4">
          <RoomCtaList
            v-if="activeCtas.length"
            :ctas="activeCtas"
            :on-click="handleCtaClick"
            class="max-h-72"
          />
          <div class="min-h-[28rem] flex-1">
            <RoomChat
              :messages="messages"
              :capabilities="capabilities"
              :chat-settings="bootstrap.chatSettings"
              :on-send="sendMessage"
              :warnings="warnings"
            />
          </div>
        </div>

        <!-- Mobile tabs -->
        <div class="md:hidden">
          <div class="mb-3 flex rounded-lg bg-slate-100 p-1 text-sm font-medium">
            <button
              type="button"
              class="flex-1 rounded-md px-3 py-1.5 transition"
              :class="mobileTab === 'chat' ? 'bg-white shadow text-slate-900' : 'text-slate-500'"
              @click="mobileTab = 'chat'"
            >
              Чат
            </button>
            <button
              type="button"
              class="flex-1 rounded-md px-3 py-1.5 transition"
              :class="mobileTab === 'cta' ? 'bg-white shadow text-slate-900' : 'text-slate-500'"
              @click="mobileTab = 'cta'"
            >
              Предложения
              <span
                v-if="activeCtas.length"
                class="ml-1 inline-flex h-4 min-w-4 items-center justify-center rounded-full bg-brand-100 px-1 text-[10px] font-semibold text-brand-700"
              >{{ activeCtas.length }}</span>
            </button>
          </div>
          <div class="h-[60vh]">
            <RoomChat
              v-if="mobileTab === 'chat'"
              :messages="messages"
              :capabilities="capabilities"
              :chat-settings="bootstrap.chatSettings"
              :on-send="sendMessage"
              :warnings="warnings"
            />
            <RoomCtaList
              v-else
              :ctas="activeCtas"
              :on-click="handleCtaClick"
            />
          </div>
        </div>
      </div>
    </template>
  </div>
</template>
