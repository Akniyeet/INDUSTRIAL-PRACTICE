<script setup lang="ts">
/**
 * Multiline text input with the same label/hint/error surface as {@code UiInput}.
 *
 * <p>Auto-resize is opt-in because most admin forms fit better inside a fixed
 * 6-row box, but event descriptions and CTA copy benefit from growing.
 */
import { computed, nextTick, onMounted, ref, useId, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    modelValue: string | null | undefined
    label?: string
    placeholder?: string
    hint?: string
    error?: string
    required?: boolean
    disabled?: boolean
    rows?: number
    maxlength?: number
    autoresize?: boolean
    id?: string
  }>(),
  { rows: 4, autoresize: false },
)

const emit = defineEmits<{ (e: 'update:modelValue', v: string): void }>()

const generatedId = useId()
const inputId = computed(() => props.id ?? `textarea-${generatedId}`)
const textarea = ref<HTMLTextAreaElement | null>(null)

const inputClasses = computed(() => [
  'input-base',
  'resize-y',
  props.error ? 'input-error' : '',
])

function autosize() {
  if (!props.autoresize || !textarea.value) return
  textarea.value.style.height = 'auto'
  textarea.value.style.height = `${textarea.value.scrollHeight}px`
}

onMounted(() => {
  if (props.autoresize) nextTick(autosize)
})
watch(() => props.modelValue, () => {
  if (props.autoresize) nextTick(autosize)
})

function onInput(e: Event) {
  const v = (e.target as HTMLTextAreaElement).value
  emit('update:modelValue', v)
}
</script>

<template>
  <div>
    <label v-if="label" :for="inputId" class="field-label">
      {{ label }}
      <span v-if="required" class="text-danger-600">*</span>
    </label>
    <textarea
      :id="inputId"
      ref="textarea"
      :value="modelValue ?? ''"
      :placeholder="placeholder"
      :disabled="disabled"
      :required="required"
      :rows="rows"
      :maxlength="maxlength"
      :class="inputClasses"
      @input="onInput"
    />
    <p v-if="error" class="field-error">{{ error }}</p>
    <p v-else-if="hint" class="field-hint">{{ hint }}</p>
    <p
      v-if="maxlength && modelValue"
      class="mt-1 text-right text-xs text-slate-400"
    >
      {{ (modelValue ?? '').length }}/{{ maxlength }}
    </p>
  </div>
</template>
