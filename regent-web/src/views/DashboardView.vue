<script setup lang="ts">
import {
  AlertTriangle,
  ArrowRight,
  Bot,
  Bug,
  Clock3,
  Coins,
  Gauge,
  ListChecks,
  RefreshCw,
  ShieldCheck,
  Workflow,
} from '@lucide/vue'
import { computed, onMounted, ref, type Component } from 'vue'
import { RouterLink } from 'vue-router'

import { getDashboardSnapshot, type DashboardSnapshot } from '@/api/dashboard'
import { ApiError } from '@/api/http'
import BugSeverityBadge from '@/components/business/BugSeverityBadge.vue'
import DecisionLevelBadge from '@/components/business/DecisionLevelBadge.vue'
import TaskStatusBadge from '@/components/business/TaskStatusBadge.vue'
import Button from '@/components/ui/button/Button.vue'
import DonutChart from '@/components/ui/chart/DonutChart.vue'
import TrendBars from '@/components/ui/chart/TrendBars.vue'
import type { DonutSegment, TrendBar } from '@/components/ui/chart/chart.types'
import Icon from '@/components/ui/icon/Icon.vue'
import IconButton from '@/components/ui/icon-button/IconButton.vue'
import Skeleton from '@/components/ui/skeleton/Skeleton.vue'
import Tooltip from '@/components/ui/tooltip/Tooltip.vue'
import type { TaskStatus } from '@/types/task'
import { formatRelative, formatTokens } from '@/utils/format'

const snapshot = ref<DashboardSnapshot | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
const lastUpdated = ref<Date | null>(null)

const integerFormatter = new Intl.NumberFormat('zh-CN')
const formatInteger = (value: number) => integerFormatter.format(value)
const formatTokenCount = (value: number) => (value === 0 ? '0' : formatTokens(value))
const weekdayFormatter = new Intl.DateTimeFormat('zh-CN', { weekday: 'short' })

function messageOf(value: unknown): string {
  if (value instanceof ApiError) return value.message
  return '无法连接后端，请确认服务已启动（默认 127.0.0.1:8787）'
}

async function load() {
  if (loading.value) return
  loading.value = true
  error.value = null
  try {
    snapshot.value = await getDashboardSnapshot()
    lastUpdated.value = new Date()
  } catch (value) {
    error.value = messageOf(value)
  } finally {
    loading.value = false
  }
}

const totalTasks = computed(() =>
  snapshot.value ? Object.values(snapshot.value.tasks).reduce((sum, count) => sum + count, 0) : 0,
)
const activeTasks = computed(() =>
  snapshot.value ? snapshot.value.tasks.QUEUED + snapshot.value.tasks.RUNNING : 0,
)
const finishedTasks = computed(() =>
  snapshot.value
    ? snapshot.value.tasks.DONE + snapshot.value.tasks.FAILED + snapshot.value.tasks.CANCELLED
    : 0,
)
const successfulFinishedTasks = computed(() =>
  snapshot.value ? snapshot.value.tasks.DONE + snapshot.value.tasks.FAILED : 0,
)
const successRate = computed(() => {
  if (successfulFinishedTasks.value === 0 || !snapshot.value) return null
  return Math.round((snapshot.value.tasks.DONE / successfulFinishedTasks.value) * 100)
})
const attentionCount = computed(() =>
  snapshot.value
    ? snapshot.value.pendingDecisions + snapshot.value.openBugs + snapshot.value.tasks.FAILED
    : 0,
)
const settledTasks = computed(() => snapshot.value?.tasks.DONE ?? 0)

/*
 * 状态配色与 TaskStatusBadge 同源：灰度留给「还没轮到」的状态，彩色只给真正在动的几档。
 * 排队中与待批准同属 warning，用透明度区分，环上和列表里都不会看混。
 */
