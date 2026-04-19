<script setup lang="ts">
/**
 * CTA management panel — event-level CRUD for call-to-action items.
 *
 * <p>Mounted as the "CTA" tab on the event detail page. Supports creating,
 * editing, toggling active state, and deleting CTAs. Each CTA has a type
 * (FILE/LINK/COURSE/FORM), placement, priority, and optional stacking.
 */
import type {
  CtaResponse,
  CtaCreateRequest,
  CtaUpdateRequest,
  CtaType,
  CtaPlacement,
  UUID,
} from '#shared/api/types'
import {
  Plus,
  RefreshCw,
  Download,
  ExternalLink,
  BookOpen,
  FileText,
  Megaphone,
  Pencil,
  Trash2,
  ToggleLeft,
  ToggleRight,
  AlertTriangle,
  MousePointerClick,
} from 'lucide-vue-next'
import { ref, computed } from 'vue'

const props = defineProps<{ eventId: UUID }>()

const api = useApi()
const toast = useToastStore()

// ---------------------------------------------------------------------------
// Data
// ---------------------------------------------------------------------------

const ctas = ref<CtaResponse[]>([])
const loading = ref(false)

async function refresh() {
  loading.value = true
  try {
    ctas.value = await api.cta.list(props.eventId)
  } catch {
    toast.error('Ошибка загрузки CTA')
  } finally {
    loading.value = false
  }
}

onMounted(refresh)

// ---------------------------------------------------------------------------
// Labels & icons
// ---------------------------------------------------------------------------

const TYPE_LABELS: Record<CtaType, string> = {
  FILE: 'Файл',
  LINK: 'Ссылка',
  COURSE: 'Курс',
  FORM: 'Форма',
}

const TYPE_ICONS: Record<CtaType, typeof Download> = {
  FILE: Download,
  LINK: ExternalLink,
  COURSE: BookOpen,
  FORM: FileText,
}

const PLACEMENT_LABELS: Record<CtaPlacement, string> = {
  INLINE: 'Инлайн',
  SIDEBAR: 'Боковая панель',
  POPUP: 'Попап',
  BELOW_VIDEO: 'Под видео',
}

const CTA_TYPES: CtaType[] = ['FILE', 'LINK', 'COURSE', 'FORM']
const PLACEMENTS: CtaPlacement[] = ['INLINE', 'SIDEBAR', 'POPUP', 'BELOW_VIDEO']

// ---------------------------------------------------------------------------
// Create / Edit modal
// ---------------------------------------------------------------------------

const modalOpen = ref(false)
const modalMode = ref<'create' | 'edit'>('create')
const editingCta = ref<CtaResponse | null>(null)
const saving = ref(false)

const form = ref({
  title: '',
  description: '',
  type: 'LINK' as CtaType,
  buttonText: '',
  actionUrl: '',
  fileUrl: '',
  placement: 'BELOW_VIDEO' as CtaPlacement,
  priority: 0,
  allowStack: false,
})

function openCreate() {
  modalMode.value = 'create'
  editingCta.value = null
  form.value = {
    title: '',
    description: '',
    type: 'LINK',
    buttonText: 'Узнать больше',
    actionUrl: '',
    fileUrl: '',
    placement: 'BELOW_VIDEO',
    priority: 0,
    allowStack: false,
  }
  modalOpen.value = true
}

function openEdit(cta: CtaResponse) {
  modalMode.value = 'edit'
  editingCta.value = cta
  form.value = {
    title: cta.title,
    description: cta.description ?? '',
    type: cta.type,
    buttonText: cta.buttonText,
    actionUrl: cta.actionUrl ?? '',
    fileUrl: cta.fileUrl ?? '',
    placement: cta.placement,
    priority: cta.priority,
    allowStack: cta.allowStack,
  }
  modalOpen.value = true
}

async function submitForm() {
  saving.value = true
  try {
    if (modalMode.value === 'create') {
      const body: CtaCreateRequest = {
        title: form.value.title,
        description: form.value.description || undefined,
        type: form.value.type,
        buttonText: form.value.buttonText,
        actionUrl: form.value.actionUrl || undefined,
        fileUrl: form.value.fileUrl || undefined,
        placement: form.value.placement,
        priority: form.value.priority,
        allowStack: form.value.allowStack,
      }
      const created = await api.cta.create(props.eventId, body)
      ctas.value.push(created)
      toast.success('CTA добавлен')
    } else if (editingCta.value) {
      const body: CtaUpdateRequest = {
        title: form.value.title,
        description: form.value.description || undefined,
        type: form.value.type,
        buttonText: form.value.buttonText,
        actionUrl: form.value.actionUrl || undefined,
        fileUrl: form.value.fileUrl || undefined,
        placement: form.value.placement,
        priority: form.value.priority,
        allowStack: form.value.allowStack,
      }
      const updated = await api.cta.update(props.eventId, editingCta.value.id, body)
      const idx = ctas.value.findIndex((c) => c.id === updated.id)
      if (idx >= 0) ctas.value[idx] = updated
      toast.success('CTA обновлён')
    }
    modalOpen.value = false
  } catch (err) {
    const apiErr = err as { detail?: string; title?: string }
    toast.error(apiErr.detail ?? 'Ошибка сохранения')
  } finally {
    saving.value = false
  }
}

