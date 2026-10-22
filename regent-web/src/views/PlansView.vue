<script setup lang="ts">
import {
  ArrowUpRight,
  CalendarClock,
  ChevronLeft,
  ListChecks,
  Play,
  Plus,
  RefreshCw,
  Search,
  X,
} from '@lucide/vue'
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { ApiError } from '@/api/http'
import { getPlan, getPlanRun, listPlans, runPlan } from '@/api/plans'
import PlanFormDialog from '@/components/business/PlanFormDialog.vue'
import TaskStatusBadge from '@/components/business/TaskStatusBadge.vue'
import Button from '@/components/ui/button/Button.vue'
import CopyButton from '@/components/ui/copy-button/CopyButton.vue'
import EmptyState from '@/components/ui/empty-state/EmptyState.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import IconButton from '@/components/ui/icon-button/IconButton.vue'
import Input from '@/components/ui/input/Input.vue'
import ListToolbar from '@/components/ui/list-toolbar/ListToolbar.vue'
import StatusDot from '@/components/ui/status-dot/StatusDot.vue'
import Table from '@/components/ui/table/Table.vue'
import type { TableColumn } from '@/components/ui/table/table.types'
import Tooltip from '@/components/ui/tooltip/Tooltip.vue'
import { useToast } from '@/composables/useToast'
import type {
  PlanDetail,
  PlanRunDetail,
  PlanRunItem,
  PlanRunSummary,
  PlanSummary,
} from '@/types/api'
import {
  planRunStatusLabel,
  planStatusLabel,
  planTaskStatusLabel,
  type PlanRunStatus,
  type PlanStatus,
  type PlanTaskStatus,
} from '@/types/plan'
import { formatDateTime } from '@/utils/format'
import { orderPlanRunItems, planRunHeadline } from '@/utils/planRunSummary'

const plans = ref<PlanSummary[]>([])
const detail = ref<PlanDetail | null>(null)
const selectedId = ref<number | null>(null)
const loading = ref(false)
const running = ref(false)
const formOpen = ref(false)
const error = ref<string | null>(null)
const keyword = ref('')
const toast = useToast()
const router = useRouter()
const runDetail = ref<PlanRunDetail | null>(null)
const runDetailLoading = ref(false)
const runDetailError = ref<string | null>(null)
let runDetailSequence = 0

const columns: TableColumn[] = [
  // 编号只是定位符，并进计划名，把宽度留给名称本身
  { key: 'name', label: '计划' },
  { key: 'status', label: '状态', width: '88px' },
  { key: 'scheduledAt', label: '执行时间', width: '104px', hideBelow: 'xl' },
  { key: 'tasks', label: '任务', align: 'right', width: '56px' },
  { key: 'result', label: '本轮结果', width: '104px', hideBelow: 'xl' },
]

/** 表格里只保留「月/日 时:分」，完整到秒的时间放 title，详情面板仍显示完整值。 */
function formatSchedule(iso: string | null): string {
  const full = formatDateTime(iso)
  return full === '—' ? full : full.slice(5, 16)
}

const selected = computed(() => plans.value.find((plan) => plan.id === selectedId.value) ?? null)
const filteredPlans = computed(() => {
  const query = keyword.value.trim().toLocaleLowerCase('zh-CN')
  if (!query) return plans.value
  return plans.value.filter((plan) =>
    [
      plan.name,
      `#${plan.id}`,
      String(plan.id),
      planStatusLabel(plan.status),
      plan.latestRun ? planRunStatusLabel(plan.latestRun.status) : '',
    ].some((value) => value.toLocaleLowerCase('zh-CN').includes(query)),
  )
})
const planCountLabel = computed(() =>
  keyword.value.trim()
    ? `${filteredPlans.value.length} / ${plans.value.length}`
    : String(plans.value.length),
)
const hasActivePlan = computed(() =>
  plans.value.some((plan) => plan.status === 'SCHEDULED' || plan.status === 'RUNNING'),
)

