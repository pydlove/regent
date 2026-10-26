<script setup lang="ts">
import {
  BadgeCheck,
  CircleAlert,
  FolderKanban,
  Laptop2,
  RefreshCw,
  ServerOff,
  ShieldCheck,
} from '@lucide/vue'
import { computed } from 'vue'

import type { DashboardData } from '@/types'

const props = defineProps<{
  dashboard: DashboardData | null
  loading: boolean
  error: string
}>()

defineEmits<{
  refresh: []
  showTasks: []
  showDecisions: []
}>()

const running = computed(() => props.dashboard?.stats.RUNNING ?? 0)
const pending = computed(() => props.dashboard?.decisions.length ?? 0)
const abnormal = computed(() =>
  (props.dashboard?.stats.FAILED ?? 0) + (props.dashboard?.stats.BLOCKED ?? 0))
const hostOnline = computed(() => Boolean(props.dashboard?.device?.online))
const codexReady = computed(() => Boolean(props.dashboard?.device?.codex.available))

function relativeTime(value?: string): string {
  if (!value) return '尚无记录'
  const delta = new Date(value).getTime() - Date.now()
  const minutes = Math.round(delta / 60_000)
  if (Math.abs(minutes) < 1) return '刚刚'
  if (Math.abs(minutes) < 60) return `${Math.abs(minutes)} 分钟前`
  const hours = Math.round(minutes / 60)
  if (Math.abs(hours) < 24) return `${Math.abs(hours)} 小时前`
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}
</script>

<template>
  <div class="view-stack">
    <div class="brand-signature">
      <strong>Regent</strong>
      <span aria-hidden="true"></span>
      <p>电脑不在身边，AI 任务随时随地开工</p>
    </div>

    <section class="panel host-panel" :class="{ offline: dashboard?.device && !hostOnline }">
      <div class="host-head">
        <span class="host-icon">
          <Laptop2 v-if="hostOnline" :size="21" aria-hidden="true" />
          <ServerOff v-else :size="21" aria-hidden="true" />
        </span>
        <div class="host-copy">
          <div class="status-line">
            <span class="status-dot" :class="{ offline: dashboard?.device && !hostOnline }"></span>
            <strong>{{ !dashboard?.device ? '等待执行电脑' : hostOnline ? '主机在线' : '主机离线' }}</strong>
          </div>
          <p>
            {{ dashboard?.device
              ? `${dashboard.device.hostname} · ${dashboard.device.platform}`
              : '登录设备代理后，手机会在这里显示连接状态' }}
          </p>
        </div>
        <button class="icon-button" type="button" aria-label="刷新首页" :disabled="loading" @click="$emit('refresh')">
          <RefreshCw :size="18" :class="{ spinning: loading }" aria-hidden="true" />
        </button>
      </div>
      <div class="host-health">
        <div>
          <span>连接</span>
          <strong :class="{ danger: dashboard?.device && !hostOnline, muted: !dashboard?.device }">
            {{ !dashboard?.device ? '未知' : hostOnline ? '在线' : '离线' }}
          </strong>
        </div>
        <div>
          <span>Codex</span>
          <strong :class="{ muted: !dashboard?.device }">
            {{ !dashboard?.device ? '未知' : codexReady ? '可用' : '不可用' }}
          </strong>
        </div>
        <div>
          <span>最后心跳</span>
          <strong :class="{ muted: !dashboard?.device }">
            {{ relativeTime(dashboard?.device?.lastSeenAt) }}
          </strong>
        </div>
      </div>
    </section>

    <p v-if="error" class="inline-alert" role="alert">
      <CircleAlert :size="17" aria-hidden="true" />
      <span>{{ error }}</span>
    </p>

    <section class="metric-strip">
      <button type="button" @click="$emit('showTasks')">
        <strong>{{ running }}</strong>
        <span>执行中</span>
      </button>
      <button type="button" @click="$emit('showDecisions')">
        <strong>{{ pending }}</strong>
        <span>待决策</span>
      </button>
      <button type="button" @click="$emit('showTasks')">
        <strong :class="{ danger: abnormal > 0 }">{{ abnormal }}</strong>
        <span>异常任务</span>
      </button>
    </section>

    <section v-if="dashboard?.decisions.length" class="section-block">
      <div class="section-head">
        <h2>需要你决定</h2>
        <button class="text-button" type="button" @click="$emit('showDecisions')">全部决策</button>
      </div>
      <button
        v-for="decision in dashboard.decisions.slice(0, 1)"
        :key="decision.id"
        class="decision-card"
        type="button"
        @click="$emit('showDecisions')"
      >
        <div class="decision-top">
          <span class="level-badge">{{ decision.effectiveLevel }}</span>
          <span class="caption">#{{ decision.id }}</span>
        </div>
        <h3>{{ decision.question }}</h3>
        <p>{{ decision.taskTitle }} · {{ decision.projectName }}</p>
        <span class="decision-cta">
          <BadgeCheck :size="17" aria-hidden="true" />
          查看建议与影响
        </span>
      </button>
    </section>

    <section class="section-block">
      <div class="section-head">
        <h2>正在执行</h2>
        <button class="text-button" type="button" @click="$emit('showTasks')">全部任务</button>
      </div>
      <div v-if="dashboard?.tasks.length" class="list-surface">
        <article v-for="task in dashboard.tasks.slice(0, 3)" :key="task.id" class="task-row">
          <span class="task-state running"></span>
          <div>
            <h3>#{{ task.id }} {{ task.title }}</h3>
            <p>{{ task.projectName }} · {{ relativeTime(task.updatedAt) }}</p>
          </div>
          <span class="status-badge blue">执行中</span>
        </article>
      </div>
      <div v-else class="empty-state">
        <ShieldCheck :size="28" aria-hidden="true" />
        <strong>当前没有执行中的任务</strong>
        <p>任务派发后，这里会显示实时状态。</p>
      </div>
    </section>

    <section v-if="dashboard?.projects.length" class="section-block">
      <div class="section-head">
        <h2>已授权项目</h2>
        <span class="caption">{{ dashboard.projects.length }} 个</span>
      </div>
      <div class="project-strip">
        <span v-for="project in dashboard.projects.slice(0, 4)" :key="project.id">
          <FolderKanban :size="14" aria-hidden="true" />
          {{ project.name }}
        </span>
      </div>
    </section>
  </div>
</template>
