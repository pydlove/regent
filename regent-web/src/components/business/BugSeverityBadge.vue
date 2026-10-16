<script setup lang="ts">
import { computed } from 'vue'

import StatusDot from '@/components/ui/status-dot/StatusDot.vue'
import type { BugSeverity } from '@/types/bug'

const props = defineProps<{ severity: BugSeverity }>()

// 严重级和决策等级共用一套色点，但含义不同：这里是「坏成什么样」，
// 那边是「要不要问人」。两列不会并排出现，所以不冲突。
const dots: Record<BugSeverity, string> = {
  BLOCKER: 'bg-danger',
  MAJOR: 'bg-warning',
  MINOR: 'bg-accent',
  TRIVIAL: 'bg-neutral',
}

const labels: Record<BugSeverity, string> = {
  BLOCKER: '阻塞',
  MAJOR: '严重',
  MINOR: '一般',
  TRIVIAL: '轻微',
}

const dot = computed(() => dots[props.severity])
const label = computed(() => labels[props.severity])
</script>

<template>
  <StatusDot :dot="dot">{{ label }}</StatusDot>
</template>
