<script setup lang="ts">
import { computed } from 'vue'

import { inputVariants, type InputVariantProps } from './input.variants'

const props = withDefaults(
  defineProps<{
    size?: InputVariantProps['size']
    invalid?: boolean
    disabled?: boolean
    readonly?: boolean
    placeholder?: string
    type?: string
  }>(),
  {
    size: 'regular',
    invalid: false,
    disabled: false,
    readonly: false,
    placeholder: '',
    type: 'text',
  },
)

const model = defineModel<string>({ default: '' })

const classes = computed(() => inputVariants({ size: props.size, invalid: props.invalid }))
</script>

<template>
  <input
    v-model="model"
    :type="type"
    :placeholder="placeholder"
    :disabled="disabled"
    :readonly="readonly"
    :aria-invalid="invalid || undefined"
    :class="classes"
  />
</template>