watch(keyword, () => {
  if (selectedId.value !== null && !filteredPlans.value.some((plan) => plan.id === selectedId.value)) {
    selectedId.value = null
    detail.value = null
  }
})

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

const runItems = computed(() => orderPlanRunItems(runDetail.value?.items ?? []))
const attentionRunItems = computed(() =>
  runItems.value.filter((item) =>
    ['FAILED', 'BLOCKED', 'CANCELLED', 'SKIPPED'].includes(item.status),
  ),
)
const runProgress = computed(() => {
  const run = detail.value?.plan.latestRun
  if (!run || run.taskCount <= 0) return 0
  return Math.min(100, Math.round((run.progressCount / run.taskCount) * 100))
})
const runProgressClass = computed(() => {
  const status = detail.value?.plan.latestRun?.status
  if (status === 'COMPLETED') return 'bg-success'
  if (status === 'PARTIAL') return 'bg-warning'
  if (status === 'FAILED') return 'bg-danger'
  return 'bg-accent'
})

async function loadRunDetail(run: PlanRunSummary | null) {
  const sequence = ++runDetailSequence
  if (!run) {
    runDetail.value = null
    runDetailLoading.value = false
    runDetailError.value = null
    return
  }

  const switching = runDetail.value?.run.id !== run.id
  if (switching) {
    runDetail.value = null
    runDetailLoading.value = true
  }
  runDetailError.value = null
  try {
    const loaded = await getPlanRun(run.id)
    if (sequence === runDetailSequence) runDetail.value = loaded
  } catch (value) {
    if (sequence === runDetailSequence) runDetailError.value = messageOf(value)
  } finally {
    if (sequence === runDetailSequence) runDetailLoading.value = false
  }
}

function retryRunDetail() {
  const run = detail.value?.plan.latestRun
  if (run) void loadRunDetail(run)
}

function openTask(item: PlanRunItem) {
  void router.push({ path: '/tasks', query: { taskId: String(item.taskId) } })
}