const statusDefinitions: Array<{
  status: TaskStatus
  label: string
  toneClass: string
}> = [
  { status: 'PENDING', label: '待执行', toneClass: 'text-neutral/40' },
  { status: 'QUEUED', label: '排队中', toneClass: 'text-warning/60' },
  { status: 'RUNNING', label: '执行中', toneClass: 'text-accent' },
  { status: 'BLOCKED', label: '待批准', toneClass: 'text-warning' },
  { status: 'DONE', label: '已完成', toneClass: 'text-success' },
  { status: 'FAILED', label: '失败', toneClass: 'text-danger' },
  { status: 'CANCELLED', label: '已终止', toneClass: 'text-neutral/25' },
]

const statusRows = computed(() =>
  statusDefinitions.map((item) => {
    const count = snapshot.value?.tasks[item.status] ?? 0
    return {
      ...item,
      count,
      percentage: totalTasks.value === 0 ? 0 : Math.round((count / totalTasks.value) * 100),
    }
  }),
)

const donutSegments = computed<DonutSegment[]>(() =>
  statusRows.value.map((row) => ({
    key: row.status,
    label: row.label,
    value: row.count,
    toneClass: row.toneClass,
  })),
)

const trendBars = computed<TrendBar[]>(() => {
  const points = snapshot.value?.trend ?? []
  return points.map((point, index) => ({
    key: point.date,
    // 最后一根永远是今天，直接写出来，用户不必自己数
    label: index === points.length - 1 ? '今天' : weekdayFormatter.format(new Date(`${point.date}T00:00:00`)),
    done: point.done,
    failed: point.failed,
    highlight: index === points.length - 1,
  }))
})
const trendDone = computed(() => snapshot.value?.trend.reduce((sum, point) => sum + point.done, 0) ?? 0)
const trendFailed = computed(() => snapshot.value?.trend.reduce((sum, point) => sum + point.failed, 0) ?? 0)
const trendEmpty = computed(() => trendDone.value === 0 && trendFailed.value === 0)

type ToneChip = string

const chipNeutral: ToneChip = 'bg-neutral/12 text-neutral'
const chipAccent: ToneChip = 'bg-accent/12 text-accent'
const chipWarning: ToneChip = 'bg-warning/15 text-warning'
const chipDanger: ToneChip = 'bg-danger/12 text-danger'
const chipSuccess: ToneChip = 'bg-success/15 text-success'

type Tile = {
  key: string
  to: string
  icon: Component
  chipClass: ToneChip
  label: string
  valueText: string
  unit: string
  valueClass: string
  caption: string
}

/*
 * 磁贴只在「这个数字需要你动」时才上色：常态是黑字灰底，有活干、有决策、有缺陷才染色。
 * 彩色磁贴互相抵消会失去重点，因此 token 这类资源指标保持中性色。
 */
const tiles = computed<Tile[]>(() => {
  const current = snapshot.value
  if (!current) return []
  const usage = current.tokenUsage
  return [
    {
      key: 'total',
      to: '/tasks',
      icon: ListChecks,
      chipClass: chipAccent,
      label: '任务总数',
      valueText: formatInteger(totalTasks.value),
      unit: '项',
      valueClass: 'text-label',
      caption: `已完成 ${formatInteger(current.tasks.DONE)} 项`,
    },
    {
      key: 'active',
      to: '/tasks',
      icon: Bot,
      chipClass: activeTasks.value > 0 ? chipSuccess : chipNeutral,
      label: '正在处理',
      valueText: formatInteger(activeTasks.value),
      unit: '项',
      valueClass: activeTasks.value > 0 ? 'text-label' : 'text-label-tertiary',
      caption: `${formatInteger(current.tasks.RUNNING)} 运行 · ${formatInteger(current.tasks.QUEUED)} 排队`,
    },
    {
      key: 'decision',
      to: '/decisions',
      icon: Workflow,
      chipClass: current.pendingDecisions > 0 ? chipWarning : chipNeutral,
      label: '待批决策',
      valueText: formatInteger(current.pendingDecisions),
      unit: '项',
      valueClass: current.pendingDecisions > 0 ? 'text-warning' : 'text-label',
      caption:
        current.pendingDecisions > 0
          ? `L2 ${formatInteger(current.pendingL2Decisions)} · L3 ${formatInteger(current.pendingL3Decisions)}`
          : '没有等待拍板的决策',
    },
    {
      key: 'bug',
      to: '/bugs',
      icon: Bug,
      chipClass: current.openBugs > 0 ? chipDanger : chipNeutral,
      label: '开放缺陷',
      valueText: formatInteger(current.openBugs),
      unit: '项',
      valueClass: current.openBugs > 0 ? 'text-danger' : 'text-label',
      caption: current.openBugs > 0 ? '等待修复或验证' : '当前无需处理',
    },
    {
      key: 'tokens',
      to: '/tasks',
      icon: Coins,
      chipClass: chipNeutral,
      label: 'Token 消耗',
      valueText: formatTokenCount(usage.total),
      unit: 'tokens',
      valueClass: 'text-label',
      caption:
        usage.total === 0
          ? '尚未产生已结算消耗'
          : `输入 ${formatTokenCount(usage.input)} · 缓存 ${formatTokenCount(usage.cachedInput)} · 输出 ${formatTokenCount(usage.output)}`,
    },
  ]
})

