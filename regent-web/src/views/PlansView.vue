<script setup lang="ts">
import {
  CalendarClock,
  ChevronLeft,
  ListChecks,
  Play,
  Plus,
  RefreshCw,
} from '@lucide/vue'
import { computed, onMounted, onUnmounted, ref } from 'vue'

import { ApiError } from '@/api/http'
import { getPlan, listPlans, runPlan } from '@/api/plans'
import PlanFormDialog from '@/components/business/PlanFormDialog.vue'
import TaskStatusBadge from '@/components/business/TaskStatusBadge.vue'
import Button from '@/components/ui/button/Button.vue'
import EmptyState from '@/components/ui/empty-state/EmptyState.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import IconButton from '@/components/ui/icon-button/IconButton.vue'
import StatusDot from '@/components/ui/status-dot/StatusDot.vue'
import Table from '@/components/ui/table/Table.vue'
import type { TableColumn } from '@/components/ui/table/table.types'
import Tooltip from '@/components/ui/tooltip/Tooltip.vue'
import { useToast } from '@/composables/useToast'
import type { PlanDetail, PlanRunSummary, PlanSummary } from '@/types/api'
import {
  planRunStatusLabel,
  planStatusLabel,
  planTaskStatusLabel,
  type PlanRunStatus,
  type PlanStatus,
  type PlanTaskStatus,
} from '@/types/plan'
import { formatDateTime } from '@/utils/format'

const plans = ref<PlanSummary[]>([])
const detail = ref<PlanDetail | null>(null)
const selectedId = ref<number | null>(null)
const loading = ref(false)
const running = ref(false)
const formOpen = ref(false)
const error = ref<string | null>(null)
const toast = useToast()

const columns: TableColumn[] = [
  { key: 'name', label: '计划' },
  { key: 'status', label: '状态', width: '96px' },
  { key: 'scheduledAt', label: '执行时间', width: '168px', hideBelow: 'md' },
  { key: 'tasks', label: '任务', align: 'right', width: '64px' },
  { key: 'result', label: '本轮结果', width: '112px', hideBelow: 'lg' },
]

const selected = computed(() => plans.value.find((plan) => plan.id === selectedId.value) ?? null)
const hasActivePlan = computed(() =>
  plans.value.some((plan) => plan.status === 'SCHEDULED' || plan.status === 'RUNNING'),
)

function messageOf(value: unknown): string {
  if (value instanceof ApiError) return value.message
  return '无法连接后端，请确认服务已启动（默认 127.0.0.1:8787）'
}

function planDot(status: PlanStatus): string {
  return status === 'COMPLETED'
    ? 'bg-success'
    : status === 'RUNNING'
      ? 'bg-accent'
      : 'bg-warning'
}

function runDot(status: PlanRunStatus | null | undefined): string {
  if (!status) return 'bg-neutral/30'
  return status === 'COMPLETED'
    ? 'bg-success'
    : status === 'RUNNING'
      ? 'bg-accent'
      : status === 'PARTIAL'
        ? 'bg-warning'
        : 'bg-danger'
}

function taskDot(status: PlanTaskStatus | null): string {
  if (!status) return 'bg-neutral/30'
  return ['SUCCEEDED'].includes(status)
    ? 'bg-success'
    : ['RUNNING', 'BLOCKED', 'PENDING'].includes(status)
      ? 'bg-accent'
      : status === 'CANCELLED' || status === 'SKIPPED'
        ? 'bg-neutral'
        : 'bg-danger'
}

async function loadDetail(id = selectedId.value) {
  if (id === null) {
    detail.value = null
    return
  }
  try {
    detail.value = await getPlan(id)
  } catch (value) {
    detail.value = null
    error.value = messageOf(value)
  }
}

async function load(preserveSelection = true) {
  if (loading.value) return
  loading.value = true
  error.value = null
  try {
    const loaded = await listPlans()
    plans.value = loaded
    const previous = preserveSelection ? selectedId.value : null
    const nextId =
      previous !== null && loaded.some((plan) => plan.id === previous)
        ? previous
        : loaded[0]?.id ?? null
    selectedId.value = nextId
    await loadDetail(nextId)
  } catch (value) {
    error.value = messageOf(value)
    plans.value = []
    detail.value = null
  } finally {
    loading.value = false
  }
}

async function select(plan: PlanSummary) {
  selectedId.value = plan.id
  await loadDetail(plan.id)
}

async function executeNow() {
  const plan = selected.value
  if (!plan || running.value) return
  running.value = true
  try {
    const run = await runPlan(plan.id)
    await load(true)
    toast.success(`计划「${plan.name}」已开始`, `本轮共 ${run.taskCount} 个任务`)
  } catch (value) {
    toast.warning('计划未能启动', messageOf(value))
  } finally {
    running.value = false
  }
}

