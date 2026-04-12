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
    toast.error('Чат баптауларын жүктеу қатесі')
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
    })
    toast.success('Баптаулар сақталды')
  } catch (err) {
    const apiErr = err as { detail?: string; title?: string }
    toast.error(apiErr.detail ?? 'Сақтау қатесі')
  } finally {
    saving.value = false
  }
}

// ---------------------------------------------------------------------------
// Slow mode options
// ---------------------------------------------------------------------------

const SLOW_MODE_OPTIONS = [
  { value: 0, label: 'Өшірулі' },
  { value: 5, label: '5 секунд' },
  { value: 10, label: '10 секунд' },
  { value: 20, label: '20 секунд' },
  { value: 30, label: '30 секунд' },
  { value: 60, label: '1 минут' },
]
</script>

<template>
  <div class="space-y-5">
    <!-- Header -->
    <div class="flex items-center justify-between gap-3">
      <div>
        <h2 class="text-lg font-semibold text-slate-900">Чат баптаулары</h2>
        <p class="mt-0.5 text-sm text-slate-500">
          Ивент деңгейіндегі чат ережелері. Барлық сессияларға қолданылады.
        </p>
      </div>
      <div class="flex items-center gap-2">
        <UiButton variant="outline" size="md" :disabled="loading" @click="load">
          <RefreshCw class="h-4 w-4" :class="loading && 'animate-spin'" />
        </UiButton>
        <UiButton variant="primary" size="md" :loading="saving" :disabled="!settings" @click="save">
          <Save class="h-4 w-4" />
          Сақтау
        </UiButton>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="loading && !settings" class="space-y-3">
      <UiSkeleton v-for="i in 4" :key="i" h="h-[64px]" rounded="rounded-xl" />
    </div>

    <!-- Settings form -->
    <template v-else-if="settings">
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
              <p class="text-xs text-slate-500">Хабарламалар модератор мақұлдағанға дейін көрінбейді</p>
            </div>
            <input
              v-model="settings.premoderationEnabled"
              type="checkbox"
              class="h-5 w-5 rounded border-slate-300 text-brand-600 focus:ring-brand-500"
            />
          </label>
          <label class="flex items-center justify-between">
            <div>
              <p class="text-sm font-medium text-slate-700">Мат сүзгісі</p>
              <p class="text-xs text-slate-500">Тыйым салынған сөздерді автоматты түрде сүзу</p>
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
              <p class="text-xs text-slate-500">Қайталанатын хабарламалар мен флудты анықтау</p>
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
          <h3 class="text-sm font-semibold text-slate-900">Жіберу шектеулері</h3>
        </div>
        <div class="space-y-4">
          <div>
            <label class="mb-1.5 block text-sm font-medium text-slate-700">Баяу режим (slow mode)</label>
            <UiSelect v-model.number="settings.slowModeSeconds">
              <option v-for="opt in SLOW_MODE_OPTIONS" :key="opt.value" :value="opt.value">
                {{ opt.label }}
              </option>
            </UiSelect>
            <p class="mt-1 text-xs text-slate-500">
              Қолданушылар хабарлама арасында осынша уақыт күтуі керек. Модераторлар мен админдер шектелмейді.
            </p>
          </div>
          <label class="flex items-center justify-between">
            <div>
              <p class="text-sm font-medium text-slate-700">Сілтемелерге рұқсат</p>
              <p class="text-xs text-slate-500">Қолданушылар чатта URL жібере алады</p>
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
          <h3 class="text-sm font-semibold text-slate-900">Көрсету</h3>
        </div>
        <div class="space-y-4">
          <label class="flex items-center justify-between">
            <div>
              <p class="text-sm font-medium text-slate-700">Қатысушылар саны</p>
              <p class="text-xs text-slate-500">Бөлмедегі белсенді қатысушылар санын көрсету</p>
            </div>
            <input
              v-model="settings.showParticipantCount"
              type="checkbox"
              class="h-5 w-5 rounded border-slate-300 text-brand-600 focus:ring-brand-500"
            />
          </label>
          <label class="flex items-center justify-between">
            <div>
              <p class="text-sm font-medium text-slate-700">Қатысушылар атаулары</p>
              <p class="text-xs text-slate-500">Чатта қолданушы атауларын көрсету</p>
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
          <h3 class="text-sm font-semibold text-slate-900">Сәлемдесу хабарламасы</h3>
        </div>
        <textarea
          v-model="settings.welcomeMessage"
          rows="3"
          class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:ring-1 focus:ring-brand-500"
          placeholder="Бөлмеге кірген кезде көрсетілетін хабарлама..."
        />
        <p class="mt-1 text-xs text-slate-500">
          Бос қалдырсаңыз, сәлемдесу хабарламасы көрсетілмейді.
        </p>
      </UiCard>
    </template>
  </div>
</template>
