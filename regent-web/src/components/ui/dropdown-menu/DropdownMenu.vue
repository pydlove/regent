<script setup lang="ts">
import { DropdownMenuContent, DropdownMenuPortal, DropdownMenuRoot, DropdownMenuTrigger } from 'reka-ui'
import { ref } from 'vue'

import { useOverlayLayer } from '@/composables/useOverlayLayer'

/*
 * 菜单是浮层，层级同样按「打开顺序」发号（见 useOverlayLayer）：根是 renderless 的，
 * 拿不到内部的开合状态，所以显式接管 open —— 菜单照常开合，只是多了一个「什么时候开」的事实源。
 */
const open = ref(false)
const { zIndex } = useOverlayLayer(open)

withDefaults(
  defineProps<{
    align?: 'start' | 'center' | 'end'
    side?: 'top' | 'right' | 'bottom' | 'left'
  }>(),
  { align: 'start', side: 'bottom' },
)
</script>

<template>
  <DropdownMenuRoot v-model:open="open">
    <DropdownMenuTrigger as-child>
      <slot name="trigger" />
    </DropdownMenuTrigger>
    <DropdownMenuPortal>
      <DropdownMenuContent
        :align="align"
        :side="side"
        :side-offset="4"
        class="min-w-44 rounded-panel bg-material p-1 shadow-popover backdrop-blur-[30px] backdrop-saturate-150"
        :style="{ zIndex }"
      >
        <slot />
      </DropdownMenuContent>
    </DropdownMenuPortal>
  </DropdownMenuRoot>
</template>
