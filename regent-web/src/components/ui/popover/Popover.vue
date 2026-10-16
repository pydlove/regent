<script setup lang="ts">
import { PopoverContent, PopoverPortal, PopoverRoot, PopoverTrigger } from 'reka-ui'

import { useOverlayLayer } from '@/composables/useOverlayLayer'

withDefaults(
  defineProps<{
    align?: 'start' | 'center' | 'end'
    side?: 'top' | 'right' | 'bottom' | 'left'
  }>(),
  { align: 'start', side: 'bottom' },
)

const open = defineModel<boolean>('open', { default: false })

// 层级按「打开顺序」发号，见 useOverlayLayer：写死 z-50 时，弹框里的气泡会被弹框的遮罩压住
const { zIndex } = useOverlayLayer(open)
</script>

<template>
  <PopoverRoot v-model:open="open">
    <PopoverTrigger as-child>
      <slot name="trigger" />
    </PopoverTrigger>
    <PopoverPortal>
      <PopoverContent
        :align="align"
        :side="side"
        :side-offset="6"
        class="rounded-panel bg-material p-3 shadow-popover backdrop-blur-[30px] backdrop-saturate-150"
        :style="{ zIndex }"
      >
        <slot />
      </PopoverContent>
    </PopoverPortal>
  </PopoverRoot>
</template>