const updatedAt = computed(() =>
  lastUpdated.value
    ? lastUpdated.value.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
    : '',
)

onMounted(() => void load())
</script>

<template>
  <div class="flex min-h-full flex-col">
    <div class="flex h-toolbar shrink-0 items-center gap-3 border-b border-separator px-5">
      <div class="flex min-w-0 items-center gap-2">
        <h1 class="text-body font-emphasis text-label">看板</h1>
        <span class="text-caption text-label-tertiary">执行、审批与风险总览</span>
      </div>

      <div class="ml-auto flex items-center gap-2">
        <span class="hidden items-center gap-1.5 text-caption text-label-tertiary sm:flex">
          <span
            class="size-1.5 rounded-full"
            :class="error ? 'bg-danger' : snapshot ? 'bg-success' : 'bg-neutral'"
          />
          {{ error ? '数据未完全同步' : snapshot ? '数据已同步' : '正在连接' }}
          <span v-if="updatedAt" class="tnum">· {{ updatedAt }}</span>
        </span>
        <Tooltip content="刷新区块数据">
          <IconButton label="刷新看板数据" size="lg" :disabled="loading" @click="load">
            <Icon :as="RefreshCw" size="large" :class="{ 'animate-spin': loading }" />
          </IconButton>
        </Tooltip>
      </div>
    </div>

    <p
      v-if="error && snapshot"
      class="border-b border-separator bg-danger/5 px-5 py-2 text-caption text-danger"
    >
      {{ error }}
    </p>

    <div v-if="snapshot" class="flex-1 space-y-3 px-5 py-4">
      <!-- 手机上两列：指标各占一行会把图表推到两屏之外；第五项独占末行，避免半张空卡。 -->
      <section class="grid grid-cols-2 gap-3 lg:grid-cols-5">
        <RouterLink
          v-for="tile in tiles"
          :key="tile.key"
          :to="tile.to"
          class="group flex min-w-0 flex-col gap-2.5 rounded-panel bg-content p-3.5 shadow-panel transition-colors duration-[120ms] ease-apple hover:bg-hover"
          :class="{ 'col-span-2 lg:col-span-1': tile.key === 'tokens' }"
        >
          <div class="flex items-center gap-2">
            <span class="grid size-6 shrink-0 place-items-center rounded-[6px]" :class="tile.chipClass">
              <Icon :as="tile.icon" size="inline" />
            </span>
            <span class="truncate text-caption text-label-secondary">{{ tile.label }}</span>
            <Icon
              :as="ArrowRight"
              size="inline"
              class="ml-auto shrink-0 text-label-tertiary opacity-0 transition-opacity duration-[120ms] ease-apple group-hover:opacity-100"
            />
          </div>
          <div class="flex items-end gap-1.5">
            <span class="tnum text-metric font-bold" :class="tile.valueClass">
              {{ tile.valueText }}
            </span>
            <span class="pb-0.5 text-caption text-label-tertiary">{{ tile.unit }}</span>
          </div>
          <p class="truncate text-caption text-label-tertiary">{{ tile.caption }}</p>
        </RouterLink>
      </section>

      <section
        class="grid grid-cols-1 gap-3 lg:grid-cols-[minmax(0,1.65fr)_minmax(300px,1fr)]"
      >
        <div class="flex min-w-0 flex-col gap-3">
          <section class="rounded-panel bg-content p-4 shadow-panel">
            <div class="flex flex-wrap items-center gap-x-4 gap-y-1">
              <div class="min-w-0">
                <h2 class="text-body font-emphasis text-label">近 7 天收场</h2>
                <p class="mt-0.5 text-caption text-label-tertiary">每天完成与失败的任务数</p>
              </div>
              <div class="ml-auto flex items-center gap-3 text-caption text-label-tertiary">
                <span class="flex items-center gap-1.5">
                  <span class="size-1.5 rounded-full bg-success" />
                  完成 <span class="tnum text-label-secondary">{{ formatInteger(trendDone) }}</span>
                </span>
                <span class="flex items-center gap-1.5">
                  <span class="size-1.5 rounded-full bg-danger" />
                  失败 <span class="tnum text-label-secondary">{{ formatInteger(trendFailed) }}</span>
                </span>
              </div>
            </div>

            <TrendBars class="mt-4" :bars="trendBars" :height="104" />

            <p
              v-if="trendEmpty"
              class="mt-2 flex items-center gap-1.5 text-caption text-label-tertiary"
            >
              <Icon :as="Clock3" size="inline" />
              近 7 天还没有收场的任务，跑起来后这里会逐天累积。
            </p>
          </section>

          <section class="flex min-w-0 flex-1 flex-col overflow-hidden rounded-panel bg-content shadow-panel">
            <div class="flex min-h-14 items-center gap-3 border-b border-separator-soft px-4 py-2.5">
              <div class="min-w-0">
                <h2 class="text-body font-emphasis text-label">执行队列</h2>
                <p class="mt-0.5 text-caption text-label-tertiary">
                  运行中的任务与等待执行位的任务
                </p>
              </div>
              <span class="tnum ml-auto shrink-0 text-caption text-label-tertiary">
                {{ formatInteger(activeTasks) }} 项
              </span>
              <RouterLink
                to="/tasks"
                class="group inline-flex shrink-0 items-center gap-1 text-caption text-accent"
              >
                任务中心
                <Icon
                  :as="ArrowRight"
                  size="inline"
                  class="transition-transform duration-[120ms] ease-apple group-hover:translate-x-0.5"
                />
              </RouterLink>
            </div>

            <div v-if="snapshot.activeTasks.length > 0">
              <RouterLink
                v-for="task in snapshot.activeTasks"
                :key="task.id"
                to="/tasks"
                class="group flex min-w-0 items-center gap-3 border-b border-separator-soft px-4 py-3 last:border-b-0 hover:bg-hover"
              >
                <TaskStatusBadge :status="task.status" />
                <div class="min-w-0 flex-1">
                  <p class="truncate text-body text-label">
                    <span class="tnum mr-1 text-label-tertiary">#{{ task.id }}</span>
                    {{ task.title }}
                  </p>
                  <p class="mt-0.5 truncate text-caption text-label-tertiary">
                    {{ task.projectName }} · 优先级 {{ task.priority }} ·
                    {{ formatRelative(task.updatedAt) }}
                  </p>
                </div>
                <div class="hidden shrink-0 items-center gap-2 sm:flex">
                  <DecisionLevelBadge :level="task.level" />
                </div>
                <Icon
                  :as="ArrowRight"
                  size="inline"
                  class="shrink-0 text-label-tertiary transition-transform duration-[120ms] ease-apple group-hover:translate-x-0.5"
                />
              </RouterLink>
            </div>

            <div v-else class="flex items-center gap-3 px-4 py-8">
              <Icon :as="Bot" size="large" class="shrink-0 text-label-tertiary" />
              <div>
                <p class="text-body font-emphasis text-label">执行位当前空闲</p>
                <p class="mt-0.5 text-caption text-label-tertiary">
                  新任务派发后会在这里显示运行状态
                </p>
              </div>
            </div>
          </section>
        </div>

        <div class="flex min-w-0 flex-col gap-3">
          <section class="rounded-panel bg-content p-4 shadow-panel">
            <div class="flex items-center gap-3">
              <div class="min-w-0">
                <h2 class="text-body font-emphasis text-label">任务构成</h2>
                <p class="mt-0.5 text-caption text-label-tertiary">当前全部任务的状态占比</p>
              </div>
              <span class="tnum ml-auto shrink-0 text-caption text-label-tertiary">
                {{ formatInteger(totalTasks) }} 项
              </span>
            </div>

            <div class="mt-4 flex items-center gap-5">
              <DonutChart
                :segments="donutSegments"
                :size="124"
                :thickness="11"
                :center-value="formatInteger(totalTasks)"
                center-label="任务"
              />
              <dl class="min-w-0 flex-1">
                <div
                  v-for="row in statusRows"
                  :key="row.status"
                  class="flex min-w-0 items-center gap-2 py-[3px]"
                >
                  <span class="size-1.5 shrink-0 rounded-full bg-current" :class="row.toneClass" />
                  <dt
                    class="truncate text-caption"
                    :class="row.count > 0 ? 'text-label-secondary' : 'text-label-tertiary'"
                  >
                    {{ row.label }}
                  </dt>
                  <dd class="tnum ml-auto shrink-0 pl-2 text-caption">
                    <span :class="row.count > 0 ? 'text-label' : 'text-label-tertiary'">
                      {{ formatInteger(row.count) }}
                    </span>
                    <span class="ml-1.5 text-label-tertiary">{{ row.percentage }}%</span>
                  </dd>
                </div>
              </dl>
            </div>
          </section>

          <section class="flex min-w-0 flex-1 flex-col overflow-hidden rounded-panel bg-content shadow-panel">
            <div class="flex min-h-14 items-center gap-3 border-b border-separator-soft px-4 py-2.5">
              <div class="min-w-0">
                <h2 class="text-body font-emphasis text-label">需要你处理</h2>
                <p class="mt-0.5 text-caption text-label-tertiary">会影响执行进度的人工事项</p>
              </div>
              <span
                v-if="attentionCount > 0"
                class="tnum ml-auto shrink-0 rounded-badge bg-warning/15 px-1.5 py-px text-caption font-emphasis text-warning"
              >
                {{ formatInteger(attentionCount) }}
              </span>
              <Icon v-else :as="ShieldCheck" size="inline" class="ml-auto text-success" />
            </div>

            <div v-if="attentionCount > 0">
              <div v-if="snapshot.pendingDecisions > 0">
                <div class="flex items-center gap-2 border-b border-separator-soft bg-window/40 px-4 py-2">
                  <p class="text-caption font-emphasis text-label-secondary">待批决策</p>
                  <span class="tnum text-caption text-label-tertiary">
                    {{ formatInteger(snapshot.pendingDecisions) }}
                  </span>
                  <RouterLink to="/decisions" class="ml-auto text-caption text-accent">
                    查看全部
                  </RouterLink>
                </div>
                <RouterLink
                  v-for="decision in snapshot.pendingDecisionItems.slice(0, 2)"
                  :key="decision.id"
                  to="/decisions"
                  class="group block border-b border-separator-soft px-4 py-2.5 hover:bg-hover"
                >
                  <div class="flex items-center gap-2">
                    <DecisionLevelBadge :level="decision.effectiveLevel" />
                    <span class="tnum ml-auto shrink-0 text-caption text-label-tertiary">
                      {{ formatRelative(decision.raisedAt) }}
                    </span>
                  </div>
                  <p class="mt-1.5 truncate text-caption text-label">{{ decision.question }}</p>
                  <p class="mt-0.5 truncate text-caption text-label-tertiary">
                    #{{ decision.taskId }} · {{ decision.projectName }}
                  </p>
                </RouterLink>
              </div>

              <div v-if="snapshot.openBugs > 0">
                <div class="flex items-center gap-2 border-b border-separator-soft bg-window/40 px-4 py-2">
                  <p class="text-caption font-emphasis text-label-secondary">开放缺陷</p>
                  <span class="tnum text-caption text-label-tertiary">
                    {{ formatInteger(snapshot.openBugs) }}
                  </span>
                  <RouterLink to="/bugs" class="ml-auto text-caption text-accent">查看全部</RouterLink>
                </div>
                <RouterLink
                  v-for="bug in snapshot.openBugItems.slice(0, 2)"
                  :key="bug.id"
                  to="/bugs"
                  class="group block border-b border-separator-soft px-4 py-2.5 hover:bg-hover"
                >
                  <div class="flex items-center gap-2">
                    <BugSeverityBadge :severity="bug.severity" />
                    <span class="tnum ml-auto shrink-0 text-caption text-label-tertiary">
                      {{ formatRelative(bug.foundAt) }}
                    </span>
                  </div>
                  <p class="mt-1.5 truncate text-caption text-label">{{ bug.title }}</p>
                  <p class="mt-0.5 truncate text-caption text-label-tertiary">
                    #{{ bug.id }} · {{ bug.projectName }}
                  </p>
                </RouterLink>
              </div>

              <RouterLink
                v-if="snapshot.tasks.FAILED > 0"
                to="/tasks"
                class="group flex items-center gap-3 px-4 py-3 hover:bg-hover"
              >
                <Icon :as="AlertTriangle" size="regular" class="shrink-0 text-danger" />
                <div class="min-w-0">
                  <p class="text-caption font-emphasis text-label">失败任务</p>
                  <p class="mt-0.5 truncate text-caption text-label-tertiary">
                    查看原因后重试或终止
                  </p>
                </div>
                <span class="tnum ml-auto text-body font-emphasis text-danger">
                  {{ formatInteger(snapshot.tasks.FAILED) }}
                </span>
                <Icon
                  :as="ArrowRight"
                  size="inline"
                  class="text-label-tertiary transition-transform duration-[120ms] ease-apple group-hover:translate-x-0.5"
                />
              </RouterLink>
            </div>

            <div v-else class="flex items-center gap-3 px-4 py-8">
              <Icon :as="ShieldCheck" size="large" class="shrink-0 text-success" />
              <div>
                <p class="text-body font-emphasis text-label">当前没有人工阻塞项</p>
                <p class="mt-0.5 text-caption text-label-tertiary">
                  新决策和缺陷会在这里进入视线
                </p>
              </div>
            </div>
          </section>
        </div>
      </section>

      <section class="rounded-panel bg-content p-4 shadow-panel">
        <div class="grid items-center gap-x-8 gap-y-4 lg:grid-cols-[minmax(240px,0.9fr)_minmax(0,1.6fr)]">
          <div class="flex items-center gap-3">
            <div class="min-w-0">
              <div class="flex items-center gap-1.5">
                <h2 class="text-body font-emphasis text-label">执行健康</h2>
                <Tooltip content="成功率＝已完成 ÷（已完成＋失败）；已终止不计入分母">
                  <Icon :as="Gauge" size="inline" class="text-label-tertiary" />
                </Tooltip>
              </div>
              <div class="mt-1.5 flex items-end gap-1.5">
                <span class="tnum text-metric font-bold text-label">
                  {{ successRate === null ? '—' : `${successRate}%` }}
                </span>
                <span class="pb-0.5 text-caption text-label-tertiary">执行成功率</span>
              </div>
            </div>
          </div>

          <div class="min-w-0">
            <div class="flex h-2 gap-px overflow-hidden rounded-full bg-neutral/12">
              <div
                class="h-full bg-success/85 transition-[width] duration-500 ease-apple"
                :style="{
                  width: `${finishedTasks === 0 ? 0 : (settledTasks / finishedTasks) * 100}%`,
                }"
                :title="`已完成 ${settledTasks}`"
              />
              <div
                class="h-full bg-danger/85 transition-[width] duration-500 ease-apple"
                :style="{
                  width: `${finishedTasks === 0 ? 0 : (snapshot.tasks.FAILED / finishedTasks) * 100}%`,
                }"
                :title="`失败 ${snapshot.tasks.FAILED}`"
              />
              <div
                class="h-full bg-neutral/45 transition-[width] duration-500 ease-apple"
                :style="{
                  width: `${finishedTasks === 0 ? 0 : (snapshot.tasks.CANCELLED / finishedTasks) * 100}%`,
                }"
                :title="`已终止 ${snapshot.tasks.CANCELLED}`"
              />
            </div>
            <dl class="mt-2.5 flex flex-wrap items-center gap-x-6 gap-y-1">
              <div class="flex items-center gap-1.5">
                <span class="size-1.5 rounded-full bg-success" />
                <dt class="text-caption text-label-tertiary">已完成</dt>
                <dd class="tnum text-caption font-emphasis text-label">
                  {{ formatInteger(settledTasks) }}
                </dd>
              </div>
              <div class="flex items-center gap-1.5">
                <span class="size-1.5 rounded-full bg-danger" />
                <dt class="text-caption text-label-tertiary">失败</dt>
                <dd
                  class="tnum text-caption font-emphasis"
                  :class="snapshot.tasks.FAILED > 0 ? 'text-danger' : 'text-label'"
                >
                  {{ formatInteger(snapshot.tasks.FAILED) }}
                </dd>
              </div>
              <div class="flex items-center gap-1.5">
                <span class="size-1.5 rounded-full bg-neutral/45" />
                <dt class="text-caption text-label-tertiary">已终止</dt>
                <dd class="tnum text-caption font-emphasis text-label">
                  {{ formatInteger(snapshot.tasks.CANCELLED) }}
                </dd>
              </div>
              <div class="ml-auto text-caption text-label-tertiary">
                已收场 <span class="tnum text-label-secondary">{{ formatInteger(finishedTasks) }}</span> 项
              </div>
            </dl>
          </div>
        </div>
      </section>
    </div>

    <div v-else-if="loading" class="flex-1 space-y-3 px-5 py-4">
      <div class="grid grid-cols-2 gap-3 lg:grid-cols-5">
        <Skeleton
          v-for="index in 5"
          :key="index"
          class="h-28 rounded-panel"
          :class="{ 'col-span-2 lg:col-span-1': index === 5 }"
        />
      </div>
      <div class="grid grid-cols-1 gap-3 lg:grid-cols-[minmax(0,1.65fr)_minmax(300px,1fr)]">
        <div class="flex flex-col gap-3">
          <Skeleton class="h-56 rounded-panel" />
          <Skeleton class="h-64 rounded-panel" />
        </div>
        <div class="flex flex-col gap-3">
          <Skeleton class="h-56 rounded-panel" />
          <Skeleton class="h-64 rounded-panel" />
        </div>
      </div>
      <Skeleton class="h-24 rounded-panel" />
    </div>

    <div v-else class="flex flex-1 items-center justify-center px-5 py-16">
      <div class="max-w-sm text-center">
        <span class="mx-auto grid size-12 place-items-center rounded-full bg-danger/10">
          <Icon :as="AlertTriangle" size="xlarge" class="text-danger" />
        </span>
        <p class="mt-3 text-body font-emphasis text-label">看板数据加载失败</p>
        <p class="mt-1 text-caption text-label-secondary">{{ error }}</p>
        <Button class="mt-4" @click="load">重新加载</Button>
      </div>
    </div>
  </div>
</template>
