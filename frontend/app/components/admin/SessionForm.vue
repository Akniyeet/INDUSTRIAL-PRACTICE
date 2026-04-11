<script setup lang="ts">
/**
 * Session create/edit form.
 *
 * <p>One surface for both modes. The `mode` and `initial` props follow the
 * same contract as {@code EventForm}. In edit mode the session `type` is
 * locked — the backend does not allow LIVE ⇆ AUTO conversion and lying to
 * admins about it would cause confusing 409s later.
 *
 * <h2>Session type semantics</h2>
 * <ul>
 *   <li>LIVE — a real broadcast. Requires a YouTube URL. Status starts at
 *       {@code SCHEDULED}, transitions through {@code LIVE} → {@code ENDED}.</li>
 *   <li>AUTO — a scheduled replay of an earlier {@code ENDED} LIVE session of
 *       the same event. Requires {@code sourceLiveSessionId}. Status starts at
 *       {@code AUTO_SCHEDULED}.</li>
 * </ul>
 *
 * <p>For AUTO sessions we show a dropdown of eligible source sessions that
 * the parent passes in via the `availableSources` prop. The parent is the one
 * with cache-coherent access to the sessions list, so we don't refetch here.
 *
 * <h2>Datetime handling</h2>
 * <p>We use a native `<input type="datetime-local">` which gives back a string
 * like {@code "2026-05-01T18:30"} in the browser's local timezone. We convert
 * that to a proper ISO-8601 instant with the zone offset via {@code new Date().toISOString()}.
 * This is good enough for MVP — admins schedule events in their own timezone
 * and that matches the browser's tz in practice. Full IANA-aware scheduling
 * is a later iteration (see §23 Frontend Implementation Conventions).
 */
import { Save, X, ArrowLeft, Radio, Youtube, History, AlertCircle } from 'lucide-vue-next'
import { ref, reactive, computed } from 'vue'
import { format } from 'date-fns'
import type {
  SessionCreateRequest,
  SessionResponse,
  SessionType,
  SessionUpdateRequest,
  UUID,
} from '#shared/api/types'

type Mode = 'create' | 'edit'

const props = withDefaults(
  defineProps<{
    mode: Mode
    eventId: UUID
    initial?: SessionResponse | null
    /** Ended LIVE sessions of the same event — eligible AUTO sources. */
    availableSources?: SessionResponse[]
    submitLabel?: string
  }>(),
  {
    initial: null,
    availableSources: () => [],
    submitLabel: undefined,
  },
)

const emit = defineEmits<{
  (e: 'submit', session: SessionResponse): void
  (e: 'cancel'): void
}>()

const api = useApi()
const toast = useToastStore()

// ---------------------------------------------------------------------------
// Form state
// ---------------------------------------------------------------------------

interface FormState {
  type: SessionType
  /** datetime-local value: "YYYY-MM-DDTHH:mm" */
  startTimeLocal: string
  /** minutes — converted to seconds on submit */
  durationMinutes: number
  youtubeUrl: string
  sourceLiveSessionId: string
}

/**
 * Turn a backend ISO-8601 instant into a value suitable for
 * `<input type="datetime-local">` (local time, no zone, no seconds).
 */
function isoToLocalInput(iso: string | null | undefined): string {
  if (!iso) return ''
  try {
    return format(new Date(iso), "yyyy-MM-dd'T'HH:mm")
  } catch {
    return ''
  }
}

const form = reactive<FormState>({
  type: props.initial?.type ?? 'LIVE',
  startTimeLocal: isoToLocalInput(props.initial?.startTime),
  durationMinutes: Math.max(1, Math.round((props.initial?.plannedDurationSeconds ?? 3600) / 60)),
  youtubeUrl: props.initial?.youtubeUrl ?? '',
  sourceLiveSessionId: props.initial?.sourceLiveSessionId ?? '',
})

const errors = reactive<Partial<Record<keyof FormState, string>>>({})
const loading = ref(false)

const typeLocked = computed(() => props.mode === 'edit')

const typeOptions = [
  { value: 'LIVE', label: 'LIVE — нақты уақыттағы эфир' },
  { value: 'AUTO', label: 'AUTO — жазылған сессияны қайта ойнату' },
]

const sourceOptions = computed(() =>
  props.availableSources
    .filter((s) => s.status === 'ENDED')
    .map((s) => ({
      value: s.id,
      label: `${formatSourceLabel(s)}`,
    })),
)

