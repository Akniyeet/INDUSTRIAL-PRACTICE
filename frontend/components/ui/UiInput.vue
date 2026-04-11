<script setup lang="ts">
/**
 * Standard text input with label, hint, error, and optional leading icon slot.
 *
 * <p>Uses `v-model` via `modelValue` / `update:modelValue` so that forms keep
 * working with the two-way binding pattern everyone already knows. Error state
 * is styled by toggling `input-error` from the main stylesheet.
 */
import { computed, useId } from 'vue'

const props = withDefaults(
  defineProps<{
    modelValue: string | number | null | undefined
    label?: string
    type?: string
    placeholder?: string
    hint?: string
    error?: string
    required?: boolean
    disabled?: boolean
    autocomplete?: string
    maxlength?: number
    id?: string
  }>(),
  { type: 'text' },
)

defineEmits<{ (e: 'update:modelValue', v: string): void }>()

const generatedId = useId()
const inputId = computed(() => props.id ?? `input-${generatedId}`)

const inputClasses = computed(() => [
  'input-base',
  props.error ? 'input-error' : '',
])
</script>

<template>
  <div>
    <label v-if="label" :for="inputId" class="field-label">
      {{ label }}
      <span v-if="required" class="text-danger-600">*</span>
    </label>
    <div class="relative">
      <input
        :id="inputId"
        :type="type"
        :value="modelValue ?? ''"
        :placeholder="placeholder"
        :disabled="disabled"
        :required="required"
        :autocomplete="autocomplete"
        :maxlength="maxlength"
        :class="inputClasses"
        @input="(e) => $emit('update:modelValue', (e.target as HTMLInputElement).value)"
      >
    </div>
    <p v-if="error" class="field-error">{{ error }}</p>
    <p v-else-if="hint" class="field-hint">{{ hint }}</p>
  </div>
</template>
