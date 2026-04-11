<script setup lang="ts">
/**
 * Native `<select>` wrapped in our label/hint/error surface.
 *
 * <p>We intentionally stay with the native select for the MVP vertical slice:
 * it's accessible, keyboard-friendly out of the box, and works in every
 * browser. A custom combobox is a later upgrade when we need multi-select or
 * search inside long lists.
 */
import { computed, useId } from 'vue'

interface Option {
  value: string
  label: string
  disabled?: boolean
}

const props = defineProps<{
  modelValue: string | null | undefined
  options: Option[]
  label?: string
  hint?: string
  error?: string
  required?: boolean
  disabled?: boolean
  placeholder?: string
  id?: string
}>()

defineEmits<{ (e: 'update:modelValue', v: string): void }>()

const generatedId = useId()
const selectId = computed(() => props.id ?? `select-${generatedId}`)

const inputClasses = computed(() => [
  'input-base',
  'pr-8',
  props.error ? 'input-error' : '',
])
</script>

<template>
  <div>
    <label v-if="label" :for="selectId" class="field-label">
      {{ label }}
      <span v-if="required" class="text-danger-600">*</span>
    </label>
    <div class="relative">
      <select
        :id="selectId"
        :value="modelValue ?? ''"
        :disabled="disabled"
        :required="required"
        :class="inputClasses"
        @change="(e) => $emit('update:modelValue', (e.target as HTMLSelectElement).value)"
      >
        <option v-if="placeholder" value="" disabled>{{ placeholder }}</option>
        <option
          v-for="opt in options"
          :key="opt.value"
          :value="opt.value"
          :disabled="opt.disabled"
        >
          {{ opt.label }}
        </option>
      </select>
      <svg
        class="pointer-events-none absolute right-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400"
        fill="none"
        stroke="currentColor"
        viewBox="0 0 24 24"
      >
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
      </svg>
    </div>
    <p v-if="error" class="field-error">{{ error }}</p>
    <p v-else-if="hint" class="field-hint">{{ hint }}</p>
  </div>
</template>