function formatSourceLabel(s: SessionResponse): string {
  try {
    return `${format(new Date(s.startTime), 'dd.MM.yyyy HH:mm')} · ${Math.round(s.plannedDurationSeconds / 60)} мин`
  } catch {
    return s.id
  }
}

const hasNoSources = computed(
  () => form.type === 'AUTO' && sourceOptions.value.length === 0,
)

// ---------------------------------------------------------------------------
// Validation
// ---------------------------------------------------------------------------

const YOUTUBE_URL_PATTERN =
  /^(https?:\/\/)?(www\.)?(youtube\.com\/(watch\?v=|live\/|embed\/)|youtu\.be\/)[\w-]{6,}.*$/

function validate(): boolean {
  for (const k of Object.keys(errors) as (keyof FormState)[]) delete errors[k]

  if (!form.startTimeLocal) {
    errors.startTimeLocal = 'Басталу уақытын таңдаңыз.'
  } else {
    const ts = new Date(form.startTimeLocal).getTime()
    if (Number.isNaN(ts)) {
      errors.startTimeLocal = 'Жарамсыз уақыт форматы.'
    } else if (props.mode === 'create' && ts < Date.now() - 5 * 60_000) {
      // Allow 5-minute grace window so scheduling "right now" works.
      errors.startTimeLocal = 'Уақыт өткен. Келешек уақытты таңдаңыз.'
    }
  }

  if (!form.durationMinutes || form.durationMinutes < 1) {
    errors.durationMinutes = 'Ұзақтығы кем дегенде 1 минут болуы тиіс.'
  } else if (form.durationMinutes > 24 * 60) {
    errors.durationMinutes = '24 сағаттан аспауы тиіс.'
  }

  if (form.type === 'LIVE') {
    if (!form.youtubeUrl.trim()) {
      errors.youtubeUrl = 'YouTube сілтемесін енгізіңіз.'
    } else if (!YOUTUBE_URL_PATTERN.test(form.youtubeUrl.trim())) {
      errors.youtubeUrl = 'Жарамсыз YouTube сілтемесі.'
    }
  }

  if (form.type === 'AUTO') {
    if (!form.sourceLiveSessionId) {
      errors.sourceLiveSessionId = 'Негізгі LIVE сессияны таңдаңыз.'
    }
  }

  return Object.keys(errors).length === 0
}

// ---------------------------------------------------------------------------
// Submit
// ---------------------------------------------------------------------------

function toIsoInstant(local: string): string {
  // `<input type="datetime-local">` returns "YYYY-MM-DDTHH:mm" (no zone).
  // `new Date(value)` interprets it as local time — `.toISOString()` then
  // gives us a UTC ISO-8601 string which is what the backend expects.
  return new Date(local).toISOString()
}

function buildCreateBody(): SessionCreateRequest {
  const body: SessionCreateRequest = {
    type: form.type,
    startTime: toIsoInstant(form.startTimeLocal),
    plannedDurationSeconds: form.durationMinutes * 60,
  }
  if (form.type === 'LIVE') {
    body.youtubeUrl = form.youtubeUrl.trim()
  } else {
    body.sourceLiveSessionId = form.sourceLiveSessionId
  }
  return body
}

function buildUpdateBody(): SessionUpdateRequest {
  const body: SessionUpdateRequest = {
    startTime: toIsoInstant(form.startTimeLocal),
    plannedDurationSeconds: form.durationMinutes * 60,
  }
  if (form.type === 'LIVE') {
    body.youtubeUrl = form.youtubeUrl.trim()
  } else {
    body.sourceLiveSessionId = form.sourceLiveSessionId
  }
  return body
}

