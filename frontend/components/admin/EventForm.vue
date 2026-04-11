<script setup lang="ts">
/**
 * Event create/edit form.
 *
 * <p>One component for both create and edit modes — the parent decides which
 * by passing an `initial` event or leaving it undefined. In edit mode we lock
 * the slug field because the backend slug is part of the public URL and
 * changing it would silently break existing landing pages. If a tenant truly
 * needs a different slug they should archive + create a new event.
 *
 * <h2>Validation</h2>
 * <p>We do minimal client-side validation here (required fields, slug format,
 * length caps). The backend is the source of truth; its {@code ProblemDetail}
 * responses flow through {@link useApi} and get surfaced as field-level errors
 * via the {@code errors} map on {@link ApiError}.
 *
 * <h2>Emits</h2>
 * <ul>
 *   <li>{@code submit} — fires after a successful backend write, receives the
 *       saved {@link EventResponse}. Parent handles navigation.</li>
 *   <li>{@code cancel} — user clicked the secondary button.</li>
 * </ul>
 */
import { X, Save, ArrowLeft, Globe, User, Image as ImageIcon, FileText } from 'lucide-vue-next'
import { ref, reactive, computed } from 'vue'
import type {
  EventCreateRequest,
  EventResponse,
  EventUpdateRequest,
} from '~/shared/api/types'

type Mode = 'create' | 'edit'

const props = withDefaults(
  defineProps<{
    mode: Mode
    initial?: EventResponse | null
    submitLabel?: string
  }>(),
  {
    initial: null,
    submitLabel: undefined,
  },
)

const emit = defineEmits<{
  (e: 'submit', event: EventResponse): void
  (e: 'cancel'): void
}>()

const api = useApi()
const toast = useToastStore()

// ---------------------------------------------------------------------------
// Form state
// ---------------------------------------------------------------------------

interface FormState {
  slug: string
  title: string
  description: string
  speakerName: string
  speakerBio: string
  coverImageUrl: string
  timezone: string
  language: string
}

const form = reactive<FormState>({
  slug: props.initial?.slug ?? '',
  title: props.initial?.title ?? '',
  description: props.initial?.description ?? '',
  speakerName: props.initial?.speakerName ?? '',
  speakerBio: props.initial?.speakerBio ?? '',
  coverImageUrl: props.initial?.coverImageUrl ?? '',
  timezone: props.initial?.timezone ?? 'Asia/Almaty',
  language: props.initial?.language ?? 'kk',
})

const errors = reactive<Partial<Record<keyof FormState, string>>>({})
const loading = ref(false)

// Common timezones. Kept short — full IANA list is overkill for MVP and we
// can expand later once we have customers in more regions.
const timezoneOptions = [
  { value: 'Asia/Almaty',    label: 'Asia/Almaty (UTC+6)' },
  { value: 'Asia/Aqtobe',    label: 'Asia/Aqtobe (UTC+5)' },
  { value: 'Europe/Moscow',  label: 'Europe/Moscow (UTC+3)' },
  { value: 'Europe/Kyiv',    label: 'Europe/Kyiv (UTC+3)' },
  { value: 'Europe/London',  label: 'Europe/London (UTC+0)' },
  { value: 'UTC',            label: 'UTC' },
]

const languageOptions = [
  { value: 'kk', label: 'Қазақша' },
  { value: 'ru', label: 'Русский' },
  { value: 'en', label: 'English' },
]

const slugLocked = computed(() => props.mode === 'edit')

// ---------------------------------------------------------------------------
// Slug autogen (create mode only)
// ---------------------------------------------------------------------------

/**
 * Mirrors the Java server-side slugifier well enough for preview purposes.
 * Backend still validates and rejects invalid slugs — this is purely a nicer
 * default so admins don't have to type it by hand.
 */
function slugify(input: string): string {
  return input
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9\s-]/g, '')
    .trim()
    .replace(/\s+/g, '-')
    .replace(/-+/g, '-')
    .slice(0, 64)
}

let slugTouched = Boolean(props.initial?.slug)

function onSlugInput() {
  slugTouched = true
}