// ---------------------------------------------------------------------------
// Toggle active
// ---------------------------------------------------------------------------

const togglingId = ref<string | null>(null)

async function toggleActive(cta: CtaResponse) {
  togglingId.value = cta.id
  try {
    const updated = await api.cta.setActive(props.eventId, cta.id, !cta.active)
    const idx = ctas.value.findIndex((c) => c.id === cta.id)
    if (idx >= 0) ctas.value[idx] = updated
  } catch {
    toast.error('Ошибка изменения состояния')
  } finally {
    togglingId.value = null
  }
}

// ---------------------------------------------------------------------------
// Delete
// ---------------------------------------------------------------------------

const deleteModalOpen = ref(false)
const pendingDelete = ref<CtaResponse | null>(null)
const deleting = ref(false)

function askDelete(cta: CtaResponse) {
  pendingDelete.value = cta
  deleteModalOpen.value = true
}

async function confirmDelete() {
  if (!pendingDelete.value) return
  deleting.value = true
  try {
    await api.cta.remove(props.eventId, pendingDelete.value.id)
    ctas.value = ctas.value.filter((c) => c.id !== pendingDelete.value!.id)
    deleteModalOpen.value = false
    pendingDelete.value = null
    toast.success('CTA удалён')
  } catch {
    toast.error('Ошибка удаления')
  } finally {
    deleting.value = false
  }
}

const totalCount = computed(() => ctas.value.length)
const activeCount = computed(() => ctas.value.filter((c) => c.active).length)
</script>