async function onCreated(plan: PlanSummary) {
  await load(false)
  selectedId.value = plan.id
  await loadDetail(plan.id)
}

let refreshTimer: number | undefined

onMounted(() => {
  void load()
  refreshTimer = window.setInterval(() => {
    if (hasActivePlan.value) void load(true)
  }, 3000)
})

onUnmounted(() => window.clearInterval(refreshTimer))

const latestRun = computed<PlanRunSummary | null>(() => detail.value?.plan.latestRun ?? null)
</script>

<template>
  <div class="flex h-full min-h-0">
    <div class="flex min-w-0 flex-1 flex-col">
      <div class="flex h-toolbar shrink-0 items-center gap-3 border-b border-separator px-5">
        <h1 class="text-body font-emphasis text-label">计划</h1>
        <span class="tnum text-body text-label-tertiary">{{ plans.length }}</span>
        <div class="ml-auto flex items-center gap-2">
          <Tooltip content="刷新">
            <IconButton label="刷新计划" size="lg" :disabled="loading" @click="load(true)">
              <Icon :as="RefreshCw" size="large" />
            </IconButton>
          </Tooltip>
          <Button variant="primary" size="lg" @click="formOpen = true">
            <Icon :as="Plus" size="inline" />新建计划
          </Button>
        </div>
      </div>

      <div class="scroll-area min-h-0 flex-1 overflow-auto">
        <Table
          :columns="columns"
          :rows="plans"
          :selected-id="selectedId"
          :show-empty="false"
          @select="select"
        >
          <template #cell="{ row, column }">
            <StatusDot v-if="column.key === 'status'" :dot="planDot(row.status)">
              {{ planStatusLabel(row.status) }}
            </StatusDot>
            <span v-else-if="column.key === 'scheduledAt'" class="text-caption text-label-secondary">
              {{ formatDateTime(row.scheduledAt) }}
            </span>
            <span
              v-else-if="column.key === 'tasks'"
              class="tnum text-caption text-label-secondary"
            >
              {{ row.taskCount }}
            </span>
            <StatusDot
              v-else-if="column.key === 'result'"
              :dot="runDot(row.latestRun?.status)"
            >
              {{ row.latestRun ? planRunStatusLabel(row.latestRun.status) : '未执行' }}
            </StatusDot>
          </template>
        </Table>

        <EmptyState
          v-if="loading && plans.length === 0"
          title="正在加载…"
          description="从后端读取计划与执行结果"
        />
        <EmptyState v-else-if="error" title="加载失败" :description="error" />
        <EmptyState
          v-else-if="plans.length === 0"
          title="还没有计划"
          description="把待执行或排队中的任务组成一次定时批次，到点后自动执行并生成总结。"
        >
          <Button variant="primary" @click="formOpen = true">
            <Icon :as="Plus" size="inline" />新建计划
          </Button>
        </EmptyState>
      </div>
    </div>

    <aside
      :class="[
        'bg-window',
        selected
          ? 'fixed inset-0 z-40 w-full overflow-y-auto border-separator p-4 lg:static lg:inset-auto lg:z-auto lg:w-96 lg:shrink-0 lg:border-l'
          : 'hidden lg:block lg:w-96 lg:shrink-0 lg:overflow-y-auto lg:border-l lg:border-separator lg:p-4',
      ]"
    >
      <template v-if="selected && detail">
        <button
          type="button"
          class="mb-3 inline-flex items-center gap-0.5 text-caption text-accent lg:hidden"
          @click="selectedId = null; detail = null"
        >
          <Icon :as="ChevronLeft" size="inline" />返回列表
        </button>

        <div class="flex items-start gap-2">
          <div class="min-w-0 flex-1">
            <p class="text-caption text-label-tertiary">计划 #{{ selected.id }}</p>
            <h2 class="mt-1 text-body font-emphasis text-label">{{ selected.name }}</h2>
          </div>
          <StatusDot :dot="planDot(selected.status)">
            {{ planStatusLabel(selected.status) }}
          </StatusDot>
        </div>

        <dl class="mt-4 space-y-2">
          <div class="flex items-center gap-2">
            <dt class="text-caption text-label-secondary">定时执行</dt>
            <dd class="ml-auto text-caption text-label">{{ formatDateTime(selected.scheduledAt) }}</dd>
          </div>
          <div class="flex items-center gap-2">
            <dt class="text-caption text-label-secondary">纳管任务</dt>
            <dd class="tnum ml-auto text-caption text-label">{{ selected.taskCount }}</dd>
          </div>
          <div v-if="selected.startedAt" class="flex items-center gap-2">
            <dt class="text-caption text-label-secondary">开始时间</dt>
            <dd class="ml-auto text-caption text-label">{{ formatDateTime(selected.startedAt) }}</dd>
          </div>
          <div v-if="selected.finishedAt" class="flex items-center gap-2">
            <dt class="text-caption text-label-secondary">完成时间</dt>
            <dd class="ml-auto text-caption text-label">{{ formatDateTime(selected.finishedAt) }}</dd>
          </div>
        </dl>

        <Button
          v-if="selected.status === 'SCHEDULED'"
          class="mt-4"
          variant="primary"
          block
          :disabled="running"
          @click="executeNow"
        >
          <Icon :as="Play" size="inline" />{{ running ? '启动中…' : '立即执行' }}
        </Button>

        <div class="mt-5 border-t border-separator pt-3">
          <div class="flex items-center gap-2">
            <Icon :as="ListChecks" size="inline" class="text-label-secondary" />
            <p class="text-caption font-emphasis text-label-secondary">任务进度</p>
            <span v-if="latestRun" class="tnum ml-auto text-caption text-label-tertiary">
              {{ latestRun.progressCount }}/{{ latestRun.taskCount }}
            </span>
          </div>

          <ul class="mt-2 space-y-1">
            <li
              v-for="task in detail.tasks"
              :key="task.id"
              class="rounded-panel border border-separator-soft bg-content px-2.5 py-2"
            >
              <div class="flex items-center gap-2">
                <span class="tnum text-caption text-label-tertiary">#{{ task.id }}</span>
                <TaskStatusBadge v-if="task.taskStatus" :status="task.taskStatus" />
                <StatusDot
                  v-if="task.planTaskStatus"
                  class="ml-auto"
                  :dot="taskDot(task.planTaskStatus)"
                >
                  {{ planTaskStatusLabel(task.planTaskStatus) }}
                </StatusDot>
              </div>
              <p class="mt-1 truncate text-caption text-label">{{ task.title }}</p>
            </li>
          </ul>
        </div>

        <div class="mt-5 border-t border-separator pt-3">
          <div class="flex items-center gap-2">
            <Icon :as="CalendarClock" size="inline" class="text-label-secondary" />
            <p class="text-caption font-emphasis text-label-secondary">执行总结</p>
            <span v-if="latestRun" class="ml-auto text-caption text-label-tertiary">
              {{ planRunStatusLabel(latestRun.status) }}
            </span>
          </div>

          <template v-if="latestRun">
            <div class="mt-2 grid grid-cols-4 gap-1 text-center">
              <div class="rounded-control bg-success/10 px-1 py-1.5">
                <p class="tnum text-body text-success">{{ latestRun.succeededCount }}</p>
                <p class="text-[10px] text-label-tertiary">成功</p>
              </div>
              <div class="rounded-control bg-danger/10 px-1 py-1.5">
                <p class="tnum text-body text-danger">{{ latestRun.failedCount }}</p>
                <p class="text-[10px] text-label-tertiary">失败</p>
              </div>
              <div class="rounded-control bg-accent/10 px-1 py-1.5">
                <p class="tnum text-body text-accent">{{ latestRun.runningCount }}</p>
                <p class="text-[10px] text-label-tertiary">运行</p>
              </div>
              <div class="rounded-control bg-neutral/10 px-1 py-1.5">
                <p class="tnum text-body text-label-secondary">
                  {{ latestRun.skippedCount + latestRun.cancelledCount }}
                </p>
                <p class="text-[10px] text-label-tertiary">跳过</p>
              </div>
            </div>
            <p
              v-if="latestRun.summary"
              class="mt-3 whitespace-pre-wrap rounded-panel border border-separator-soft bg-content px-3 py-2 text-caption leading-relaxed text-label-secondary"
            >
              {{ latestRun.summary }}
            </p>
            <p v-else class="mt-3 text-caption text-label-tertiary">
              任务全部收场后生成执行总结。
              <template v-if="latestRun.blockedCount > 0">
                当前有 {{ latestRun.blockedCount }} 个任务在等待决策。
              </template>
            </p>
          </template>
          <p v-else class="mt-3 text-caption text-label-tertiary">
            计划尚未执行，到点后由平台自动启动。
          </p>
        </div>
      </template>

      <p v-else class="text-caption text-label-tertiary">选中一个计划查看任务与执行总结</p>
    </aside>
  </div>

  <PlanFormDialog v-model:open="formOpen" @created="onCreated" />
</template>
