<script setup lang="ts">
import Button from '@/components/ui/button/Button.vue'
import type { ButtonVariantProps } from '@/components/ui/button/button.variants'

const props = withDefaults(
  defineProps<{
    /** 图标按钮没有可见文字，label 是唯一的无障碍名称来源，必填 */
    label: string
    variant?: ButtonVariantProps['variant']
    size?: 'sm' | 'regular' | 'lg'
    disabled?: boolean
  }>(),
  { variant: 'ghost', size: 'regular', disabled: false },
)

// 宽度必须跟着 size 走。之前死写 w-control，sm 变成 24×20、lg 变成 24×28，
// 都不是正方形，一排图标按钮就参差不齐。
const width = { sm: 'w-control-sm', regular: 'w-control', lg: 'w-control-lg' }
</script>

<template>
  <!-- class 只用于布局：这里把宽度压成正方形，视觉仍由 Button 的变体决定 -->
  <Button
    :variant="props.variant"
    :size="props.size"
    :disabled="props.disabled"
    :aria-label="props.label"
    :title="props.label"
    :class="[width[props.size], 'px-0']"
  >
    <slot />
  </Button>
</template>
