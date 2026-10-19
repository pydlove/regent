<script setup lang="ts">
import {
  AlertTriangle,
  ArrowRight,
  Bot,
  Bug,
  CircleAlert,
  CircleCheck,
  Clock3,
  Coins,
  Gauge,
  ListChecks,
  RefreshCw,
  ShieldCheck,
  TrendingUp,
  Workflow,
} from '@lucide/vue'
import { computed, onMounted, onUnmounted, ref, watch, type Component } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

import { getDashboardSnapshot, type DashboardSnapshot } from '@/api/dashboard'
import { ApiError } from '@/api/http'
import BugSeverityBadge from '@/components/business/BugSeverityBadge.vue'
import DecisionLevelBadge from '@/components/business/DecisionLevelBadge.vue'
import TaskStatusBadge from '@/components/business/TaskStatusBadge.vue'
import TokenUsageDialog from '@/components/business/TokenUsageDialog.vue'
import Button from '@/components/ui/button/Button.vue'
import DonutChart from '@/components/ui/chart/DonutChart.vue'
import TrendArea from '@/components/ui/chart/TrendArea.vue'
import type { DonutSegment, TrendDatum } from '@/components/ui/chart/chart.types'
import Icon from '@/components/ui/icon/Icon.vue'
import IconButton from '@/components/ui/icon-button/IconButton.vue'
import Skeleton from '@/components/ui/skeleton/Skeleton.vue'
import Tooltip from '@/components/ui/tooltip/Tooltip.vue'
import { useTaskStore } from '@/stores/useTaskStore'
import type { TaskStatus } from '@/types/task'
import { formatRelative, formatTokenYi, formatTokens } from '@/utils/format'

const snapshot = ref<DashboardSnapshot | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
const lastUpdated = ref<Date | null>(null)
const tokenDialogOpen = ref(false)
const router = useRouter()
const store = useTaskStore()
let loadSequence = 0

const integerFormatter = new Intl.NumberFormat('zh-CN')
const formatInteger = (value: number) => integerFormatter.format(value)
const formatTokenCount = (value: number) => (value === 0 ? '0' : formatTokens(value))
const weekdayFormatter = new Intl.DateTimeFormat('zh-CN', { weekday: 'short' })

function messageOf(value: unknown): string {
  if (value instanceof ApiError) return value.message
  return '无法连接后端，请确认服务已启动（默认 127.0.0.1:8787）'
}

