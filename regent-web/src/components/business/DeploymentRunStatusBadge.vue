<script setup lang="ts">
import { computed } from 'vue'

import StatusDot from '@/components/ui/status-dot/StatusDot.vue'
import type { DeploymentRunStatus } from '@/types/api'

const props = withDefaults(
  defineProps<{ status?: DeploymentRunStatus | null }>(),
  { status: null },
)

const labels: Record<DeploymentRunStatus, string> = {
  QUEUED: '排队中',
  RUNNING: '部署中',
  SUCCESS: '成功',
  FAILED: '失败',
  TIMEOUT: '超时',
  INTERRUPTED: '已中断',
}

const dots: Record<DeploymentRunStatus, string> = {
  QUEUED: 'bg-warning',
  RUNNING: 'bg-accent',
  SUCCESS: 'bg-success',
  FAILED: 'bg-danger',
  TIMEOUT: 'bg-neutral',
  INTERRUPTED: 'bg-neutral',
}

const label = computed(() => (props.status ? labels[props.status] : '未部署'))
const dot = computed(() => (props.status ? dots[props.status] : 'bg-neutral/30'))
</script>

<template>
  <StatusDot :dot="dot">{{ label }}</StatusDot>
</template>
