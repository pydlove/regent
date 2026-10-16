<script setup lang="ts">
import { SwitchRoot, SwitchThumb } from 'reka-ui'

withDefaults(defineProps<{ label?: string; disabled?: boolean }>(), {
  label: '',
  disabled: false,
})

const model = defineModel<boolean>({ default: false })
</script>

<template>
  <label class="inline-flex items-center gap-2 text-body text-label">
    <SwitchRoot
      v-model="model"
      :disabled="disabled"
      class="relative h-[15px] w-[26px] shrink-0 rounded-full bg-neutral/40 transition-colors duration-[120ms] ease-apple data-[state=checked]:bg-success disabled:opacity-40"
    >
      <SwitchThumb
        class="block size-[13px] translate-x-[1px] rounded-full bg-white shadow-control transition-transform duration-[160ms] ease-apple data-[state=checked]:translate-x-[12px]"
      />
    </SwitchRoot>
    <span v-if="label">{{ label }}</span>
  </label>
</template>