async function load() {
  const sequence = ++loadSequence
  const projectId = store.projectId
  loading.value = true
  error.value = null
  try {
    const next = await getDashboardSnapshot(projectId)
    if (sequence !== loadSequence) return
    snapshot.value = next
    lastUpdated.value = new Date()
  } catch (value) {
    if (sequence !== loadSequence) return
    error.value = messageOf(value)
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

const scopeLabel = computed(() => {
  if (store.projectId === null) return '全部项目'
  return store.projects.find((project) => project.id === store.projectId)?.name
    ?? `项目 #${store.projectId}`
})

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
const cachedHitRate = computed(() => {
  const usage = snapshot.value?.tokenUsage
  if (!usage || usage.input === 0) return null
  return Math.round((usage.cachedInput / usage.input) * 100)
})

const attentionSummary = computed(() => {
  const current = snapshot.value
  if (!current) return ''
  return [
    current.pendingDecisions > 0 ? `${formatInteger(current.pendingDecisions)} 项决策` : '',
    current.openBugs > 0 ? `${formatInteger(current.openBugs)} 个缺陷` : '',
    current.tasks.FAILED > 0 ? `${formatInteger(current.tasks.FAILED)} 项失败任务` : '',
  ]
    .filter(Boolean)
    .join(' · ')
})

const focusTitle = computed(() => {
  const current = snapshot.value
  if (!current) return loading.value ? '正在同步运行数据' : '暂时无法读取工作台'
  if (current.pendingDecisions > 0) return `有 ${formatInteger(current.pendingDecisions)} 项决策等你拍板`
  if (current.tasks.FAILED > 0) return `有 ${formatInteger(current.tasks.FAILED)} 项任务执行失败`
  if (current.openBugs > 0) return `有 ${formatInteger(current.openBugs)} 个缺陷等待处理`
  if (activeTasks.value > 0) return `${formatInteger(activeTasks.value)} 项任务正在推进`
  return '平台运行平稳'
})

const focusDescription = computed(() => {
  const current = snapshot.value
  if (!current) return error.value ?? '正在读取任务、决策与缺陷状态。'
  if (attentionCount.value > 0) {
    return `当前待处理 ${attentionSummary.value}；已完成 ${formatInteger(current.tasks.DONE)} 项任务。`
  }
  if (activeTasks.value > 0) {
    return `当前 ${formatInteger(current.tasks.RUNNING)} 项运行、${formatInteger(current.tasks.QUEUED)} 项排队，没有人工阻塞项。`
  }
  return `全部 ${formatInteger(totalTasks.value)} 项任务已收场，可以开始录入下一项需求。`
})

const primaryAction = computed(() => {
  const current = snapshot.value
  if (current && current.pendingDecisions > 0) {
    return { to: '/decisions', label: `处理 ${formatInteger(current.pendingDecisions)} 项决策` }
  }
  if (current && current.tasks.FAILED > 0) return { to: '/tasks', label: '查看失败任务' }
  if (current && current.openBugs > 0) {
    return { to: '/bugs', label: `查看 ${formatInteger(current.openBugs)} 个缺陷` }
  }
  return { to: '/tasks', label: '打开任务中心' }
})

const goPrimaryAction = () => router.push(primaryAction.value.to)
const openMetric = (metric: Metric) => {
  if (metric.key === 'tokens') {
    tokenDialogOpen.value = true
    return
  }
  void router.push(metric.to)
}

type Metric = {
  key: string
  to: string
  icon: Component
  iconClass: string
  label: string
  valueText: string
  unit: string
  secondary?: string
  valueClass: string
  caption: string
}

const metrics = computed<Metric[]>(() => {
  const current = snapshot.value
  if (!current) return []
  const rateTone =
    successRate.value === null
      ? 'text-label-tertiary'
      : successRate.value >= 90
        ? 'text-success'
        : successRate.value >= 70
          ? 'text-warning'
          : 'text-danger'
  return [
    {
      key: 'attention',
      to: primaryAction.value.to,
      icon: attentionCount.value > 0 ? CircleAlert : CircleCheck,
      iconClass:
        attentionCount.value > 0 ? 'bg-warning/15 text-warning' : 'bg-success/12 text-success',
      label: '待你处理',
      valueText: formatInteger(attentionCount.value),
      unit: '项',
      valueClass: attentionCount.value > 0 ? 'text-warning' : 'text-label',
      caption: attentionCount.value > 0 ? attentionSummary.value : '所有事项都已处理',
    },
    {
      key: 'active',
      to: '/tasks',
      icon: Bot,
      iconClass: activeTasks.value > 0 ? 'bg-accent/12 text-accent' : 'bg-neutral/12 text-neutral',
      label: '正在执行',
      valueText: formatInteger(activeTasks.value),
      unit: '项',
      valueClass: activeTasks.value > 0 ? 'text-label' : 'text-label-tertiary',
      caption: `${formatInteger(current.tasks.RUNNING)} 运行 · ${formatInteger(current.tasks.QUEUED)} 排队`,
    },
    {
      key: 'total',
      to: '/tasks',
      icon: ListChecks,
      iconClass: 'bg-accent/12 text-accent',
      label: '任务总数',
      valueText: formatInteger(totalTasks.value),
      unit: '项',
      valueClass: 'text-label',
      caption: `已完成 ${formatInteger(current.tasks.DONE)} 项`,
    },
    {
      key: 'success',
      to: '/tasks',
      icon: Gauge,
      iconClass: 'bg-success/12 text-success',
      label: '执行成功率',
      valueText: successRate.value === null ? '—' : `${successRate.value}%`,
      unit: '',
      valueClass: rateTone,
      caption:
        successfulFinishedTasks.value === 0
          ? '还没有可评估的执行'
          : `${formatInteger(successfulFinishedTasks.value)} 项已收场`,
    },
    {
      key: 'tokens',
      to: '/tasks',
      icon: Coins,
      iconClass: 'bg-neutral/12 text-neutral',
      label: 'Token 消耗',
      valueText: formatTokenCount(current.tokenUsage.total),
      unit: 'tokens',
      secondary: formatTokenYi(current.tokenUsage.total)
        ? `≈ ${formatTokenYi(current.tokenUsage.total)} 亿 tokens`
        : '',
      valueClass: 'text-label',
      caption:
        cachedHitRate.value === null
          ? '尚未产生已结算消耗'
          : `缓存命中 ${cachedHitRate.value}% · 输出 ${formatTokenCount(current.tokenUsage.output)}`,
    },
  ]
})

const statusDefinitions: Array<{
  status: TaskStatus
  label: string
  toneClass: string
  barClass: string
}> = [
  { status: 'PENDING', label: '待执行', toneClass: 'text-neutral/40', barClass: 'bg-neutral/30' },
  { status: 'QUEUED', label: '排队中', toneClass: 'text-warning/60', barClass: 'bg-warning/55' },
  { status: 'RUNNING', label: '执行中', toneClass: 'text-accent', barClass: 'bg-accent' },
  { status: 'BLOCKED', label: '待批准', toneClass: 'text-warning', barClass: 'bg-warning' },
  { status: 'DONE', label: '已完成', toneClass: 'text-success', barClass: 'bg-success' },
  { status: 'FAILED', label: '失败', toneClass: 'text-danger', barClass: 'bg-danger' },
  {
    status: 'CANCELLED',
    label: '已终止',
    toneClass: 'text-neutral/25',
    barClass: 'bg-neutral/35',
  },
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

const trendPoints = computed<TrendDatum[]>(() => {
  const points = snapshot.value?.trend ?? []
  return points.map((point, index) => ({
    key: point.date,
    label:
      index === points.length - 1
        ? '今天'
        : weekdayFormatter.format(new Date(`${point.date}T00:00:00`)),
    done: point.done,
    failed: point.failed,
    highlight: index === points.length - 1,
  }))
})
const trendDone = computed(() => snapshot.value?.trend.reduce((sum, point) => sum + point.done, 0) ?? 0)
const trendFailed = computed(
  () => snapshot.value?.trend.reduce((sum, point) => sum + point.failed, 0) ?? 0,
)
const trendEmpty = computed(() => trendDone.value === 0 && trendFailed.value === 0)

const healthSegments = computed(() => {
  const current = snapshot.value
  if (!current || finishedTasks.value === 0) return []
  return [
    {
      key: 'done',
      label: '已完成',
      count: current.tasks.DONE,
      width: (current.tasks.DONE / finishedTasks.value) * 100,
      barClass: 'bg-success',
    },
    {
      key: 'failed',
      label: '失败',
      count: current.tasks.FAILED,
      width: (current.tasks.FAILED / finishedTasks.value) * 100,
      barClass: 'bg-danger',
    },
    {
      key: 'cancelled',
      label: '已终止',
      count: current.tasks.CANCELLED,
      width: (current.tasks.CANCELLED / finishedTasks.value) * 100,
      barClass: 'bg-neutral/40',
    },
  ]
})

const qualityLabel = computed(() => {
  if (successRate.value === null) return '暂无样本'
  if (successRate.value >= 95) return '执行稳定'
  if (successRate.value >= 80) return '总体可控'
  return '需要复盘'
})

const updatedAt = computed(() =>
  lastUpdated.value
    ? lastUpdated.value.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
    : '',
)

watch(
  () => store.projectId,
  () => {
    snapshot.value = null
    lastUpdated.value = null
    error.value = null
    void load()
  },
)

onMounted(() => void load())
onUnmounted(() => {
  loadSequence++
})
</script>

<template>
  <div class="min-h-full bg-dashboard">
    <div class="mx-auto flex w-full max-w-[1480px] flex-col px-4 pb-8 pt-4 sm:px-6 lg:px-8 lg:pb-10 lg:pt-6">
      <header class="rounded-card border border-separator-soft bg-content px-4 py-4 shadow-panel sm:px-5 lg:px-6">
        <div class="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          <div class="min-w-0">
            <div class="flex flex-wrap items-center gap-2.5">
              <span class="inline-flex items-center gap-1.5 rounded-full bg-accent/8 px-2.5 py-1 text-subhead font-emphasis text-accent">
                <span
                  class="size-1.5 rounded-full"
                  :class="error ? 'bg-danger' : snapshot ? 'bg-success' : 'bg-neutral'"
                />
                实时运行概览
              </span>
              <span v-if="updatedAt" class="text-subhead text-label-tertiary">
                更新于 <span class="tnum">{{ updatedAt }}</span>
              </span>
              <span class="text-subhead text-label-tertiary">{{ scopeLabel }}</span>
            </div>
            <h1 class="mt-3 max-w-4xl text-metric font-bold tracking-[-0.015em] text-label">{{ focusTitle }}</h1>
            <p class="mt-1 max-w-3xl text-body leading-5 text-label-secondary">{{ focusDescription }}</p>
          </div>

          <div class="flex shrink-0 flex-wrap items-center gap-2">
            <Button variant="primary" @click="goPrimaryAction">
              {{ primaryAction.label }}
              <Icon :as="ArrowRight" size="inline" />
            </Button>
            <Tooltip content="刷新工作台数据">
              <IconButton label="刷新工作台数据" variant="default" :disabled="loading" @click="load">
                <Icon :as="RefreshCw" :class="{ 'animate-spin': loading }" />
              </IconButton>
            </Tooltip>
          </div>
        </div>
      </header>

      <div
        v-if="error && snapshot"
        role="alert"
        class="mt-3 flex items-center gap-2 rounded-panel bg-danger/8 px-4 py-2 text-subhead text-danger"
      >
        <Icon :as="AlertTriangle" size="inline" />
        <span class="min-w-0 flex-1">{{ error }}</span>
        <button class="font-emphasis underline underline-offset-2" @click="load">重试</button>
      </div>

      <div
        v-if="snapshot && snapshot.unavailableSections.length > 0"
        role="status"
        class="mt-3 flex items-center gap-2 rounded-panel bg-warning/8 px-4 py-2 text-subhead text-warning"
      >
        <Icon :as="AlertTriangle" size="inline" />
        <span class="min-w-0 flex-1">
          部分数据暂未加载：{{ snapshot.unavailableSections.join('、') }}
        </span>
        <button class="font-emphasis underline underline-offset-2" @click="load">重新加载</button>
      </div>

      <template v-if="snapshot">
        <section
          aria-label="核心指标"
          class="mt-4 grid grid-cols-2 gap-2.5 md:grid-cols-5"
        >
          <button
            v-for="metric in metrics"
            :key="metric.key"
            type="button"
            class="group relative flex min-h-[120px] min-w-0 flex-col overflow-hidden rounded-card border border-separator-soft bg-content px-4 py-3.5 text-left shadow-panel transition duration-200 ease-apple hover:-translate-y-0.5 hover:border-accent/20 hover:shadow-popover focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-accent/35"
            :class="{ 'col-span-2 md:col-span-1': metric.key === 'tokens' }"
            @click="openMetric(metric)"
          >
            <div class="relative flex items-center gap-2">
              <span class="grid size-8 shrink-0 place-items-center rounded-panel" :class="metric.iconClass">
                <Icon :as="metric.icon" size="inline" />
              </span>
              <span class="truncate text-subhead font-emphasis text-label-secondary">{{ metric.label }}</span>
              <Icon
                :as="ArrowRight"
                size="inline"
                class="absolute right-0 top-1/2 -translate-y-1/2 text-label-tertiary opacity-0 transition-opacity duration-[120ms] ease-apple group-hover:opacity-100"
              />
            </div>
            <div class="relative mt-3 flex min-w-0 items-baseline gap-1.5">
              <span class="tnum truncate text-metric font-bold" :class="metric.valueClass">
                {{ metric.valueText }}
              </span>
              <span v-if="metric.unit" class="shrink-0 text-subhead text-label-tertiary">
                {{ metric.unit }}
              </span>
            </div>
            <p
              v-if="metric.secondary"
              class="tnum relative mt-0.5 text-subhead font-emphasis text-label-secondary"
            >
              {{ metric.secondary }}
            </p>
            <p class="relative mt-1 line-clamp-1 text-subhead text-label-tertiary md:line-clamp-2">{{ metric.caption }}</p>
          </button>
        </section>

        <section
          class="mt-4 grid items-start gap-3 xl:grid-cols-[minmax(0,1.55fr)_minmax(380px,1fr)]"
        >
          <div class="contents xl:order-1 xl:flex xl:min-w-0 xl:flex-col xl:gap-3">
          <section class="order-1 flex min-w-0 flex-col overflow-hidden rounded-card border border-separator-soft bg-content shadow-panel">
            <div class="flex flex-wrap items-start gap-x-6 gap-y-2 px-5 pt-5">
              <div class="flex min-w-0 items-center gap-3">
                <span class="grid size-8 shrink-0 place-items-center rounded-panel bg-accent/12 text-accent">
                  <Icon :as="TrendingUp" />
                </span>
                <div class="min-w-0">
                  <h2 class="text-title font-emphasis text-label">执行趋势</h2>
                  <p class="mt-0.5 text-subhead text-label-tertiary">过去 7 天的完成与失败节奏</p>
                </div>
              </div>
              <div class="ml-auto flex items-center gap-5 text-subhead text-label-tertiary">
                <span class="flex items-center gap-1.5">
                  <span class="size-2 rounded-full bg-success" />
                  完成 <span class="tnum font-emphasis text-label-secondary">{{ formatInteger(trendDone) }}</span>
                </span>
                <span class="flex items-center gap-1.5">
                  <span class="size-2 rounded-full bg-danger" />
                  失败 <span class="tnum font-emphasis text-label-secondary">{{ formatInteger(trendFailed) }}</span>
                </span>
              </div>
            </div>

            <div class="px-5 pb-2 pt-4">
              <TrendArea :points="trendPoints" :height="128" />
              <p
                v-if="trendEmpty"
                class="mt-2 flex items-center gap-1.5 text-subhead text-label-tertiary"
              >
                <Icon :as="Clock3" size="inline" />
                近 7 天还没有收场任务，跑起来后这里会逐天累积。
              </p>
            </div>

            <div class="mt-auto border-t border-separator-soft">
              <div class="flex items-center gap-3 px-5 py-4">
                <div class="min-w-0">
                  <h3 class="text-title font-emphasis text-label">执行队列</h3>
                  <p class="mt-1 text-subhead text-label-tertiary">运行中与排队任务</p>
                </div>
                <span class="tnum ml-auto shrink-0 text-subhead text-label-tertiary">
                  {{ formatInteger(activeTasks) }} 项
                </span>
                <RouterLink
                  to="/tasks"
                  class="group inline-flex shrink-0 items-center gap-1 text-subhead text-accent"
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
                  class="group flex min-w-0 items-center gap-3 border-t border-separator-soft px-5 py-3.5 hover:bg-hover"
                >
                  <TaskStatusBadge :status="task.status" />
                  <div class="min-w-0 flex-1">
                    <p class="truncate text-body leading-5 text-label">
                      <span class="tnum mr-1 text-label-tertiary">#{{ task.id }}</span>
                      {{ task.title }}
                    </p>
                    <p class="mt-1 truncate text-subhead text-label-tertiary">
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

              <div v-else class="flex items-center gap-3 border-t border-separator-soft px-5 py-6">
                <span class="grid size-8 shrink-0 place-items-center rounded-[8px] bg-success/10">
                  <Icon :as="CircleCheck" size="large" class="text-success" />
                </span>
                <div>
                  <p class="text-title font-emphasis text-label">执行位当前空闲</p>
                  <p class="mt-1 text-subhead text-label-tertiary">新任务派发后会在这里显示运行状态</p>
                </div>
              </div>
            </div>
          </section>

          <article class="order-3 overflow-hidden rounded-card border border-separator-soft bg-content shadow-panel">
            <div class="flex items-center gap-3 px-5 pt-5">
              <span class="grid size-8 shrink-0 place-items-center rounded-panel bg-accent/12 text-accent">
                <Icon :as="ListChecks" />
              </span>
              <div class="min-w-0">
                <h2 class="text-title font-emphasis text-label">任务构成</h2>
                <p class="mt-0.5 text-subhead text-label-tertiary">任务在各阶段的状态分布</p>
              </div>
              <span class="tnum ml-auto shrink-0 text-subhead text-label-tertiary">
                {{ formatInteger(totalTasks) }} 项
              </span>
            </div>

            <div class="grid items-center gap-5 px-5 pb-5 pt-4 md:grid-cols-[132px_minmax(0,1fr)]">
              <div class="flex flex-col items-center justify-center">
                <DonutChart
                  :segments="donutSegments"
                  :size="124"
                  :thickness="11"
                  :center-value="formatInteger(totalTasks)"
                  center-label="任务"
                />
                <p class="mt-2.5 text-subhead text-label-tertiary">
                  已完成 <span class="tnum font-emphasis text-label-secondary">{{ formatInteger(snapshot.tasks.DONE) }}</span>
                </p>
              </div>

              <dl class="grid min-w-0 content-center gap-2.5">
                <div
                  v-for="row in statusRows"
                  :key="row.status"
                  class="grid grid-cols-[64px_minmax(0,1fr)_76px] items-center gap-3"
                >
                  <dt
                    class="truncate text-subhead"
                    :class="row.count > 0 ? 'text-label-secondary' : 'text-label-tertiary'"
                  >
                    {{ row.label }}
                  </dt>
                  <div class="h-1.5 overflow-hidden rounded-full bg-neutral/10">
                    <div
                      class="h-full rounded-full transition-[width] duration-500 ease-apple"
                      :class="row.barClass"
                      :style="{ width: `${row.percentage}%` }"
                    />
                  </div>
                  <dd class="tnum text-right text-subhead">
                    <span :class="row.count > 0 ? 'font-emphasis text-label' : 'text-label-tertiary'">
                      {{ formatInteger(row.count) }}
                    </span>
                    <span class="ml-1 text-label-tertiary">{{ row.percentage }}%</span>
                  </dd>
                </div>
              </dl>
            </div>
          </article>
          </div>

          <div class="contents xl:order-2 xl:flex xl:min-w-0 xl:flex-col xl:gap-3">
          <aside class="order-2 flex min-w-0 flex-col overflow-hidden rounded-card border border-separator-soft bg-content shadow-panel">
            <div class="flex items-center gap-3 border-b border-separator-soft px-5 py-4">
              <span
                class="grid size-9 shrink-0 place-items-center rounded-panel"
                :class="attentionCount > 0 ? 'bg-warning/15 text-warning' : 'bg-success/12 text-success'"
              >
                <Icon :as="attentionCount > 0 ? CircleAlert : ShieldCheck" />
              </span>
              <div class="min-w-0">
                <h2 class="text-title font-emphasis text-label">待办中心</h2>
                <p class="mt-0.5 text-subhead text-label-tertiary">优先处理会阻塞执行的事项</p>
              </div>
              <span
                class="tnum ml-auto shrink-0 rounded-badge px-2.5 py-1 text-subhead font-emphasis"
                :class="attentionCount > 0 ? 'bg-warning/15 text-warning' : 'bg-success/12 text-success'"
              >
                {{ formatInteger(attentionCount) }}
              </span>
            </div>

            <template v-if="attentionCount > 0">
              <div class="grid grid-cols-3 gap-2 border-b border-separator-soft px-4 py-3">
                <RouterLink to="/decisions" class="group min-w-0 rounded-panel border border-separator-soft bg-dashboard px-2.5 py-2.5 transition-colors duration-150 ease-apple hover:border-accent/20 hover:bg-accent/4">
                  <span class="mb-1.5 flex items-center justify-between text-warning">
                    <span class="grid size-7 place-items-center rounded-[8px] bg-warning/12">
                      <Icon :as="Workflow" size="inline" />
                    </span>
                    <Icon :as="ArrowRight" size="inline" class="opacity-45 transition-transform duration-150 ease-apple group-hover:translate-x-0.5" />
                  </span>
                  <p class="truncate text-subhead text-label-secondary">待批决策</p>
                  <p class="tnum mt-0.5 text-title font-bold text-warning">
                    {{ formatInteger(snapshot.pendingDecisions) }}
                  </p>
                  <p class="mt-1 truncate text-subhead text-label-tertiary">
                    L2 {{ snapshot.pendingL2Decisions }} · L3 {{ snapshot.pendingL3Decisions }}
                  </p>
                </RouterLink>
                <RouterLink to="/bugs" class="group min-w-0 rounded-panel border border-separator-soft bg-dashboard px-2.5 py-2.5 transition-colors duration-150 ease-apple hover:border-accent/20 hover:bg-accent/4">
                  <span class="mb-1.5 flex items-center justify-between text-danger">
                    <span class="grid size-7 place-items-center rounded-[8px] bg-danger/12">
                      <Icon :as="Bug" size="inline" />
                    </span>
                    <Icon :as="ArrowRight" size="inline" class="opacity-45 transition-transform duration-150 ease-apple group-hover:translate-x-0.5" />
                  </span>
                  <p class="truncate text-subhead text-label-secondary">开放缺陷</p>
                  <p class="tnum mt-0.5 text-title font-bold text-danger">
                    {{ formatInteger(snapshot.openBugs) }}
                  </p>
                  <p class="mt-1 truncate text-subhead text-label-tertiary">等待修复或验证</p>
                </RouterLink>
                <RouterLink to="/tasks" class="group min-w-0 rounded-panel border border-separator-soft bg-dashboard px-2.5 py-2.5 transition-colors duration-150 ease-apple hover:border-accent/20 hover:bg-accent/4">
                  <span class="mb-1.5 flex items-center justify-between text-danger">
                    <span class="grid size-7 place-items-center rounded-[8px] bg-danger/12">
                      <Icon :as="AlertTriangle" size="inline" />
                    </span>
                    <Icon :as="ArrowRight" size="inline" class="opacity-45 transition-transform duration-150 ease-apple group-hover:translate-x-0.5" />
                  </span>
                  <p class="truncate text-subhead text-label-secondary">失败任务</p>
                  <p class="tnum mt-0.5 text-title font-bold text-danger">
                    {{ formatInteger(snapshot.tasks.FAILED) }}
                  </p>
                  <p class="mt-1 truncate text-subhead text-label-tertiary">需要重试或终止</p>
                </RouterLink>
              </div>

              <div class="min-h-0 flex-1">
                <RouterLink
                  v-for="decision in snapshot.pendingDecisionItems.slice(0, 2)"
                  :key="`decision-${decision.id}`"
                  to="/decisions"
                  class="group flex items-start gap-3 border-b border-separator-soft px-5 py-3.5 transition-colors duration-150 ease-apple hover:bg-hover"
                >
                  <span class="mt-0.5 grid size-8 shrink-0 place-items-center rounded-panel bg-warning/14 text-warning">
                    <Icon :as="Workflow" size="inline" />
                  </span>
                  <div class="min-w-0 flex-1">
                    <div class="flex items-center gap-2">
                      <DecisionLevelBadge :level="decision.effectiveLevel" />
                      <span class="tnum ml-auto shrink-0 text-subhead text-label-tertiary">
                        {{ formatRelative(decision.raisedAt) }}
                      </span>
                    </div>
                    <p class="mt-2 line-clamp-2 text-body leading-5 text-label">{{ decision.question }}</p>
                    <p class="mt-1 truncate text-subhead text-label-tertiary">
                      #{{ decision.taskId }} · {{ decision.projectName }}
                    </p>
                  </div>
                  <Icon
                    :as="ArrowRight"
                    size="inline"
                    class="mt-2 shrink-0 text-label-tertiary transition-transform duration-[120ms] ease-apple group-hover:translate-x-0.5"
                  />
                </RouterLink>

                <RouterLink
                  v-for="bug in snapshot.openBugItems.slice(0, 1)"
                  :key="`bug-${bug.id}`"
                  to="/bugs"
                  class="group flex items-start gap-3 border-b border-separator-soft px-5 py-3.5 transition-colors duration-150 ease-apple hover:bg-hover"
                >
                  <span class="mt-0.5 grid size-8 shrink-0 place-items-center rounded-panel bg-danger/12 text-danger">
                    <Icon :as="Bug" size="inline" />
                  </span>
                  <div class="min-w-0 flex-1">
                    <div class="flex items-center gap-2">
                      <BugSeverityBadge :severity="bug.severity" />
                      <span class="tnum ml-auto shrink-0 text-subhead text-label-tertiary">
                        {{ formatRelative(bug.foundAt) }}
                      </span>
                    </div>
                    <p class="mt-2 line-clamp-2 text-body leading-5 text-label">{{ bug.title }}</p>
                    <p class="mt-1 truncate text-subhead text-label-tertiary">
                      #{{ bug.id }} · {{ bug.projectName }}
                    </p>
                  </div>
                  <Icon
                    :as="ArrowRight"
                    size="inline"
                    class="mt-2 shrink-0 text-label-tertiary transition-transform duration-[120ms] ease-apple group-hover:translate-x-0.5"
                  />
                </RouterLink>

                <RouterLink
                  v-if="
                    snapshot.tasks.FAILED > 0 &&
                    snapshot.pendingDecisions === 0 &&
                    snapshot.openBugs === 0
                  "
                  to="/tasks"
                  class="group flex items-center gap-3 px-5 py-3.5 hover:bg-hover"
                >
                  <span class="grid size-8 shrink-0 place-items-center rounded-panel bg-danger/12 text-danger">
                    <Icon :as="AlertTriangle" size="inline" />
                  </span>
                  <div class="min-w-0 flex-1">
                    <p class="text-body font-emphasis text-label">失败任务</p>
                    <p class="mt-1 truncate text-subhead text-label-tertiary">查看原因后重试或终止</p>
                  </div>
                  <span class="tnum text-title font-emphasis text-danger">
                    {{ formatInteger(snapshot.tasks.FAILED) }}
                  </span>
                  <Icon
                    :as="ArrowRight"
                    size="inline"
                    class="text-label-tertiary transition-transform duration-[120ms] ease-apple group-hover:translate-x-0.5"
                  />
                </RouterLink>
              </div>
            </template>

            <div v-else class="flex flex-1 flex-col items-center justify-center px-5 py-8 text-center">
              <span class="grid size-10 place-items-center rounded-full bg-success/10">
                <Icon :as="ShieldCheck" size="xlarge" class="text-success" />
              </span>
              <p class="mt-3 text-title font-emphasis text-label">当前没有人工阻塞项</p>
              <p class="mt-1.5 text-subhead text-label-tertiary">新的决策、缺陷和失败任务会在这里进入视线</p>
            </div>
          </aside>
          <article class="order-4 overflow-hidden rounded-card border border-separator-soft bg-content shadow-panel">
            <div class="flex items-center gap-3 px-5 pt-5">
              <span class="grid size-8 shrink-0 place-items-center rounded-panel bg-success/12 text-success">
                <Icon :as="Gauge" />
              </span>
              <div class="min-w-0">
                <div class="flex items-center gap-2">
                  <h2 class="text-title font-emphasis text-label">执行质量</h2>
                  <Tooltip content="成功率＝已完成 ÷（已完成＋失败）；已终止不计入分母">
                    <Icon :as="CircleAlert" size="inline" class="text-label-tertiary" />
                  </Tooltip>
                </div>
                <p class="mt-0.5 text-subhead text-label-tertiary">收场任务的成功与稳定性</p>
              </div>
              <span
                class="ml-auto shrink-0 rounded-badge bg-success/12 px-2.5 py-1 text-subhead font-emphasis text-success"
              >
                {{ qualityLabel }}
              </span>
            </div>

            <div class="px-5 pb-5 pt-4">
              <div class="flex items-end gap-2">
                <span class="tnum text-display font-bold text-label">
                  {{ successRate === null ? '—' : `${successRate}%` }}
                </span>
                <span class="pb-1 text-subhead text-label-tertiary">执行成功率</span>
                <span class="tnum ml-auto pb-1 text-subhead text-label-tertiary">
                  {{ formatInteger(finishedTasks) }} 项已收场
                </span>
              </div>

              <div class="mt-4 flex h-2 gap-px overflow-hidden rounded-full bg-neutral/12">
                <div
                  v-for="segment in healthSegments"
                  :key="segment.key"
                  class="h-full transition-[width] duration-500 ease-apple"
                  :class="segment.barClass"
                  :style="{ width: `${segment.width}%` }"
                  :title="`${segment.label} ${segment.count}`"
                />
              </div>

              <dl class="mt-4 grid grid-cols-3 gap-2">
                <div class="rounded-panel bg-dashboard px-3 py-2.5">
                  <dt class="flex items-center gap-1.5 text-subhead text-label-tertiary">
                    <span class="size-1.5 rounded-full bg-success" />已完成
                  </dt>
                  <dd class="tnum mt-1.5 text-title font-emphasis text-label">{{ formatInteger(settledTasks) }}</dd>
                </div>
                <div class="rounded-panel bg-dashboard px-3 py-2.5">
                  <dt class="flex items-center gap-1.5 text-subhead text-label-tertiary">
                    <span class="size-1.5 rounded-full bg-danger" />失败
                  </dt>
                  <dd class="tnum mt-1.5 text-title font-emphasis" :class="snapshot.tasks.FAILED > 0 ? 'text-danger' : 'text-label'">
                    {{ formatInteger(snapshot.tasks.FAILED) }}
                  </dd>
                </div>
                <div class="rounded-panel bg-dashboard px-3 py-2.5">
                  <dt class="flex items-center gap-1.5 text-subhead text-label-tertiary">
                    <span class="size-1.5 rounded-full bg-neutral/50" />已终止
                  </dt>
                  <dd class="tnum mt-1.5 text-title font-emphasis text-label">{{ formatInteger(snapshot.tasks.CANCELLED) }}</dd>
                </div>
              </dl>

              <p class="mt-4 text-subhead text-label-tertiary">
                {{ successfulFinishedTasks }} 项可评估，已终止不计入成功率
              </p>
            </div>
          </article>
          </div>
        </section>
      </template>

      <div v-else-if="loading" class="mt-5">
        <div class="grid grid-cols-2 gap-2.5 md:grid-cols-5">
          <Skeleton
            v-for="index in 5"
            :key="index"
            class="h-[120px] rounded-card bg-content"
            :class="{ 'col-span-2 md:col-span-1': index === 5 }"
          />
        </div>
        <div class="mt-4 grid gap-3 xl:grid-cols-[minmax(0,1.55fr)_minmax(380px,1fr)]">
          <div class="contents xl:flex xl:flex-col xl:gap-3">
            <Skeleton class="order-1 h-[360px] rounded-card" />
            <Skeleton class="order-3 h-40 rounded-card" />
          </div>
          <div class="contents xl:flex xl:flex-col xl:gap-3">
            <Skeleton class="order-2 h-[360px] rounded-card" />
            <Skeleton class="order-4 h-40 rounded-card" />
          </div>
        </div>
      </div>

      <div v-else class="mt-5 flex min-h-[420px] items-center justify-center rounded-card border border-separator-soft bg-content px-5 py-16 shadow-panel">
        <div class="max-w-sm text-center">
          <span class="mx-auto grid size-12 place-items-center rounded-full bg-danger/10">
            <Icon :as="AlertTriangle" size="xlarge" class="text-danger" />
          </span>
          <p class="mt-3 text-title font-emphasis text-label">工作台数据加载失败</p>
          <p class="mt-1.5 text-subhead text-label-secondary">{{ error }}</p>
          <Button class="mt-4" @click="load">重新加载</Button>
        </div>
      </div>
    </div>

    <TokenUsageDialog
      v-model:open="tokenDialogOpen"
      :project-id="store.projectId"
      :scope-label="scopeLabel"
    />
  </div>
</template>
