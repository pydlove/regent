<script setup lang="ts">
import { computed } from 'vue'

import StatusDot from '@/components/ui/status-dot/StatusDot.vue'
import type { TestCaseRunStatus } from '@/types/api'

const props = defineProps<{ status: TestCaseRunStatus }>()

const dots: Record<TestCaseRunStatus, string> = {
  QUEUED: 'bg-neutral',
  RUNNING: 'bg-accent',
  SUCCESS: 'bg-success',
  FAILED: 'bg-danger',
  TIMEOUT: 'bg-warning',
  INTERRUPTED: 'bg-neutral',
  SKIPPED: 'bg-neutral',
}

const labels: Record<TestCaseRunStatus, string> = {
  QUEUED: '等待执行',
  RUNNING: '执行中',
  SUCCESS: '通过',
  FAILED: '失败',
  TIMEOUT: '超时',
  INTERRUPTED: '已中断',
  SKIPPED: '已跳过',
}

const dot = computed(() => dots[props.status])
const label = computed(() => labels[props.status])
</script>

<template>
  <StatusDot :dot="dot">{{ label }}</StatusDot>
</template>
