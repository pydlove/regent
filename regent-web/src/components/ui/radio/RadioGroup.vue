<script setup lang="ts">
import { RadioGroupIndicator, RadioGroupItem, RadioGroupRoot } from 'reka-ui'

withDefaults(
  defineProps<{
    items: { value: string; label: string }[]
    orientation?: 'horizontal' | 'vertical'
    disabled?: boolean
  }>(),
  { orientation: 'vertical', disabled: false },
)

const model = defineModel<string>({ default: '' })
</script>

<template>
  <RadioGroupRoot
    v-model="model"
    :orientation="orientation"
    :disabled="disabled"
    :class="orientation === 'horizontal' ? 'flex items-center gap-4' : 'flex flex-col gap-1.5'"
  >
    <label
      v-for="item in items"
      :key="item.value"
      class="inline-flex items-center gap-1.5 text-body text-label"
    >
      <RadioGroupItem
        :value="item.value"
        class="flex size-3.5 shrink-0 items-center justify-center rounded-full border border-separator bg-content shadow-control transition-colors duration-[120ms] ease-apple data-[state=checked]:border-accent"
      >
        <RadioGroupIndicator class="size-1.5 rounded-full bg-accent" />
      </RadioGroupItem>
      <span>{{ item.label }}</span>
    </label>
  </RadioGroupRoot>
</template>