async function loadDetail(id = selectedId.value) {
  if (id === null) {
    detail.value = null
    void loadRunDetail(null)
    return
  }
  try {
    const loaded = await getPlan(id)
    if (selectedId.value !== id) return
    detail.value = loaded
    void loadRunDetail(loaded.plan.latestRun)
  } catch (value) {
    detail.value = null
    void loadRunDetail(null)
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
    const previousStillExists =
      previous !== null && loaded.some((plan) => plan.id === previous)
    // 手机端选中即整屏详情，落地页应该是列表本身；桌面才自动选中第一条，避免右栏空着
    const compact = window.matchMedia('(max-width: 1023px)').matches
    const nextId = previousStillExists ? previous : compact ? null : loaded[0]?.id ?? null
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
  keyword.value = ''
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
      <ListToolbar>
        <template #title>
          <h1 class="text-body font-emphasis text-label">计划</h1>
          <span class="tnum text-body text-label-tertiary">{{ planCountLabel }}</span>
        </template>
        <template #filters>
          <div class="relative min-w-0 flex-1 sm:w-52 sm:flex-none">
            <Icon
              :as="Search"
              size="inline"
              class="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-label-tertiary"
            />
            <Input
              v-model="keyword"
              size="lg"
              placeholder="搜索计划名称、编号或状态"
              aria-label="搜索计划"
              class="pl-7 pr-7"
            />
            <button
              v-if="keyword"
              type="button"
              class="absolute right-1 top-1/2 flex size-5 -translate-y-1/2 items-center justify-center rounded-control text-label-tertiary hover:bg-hover hover:text-label"
              aria-label="清空计划搜索"
              @click="keyword = ''"
            >
              <Icon :as="X" size="inline" />
            </button>
          </div>
        </template>
        <template #actions>
          <Tooltip content="刷新">
            <IconButton label="刷新计划" size="lg" :disabled="loading" @click="load(true)">
              <Icon :as="RefreshCw" size="large" />
            </IconButton>
          </Tooltip>
          <Button variant="primary" size="lg" @click="formOpen = true">
            <Icon :as="Plus" size="inline" />新建计划
          </Button>
        </template>
      </ListToolbar>

      <div class="scroll-area min-h-0 flex-1 overflow-auto">
        <Table
          :columns="columns"
          :rows="filteredPlans"
          :selected-id="selectedId"
          :show-empty="false"
          touch-rows
          @select="select"
        >
          <template #cell="{ row, column }">
            <span v-if="column.key === 'name'" class="flex min-w-0 items-center gap-2">
              <span class="tnum shrink-0 text-caption text-label-tertiary">#{{ row.id }}</span>
              <span class="min-w-0 flex-1 truncate font-emphasis text-label">{{ row.name }}</span>
              <CopyButton
                :text="row.name"
                label="复制计划名称"
                success-message="计划名称已复制"
              />
            </span>
            <StatusDot v-else-if="column.key === 'status'" :dot="planDot(row.status)">
              {{ planStatusLabel(row.status) }}
            </StatusDot>
            <span
              v-else-if="column.key === 'scheduledAt'"
              class="tnum text-caption text-label-secondary"
              :title="formatDateTime(row.scheduledAt)"
            >
              {{ formatSchedule(row.scheduledAt) }}
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
        />
        <EmptyState
          v-else-if="filteredPlans.length === 0"
          title="没有匹配的计划"
          description="换个名称、编号或状态关键词再试。"
        >
          <Button @click="keyword = ''">清除搜索</Button>
        </EmptyState>
      </div>
    </div>

    <aside
      :class="[
        'bg-window',
        selected
          ? 'fixed inset-0 z-40 w-full overflow-y-auto border-separator p-4 lg:static lg:inset-auto lg:z-auto lg:w-80 lg:shrink-0 lg:border-l'
          : 'hidden lg:block lg:w-80 lg:shrink-0 lg:overflow-y-auto lg:border-l lg:border-separator lg:p-4',
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
          <div class="flex min-w-0 flex-1 items-start gap-1">
            <div class="min-w-0 flex-1">
              <p class="text-caption text-label-tertiary">计划 #{{ selected.id }}</p>
              <h2 class="mt-1 text-body font-emphasis text-label">{{ selected.name }}</h2>
            </div>
            <CopyButton
              :text="selected.name"
              label="复制计划名称"
              success-message="计划名称已复制"
            />
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
            <StatusDot
              v-if="latestRun"
              class="ml-auto"
              :dot="runDot(latestRun.status)"
            >
              {{ planRunStatusLabel(latestRun.status) }}
            </StatusDot>
          </div>

          <template v-if="latestRun">
            <div class="mt-2 rounded-panel border border-separator-soft bg-content p-3">
              <p class="text-body font-emphasis leading-snug text-label">
                {{ planRunHeadline(latestRun) }}
              </p>
              <p class="mt-1 text-caption text-label-tertiary">
                {{ latestRun.progressCount }}/{{ latestRun.taskCount }} 个任务已收场 ·
                {{ latestRun.trigger === 'MANUAL' ? '手动执行' : '定时执行' }}
              </p>

              <div class="mt-3 h-1 overflow-hidden rounded-full bg-neutral/10">
                <div
                  class="h-full rounded-full transition-[width,background-color] duration-300"
                  :class="runProgressClass"
                  :style="{ width: `${runProgress}%` }"
                />
              </div>

              <div class="mt-3 grid grid-cols-4 gap-1.5 text-center">
                <div class="rounded-control border border-success/15 bg-success/10 px-1 py-1.5">
                  <p class="tnum text-body font-emphasis text-success">
                    {{ latestRun.succeededCount }}
                  </p>
                  <p class="text-[10px] leading-tight text-label-tertiary">成功</p>
                </div>
                <div class="rounded-control border border-danger/15 bg-danger/10 px-1 py-1.5">
                  <p class="tnum text-body font-emphasis text-danger">
                    {{ latestRun.failedCount }}
                  </p>
                  <p class="text-[10px] leading-tight text-label-tertiary">失败</p>
                </div>
                <div class="rounded-control border border-accent/15 bg-accent/10 px-1 py-1.5">
                  <p class="tnum text-body font-emphasis text-accent">
                    {{ latestRun.runningCount }}
                  </p>
                  <p class="text-[10px] leading-tight text-label-tertiary">进行中</p>
                </div>
                <div class="rounded-control border border-separator-soft bg-window px-1 py-1.5">
                  <p class="tnum text-body font-emphasis text-label-secondary">
                    {{ latestRun.cancelledCount + latestRun.skippedCount }}
                  </p>
                  <p class="text-[10px] leading-tight text-label-tertiary">终止/跳过</p>
                </div>
              </div>

              <p
                v-if="latestRun.blockedCount > 0"
                class="mt-2 flex items-center gap-1.5 text-caption text-warning"
              >
                <span class="size-1.5 shrink-0 rounded-full bg-warning" />
                {{ latestRun.blockedCount }} 个任务等待决策
              </p>
            </div>

            <div class="mt-3 flex items-center gap-2">
              <p class="text-caption font-emphasis text-label-secondary">需要关注</p>
              <span
                v-if="attentionRunItems.length > 0"
                class="tnum ml-auto text-caption text-label-tertiary"
              >
                {{ attentionRunItems.length }}
              </span>
            </div>

            <p v-if="runDetailLoading" class="mt-2 text-caption text-label-tertiary">
              正在读取任务结果…
            </p>
            <div
              v-else-if="runDetailError"
              class="mt-2 rounded-panel border border-danger/15 bg-danger/5 px-2.5 py-2"
            >
              <p class="text-caption font-emphasis text-danger">任务结果读取失败</p>
              <p class="mt-0.5 line-clamp-2 text-caption text-label-tertiary">
                {{ runDetailError }}
              </p>
              <button
                type="button"
                class="mt-1 text-caption font-emphasis text-accent hover:underline"
                @click="retryRunDetail"
              >
                重新读取
              </button>
            </div>
            <p
              v-else-if="attentionRunItems.length === 0"
              class="mt-2 flex items-center gap-1.5 text-caption text-success"
            >
              <span class="size-1.5 shrink-0 rounded-full bg-success" />
              没有失败、终止或等待决策的任务。
            </p>
            <ol v-else class="scroll-area mt-2 max-h-80 space-y-1.5 overflow-y-auto pr-0.5">
              <li v-for="item in attentionRunItems" :key="item.taskId">
                <button
                  type="button"
                  class="group w-full rounded-panel border border-separator-soft bg-content px-2.5 py-2 text-left transition-colors duration-100 ease-apple hover:border-accent/30 hover:bg-hover"
                  :title="`查看任务 #${item.taskId}`"
                  @click="openTask(item)"
                >
                  <span class="flex items-center gap-2">
                    <span class="tnum text-caption text-label-tertiary">#{{ item.taskId }}</span>
                    <StatusDot :dot="taskDot(item.status)" class="ml-auto">
                      {{ planTaskStatusLabel(item.status) }}
                    </StatusDot>
                    <Icon
                      :as="ArrowUpRight"
                      size="inline"
                      class="shrink-0 text-label-tertiary opacity-50 transition-opacity group-hover:text-accent group-hover:opacity-100"
                    />
                  </span>
                  <span class="mt-1 line-clamp-2 text-caption font-emphasis leading-snug text-label">
                    {{ item.title }}
                  </span>
                  <span
                    v-if="item.detail"
                    class="mt-1 line-clamp-2 text-caption leading-relaxed text-label-tertiary"
                  >
                    {{ item.detail }}
                  </span>
                </button>
              </li>
            </ol>

            <details
              v-if="latestRun.summary && (runDetailError || runItems.length === 0)"
              class="mt-3"
            >
              <summary class="cursor-pointer text-caption text-label-tertiary hover:text-label">
                查看原始执行总结
              </summary>
              <p
                class="mt-2 whitespace-pre-wrap rounded-control border border-separator-soft bg-content px-2.5 py-2 text-caption leading-relaxed text-label-tertiary"
              >
                {{ latestRun.summary }}
              </p>
            </details>
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
