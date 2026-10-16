<script setup lang="ts">
import { computed } from 'vue'

import StatusDot from '@/components/ui/status-dot/StatusDot.vue'
import type { TaskStatus } from '@/types/task'

const props = defineProps<{ status: TaskStatus }>()

/*
 * 状态中文名严格对应 ARCHITECTURE.md 2.3 的状态机，不要另起叫法 ——
 * 「待批准」和「阻塞中」混用会让排查问题时对不上后端日志。
 */
const dots: Record<TaskStatus, string> = {
  PENDING: 'bg-neutral',
  RUNNING: 'bg-accent',
  DONE: 'bg-success',
  BLOCKED: 'bg-warning',
  FAILED: 'bg-danger',
  CANCELLED: 'bg-neutral',
}

const labels: Record<TaskStatus, string> = {
  PENDING: '待执行',
  RUNNING: '执行中',
  DONE: '已完成',
  BLOCKED: '待批准',
  FAILED: '失败',
  CANCELLED: '已终止',
}

const dot = computed(() => dots[props.status])
const label = computed(() => labels[props.status])
</script>

<template>
  <StatusDot :dot="dot">{{ label }}</StatusDot>
</template>
