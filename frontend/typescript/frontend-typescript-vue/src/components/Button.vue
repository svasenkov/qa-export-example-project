<script setup lang="ts">
withDefaults(
  defineProps<{
    variant?: 'primary' | 'secondary' | 'ghost' | 'danger';
    block?: boolean;
    type?: 'button' | 'submit' | 'reset';
    disabled?: boolean;
    id?: string;
  }>(),
  {
    variant: 'primary',
    block: false,
    type: 'button',
    disabled: false,
  },
);

const variantClass: Record<'primary' | 'secondary' | 'ghost' | 'danger', string> = {
  primary: 'btn--primary',
  secondary: 'btn--secondary',
  ghost: 'btn--ghost',
  danger: 'btn--danger',
};
</script>

<template>
  <button
    :id="id"
    :type="type"
    class="btn"
    :class="[variantClass[variant], block && 'btn--block']"
    :disabled="disabled"
  >
    <slot />
  </button>
</template>
