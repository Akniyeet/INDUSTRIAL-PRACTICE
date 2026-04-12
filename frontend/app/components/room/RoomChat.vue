<script setup lang="ts">
/**
 * Live chat panel for the participant room.
 *
 * <p>The component owns four concerns:
 * <ol>
 *   <li><b>History seed</b> — messages from {@code RoomBootstrapResponse.recentChat}
 *       arrive as a prop and are rendered immediately.</li>
 *   <li><b>Live tail</b> — the parent page subscribes to the Centrifugo
 *       {@code chat} channel and pushes new messages via the messages model.</li>
 *   <li><b>Reply</b> — users can tap a message to quote-reply. One-level only.</li>
 *   <li><b>Send + slow mode hint</b> — disabled when capabilities forbid
 *       sending or when the local cooldown is still ticking.</li>
 * </ol>
 */
import type {
  RoomCapabilitiesView,
  RoomChatMessageView,
  RoomChatSettingsView,
} from '#shared/api/types'
import { Send, Loader2, X, Reply, AlertTriangle } from 'lucide-vue-next'
import { computed, nextTick, ref, watch } from 'vue'

const props = defineProps<{
  messages: RoomChatMessageView[]
  capabilities: RoomCapabilitiesView
  chatSettings: RoomChatSettingsView
  onSend: (text: string, replyToMessageId?: string) => Promise<void>
  /** Private warnings pushed from the state channel. */
  warnings?: Array<{ reason: string; receivedAt: number }>
}>()

const text = ref('')
const sending = ref(false)
const lastSentAt = ref<number | null>(null)

// ---------------------------------------------------------------------------
// Reply state
// ---------------------------------------------------------------------------
const replyTo = ref<RoomChatMessageView | null>(null)

function selectReply(m: RoomChatMessageView) {
  // One-level only: can't reply to a reply, system, or historical message
  if (m.replyToMessageId) return
  if (m.messageType === 'SYSTEM' || m.messageType === 'HISTORICAL') return
  if (!props.capabilities.canSendChat) return
  replyTo.value = m
  // Focus the input
  nextTick(() => {
    const input = document.querySelector<HTMLInputElement>('#chat-input')
    input?.focus()
  })
}

function cancelReply() {
  replyTo.value = null
}

// ---------------------------------------------------------------------------
// Warning dismiss
// ---------------------------------------------------------------------------
const dismissedWarnings = ref(new Set<number>())

function dismissWarning(receivedAt: number) {
  dismissedWarnings.value.add(receivedAt)
}

const activeWarnings = computed(() =>
  (props.warnings ?? []).filter(w => !dismissedWarnings.value.has(w.receivedAt)),
)

// ---------------------------------------------------------------------------
// Slow-mode countdown
// ---------------------------------------------------------------------------
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

// ---------------------------------------------------------------------------
// Chat mode awareness
// ---------------------------------------------------------------------------
const chatDisabledMessage = computed(() => {
  if (!props.capabilities.canSendChat) {
    return 'Чат тек оқуға ашық'
  }
  return null
})

const sendDisabled = computed(() => {
  if (!props.capabilities.canSendChat) return true
  if (sending.value) return true
  if (cooldown.value > 0) return true
  return text.value.trim().length === 0
})

const placeholder = computed(() => {
  if (chatDisabledMessage.value) return chatDisabledMessage.value
  if (cooldown.value > 0) return `${cooldown.value} сек кейін жіберуге болады`
  if (replyTo.value) return 'Жауабыңызды жазыңыз...'
  return 'Хабарлама жазыңыз...'
})

