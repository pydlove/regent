<script setup lang="ts">
import Badge from '@/components/ui/badge/Badge.vue'

const props = defineProps<{ level: 'L0' | 'L1' | 'L2' | 'L3' }>()

// 等级只决定标签颜色，文字照常。L3 不写成红色加粗：等级高不等于出错，
// 把「强阻塞」画得像故障，人就会以为系统坏了。
const tones = {
  L0: 'neutral',
  L1: 'accent',
  L2: 'warning',
  L3: 'danger',
} as const satisfies Record<
  'L0' | 'L1' | 'L2' | 'L3',
  'neutral' | 'accent' | 'warning' | 'danger'
>

const labels: Record<'L0' | 'L1' | 'L2' | 'L3', string> = {
  L0: '自主',
  L1: '自主·日报',
  L2: '需批准',
  L3: '强阻塞',
}
</script>

<template>
  <Badge :tone="tones[props.level]">{{ level }} · {{ labels[props.level] }}</Badge>
</template>
