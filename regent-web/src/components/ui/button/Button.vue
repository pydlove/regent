<script setup lang="ts">
import { computed } from 'vue'

import { buttonVariants, type ButtonVariantProps } from './button.variants'

const props = withDefaults(
  defineProps<{
    variant?: ButtonVariantProps['variant']
    size?: ButtonVariantProps['size']
    block?: boolean
    disabled?: boolean
    type?: 'button' | 'submit' | 'reset'
  }>(),
  { variant: 'default', size: 'regular', block: false, disabled: false, type: 'button' },
)

const classes = computed(() =>
  buttonVariants({ variant: props.variant, size: props.size, block: props.block }),
)
</script>

<template>
  <button :type="type" :disabled="disabled" :class="classes">
    <slot />
  </button>
</template>
