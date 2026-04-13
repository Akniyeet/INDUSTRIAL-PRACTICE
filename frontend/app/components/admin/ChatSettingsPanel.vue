<script setup lang="ts">
/**
 * Chat settings panel — event-level chat configuration.
 *
 * <p>Mounted as the "Чат баптаулары" tab on the event detail page. Loads
 * current settings and saves partial updates via PATCH.
 */
import type { ChatSettingsResponse, UUID } from '#shared/api/types'
import {
  Save,
  RefreshCw,
  MessageSquare,
  Shield,
  Clock,
  Link2,
  Eye,
  Users,
} from 'lucide-vue-next'
import { ref } from 'vue'

const props = defineProps<{ eventId: UUID }>()

const api = useApi()
const toast = useToastStore()

// ---------------------------------------------------------------------------
// Data
// ---------------------------------------------------------------------------

const settings = ref<ChatSettingsResponse | null>(null)
const loading = ref(false)
const saving = ref(false)

async function load() {
  loading.value = true
  try {
    settings.value = await api.chat.getSettings(props.eventId)
  } catch {
    toast.error('Ошибка загрузки настроек чата')
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function save() {
  if (!settings.value) return
  saving.value = true
  try {
    settings.value = await api.chat.updateSettings(props.eventId, {
      allowLinks: settings.value.allowLinks,
      slowModeSeconds: settings.value.slowModeSeconds,
      showParticipantCount: settings.value.showParticipantCount,
      showParticipantNames: settings.value.showParticipantNames,
      welcomeMessage: settings.value.welcomeMessage,
      premoderationEnabled: settings.value.premoderationEnabled,
      profanityFilterEnabled: settings.value.profanityFilterEnabled,
      antiSpamEnabled: settings.value.antiSpamEnabled,
      chatMode: settings.value.chatMode,
    })
    toast.success('Настройки сохранены')
  } catch (err) {
    const apiErr = err as { detail?: string; title?: string }
    toast.error(apiErr.detail ?? 'Ошибка сохранения')
  } finally {
    saving.value = false
  }
}

// ---------------------------------------------------------------------------
// Slow mode options
// ---------------------------------------------------------------------------

const SLOW_MODE_OPTIONS = [
  { value: 0, label: 'Выключено' },
  { value: 5, label: '5 секунд' },
  { value: 10, label: '10 секунд' },
  { value: 20, label: '20 секунд' },
  { value: 30, label: '30 секунд' },
  { value: 60, label: '1 минута' },
]

const CHAT_MODE_OPTIONS = [
  { value: 'EVERYONE', label: 'Все могут писать', desc: 'Аутентифицированные пользователи могут писать в чат' },
  { value: 'ADMINS_ONLY', label: 'Только админы', desc: 'Только модераторы и админы могут писать' },
  { value: 'DISABLED', label: 'Чат выключен', desc: 'Никто не может писать, режим только для чтения' },
]
</script>

<template>
  <div class="space-y-5">
    <!-- Header -->
    <div class="flex items-center justify-between gap-3">
      <div>
        <h2 class="text-lg font-semibold text-slate-900">Настройки чата</h2>
        <p class="mt-0.5 text-sm text-slate-500">
          Правила чата на уровне мероприятия. Применяются ко всем сессиям.
        </p>
      </div>
      <div class="flex items-center gap-2">
        <UiButton variant="outline" size="md" :disabled="loading" @click="load">
          <RefreshCw class="h-4 w-4" :class="loading && 'animate-spin'" />
        </UiButton>
        <UiButton variant="primary" size="md" :loading="saving" :disabled="!settings" @click="save">
          <Save class="h-4 w-4" />
          Сохранить
        </UiButton>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="loading && !settings" class="space-y-3">
      <UiSkeleton v-for="i in 4" :key="i" h="h-[64px]" rounded="rounded-xl" />
    </div>

    <!-- Settings form -->
    <template v-else-if="settings">
      <!-- Chat mode section -->
      <UiCard>
        <div class="mb-4 flex items-center gap-2">
          <MessageSquare class="h-4 w-4 text-slate-500" />
          <h3 class="text-sm font-semibold text-slate-900">Режим чата</h3>
        </div>
        <div class="space-y-2">
          <label
            v-for="opt in CHAT_MODE_OPTIONS"
            :key="opt.value"
            class="flex cursor-pointer items-start gap-3 rounded-lg border p-3 transition-colors"
            :class="settings.chatMode === opt.value
              ? 'border-brand-300 bg-brand-50/50'
              : 'border-slate-200 hover:bg-slate-50'"
          >
            <input
              v-model="settings.chatMode"
              type="radio"
              :value="opt.value"
              class="mt-0.5 h-4 w-4 border-slate-300 text-brand-600 focus:ring-brand-500"
            />
            <div>
              <p class="text-sm font-medium text-slate-900">{{ opt.label }}</p>
              <p class="text-xs text-slate-500">{{ opt.desc }}</p>
            </div>
          </label>
        </div>
      </UiCard>

      <!-- Moderation section -->
      <UiCard>
        <div class="mb-4 flex items-center gap-2">
          <Shield class="h-4 w-4 text-slate-500" />
          <h3 class="text-sm font-semibold text-slate-900">Модерация</h3>
        </div>
        <div class="space-y-4">
          <label class="flex items-center justify-between">
            <div>
              <p class="text-sm font-medium text-slate-700">Премодерация</p>
              <p class="text-xs text-slate-500">Сообщения не видны до одобрения модератором</p>
            </div>
            <input
              v-model="settings.premoderationEnabled"
              type="checkbox"
              class="h-5 w-5 rounded border-slate-300 text-brand-600 focus:ring-brand-500"
            />
          </label>
          <label class="flex items-center justify-between">
            <div>
              <p class="text-sm font-medium text-slate-700">Фильтр нецензурной лексики</p>
              <p class="text-xs text-slate-500">Автоматическая фильтрация запрещённых слов</p>
            </div>
            <input
              v-model="settings.profanityFilterEnabled"
              type="checkbox"
              class="h-5 w-5 rounded border-slate-300 text-brand-600 focus:ring-brand-500"
            />
          </label>
          <label class="flex items-center justify-between">
            <div>
              <p class="text-sm font-medium text-slate-700">Анти-спам</p>
              <p class="text-xs text-slate-500">Обнаружение повторяющихся сообщений и флуда</p>
            </div>
            <input
              v-model="settings.antiSpamEnabled"
              type="checkbox"
              class="h-5 w-5 rounded border-slate-300 text-brand-600 focus:ring-brand-500"
            />
          </label>
        </div>
      </UiCard>

      <!-- Rate limiting section -->
      <UiCard>
        <div class="mb-4 flex items-center gap-2">
          <Clock class="h-4 w-4 text-slate-500" />
          <h3 class="text-sm font-semibold text-slate-900">Ограничения отправки</h3>
        </div>
        <div class="space-y-4">
          <div>
            <label class="mb-1.5 block text-sm font-medium text-slate-700">Медленный режим (slow mode)</label>
            <UiSelect v-model.number="settings.slowModeSeconds">
              <option v-for="opt in SLOW_MODE_OPTIONS" :key="opt.value" :value="opt.value">
                {{ opt.label }}
              </option>
            </UiSelect>
            <p class="mt-1 text-xs text-slate-500">
              Пользователи должны ждать указанное время между сообщениями. Модераторы и админы не ограничены.
            </p>
          </div>
          <label class="flex items-center justify-between">
            <div>
              <p class="text-sm font-medium text-slate-700">Разрешить ссылки</p>
              <p class="text-xs text-slate-500">Пользователи могут отправлять URL в чате</p>
            </div>
            <input
              v-model="settings.allowLinks"
              type="checkbox"
              class="h-5 w-5 rounded border-slate-300 text-brand-600 focus:ring-brand-500"
            />
          </label>
        </div>
      </UiCard>

      <!-- Display section -->
      <UiCard>
        <div class="mb-4 flex items-center gap-2">
          <Eye class="h-4 w-4 text-slate-500" />
          <h3 class="text-sm font-semibold text-slate-900">Отображение</h3>
        </div>
        <div class="space-y-4">
          <label class="flex items-center justify-between">
            <div>
              <p class="text-sm font-medium text-slate-700">Количество участников</p>
              <p class="text-xs text-slate-500">Показывать количество активных участников в комнате</p>
            </div>
            <input
              v-model="settings.showParticipantCount"
              type="checkbox"
              class="h-5 w-5 rounded border-slate-300 text-brand-600 focus:ring-brand-500"
            />
          </label>
          <label class="flex items-center justify-between">
            <div>
              <p class="text-sm font-medium text-slate-700">Имена участников</p>
              <p class="text-xs text-slate-500">Показывать имена пользователей в чате</p>
            </div>
            <input
              v-model="settings.showParticipantNames"
              type="checkbox"
              class="h-5 w-5 rounded border-slate-300 text-brand-600 focus:ring-brand-500"
            />
          </label>
        </div>
      </UiCard>

      <!-- Welcome message section -->
      <UiCard>
        <div class="mb-4 flex items-center gap-2">
          <MessageSquare class="h-4 w-4 text-slate-500" />
          <h3 class="text-sm font-semibold text-slate-900">Приветственное сообщение</h3>
        </div>
        <textarea
          v-model="settings.welcomeMessage"
          rows="3"
          class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:ring-1 focus:ring-brand-500"
          placeholder="Сообщение, отображаемое при входе в комнату..."
        />
        <p class="mt-1 text-xs text-slate-500">
          Если оставить пустым, приветственное сообщение не будет показано.
        </p>
      </UiCard>
    </template>
  </div>
</template>
