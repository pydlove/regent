<script setup lang="ts">
import Badge from '@/components/ui/badge/Badge.vue'
import StatusDot from '@/components/ui/status-dot/StatusDot.vue'
import type { TaskStatus } from '@/types/task'

const props = withDefaults(
  defineProps<{
    status: TaskStatus
    /** 表格等密集列表用 dot；详情面板、弹框继续用 badge。 */
    variant?: 'badge' | 'dot'
  }>(),
  { variant: 'badge' },
)

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

const dots: Record<TaskStatus, string> = {
  PENDING: 'bg-neutral',
  QUEUED: 'bg-warning',
  RUNNING: 'bg-accent',
  DONE: 'bg-success',
  BLOCKED: 'bg-warning',
  FAILED: 'bg-danger',
  CANCELLED: 'bg-neutral',
}
</script>

<template>
  <StatusDot v-if="props.variant === 'dot'" :dot="dots[props.status]">
    {{ labels[props.status] }}
  </StatusDot>
  <Badge v-else :tone="tones[props.status]">{{ labels[props.status] }}</Badge>
</template>
