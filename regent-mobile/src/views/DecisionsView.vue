<script setup lang="ts">
import { BadgeCheck, RefreshCw } from '@lucide/vue'

import type { DashboardData } from '@/types'

defineProps<{
  dashboard: DashboardData | null
  loading: boolean
}>()

defineEmits<{
  refresh: []
}>()
</script>

<template>
  <section class="view-stack">
    <div class="section-head page-heading">
      <div>
        <p class="eyebrow">人工决策</p>
        <h2>等待你拍板</h2>
      </div>
      <button class="icon-button" type="button" aria-label="刷新决策" :disabled="loading" @click="$emit('refresh')">
        <RefreshCw :size="18" :class="{ spinning: loading }" aria-hidden="true" />
      </button>
    </div>

    <div v-if="dashboard?.decisions.length" class="list-surface">
      <article
        v-for="decision in dashboard.decisions"
        :key="decision.id"
        class="decision-row"
      >
        <div class="decision-top">
          <span class="level-badge">{{ decision.effectiveLevel }}</span>
          <span class="caption">#{{ decision.id }}</span>
        </div>
        <h3>{{ decision.question }}</h3>
        <p>{{ decision.projectName }} · {{ decision.taskTitle }}</p>
        <div class="decision-note">{{ decision.recommendation }}</div>
      </article>
    </div>
    <div v-else class="empty-state panel">
      <BadgeCheck :size="30" aria-hidden="true" />
      <strong>没有待处理决策</strong>
      <p>需要人工判断的任务会出现在这里。</p>
    </div>
  </section>
</template>
