<script setup lang="ts">
/**
 * Admin live control room at {@code /admin/sessions/{id}/live}.
 *
 * <p>This is where moderators actually run a broadcast. The page stitches
 * together:
 * <ol>
 *   <li>The same {@link YoutubePlayer} wrapper viewers see, so the moderator
 *       can verify the stream is actually playing what they think it is.</li>
 *   <li>A moderator-flavoured chat view with delete / mute / chat-ban
 *       quick actions and an admin-persona send box.</li>
 *   <li>A CTA show/hide control grid — each toggle posts to the live
 *       control endpoint which both fires a timeline row (for AUTO replay)
 *       and broadcasts on the control channel (for the current viewers).</li>
 *   <li>A moderation audit tail that refreshes after every action.</li>
 *   <li>Presence count + end-live button.</li>
 * </ol>
 *
 * <p>The page reuses the same bootstrap contract as the participant room —
 * the only difference is it expects {@code capabilities.canModerate} to be
 * true. If the backend says it isn't, we show a permission-denied card.
 *
 * <p>All realtime wiring is browser-only. The Centrifugo subscriptions for
 * chat / timeline / system are the same channels the participants use, so
 * we see everything they see the instant it lands.
 */
import type {
  CtaResponse,
  ModerationActionResponse,
  RoomBootstrapResponse,
  RoomChatMessageView,
  UUID,
} from '#shared/api/types'
import {
  Radio,
  Square,
  Users,
  AlertTriangle,
  RefreshCw,
  Download,
} from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'

definePageMeta({
  layout: 'admin',
  middleware: 'auth',
})

const route = useRoute()
const router = useRouter()
const api = useApi()
const toast = useToastStore()

const sessionId = computed(() => route.params.id as string)

// ---------------------------------------------------------------------------
// Bootstrap
// ---------------------------------------------------------------------------
const { data: bootstrap, error: bootstrapError, refresh: refreshBootstrap } =
  await useAsyncData<RoomBootstrapResponse>(
    () => `admin-live-boot:${sessionId.value}`,
    () => api.room.bootstrap(sessionId.value),
    { watch: [sessionId] },
  )

useHead(() => ({
  title: bootstrap.value?.event?.title
    ? `${bootstrap.value.event.title} — Live control`
    : 'Live control',
}))

// ---------------------------------------------------------------------------
// Local reactive state
// ---------------------------------------------------------------------------
const messages = ref<RoomChatMessageView[]>([])
const ctas = ref<CtaResponse[]>([])
const activeCtaIds = ref<Set<UUID>>(new Set())
const pendingCtaIds = ref<Set<UUID>>(new Set())
const presentNow = ref(0)
const moderationLog = ref<ModerationActionResponse[]>([])
const sessionStatus = shallowRef<RoomBootstrapResponse['session']['status'] | null>(null)

watch(
  bootstrap,
  async (b) => {
    if (!b) return
    messages.value    = [...b.recentChat]
    presentNow.value  = b.presentNowCount
    activeCtaIds.value = new Set(b.activeCtas.map((c) => c.id))
    sessionStatus.value = b.session.status
    // Load full CTA catalogue for the event (bootstrap only ships the
    // currently-visible ones; the toggle panel needs every defined CTA).
    try {
      ctas.value = await api.cta.list(b.event.id)
    } catch {
      ctas.value = [...b.activeCtas]
    }
    // Initial moderation log seed.
    try {
      moderationLog.value = await api.moderation.log(b.session.id, 50)
    } catch {
      moderationLog.value = []
    }
  },
  { immediate: true },
)

// ---------------------------------------------------------------------------
// Centrifuge wiring — same channels as the participant room
// ---------------------------------------------------------------------------
type ChatPub = { message: RoomChatMessageView } | RoomChatMessageView
type CtaPub = {
  type: 'CTA_SHOW' | 'CTA_HIDE'
  cta?: CtaResponse
  ctaId?: string
}
/** Presence channel emits the current attendee count. */
type PresencePub = {
  type?: 'PresenceChanged'
  presentNow?: number
}
/** State channel emits session-status transitions (SCHEDULED → LIVE → ENDED). */
type StatePub = {
  type: 'SessionStatusChanged'
  status?: RoomBootstrapResponse['session']['status']
}

const subs: Array<{ unsubscribe: () => void }> = []