<template>
  <div class="space-y-5">
    <!-- Header -->
    <div class="flex items-center justify-between gap-3">
      <div>
        <h2 class="text-lg font-semibold text-slate-900">Элементы CTA</h2>
        <p class="mt-0.5 text-sm text-slate-500">
          {{ totalCount }} CTA, {{ activeCount }} активных. Типы: файл, ссылка, курс или форма.
        </p>
      </div>
      <div class="flex items-center gap-2">
        <UiButton variant="outline" size="md" :disabled="loading" @click="refresh">
          <RefreshCw class="h-4 w-4" :class="loading && 'animate-spin'" />
        </UiButton>
        <UiButton variant="primary" size="md" @click="openCreate">
          <Plus class="h-4 w-4" />
          Новый CTA
        </UiButton>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="loading && ctas.length === 0" class="space-y-2">
      <UiSkeleton v-for="i in 3" :key="i" h="h-[72px]" rounded="rounded-xl" />
    </div>

    <!-- Empty -->
    <UiEmpty
      v-else-if="ctas.length === 0"
      title="Нет CTA"
      description="Добавьте первый CTA — файл, ссылку, курс или форму."
    >
      <template #icon><Megaphone class="h-5 w-5" /></template>
      <template #actions>
        <UiButton variant="primary" @click="openCreate">
          <Plus class="h-4 w-4" />
          Новый CTA
        </UiButton>
      </template>
    </UiEmpty>

    <!-- CTA list -->
    <UiCard v-else :padded="false">
      <ul class="divide-y divide-slate-100">
        <li
          v-for="cta in ctas"
          :key="cta.id"
          class="flex items-center justify-between px-5 py-3.5"
          :class="!cta.active && 'opacity-50'"
        >
          <div class="flex items-center gap-3 min-w-0">
            <div
              class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg"
              :class="cta.active ? 'bg-brand-50 text-brand-600' : 'bg-slate-100 text-slate-400'"
            >
              <component :is="TYPE_ICONS[cta.type] || MousePointerClick" class="h-5 w-5" />
            </div>
            <div class="min-w-0">
              <p class="truncate font-medium text-slate-900">{{ cta.title }}</p>
              <div class="flex flex-wrap items-center gap-x-2 gap-y-0.5 text-xs text-slate-500">
                <span class="rounded bg-slate-100 px-1.5 py-0.5 font-medium">{{ TYPE_LABELS[cta.type] }}</span>
                <span>{{ PLACEMENT_LABELS[cta.placement] }}</span>
                <span>Приоритет: {{ cta.priority }}</span>
                <span v-if="cta.allowStack" class="text-brand-600">Стек ✓</span>
              </div>
            </div>
          </div>
          <div class="flex items-center gap-1">
            <button
              class="rounded p-1.5 transition-colors"
              :class="cta.active ? 'text-success-600 hover:bg-success-50' : 'text-slate-400 hover:bg-slate-100'"
              :disabled="togglingId === cta.id"
              @click="toggleActive(cta)"
            >
              <component :is="cta.active ? ToggleRight : ToggleLeft" class="h-4 w-4" />
            </button>
            <button
              class="rounded p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600"
              @click="openEdit(cta)"
            >
              <Pencil class="h-4 w-4" />
            </button>
            <button
              class="rounded p-1.5 text-slate-400 hover:bg-danger-50 hover:text-danger-600"
              @click="askDelete(cta)"
            >
              <Trash2 class="h-4 w-4" />
            </button>
          </div>
        </li>
      </ul>
    </UiCard>

    <!-- Create/Edit modal -->
    <UiModal
      v-model="modalOpen"
      :title="modalMode === 'create' ? 'Новый CTA' : 'Редактирование CTA'"
      size="lg"
    >
      <div class="space-y-4">
        <div>
          <label class="mb-1.5 block text-sm font-medium text-slate-700">Заголовок</label>
          <UiInput v-model="form.title" placeholder="Например: Записаться на курс" />
        </div>
        <div>
          <label class="mb-1.5 block text-sm font-medium text-slate-700">Описание</label>
          <textarea
            v-model="form.description"
            rows="2"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:ring-1 focus:ring-brand-500"
            placeholder="Дополнительная информация..."
          />
        </div>
        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="mb-1.5 block text-sm font-medium text-slate-700">Тип</label>

            <UiSelect v-model="form.type">
              <option v-for="t in CTA_TYPES" :key="t" :value="t">{{ TYPE_LABELS[t] }}</option>
            </UiSelect>
          </div>
          <div>
            <label class="mb-1.5 block text-sm font-medium text-slate-700">Размещение</label>
            <UiSelect v-model="form.placement">
              <option v-for="p in PLACEMENTS" :key="p" :value="p">{{ PLACEMENT_LABELS[p] }}</option>
            </UiSelect>
          </div>
        </div>
        <div>
          <label class="mb-1.5 block text-sm font-medium text-slate-700">Текст кнопки</label>
          <UiInput v-model="form.buttonText" placeholder="Узнать больше" />
        </div>
        <div v-if="form.type !== 'FILE'">
          <label class="mb-1.5 block text-sm font-medium text-slate-700">URL ссылки</label>
          <UiInput v-model="form.actionUrl" placeholder="https://..." />
        </div>
        <div v-if="form.type === 'FILE'">
          <label class="mb-1.5 block text-sm font-medium text-slate-700">URL файла</label>
          <UiInput v-model="form.fileUrl" placeholder="https://... или путь MinIO" />
        </div>
        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="mb-1.5 block text-sm font-medium text-slate-700">Приоритет</label>

            <UiInput v-model.number="form.priority" type="number" :min="0" />
          </div>
          <div class="flex items-end pb-1">
            <label class="flex items-center gap-2 text-sm text-slate-700">
              <input
                v-model="form.allowStack"
                type="checkbox"
                class="h-4 w-4 rounded border-slate-300 text-brand-600 focus:ring-brand-500"
              />
              Разрешить стек
            </label>
          </div>
        </div>
      </div>
      <template #footer>
        <UiButton variant="outline" @click="modalOpen = false">Отмена</UiButton>
        <UiButton variant="primary" :loading="saving" :disabled="!form.title || !form.buttonText" @click="submitForm">
          {{ modalMode === 'create' ? 'Добавить' : 'Сохранить' }}
        </UiButton>
      </template>
    </UiModal>

    <!-- Delete confirm -->
    <UiModal v-model="deleteModalOpen" title="Удаление CTA" size="sm">
      <div class="flex items-start gap-3 rounded-lg border border-danger-200 bg-danger-50 p-3">
        <AlertTriangle class="mt-0.5 h-5 w-5 shrink-0 text-danger-500" />
        <p class="text-sm text-danger-800">
          CTA «{{ pendingDelete?.title }}» будет удалён безвозвратно. Если действия таймлайна ссылаются на этот CTA, они перестанут работать.
        </p>
      </div>
      <template #footer>
        <UiButton variant="outline" :disabled="deleting" @click="deleteModalOpen = false">Отмена</UiButton>
        <UiButton variant="danger" :loading="deleting" @click="confirmDelete">
          <Trash2 class="h-4 w-4" />
          Удалить
        </UiButton>
      </template>
    </UiModal>
  </div>
</template>