function onTitleInput(value: string) {
  form.title = value
  if (!slugLocked.value && !slugTouched) {
    form.slug = slugify(value)
  }
}

// ---------------------------------------------------------------------------
// Validation
// ---------------------------------------------------------------------------

const SLUG_PATTERN = /^[a-z0-9]+(?:-[a-z0-9]+)*$/

function validate(): boolean {
  for (const k of Object.keys(errors) as (keyof FormState)[]) delete errors[k]

  if (!form.title.trim()) {
    errors.title = 'Атауды енгізіңіз.'
  } else if (form.title.length > 200) {
    errors.title = 'Атау 200 таңбадан аспауы керек.'
  }

  if (props.mode === 'create') {
    if (!form.slug.trim()) {
      errors.slug = 'Slug енгізіңіз.'
    } else if (!SLUG_PATTERN.test(form.slug)) {
      errors.slug = 'Slug тек кіші латын әріптері, сандар және дефис болуы тиіс.'
    } else if (form.slug.length > 64) {
      errors.slug = 'Slug 64 таңбадан аспауы керек.'
    }
  }

  if (form.description && form.description.length > 4000) {
    errors.description = 'Сипаттама 4000 таңбадан аспауы керек.'
  }

  if (form.coverImageUrl && !/^https?:\/\//.test(form.coverImageUrl)) {
    errors.coverImageUrl = 'URL http:// немесе https:// арқылы басталуы тиіс.'
  }

  return Object.keys(errors).length === 0
}

// ---------------------------------------------------------------------------
// Submit
// ---------------------------------------------------------------------------

function buildCreateBody(): EventCreateRequest {
  return {
    slug: form.slug.trim(),
    title: form.title.trim(),
    description: form.description.trim() || undefined,
    speakerName: form.speakerName.trim() || undefined,
    speakerBio: form.speakerBio.trim() || undefined,
    coverImageUrl: form.coverImageUrl.trim() || undefined,
    timezone: form.timezone || undefined,
    language: form.language || undefined,
  }
}

function buildUpdateBody(): EventUpdateRequest {
  // For edit mode, send the full set of editable fields; the backend handles
  // no-op diffs. Explicit nulls let admins clear optional fields.
  return {
    title: form.title.trim(),
    description: form.description.trim() || undefined,
    speakerName: form.speakerName.trim() || undefined,
    speakerBio: form.speakerBio.trim() || undefined,
    coverImageUrl: form.coverImageUrl.trim() || undefined,
    timezone: form.timezone || undefined,
    language: form.language || undefined,
  }
}

