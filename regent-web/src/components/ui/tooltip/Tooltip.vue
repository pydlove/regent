<script setup lang="ts">
import { TooltipContent, TooltipPortal, TooltipRoot, TooltipTrigger } from 'reka-ui'

withDefaults(
  defineProps<{
    content: string
    side?: 'top' | 'right' | 'bottom' | 'left'
    delay?: number
  }>(),
  { side: 'top', delay: 400 },
)
</script>

<template>
  <!-- TooltipProvider 在 App.vue 根部统一提供 -->
  <TooltipRoot :delay-duration="delay">
    <TooltipTrigger as-child>
      <slot />
    </TooltipTrigger>
    <TooltipPortal>
      <TooltipContent
        :side="side"
        :side-offset="6"
        class="z-50 rounded-control bg-material px-2 py-0.5 text-caption text-label shadow-popover backdrop-blur-[30px] backdrop-saturate-150"
      >
        {{ content }}
      </TooltipContent>
    </TooltipPortal>
  </TooltipRoot>
</template>
