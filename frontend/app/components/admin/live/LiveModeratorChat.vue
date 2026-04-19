<script setup lang="ts">
/**
 * Moderator-facing chat view.
 *
 * <p>Looks similar to {@code RoomChat} but every row has a quick-action menu
 * (delete message / mute sender / chat-ban sender) and the send box is
 * locked to the {@code ADMIN} persona — anything the moderator types goes
 * out as an admin broadcast with the stronger styling everyone else's room
 * renders differently.
 *
 * <p>Messages arrive as a prop from the parent live-control page, which owns
 * the Centrifuge subscription. The component is pure UI: it emits action
 * requests and leaves the actual API call (including toast + confirmation)
 * to the parent so the moderation log can refresh in the same tick.
 */
import type { RoomChatMessageView, UUID } from '#shared/api/types'
import { MoreVertical, Send, Trash2, VolumeX, ShieldBan } from 'lucide-vue-next'
import { computed, nextTick, ref, watch } from 'vue'

const props = defineProps<{
  messages: RoomChatMessageView[]
  /** Wired by parent — sends as ADMIN persona. */
  onSend: (text: string) => Promise<void>
}>()

const emit = defineEmits<{
  (e: 'delete-message', messageId: UUID): void
  (e: 'mute-user', userId: UUID, label: string | null): void
  (e: 'chat-ban-user', userId: UUID, label: string | null): void
}>()

/**
 * Stable avatar initial — backend doesn't ship a resolved display name, so we
 * fall back to the first hex character of the author uuid (deterministic per
 * user). SYSTEM and ADMIN rows get fixed markers.
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

const text = ref('')
const sending = ref(false)

// Which message id currently shows the quick-action popover.
const openMenuId = ref<UUID | null>(null)

function toggleMenu(id: UUID) {
  openMenuId.value = openMenuId.value === id ? null : id
}

async function handleSend() {
  const value = text.value.trim()
  if (!value || sending.value) return
  sending.value = true
  try {
    await props.onSend(value)
    text.value = ''
  } finally {
    sending.value = false
  }
}

function handleDelete(messageId: UUID) {
  openMenuId.value = null
  emit('delete-message', messageId)
}

function handleMute(userId: UUID | null, displayName: string | null) {
  if (!userId) return
  openMenuId.value = null
  emit('mute-user', userId, displayName)
}

function handleChatBan(userId: UUID | null, displayName: string | null) {
  if (!userId) return
  openMenuId.value = null
  emit('chat-ban-user', userId, displayName)
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

const timeFmt = new Intl.DateTimeFormat('ru-RU', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
function formatTime(iso: string): string {
  try { return timeFmt.format(new Date(iso)) } catch { return '' }
}

const visibleMessages = computed(() => props.messages)
</script>

<template>
  <div class="flex h-full flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-soft">
    <header class="flex items-center justify-between border-b border-slate-100 px-4 py-3">
      <div>
        <h3 class="text-sm font-semibold text-slate-900">Чат · Модератор</h3>
        <p class="text-xs text-slate-500">{{ visibleMessages.length }} хабарлама</p>
      </div>
    </header>

    <div
      ref="scrollEl"
      class="flex-1 overflow-y-auto px-4 py-3"
    >
      <div v-if="visibleMessages.length === 0" class="flex h-full items-center justify-center text-sm text-slate-400">
        Әзірге хабарламалар жоқ
      </div>
      <ul v-else class="space-y-2">
        <li
          v-for="m in visibleMessages"
          :key="m.id"
          class="group relative flex items-start gap-3 rounded-lg px-2 py-1.5 hover:bg-slate-50"
        >
          <div
            class="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full bg-brand-100 text-xs font-semibold text-brand-700"
            :class="{
              'bg-warning-100 text-warning-700': m.messageType === 'ADMIN',
              'bg-slate-100 text-slate-500': m.messageType === 'SYSTEM',
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
              <span class="text-[11px] text-slate-400">{{ formatTime(m.createdAt) }}</span>
            </div>
            <p class="break-words text-sm text-slate-800">{{ m.text }}</p>
          </div>

          <!-- Action menu trigger -->
          <button
            type="button"
            class="invisible flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-md text-slate-400 hover:bg-slate-200 hover:text-slate-700 group-hover:visible"
            :class="{ '!visible': openMenuId === m.id }"
            aria-label="Әрекеттер"
            @click="toggleMenu(m.id)"
          >
            <MoreVertical class="h-4 w-4" />
          </button>

          <!-- Popover -->
          <div
            v-if="openMenuId === m.id"
            class="absolute right-2 top-9 z-10 w-48 rounded-lg border border-slate-200 bg-white p-1 shadow-lg"
          >
            <button
              type="button"
              class="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-xs font-medium text-danger-700 hover:bg-danger-50"
              @click="handleDelete(m.id)"
            >
              <Trash2 class="h-3.5 w-3.5" />
              Хабарламаны жою
            </button>
            <button
              type="button"
              :disabled="!m.userId"
              class="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-xs font-medium text-slate-700 hover:bg-slate-100 disabled:cursor-not-allowed disabled:text-slate-300"
              @click="handleMute(m.userId, authorLabel(m))"
            >
              <VolumeX class="h-3.5 w-3.5" />
              Пайдаланушыны уақытша үнсіз қою
            </button>
            <button
              type="button"
              :disabled="!m.userId"
              class="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-xs font-medium text-slate-700 hover:bg-slate-100 disabled:cursor-not-allowed disabled:text-slate-300"
              @click="handleChatBan(m.userId, authorLabel(m))"
            >
              <ShieldBan class="h-3.5 w-3.5" />
              Чаттан бан
            </button>
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
          placeholder="Админ ретінде жазу..."
          class="flex-1 rounded-lg border border-warning-200 bg-white px-3 py-2 text-sm focus:border-warning-500 focus:outline-none focus:ring-2 focus:ring-warning-100"
          @keydown.enter.prevent="handleSend"
        >
        <button
          type="submit"
          :disabled="sending || text.trim().length === 0"
          class="inline-flex h-10 w-10 items-center justify-center rounded-lg bg-warning-600 text-white transition hover:bg-warning-700 disabled:cursor-not-allowed disabled:bg-slate-300"
          aria-label="Админ ретінде жіберу"
        >
          <Send class="h-4 w-4" />
        </button>
      </form>
    </footer>
  </div>
</template>
