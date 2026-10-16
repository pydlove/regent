<script setup lang="ts">
import { computed } from 'vue'

import { inputVariants, type InputVariantProps } from './input.variants'

const props = withDefaults(
  defineProps<{
    size?: InputVariantProps['size']
    invalid?: boolean
    disabled?: boolean
    placeholder?: string
    rows?: number
  }>(),
  { size: 'regular', invalid: false, disabled: false, placeholder: '', rows: 3 },
)

const model = defineModel<string>({ default: '' })

const classes = computed(() =>
  inputVariants({ size: props.size, invalid: props.invalid, multiline: true }),
)
</script>

<template>
  <textarea
    v-model="model"
    :rows="rows"
    :placeholder="placeholder"
    :disabled="disabled"
    :aria-invalid="invalid || undefined"
    :class="classes"
  />
</template>
