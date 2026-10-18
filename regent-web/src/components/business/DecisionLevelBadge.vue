<script setup lang="ts">
import Badge from '@/components/ui/badge/Badge.vue'

const props = withDefaults(
  defineProps<{
    level: 'L0' | 'L1' | 'L2' | 'L3'
    /** 表格里只保留等级代号，完整含义通过 title 提供，避免每行重复一段说明。 */
    compact?: boolean
  }>(),
  { compact: false },
)

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
  <Badge :tone="tones[props.level]" :title="`${level} · ${labels[props.level]}`">
    <template v-if="props.compact">{{ level }}</template>
    <template v-else>{{ level }} · {{ labels[props.level] }}</template>
  </Badge>
</template>