// ---------------------------------------------------------------------------
// Send
// ---------------------------------------------------------------------------
async function handleSend() {
  const value = text.value.trim()
  if (sendDisabled.value || !value) return
  sending.value = true
  try {
    await props.onSend(value, replyTo.value?.id)
    text.value = ''
    replyTo.value = null
    lastSentAt.value = Date.now()
    startCooldown()
  } finally {
    sending.value = false
  }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
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

function avatarInitial(m: RoomChatMessageView): string {
  if (m.messageType === 'SYSTEM') return '•'
  if (m.messageType === 'ADMIN') return 'A'
  if (m.messageType === 'HISTORICAL') return 'H'
  const id = m.userId ?? ''
  return id ? id.slice(0, 1).toUpperCase() : '?'
}

function authorLabel(m: RoomChatMessageView): string {
  if (m.messageType === 'SYSTEM') return 'Жүйе'
  if (m.messageType === 'ADMIN') return 'Админ'
  if (m.messageType === 'HISTORICAL') return 'Тарихи'
  return m.userId ? `${m.userId.slice(0, 8)}…` : 'Аноним'
}

/** Find the original message being replied to */
function findReplyTarget(id: string | null): RoomChatMessageView | undefined {
  if (!id) return undefined
  return props.messages.find(m => m.id === id)
}
</script>

<template>
  <div class="flex h-full flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-soft">
    <!-- Header -->
    <header class="flex items-center justify-between border-b border-slate-100 px-4 py-3">
      <h3 class="text-sm font-semibold text-slate-900">Чат</h3>
      <div class="flex items-center gap-2">
        <span
          v-if="chatDisabledMessage && !capabilities.canSendChat"
          class="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-500"
        >
          Тек оқу
        </span>
        <span
          v-if="chatSettings.slowModeSeconds > 0 && !capabilities.bypassSlowMode"
          class="rounded-full bg-warning-100 px-2 py-0.5 text-xs font-medium text-warning-700"
        >
          Slow mode {{ chatSettings.slowModeSeconds }}s
        </span>
      </div>
    </header>

    <!-- Private warning banner -->
    <div
      v-for="w in activeWarnings"
      :key="w.receivedAt"
      class="flex items-center gap-2 border-b border-warning-200 bg-warning-50 px-4 py-2"
    >
      <AlertTriangle class="h-4 w-4 flex-shrink-0 text-warning-600" />
      <p class="flex-1 text-xs text-warning-800">
        <strong>Модератордан ескерту:</strong> {{ w.reason || 'Чат ережелерін сақтаңыз.' }}
      </p>
      <button
        type="button"
        class="text-warning-500 hover:text-warning-700"
        @click="dismissWarning(w.receivedAt)"
      >
        <X class="h-3.5 w-3.5" />
      </button>
    </div>

    <!-- Messages -->
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
          class="group flex items-start gap-3"
          :class="{
            'opacity-70': m.messageType === 'SYSTEM',
            'opacity-60 italic': m.messageType === 'HISTORICAL',
          }"
        >
          <div
            class="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full text-xs font-semibold"
            :class="{
              'bg-warning-100 text-warning-700': m.messageType === 'ADMIN',
              'bg-slate-100 text-slate-500': m.messageType === 'SYSTEM',
              'bg-slate-50 text-slate-400': m.messageType === 'HISTORICAL',
              'bg-brand-100 text-brand-700': m.messageType !== 'ADMIN' && m.messageType !== 'SYSTEM' && m.messageType !== 'HISTORICAL',
            }"
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
              <span
                v-if="m.messageType === 'HISTORICAL'"
                class="rounded bg-slate-100 px-1.5 py-0.5 text-[10px] font-medium text-slate-500"
              >Тарихи</span>
              <span class="text-[11px] text-slate-400">{{ formatTime(m.createdAt) }}</span>
              <!-- Reply button (visible on hover, only for USER/ADMIN messages without replyTo) -->
              <button
                v-if="capabilities.canSendChat && !m.replyToMessageId && m.messageType !== 'SYSTEM' && m.messageType !== 'HISTORICAL'"
                type="button"
                class="ml-auto opacity-0 transition-opacity group-hover:opacity-100"
                title="Жауап беру"
                @click="selectReply(m)"
              >
                <Reply class="h-3.5 w-3.5 text-slate-400 hover:text-brand-600" />
              </button>
            </div>
            <!-- Reply quote -->
            <div
              v-if="m.replyToMessageId"
              class="mt-1 rounded border-l-2 border-brand-300 bg-brand-50/50 px-2 py-1"
            >
              <p class="truncate text-xs text-slate-500">
                {{ findReplyTarget(m.replyToMessageId)?.text ?? 'Жойылған хабарлама' }}
              </p>
            </div>
            <p class="break-words text-sm text-slate-800">{{ m.text }}</p>
          </div>
        </li>
      </ul>
    </div>

    <!-- Footer: reply bar + input -->
    <footer class="border-t border-slate-100 bg-slate-50">
      <!-- Reply preview -->
      <div
        v-if="replyTo"
        class="flex items-center gap-2 border-b border-slate-200 bg-brand-50 px-3 py-2"
      >
        <Reply class="h-3.5 w-3.5 flex-shrink-0 text-brand-500" />
        <p class="flex-1 truncate text-xs text-slate-600">
          <span class="font-medium">{{ authorLabel(replyTo) }}:</span>
          {{ replyTo.text }}
        </p>
        <button type="button" class="text-slate-400 hover:text-slate-600" @click="cancelReply">
          <X class="h-3.5 w-3.5" />
        </button>
      </div>

      <form class="flex items-center gap-2 px-3 py-3" @submit.prevent="handleSend">
        <input
          id="chat-input"
          v-model="text"
          type="text"
          maxlength="500"
          :disabled="!capabilities.canSendChat"
          :placeholder="placeholder"
          class="flex-1 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-100 disabled:bg-slate-100 disabled:text-slate-400"
          @keydown.enter.prevent="handleSend"
        />
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
