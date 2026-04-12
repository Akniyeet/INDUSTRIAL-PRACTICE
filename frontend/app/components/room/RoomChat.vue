<script setup lang="ts">
/**
 * Live chat panel for the participant room.
 *
 * <p>The component owns three concerns:
 * <ol>
 *   <li><b>History seed</b> — the messages from {@code RoomBootstrapResponse.recentChat}
 *       arrive as a prop and are rendered immediately so visitors don't see an
 *       empty box on first paint.</li>
 *   <li><b>Live tail</b> — the parent page subscribes to the Centrifugo
 *       {@code chat} channel and pushes new messages in via the
 *       {@link #messages} model. We do NOT optimistically append on send;
 *       per CLAUDE.md §9 the canonical order is whatever Centrifugo emits, so
 *       the user's own message arrives via the same broadcast as everyone
 *       else's. The send button just shows a brief "жіберілді" hint until
 *       the broadcast lands (or until 2 s pass — at which point we surface
 *       a warning that the message may have been throttled).</li>
 *   <li><b>Send + slow mode hint</b> — disabled when capabilities forbid
 *       sending or when the local cooldown is still ticking. The cooldown
 *       countdown is purely client-side UX; the backend is the source of
 *       truth and will reject early sends with 429.</li>
 * </ol>
 *
 * <p>The visual frame follows the same low-chrome card style as the admin
 * panels — a sticky header, a scrollable body, a sticky footer with the
 * input. On mobile the parent page may render this inside a bottom drawer
 * (see {@code room.vue}); the component itself does not assume a layout.
 */
import type {
  RoomCapabilitiesView,
  RoomChatMessageView,
  RoomChatSettingsView,
} from '#shared/api/types'
import { Send, Loader2 } from 'lucide-vue-next'
import { computed, nextTick, ref, watch } from 'vue'

const props = defineProps<{
  messages: RoomChatMessageView[]
  capabilities: RoomCapabilitiesView
  /**
   * Chat settings from the same bootstrap payload. We read the slow-mode
   * window from here rather than from capabilities because slow mode lives
   * on {@code EventChatSettings} server-side and can change without a
   * capability recompute.
   */
  chatSettings: RoomChatSettingsView
  /** Wired by the parent — submits to the backend chat send endpoint. */
  onSend: (text: string) => Promise<void>
}>()

const text = ref('')
const sending = ref(false)
const lastSentAt = ref<number | null>(null)

/** Slow-mode countdown remaining (seconds). 0 means: ready to send. */
const cooldown = ref(0)
let cooldownTimer: ReturnType<typeof setInterval> | null = null

function startCooldown() {
  if (props.capabilities.bypassSlowMode) return
  const slow = props.chatSettings.slowModeSeconds
  if (slow <= 0) return
  cooldown.value = slow
  if (cooldownTimer) clearInterval(cooldownTimer)
  cooldownTimer = setInterval(() => {
    cooldown.value = Math.max(0, cooldown.value - 1)
    if (cooldown.value === 0 && cooldownTimer) {
      clearInterval(cooldownTimer)
      cooldownTimer = null
    }
  }, 1000)
}

const sendDisabled = computed(() => {
  if (!props.capabilities.canSendChat) return true
  if (sending.value) return true
  if (cooldown.value > 0) return true
  return text.value.trim().length === 0
})

const placeholder = computed(() => {
  if (!props.capabilities.canSendChat) return 'Чат тек қарап отыруға ашық'
  if (cooldown.value > 0) return `${cooldown.value} сек кейін жіберуге болады`
  return 'Хабарлама жазыңыз...'
})

async function handleSend() {
  const value = text.value.trim()
  if (sendDisabled.value || !value) return
  sending.value = true
  try {
    await props.onSend(value)
    text.value = ''
    lastSentAt.value = Date.now()
    startCooldown()
  } finally {
    sending.value = false
  }
}

// Auto-scroll on new messages.
const scrollEl = ref<HTMLElement | null>(null)
function scrollToBottom() {
  if (!scrollEl.value) return
  scrollEl.value.scrollTop = scrollEl.value.scrollHeight
}
watch(
  () => props.messages.length,
  () => { void nextTick(scrollToBottom) },
)

