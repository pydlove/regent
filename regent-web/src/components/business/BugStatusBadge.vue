<script setup lang="ts">
import { computed } from 'vue'

import StatusDot from '@/components/ui/status-dot/StatusDot.vue'
import type { BugStatus } from '@/types/bug'

const props = defineProps<{ status: BugStatus }>()

const dots: Record<BugStatus, string> = {
  OPEN: 'bg-danger',
  FIXING: 'bg-accent',
  FIXED: 'bg-warning',
  CLOSED: 'bg-neutral',
  WONTFIX: 'bg-neutral',
}

const labels: Record<BugStatus, string> = {
  OPEN: '待处理',
  FIXING: '修复中',
  FIXED: '待验证',
  CLOSED: '已关闭',
  WONTFIX: '不修复',
}

const dot = computed(() => dots[props.status])
const label = computed(() => labels[props.status])
</script>

<template>
  <StatusDot :dot="dot">{{ label }}</StatusDot>
</template>
