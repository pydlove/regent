<script setup lang="ts">
import { computed } from 'vue'

import StatusDot from '@/components/ui/status-dot/StatusDot.vue'
import type { RunStatus } from '@/types/task'

const props = defineProps<{ status: RunStatus }>()

/*
 * 色点语义与任务状态共用一套：蓝=在跑、橙=在等人、绿=成了、红=没成、灰=没有下文。
 * 超时用灰而不是红：它既不是「逻辑失败」也不是「环境坏了」，是「跑太久被平台掐了」，
 * 用红色会让人以为代码有问题。
 */
const dots: Record<RunStatus, string> = {
  RUNNING: 'bg-accent',
  SUCCESS: 'bg-success',
  BLOCKED: 'bg-warning',
  FAILED: 'bg-danger',
  TIMEOUT: 'bg-neutral',
}

const labels: Record<RunStatus, string> = {
  RUNNING: '执行中',
  SUCCESS: '成功',
  BLOCKED: '待批准',
  FAILED: '失败',
  TIMEOUT: '超时',
}

const dot = computed(() => dots[props.status])
const label = computed(() => labels[props.status])
</script>

<template>
  <StatusDot :dot="dot">{{ label }}</StatusDot>
</template>
