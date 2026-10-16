<script setup lang="ts">
import Badge from '@/components/ui/badge/Badge.vue'
import type { TaskStatus } from '@/types/task'

const props = defineProps<{ status: TaskStatus }>()

/*
 * 状态中文名严格对应 ARCHITECTURE.md 2.3 的状态机，不要另起叫法 ——
 * 「待批准」和「阻塞中」混用会让排查问题时对不上后端日志。
 */
const tones = {
  PENDING: 'neutral',
  QUEUED: 'warning',
  RUNNING: 'accent',
  DONE: 'success',
  BLOCKED: 'warning',
  FAILED: 'danger',
  CANCELLED: 'neutral',
} as const satisfies Record<TaskStatus, 'neutral' | 'accent' | 'success' | 'warning' | 'danger'>

const labels: Record<TaskStatus, string> = {
  PENDING: '待执行',
  QUEUED: '排队中',
  RUNNING: '执行中',
  DONE: '已完成',
  BLOCKED: '待批准',
  FAILED: '失败',
  CANCELLED: '已终止',
}
</script>

<template>
  <Badge :tone="tones[props.status]">{{ labels[props.status] }}</Badge>
</template>
