<script setup lang="ts">
import { computed } from 'vue'

import Badge from '@/components/ui/badge/Badge.vue'
import type { GitCommitStatus } from '@/types/api'

const props = defineProps<{ status: GitCommitStatus }>()

const label = computed(() => ({
  RUNNING: '提交中',
  SUCCEEDED: '已提交',
  FAILED: '提交失败',
})[props.status])

const tone = computed(() => ({
  RUNNING: 'warning' as const,
  SUCCEEDED: 'success' as const,
  FAILED: 'danger' as const,
})[props.status])
</script>

<template>
  <Badge :tone="tone">{{ label }}</Badge>
</template>
