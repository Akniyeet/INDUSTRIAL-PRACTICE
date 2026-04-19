<script setup lang="ts">
/**
 * Tailing log of moderation actions for the current session.
 *
 * <p>Populated by the parent live-control page — which either seeds it from
 * {@code GET /sessions/{id}/moderation/log} on mount, or prepends a row
 * after every successful moderation call. The component does not subscribe
 * to anything itself; that keeps it trivially testable and means the same
 * list can be reused in offline review later.
 *
 * <p>We intentionally keep this skinny — the audit surface in §53 has more
 * fields (reason, durationSeconds, etc.) but the live room only needs a
 * quick visual confirmation that a moderator's action landed.
 */
import type { ModerationActionResponse, ModerationActionType } from '#shared/api/types'
import { AlertTriangle, EyeOff, Hammer, Trash2, VolumeX, ShieldBan, LogOut } from 'lucide-vue-next'
import { computed } from 'vue'

const props = defineProps<{
  actions: ModerationActionResponse[]
}>()

const sorted = computed(() =>
  [...props.actions].sort(
    (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
  ),
)

function iconFor(type: ModerationActionType) {
  switch (type) {
    case 'WARNING':        return AlertTriangle
    case 'MUTE':           return VolumeX
    case 'CHAT_BAN':       return ShieldBan
    case 'ROOM_REMOVE':    return LogOut
    case 'FULL_BAN':       return Hammer
    case 'MESSAGE_DELETE': return Trash2
    case 'MESSAGE_HIDE':   return EyeOff
    default:               return AlertTriangle
  }
}

function colorFor(type: ModerationActionType): string {
  switch (type) {
    case 'WARNING':        return 'bg-warning-100 text-warning-700'
    case 'MUTE':           return 'bg-slate-100 text-slate-700'
    case 'CHAT_BAN':       return 'bg-orange-100 text-orange-700'
    case 'ROOM_REMOVE':    return 'bg-orange-100 text-orange-700'
    case 'FULL_BAN':       return 'bg-danger-100 text-danger-700'
    case 'MESSAGE_DELETE': return 'bg-danger-100 text-danger-700'
    case 'MESSAGE_HIDE':   return 'bg-slate-100 text-slate-700'
    default:               return 'bg-slate-100 text-slate-700'
  }
}

function labelFor(type: ModerationActionType): string {
  switch (type) {
    case 'WARNING':        return 'Ескерту'
    case 'MUTE':           return 'Уақытша үнсіз қою'
    case 'CHAT_BAN':       return 'Чаттан бан'
    case 'ROOM_REMOVE':    return 'Бөлмеден шығару'
    case 'FULL_BAN':       return 'Толық бан'
    case 'MESSAGE_DELETE': return 'Хабарлама жойылды'
    case 'MESSAGE_HIDE':   return 'Хабарлама жасырылды'
    default:               return type
  }
}

const timeFmt = new Intl.DateTimeFormat('ru-RU', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
function formatTime(iso: string): string {
  try { return timeFmt.format(new Date(iso)) } catch { return '' }
}
</script>

<template>
  <div class="flex h-full flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-soft">
    <header class="border-b border-slate-100 px-4 py-3">
      <h3 class="text-sm font-semibold text-slate-900">Модерация журналы</h3>
      <p class="text-xs text-slate-500">Соңғы әрекеттер</p>
    </header>

    <div class="flex-1 overflow-y-auto p-4">
      <div v-if="sorted.length === 0" class="flex h-full items-center justify-center text-sm text-slate-400">
        Модерация әрекеттері жоқ
      </div>
      <ul v-else class="space-y-2">
        <li
          v-for="a in sorted"
          :key="a.id"
          class="flex items-start gap-3 rounded-lg border border-slate-100 bg-slate-50 px-3 py-2"
        >
          <div
            class="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-lg"
            :class="colorFor(a.actionType)"
          >
            <component :is="iconFor(a.actionType)" class="h-4 w-4" />
          </div>
          <div class="min-w-0 flex-1">
            <div class="flex items-center justify-between gap-2">
              <span class="truncate text-xs font-semibold text-slate-800">
                {{ labelFor(a.actionType) }}
              </span>
              <span class="flex-shrink-0 text-[11px] text-slate-400">{{ formatTime(a.createdAt) }}</span>
            </div>
            <p class="mt-0.5 truncate text-[11px] text-slate-500">
              <span v-if="a.durationSeconds">{{ a.durationSeconds }}s · </span>
              <span v-if="a.reason">{{ a.reason }}</span>
              <span v-else class="italic">себеп көрсетілмеген</span>
            </p>
          </div>
        </li>
      </ul>
    </div>
  </div>
</template>