async function onSubmit() {
  if (!validate()) {
    toast.warning('Формада қателер бар')
    return
  }

  loading.value = true
  try {
    let saved: EventResponse
    if (props.mode === 'create') {
      saved = await api.events.create(buildCreateBody())
      toast.success('Ивент жасалды')
    } else {
      if (!props.initial) throw new Error('Edit mode without initial event')
      saved = await api.events.update(props.initial.id, buildUpdateBody())
      toast.success('Өзгерістер сақталды')
    }
    emit('submit', saved)
  } catch (err) {
    // Server-side validation: map per-field errors back onto the form.
    const apiErr = err as { errors?: Record<string, string>; status?: number }
    if (apiErr.errors) {
      for (const [field, message] of Object.entries(apiErr.errors)) {
        if (field in form) {
          (errors as Record<string, string>)[field] = message
        }
      }
    }
    // Network/unexpected errors already surfaced by useApi onError handler.
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <form class="space-y-6" @submit.prevent="onSubmit">
    <!-- Section 1: Basics -->
    <UiCard>
      <template #header>
        <div class="flex items-center gap-2">
          <FileText class="h-4 w-4 text-slate-400" />
          <h3 class="text-base font-semibold text-slate-900">Негізгі ақпарат</h3>
        </div>
        <p class="mt-0.5 text-sm text-slate-500">
          Ивенттің атауы және бірегей мекенжайы.
        </p>
      </template>

      <div class="grid gap-5">
        <UiInput
          label="Атауы"
          :model-value="form.title"
          placeholder="Мысалы: Java backend бойынша тегін сабақ"
          :error="errors.title"
          :maxlength="200"
          required
          @update:model-value="onTitleInput"
        />

        <UiInput
          v-model="form.slug"
          label="Slug (URL)"
          placeholder="java-backend-free-lesson"
          :error="errors.slug"
          :hint="slugLocked
            ? 'Slug өзгертілмейді. Жаңа ивент жасау арқылы ауыстырыңыз.'
            : '/event/' + (form.slug || '...') + ' — ивенттің ашық мекенжайы.'"
          :disabled="slugLocked"
          :maxlength="64"
          required
          @update:model-value="onSlugInput"
        />

        <UiTextarea
          v-model="form.description"
          label="Сипаттама"
          placeholder="Ивент туралы қысқаша..."
          :error="errors.description"
          :maxlength="4000"
          :rows="5"
          autoresize
        />
      </div>
    </UiCard>

    <!-- Section 2: Speaker -->
    <UiCard>
      <template #header>
        <div class="flex items-center gap-2">
          <User class="h-4 w-4 text-slate-400" />
          <h3 class="text-base font-semibold text-slate-900">Спикер</h3>
        </div>
        <p class="mt-0.5 text-sm text-slate-500">
          Лендингте көрсетіледі.
        </p>
      </template>

      <div class="grid gap-5">
        <UiInput
          v-model="form.speakerName"
          label="Спикер аты"
          placeholder="Мысалы: Абай Құнанбайұлы"
          :maxlength="200"
        />

        <UiTextarea
          v-model="form.speakerBio"
          label="Спикер биосы"
          placeholder="Қысқаша тәжірибе, атақ, жетістіктер..."
          :maxlength="1000"
          :rows="3"
          autoresize
        />
      </div>
    </UiCard>

    <!-- Section 3: Media + locale -->
    <UiCard>
      <template #header>
        <div class="flex items-center gap-2">
          <Globe class="h-4 w-4 text-slate-400" />
          <h3 class="text-base font-semibold text-slate-900">Медиа және тіл</h3>
        </div>
      </template>

      <div class="grid gap-5">
        <div>
          <UiInput
            v-model="form.coverImageUrl"
            label="Мұқаба суреті (URL)"
            placeholder="https://..."
            :error="errors.coverImageUrl"
            hint="Жарияланғаннан кейін бұл сурет лендинг пен тізімдерде көрінеді."
          />
          <div
            v-if="form.coverImageUrl && !errors.coverImageUrl"
            class="mt-3 overflow-hidden rounded-xl border border-slate-200 bg-slate-50"
          >
            <img
              :src="form.coverImageUrl"
              :alt="form.title || 'cover'"
              class="h-40 w-full object-cover"
              @error="errors.coverImageUrl = 'Сурет жүктелмеді. URL дұрыс па?'"
            />
          </div>
          <div
            v-else
            class="mt-3 flex h-32 items-center justify-center rounded-xl border border-dashed border-slate-200 bg-slate-50 text-slate-400"
          >
            <ImageIcon class="h-6 w-6" />
          </div>
        </div>

        <div class="grid gap-5 sm:grid-cols-2">
          <UiSelect
            v-model="form.timezone"
            label="Уақыт белдеуі"
            :options="timezoneOptions"
          />
          <UiSelect
            v-model="form.language"
            label="Тілі"
            :options="languageOptions"
          />
        </div>
      </div>
    </UiCard>

    <!-- Actions -->
    <div class="sticky bottom-0 flex items-center justify-between gap-3 rounded-xl border border-slate-200 bg-white/95 px-4 py-3 shadow-sm backdrop-blur">
      <UiButton variant="ghost" type="button" @click="emit('cancel')">
        <ArrowLeft class="h-4 w-4" />
        Қайту
      </UiButton>

      <div class="flex items-center gap-2">
        <UiButton variant="outline" type="button" :disabled="loading" @click="emit('cancel')">
          <X class="h-4 w-4" />
          Бас тарту
        </UiButton>
        <UiButton variant="primary" type="submit" :loading="loading">
          <Save class="h-4 w-4" />
          {{ submitLabel ?? (mode === 'create' ? 'Жасау' : 'Сақтау') }}
        </UiButton>
      </div>
    </div>
  </form>
</template>