async function onSubmit() {
  if (!validate()) {
    toast.warning('Формада қателер бар')
    return
  }

  loading.value = true
  try {
    let saved: SessionResponse
    if (props.mode === 'create') {
      saved = await api.sessions.create(props.eventId, buildCreateBody())
      toast.success('Сессия жасалды')
    } else {
      if (!props.initial) throw new Error('Edit mode without initial session')
      saved = await api.sessions.update(props.initial.id, buildUpdateBody())
      toast.success('Сессия жаңартылды')
    }
    emit('submit', saved)
  } catch (err) {
    const apiErr = err as { errors?: Record<string, string> }
    if (apiErr.errors) {
      for (const [field, message] of Object.entries(apiErr.errors)) {
        if (field in form) {
          (errors as Record<string, string>)[field] = message
        }
      }
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <form class="space-y-5" @submit.prevent="onSubmit">
    <!-- Type -->
    <div>
      <label class="field-label">
        Сессия түрі
        <span class="text-danger-600">*</span>
      </label>
      <div class="grid gap-2 sm:grid-cols-2">
        <label
          v-for="opt in typeOptions"
          :key="opt.value"
          class="flex cursor-pointer items-start gap-3 rounded-xl border border-slate-200 p-3 transition-colors"
          :class="[
            form.type === opt.value ? 'border-brand-500 bg-brand-50/40 ring-1 ring-brand-500/30' : 'hover:bg-slate-50',
            typeLocked && 'cursor-not-allowed opacity-60',
          ]"
        >
          <input
            v-model="form.type"
            type="radio"
            :value="opt.value"
            :disabled="typeLocked"
            class="mt-0.5 accent-brand-600"
          />
          <div class="min-w-0 flex-1">
            <div class="flex items-center gap-1.5 text-sm font-medium text-slate-900">
              <Radio v-if="opt.value === 'LIVE'" class="h-3.5 w-3.5 text-danger-500" />
              <History v-else class="h-3.5 w-3.5 text-brand-500" />
              {{ opt.value }}
            </div>
            <p class="mt-0.5 text-xs text-slate-500">{{ opt.label.split(' — ')[1] }}</p>
          </div>
        </label>
      </div>
      <p v-if="typeLocked" class="field-hint">
        Сессия түрі жасалғаннан кейін өзгертілмейді.
      </p>
    </div>

    <!-- Datetime + duration row -->
    <div class="grid gap-4 sm:grid-cols-2">
      <div>
        <label class="field-label" for="session-start">
          Басталу уақыты <span class="text-danger-600">*</span>
        </label>
        <input
          id="session-start"
          v-model="form.startTimeLocal"
          type="datetime-local"
          class="input-base"
          :class="errors.startTimeLocal && 'input-error'"
          required
        />
        <p v-if="errors.startTimeLocal" class="field-error">{{ errors.startTimeLocal }}</p>
        <p v-else class="field-hint">Браузердің уақыт белдеуі бойынша.</p>
      </div>

      <UiInput
        v-model.number="form.durationMinutes"
        type="number"
        label="Ұзақтығы (минут)"
        placeholder="60"
        :error="errors.durationMinutes"
        hint="Жоспарланған ұзақтық. Эфир бұдан ерте де, кеш те аяқталуы мүмкін."
        required
      />
    </div>

    <!-- LIVE: YouTube URL -->
    <div v-if="form.type === 'LIVE'">
      <UiInput
        v-model="form.youtubeUrl"
        label="YouTube сілтемесі"
        placeholder="https://www.youtube.com/watch?v=..."
        :error="errors.youtubeUrl"
        required
      />
      <p v-if="!errors.youtubeUrl" class="field-hint flex items-center gap-1">
        <Youtube class="h-3 w-3" /> watch, live/ немесе youtu.be форматы қабылданады.
      </p>
    </div>

    <!-- AUTO: source selector -->
    <div v-else>
      <UiSelect
        v-model="form.sourceLiveSessionId"
        label="Негізгі LIVE сессия"
        :options="sourceOptions"
        :placeholder="hasNoSources ? 'Аяқталған LIVE сессиялар жоқ' : 'Таңдаңыз'"
        :error="errors.sourceLiveSessionId"
        :disabled="hasNoSources"
        required
      />
      <p v-if="hasNoSources" class="field-hint flex items-start gap-1 text-warning-700">
        <AlertCircle class="mt-0.5 h-3 w-3 shrink-0" />
        AUTO сессия жасау үшін алдымен LIVE сессияны аяқтау керек. Оның видео мен чаты replay үшін қайнар көз болады.
      </p>
    </div>

    <!-- Actions -->
    <div class="flex items-center justify-between gap-3 border-t border-slate-100 pt-4">
      <UiButton variant="ghost" type="button" @click="emit('cancel')">
        <ArrowLeft class="h-4 w-4" />
        Қайту
      </UiButton>

      <div class="flex items-center gap-2">
        <UiButton variant="outline" type="button" :disabled="loading" @click="emit('cancel')">
          <X class="h-4 w-4" />
          Бас тарту
        </UiButton>
        <UiButton
          variant="primary"
          type="submit"
          :loading="loading"
          :disabled="hasNoSources"
        >
          <Save class="h-4 w-4" />
          {{ submitLabel ?? (mode === 'create' ? 'Жасау' : 'Сақтау') }}
        </UiButton>
      </div>
    </div>
  </form>
</template>
