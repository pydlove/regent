<script setup lang="ts">
import { ListChecks, RefreshCw } from '@lucide/vue'

import type { DashboardData } from '@/types'

defineProps<{
  dashboard: DashboardData | null
  loading: boolean
}>()

defineEmits<{
  refresh: []
}>()

function relativeTime(value: string): string {
  const minutes = Math.round((new Date(value).getTime() - Date.now()) / 60_000)
  if (Math.abs(minutes) < 1) return '刚刚'
  if (Math.abs(minutes) < 60) return `${Math.abs(minutes)} 分钟前`
  const hours = Math.round(minutes / 60)
  if (Math.abs(hours) < 24) return `${Math.abs(hours)} 小时前`
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'numeric',
    day: 'numeric',
  }).format(new Date(value))
}

function statusLabel(status: string): string {
  return status === 'RUNNING' ? '执行中' : status
}
</script>

<template>
  <section class="view-stack">
    <div class="section-head page-heading">
      <div>
        <p class="eyebrow">执行队列</p>
        <h2>运行中的任务</h2>
      </div>
      <button class="icon-button" type="button" aria-label="刷新任务" :disabled="loading" @click="$emit('refresh')">
        <RefreshCw :size="18" :class="{ spinning: loading }" aria-hidden="true" />
      </button>
    </div>

    <div v-if="dashboard?.tasks.length" class="list-surface">
      <article v-for="task in dashboard.tasks" :key="task.id" class="task-row roomy">
        <span class="task-state running"></span>
        <div>
          <h3>#{{ task.id }} {{ task.title }}</h3>
          <p>{{ task.projectName }} · {{ task.level }} · {{ relativeTime(task.updatedAt) }}</p>
        </div>
        <span class="status-badge blue">{{ statusLabel(task.status) }}</span>
      </article>
    </div>
    <div v-else class="empty-state panel">
      <ListChecks :size="30" aria-hidden="true" />
      <strong>没有运行中的任务</strong>
      <p>P0 技术验证阶段暂不提供手机新建任务。</p>
    </div>
  </section>
</template>
