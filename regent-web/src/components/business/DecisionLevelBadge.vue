<script setup lang="ts">
import { computed } from 'vue'

import StatusDot from '@/components/ui/status-dot/StatusDot.vue'

const props = defineProps<{ level: 'L0' | 'L1' | 'L2' | 'L3' }>()

// 等级只决定色点，文字照常。L3 不写成红色加粗：等级高不等于出错，
// 把「强阻塞」画得像故障，人就会以为系统坏了。
const dots: Record<'L0' | 'L1' | 'L2' | 'L3', string> = {
  L0: 'bg-neutral',
  L1: 'bg-accent',
  L2: 'bg-warning',
  L3: 'bg-danger',
}

const labels: Record<'L0' | 'L1' | 'L2' | 'L3', string> = {
  L0: '自主',
  L1: '自主·日报',
  L2: '需批准',
  L3: '强阻塞',
}

const dot = computed(() => dots[props.level])
const label = computed(() => labels[props.level])
</script>

<template>
  <StatusDot :dot="dot">{{ level }} · {{ label }}</StatusDot>
</template>
