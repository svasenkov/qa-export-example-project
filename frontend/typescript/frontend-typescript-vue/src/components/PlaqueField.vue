<script setup lang="ts">
import { computed, useAttrs } from 'vue';

defineOptions({ inheritAttrs: false });

const props = withDefaults(
  defineProps<{
    label: string;
    id?: string;
    name?: string;
    type?: string;
    autocomplete?: string;
    modelValue?: string;
    labelVariant?: 'param' | 'caption';
    divided?: boolean;
    stretch?: boolean;
  }>(),
  {
    type: 'text',
    modelValue: '',
    labelVariant: 'caption',
    divided: true,
    stretch: true,
  },
);

const emit = defineEmits<{
  'update:modelValue': [value: string];
}>();

const attrs = useAttrs();

/** Autofill / DevTools: every form control needs id or name. */
const controlId = computed(() => props.id);
const controlName = computed(() => props.name ?? props.id);

const labelClass = (variant: 'param' | 'caption') =>
  variant === 'param' ? 'plaque-field__label' : 'plaque-field__text';
</script>

<template>
  <label
    class="plaque-field"
    :class="{
      'plaque-field--divided': divided,
      'plaque-field--stretch': stretch,
    }"
  >
    <span
      :class="labelClass(labelVariant)"
      :title="labelVariant === 'param' ? label : undefined"
    >
      {{ label }}
    </span>
    <span v-if="divided" class="plaque-divider" aria-hidden="true" />
    <input
      :id="controlId"
      :name="controlName"
      class="input plaque-field__control"
      :type="type"
      :autocomplete="autocomplete"
      :value="modelValue"
      v-bind="attrs"
      @input="emit('update:modelValue', ($event.target as HTMLInputElement).value)"
    />
  </label>
</template>