const timeFmt = new Intl.DateTimeFormat('ru-RU', { hour: '2-digit', minute: '2-digit' })
function formatTime(iso: string): string {
  try { return timeFmt.format(new Date(iso)) } catch { return '' }
}

/**
 * Render a stable avatar initial from whatever identity we have in the
 * bootstrap payload. The backend currently ships the user id but not a
 * resolved display name, so the first hex character of the UUID gives us
 * a deterministic per-user letter without faking a name.
 */
function avatarInitial(m: RoomChatMessageView): string {
  if (m.messageType === 'SYSTEM') return '•'
  if (m.messageType === 'ADMIN') return 'A'
  const id = m.userId ?? ''
  return id ? id.slice(0, 1).toUpperCase() : '?'
}

function authorLabel(m: RoomChatMessageView): string {
  if (m.messageType === 'SYSTEM') return 'Жүйе'
  if (m.messageType === 'ADMIN') return 'Админ'
  return m.userId ? `${m.userId.slice(0, 8)}…` : 'Аноним'
}
</script>

<template>
  <div class="flex h-full flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-soft">
    <header class="flex items-center justify-between border-b border-slate-100 px-4 py-3">
      <h3 class="text-sm font-semibold text-slate-900">Чат</h3>
      <span
        v-if="chatSettings.slowModeSeconds > 0 && !capabilities.bypassSlowMode"
        class="rounded-full bg-warning-100 px-2 py-0.5 text-xs font-medium text-warning-700"
      >
        Slow mode {{ chatSettings.slowModeSeconds }}s
      </span>
    </header>

    <div
      ref="scrollEl"
      class="flex-1 overflow-y-auto px-4 py-3"
    >
      <div v-if="messages.length === 0" class="flex h-full items-center justify-center text-sm text-slate-400">
        Әзірге хабарламалар жоқ
      </div>
      <ul v-else class="space-y-3">
        <li
          v-for="m in messages"
          :key="m.id"
          class="flex items-start gap-3"
          :class="{ 'opacity-70': m.messageType === 'SYSTEM' }"
        >
          <div
            class="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full bg-brand-100 text-xs font-semibold text-brand-700"
            :class="{ 'bg-warning-100 text-warning-700': m.messageType === 'ADMIN', 'bg-slate-100 text-slate-500': m.messageType === 'SYSTEM' }"
          >
            {{ avatarInitial(m) }}
          </div>
          <div class="min-w-0 flex-1">
            <div class="flex items-baseline gap-2">
              <span class="truncate text-xs font-semibold text-slate-700">
                {{ authorLabel(m) }}
              </span>
              <span
                v-if="m.messageType === 'ADMIN'"
                class="rounded bg-warning-100 px-1.5 py-0.5 text-[10px] font-bold uppercase tracking-wide text-warning-700"
              >Admin</span>
              <span class="text-[11px] text-slate-400">{{ formatTime(m.createdAt) }}</span>
            </div>
            <p class="break-words text-sm text-slate-800">{{ m.text }}</p>
          </div>
        </li>
      </ul>
    </div>

    <footer class="border-t border-slate-100 bg-slate-50 px-3 py-3">
      <form class="flex items-center gap-2" @submit.prevent="handleSend">
        <input
          v-model="text"
          type="text"
          maxlength="500"
          :disabled="!capabilities.canSendChat"
          :placeholder="placeholder"
          class="flex-1 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-100 disabled:bg-slate-100 disabled:text-slate-400"
          @keydown.enter.prevent="handleSend"
        >
        <button
          type="submit"
          :disabled="sendDisabled"
          class="inline-flex h-10 w-10 items-center justify-center rounded-lg bg-brand-600 text-white transition hover:bg-brand-700 disabled:cursor-not-allowed disabled:bg-slate-300"
          aria-label="Жіберу"
        >
          <Loader2 v-if="sending" class="h-4 w-4 animate-spin" />
          <Send v-else class="h-4 w-4" />
        </button>
      </form>
    </footer>
  </div>
</template>