onMounted(() => {
  if (!bootstrap.value) return
  const { channels } = bootstrap.value
  const { subscribe } = useCentrifuge()

  subs.push(
    subscribe<ChatPub>(channels.chat, {
      onPublication: (data) => {
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
          const next = new Set(activeCtaIds.value)
          next.add(evt.cta.id)
          activeCtaIds.value = next
        } else if (evt.type === 'CTA_HIDE') {
          const targetId = evt.ctaId ?? evt.cta?.id
          if (targetId) {
            const next = new Set(activeCtaIds.value)
            next.delete(targetId)
            activeCtaIds.value = next
          }
        }
      },
    }),
  )

  subs.push(
    subscribe<PresencePub>(channels.presence, {
      onPublication: (evt) => {
        if (typeof evt.presentNow === 'number') presentNow.value = evt.presentNow
      },
    }),
  )

  subs.push(
    subscribe<StatePub>(channels.state, {
      onPublication: (evt) => {
        if (evt.type === 'SessionStatusChanged' && evt.status) {
          sessionStatus.value = evt.status
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
// Moderation actions
// ---------------------------------------------------------------------------
function prependLog(action: ModerationActionResponse) {
  moderationLog.value = [action, ...moderationLog.value].slice(0, 100)
}

async function sendAdminMessage(text: string) {
  if (!sessionId.value) return
  await api.chat.send(sessionId.value, { text })
}

async function onDeleteMessage(messageId: UUID) {
  try {
    const action = await api.moderation.deleteMessage(sessionId.value, { messageId })
    prependLog(action)
    // Optimistically hide from the local list — the real hide also lands
    // via Centrifugo but the moderator wants instant feedback.
    messages.value = messages.value.filter((m) => m.id !== messageId)
    toast.success('Хабарлама жойылды')
  } catch {
    // global error handler toasts
  }
}

async function onMuteUser(userId: UUID, displayName: string | null) {
  try {
    const action = await api.moderation.mute(sessionId.value, {
      targetUserId: userId,
      durationSeconds: 300,
      reason: 'Slow down',
    })
    prependLog(action)
    toast.success(`${displayName ?? 'Қолданушы'} 5 минутқа үнсіз қойылды`)
  } catch {
    // global handler
  }
}

async function onChatBanUser(userId: UUID, displayName: string | null) {
  try {
    const action = await api.moderation.chatBan(sessionId.value, {
      targetUserId: userId,
      reason: 'Chat abuse',
    })
    prependLog(action)
    toast.success(`${displayName ?? 'Қолданушы'} чаттан банға түсті`)
  } catch {
    // global handler
  }
}

// ---------------------------------------------------------------------------
// CTA control
// ---------------------------------------------------------------------------
async function onCtaToggle(cta: CtaResponse, show: boolean) {
  if (pendingCtaIds.value.has(cta.id)) return
  const next = new Set(pendingCtaIds.value)
  next.add(cta.id)
  pendingCtaIds.value = next
  try {
    if (show) {
      await api.cta.show(sessionId.value, cta.id)
      const ids = new Set(activeCtaIds.value)
      ids.add(cta.id)
      activeCtaIds.value = ids
      toast.success(`"${cta.title}" көрсетілді`)
    } else {
      await api.cta.hide(sessionId.value, cta.id)
      const ids = new Set(activeCtaIds.value)
      ids.delete(cta.id)
      activeCtaIds.value = ids
      toast.success(`"${cta.title}" жасырылды`)
    }
  } catch {
    // global toast
  } finally {
    const done = new Set(pendingCtaIds.value)
    done.delete(cta.id)
    pendingCtaIds.value = done
  }
}

// ---------------------------------------------------------------------------
// Session lifecycle
// ---------------------------------------------------------------------------
const endingLive = ref(false)
const endLiveModalOpen = ref(false)

const isLive = computed(
  () => sessionStatus.value === 'LIVE' || sessionStatus.value === 'AUTO_LIVE',
)

async function endLive() {
  if (!bootstrap.value) return
  endingLive.value = true
  try {
    const updated = bootstrap.value.session.type === 'AUTO'
      ? await api.sessions.endAuto(sessionId.value)
      : await api.sessions.endLive(sessionId.value)
    sessionStatus.value = updated.status
    toast.success('Эфир аяқталды')
    endLiveModalOpen.value = false
  } finally {
    endingLive.value = false
  }
}

function downloadExport() {
  const config = useRuntimeConfig()
  const base = config.public.apiBase || '/api/backend'
  window.open(`${base}/v1/sessions/${sessionId.value}/export`, '_blank')
}

// Permission guard — we read it lazily from bootstrap.
const canModerate = computed(() => bootstrap.value?.capabilities.canModerate ?? false)
</script>

<template>
  <div>
    <!-- Bootstrap error -->
    <UiCard v-if="bootstrapError" class="border-danger-200 bg-danger-50/60">
      <div class="flex items-start gap-3">
        <AlertTriangle class="mt-0.5 h-5 w-5 text-danger-500" />
        <div class="flex-1">
          <h3 class="text-sm font-semibold text-danger-800">Эфир жүктелмеді</h3>
          <p class="mt-1 text-sm text-danger-700">
            Сессия табылмады немесе сізде оған кіру құқығы жоқ.
          </p>
        </div>
      </div>
      <template #footer>
        <UiButton variant="outline" size="sm" @click="router.back()">Артқа</UiButton>
        <UiButton variant="primary" size="sm" @click="refreshBootstrap()">Қайта көру</UiButton>
      </template>
    </UiCard>

    <!-- Missing moderator role -->
    <UiCard v-else-if="bootstrap && !canModerate" class="border-warning-200 bg-warning-50/60">
      <div class="flex items-start gap-3">
        <AlertTriangle class="mt-0.5 h-5 w-5 text-warning-500" />
        <div class="flex-1">
          <h3 class="text-sm font-semibold text-warning-800">Кіру құқығы жетпейді</h3>
          <p class="mt-1 text-sm text-warning-700">
            Тірі эфирді басқару үшін модератор/пресентер рөлі қажет. Өтінеміз,
            тенант әкімшісімен байланысыңыз.
          </p>
        </div>
      </div>
    </UiCard>

    <!-- Content -->
    <template v-else-if="bootstrap">
      <PageHeader
        :title="bootstrap.event.title"
        :subtitle="`Сессия · ${new Date(bootstrap.session.startTime).toLocaleString('ru-RU')}`"
        :breadcrumbs="[
          { label: 'Басты бет', to: '/admin' },
          { label: 'Ивенттер', to: '/admin/events' },
          { label: bootstrap.event.title, to: `/admin/events/${bootstrap.event.id}` },
          { label: 'Эфирді басқару' },
        ]"
      >
        <template #actions>
          <UiButton
            variant="ghost"
            size="md"
            @click="refreshBootstrap()"
          >
            <RefreshCw class="h-4 w-4" />
            Жаңарту
          </UiButton>
          <UiButton
            v-if="isLive"
            variant="danger"
            size="md"
            :loading="endingLive"
            @click="endLiveModalOpen = true"
          >
            <Square class="h-4 w-4" />
            Эфирді аяқтау
          </UiButton>
          <UiButton
            variant="outline"
            size="md"
            @click="downloadExport()"
          >
            <Download class="h-4 w-4" />
            Excel
          </UiButton>
        </template>
      </PageHeader>

      <!-- Status strip -->
      <div
        class="mb-5 flex flex-wrap items-center gap-3 rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm"
      >
        <span
          v-if="isLive"
          class="inline-flex items-center gap-1.5 text-xs font-medium text-danger-700"
        >
          <span class="h-1.5 w-1.5 animate-pulse rounded-full bg-danger-600" />
          Эфирде
        </span>
        <SessionStatusBadge v-else :status="sessionStatus ?? bootstrap.session.status" />
        <span class="text-slate-300">·</span>
        <span class="inline-flex items-center gap-1.5 text-slate-600">
          <Users class="h-4 w-4" />
          <span class="font-semibold text-slate-900">{{ presentNow }}</span>
          көрермен
        </span>
        <span class="text-slate-300">·</span>
        <span class="text-slate-500">
          Тип: <span class="font-medium text-slate-900">{{ bootstrap.session.type }}</span>
        </span>
      </div>

      <!-- Three-column workspace -->
      <div class="grid gap-4 lg:grid-cols-12">
        <!-- Video + CTA control -->
        <div class="space-y-4 lg:col-span-7">
          <div class="aspect-video w-full overflow-hidden rounded-2xl">
            <YoutubePlayer
              :embed-url="bootstrap.session.youtubeEmbedUrl"
              :title="bootstrap.event.title"
            />
          </div>
          <div class="min-h-[20rem]">
            <LiveCtaControl
              :ctas="ctas"
              :active-cta-ids="activeCtaIds"
              :pending-ids="pendingCtaIds"
              @toggle="onCtaToggle"
            />
          </div>
        </div>

        <!-- Chat + Moderation log -->
        <div class="space-y-4 lg:col-span-5">
          <div class="h-[32rem]">
            <LiveModeratorChat
              :messages="messages"
              :on-send="sendAdminMessage"
              @delete-message="onDeleteMessage"
              @mute-user="onMuteUser"
              @chat-ban-user="onChatBanUser"
            />
          </div>
          <div class="h-[20rem]">
            <LiveModerationLog :actions="moderationLog" />
          </div>
        </div>
      </div>
    </template>

    <!-- End-live confirm -->
    <UiModal v-model="endLiveModalOpen" title="Эфирді аяқтау">
      <p class="text-sm text-slate-600">
        Эфирді аяқтағаннан кейін көрермендер үшін бөлме жабылады және финалдау
        процесі басталады (аналитика, чат архиві, таймлайн дайындығы). Бұл
        әрекетті кері қайтару мүмкін емес.
      </p>
      <template #footer>
        <UiButton variant="outline" :disabled="endingLive" @click="endLiveModalOpen = false">
          Бас тарту
        </UiButton>
        <UiButton variant="danger" :loading="endingLive" @click="endLive">
          <Square class="h-4 w-4" />
          Аяқтау
        </UiButton>
      </template>
    </UiModal>
  </div>
</template>
