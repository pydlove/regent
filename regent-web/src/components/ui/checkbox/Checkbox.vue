<script setup lang="ts">
import { Check, Minus } from '@lucide/vue'
import { CheckboxIndicator, CheckboxRoot } from 'reka-ui'

import Icon from '@/components/ui/icon/Icon.vue'

withDefaults(defineProps<{ label?: string; disabled?: boolean }>(), {
  label: '',
  disabled: false,
})

const model = defineModel<boolean | 'indeterminate'>({ default: false })
</script>

<template>
  <label class="inline-flex items-center gap-1.5 text-body text-label">
    <CheckboxRoot
      v-model="model"
      :disabled="disabled"
      class="flex size-3.5 shrink-0 items-center justify-center rounded-[3px] border border-separator bg-content shadow-control transition-colors duration-[120ms] ease-apple data-[state=checked]:border-accent data-[state=checked]:bg-accent data-[state=indeterminate]:border-accent data-[state=indeterminate]:bg-accent disabled:opacity-40"
    >
      <CheckboxIndicator class="text-white">
        <!-- lucide 的 Check 在 24 网格里只占 16 个单位的宽，渲染到 14px 正好缩成 9px 的对勾；
             塞回 10px 反而比格还小，勾会飘在框中间。 -->
        <Icon v-if="model === 'indeterminate'" :as="Minus" size="inline" />
        <Icon v-else :as="Check" size="inline" />
      </CheckboxIndicator>
    </CheckboxRoot>
    <span v-if="label">{{ label }}</span>
  </label>
</template>
