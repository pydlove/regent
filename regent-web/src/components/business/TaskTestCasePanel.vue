<script setup lang="ts">
import { Eye, FlaskConical, Pencil } from '@lucide/vue'
import { computed, onUnmounted, ref, watch } from 'vue'

import { ApiError } from '@/api/http'
import {
  listTaskTestCaseRuns,
  listTaskTestCases,
} from '@/api/testCases'
import TaskTestCaseDialog from '@/components/business/TaskTestCaseDialog.vue'
import TestCaseRunsDialog from '@/components/business/TestCaseRunsDialog.vue'
import TestCaseRunStatusBadge from '@/components/business/TestCaseRunStatusBadge.vue'
import Badge from '@/components/ui/badge/Badge.vue'
import Button from '@/components/ui/button/Button.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import IconButton from '@/components/ui/icon-button/IconButton.vue'
import Tooltip from '@/components/ui/tooltip/Tooltip.vue'
import type {
  TaskTestCaseBinding,
  TestCaseRunSummary,
} from '@/types/api'
import type { TaskStatus } from '@/types/task'

const props = defineProps<{
  taskId: number
  projectId: number
  status: TaskStatus
}>()

const bindings = ref<TaskTestCaseBinding[]>([])
const runs = ref<TestCaseRunSummary[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const dialogOpen = ref(false)
const runsOpen = ref(false)
let timer: number | undefined

const editable = computed(
  () =>
    props.status === 'PENDING' ||
    props.status === 'QUEUED' ||
    props.status === 'FAILED' ||
    props.status === 'CANCELLED',
)
const latestByCase = computed(() => {
  const map = new Map<number, TestCaseRunSummary>()
  for (const run of runs.value) {
    if (!map.has(run.testCaseId)) map.set(run.testCaseId, run)
  }
  return map
})
const hasActiveRuns = computed(() =>
  runs.value.some((run) => run.status === 'QUEUED' || run.status === 'RUNNING'),
)
const awaitingRuns = computed(
  () => props.status === 'DONE' && bindings.value.length > 0 && runs.value.length === 0,
)
const shouldPoll = computed(() => hasActiveRuns.value || awaitingRuns.value)
const summaryText = computed(() => {
  if (hasActiveRuns.value) return '测试执行中，结果会自动刷新'
  if (runs.value.some((run) => run.status === 'FAILED' || run.status === 'TIMEOUT')) {
    return '有测试未通过，任务状态不受影响'
  }
  if (runs.value.length > 0) return '最近一轮测试已完成'
  if (bindings.value.length > 0 && props.status === 'DONE') return '测试即将开始'
  return ''
})

function messageOf(e: unknown): string {
  if (e instanceof ApiError) return e.message
  return '无法连接后端，请确认服务已启动（默认 127.0.0.1:8787）'
}

const stopTimer = () => {
  window.clearInterval(timer)
  timer = undefined
}

const scheduleRefresh = () => {
  stopTimer()
  if (shouldPoll.value) {
    timer = window.setInterval(() => void refreshRuns(), 3000)
  }
}

const refreshRuns = async () => {
  try {
    runs.value = await listTaskTestCaseRuns(props.taskId)
    scheduleRefresh()
  } catch {
    // 测试结果是附加信息，轮询失败保持上一次结果
  }
}

const load = async () => {
  loading.value = true
  error.value = null
  try {
    const [nextBindings, nextRuns] = await Promise.all([
      listTaskTestCases(props.taskId),
      listTaskTestCaseRuns(props.taskId),
    ])
    bindings.value = nextBindings
    runs.value = nextRuns
    scheduleRefresh()
  } catch (e) {
    error.value = messageOf(e)
    bindings.value = []
    runs.value = []
    stopTimer()
  } finally {
    loading.value = false
  }
}

watch(
  () => props.taskId,
  () => {
    stopTimer()
    bindings.value = []
    runs.value = []
    void load()
  },
  { immediate: true },
)

watch(
  () => props.status,
  (status, previous) => {
    if (status === 'DONE' && previous !== 'DONE' && bindings.value.length > 0) {
      void refreshRuns()
    }
  },
)

watch(shouldPoll, () => scheduleRefresh())

onUnmounted(stopTimer)

const onSaved = async (next: TaskTestCaseBinding[]) => {
  bindings.value = next
  await refreshRuns()
}
</script>

<template>
  <div class="mt-4 border-t border-separator pt-3">
    <div class="flex items-center gap-2">
      <p class="text-caption font-emphasis text-label-secondary">测试用例</p>
      <span v-if="bindings.length > 0" class="tnum text-caption text-label-tertiary">
        {{ bindings.length }}
      </span>
      <Tooltip :content="editable ? '选择任务完成后要跑的测试' : '任务已开始执行，不能改绑'">
        <IconButton
          v-if="editable || bindings.length > 0"
          :label="editable ? '管理任务测试用例' : '查看任务测试用例'"
          size="sm"
          class="ml-auto"
          @click="dialogOpen = true"
        >
          <Icon :as="editable ? Pencil : Eye" size="regular" />
        </IconButton>
      </Tooltip>
    </div>

    <p v-if="loading" class="mt-2 text-caption text-label-tertiary">正在读取测试用例…</p>
    <p v-else-if="error" class="mt-2 text-caption text-danger">{{ error }}</p>
    <p v-else-if="bindings.length === 0" class="mt-2 text-caption text-label-tertiary">
      还没有绑定。任务成功完成后，绑定的 Web / 接口测试会依次执行并留下记录。
    </p>
    <template v-else>
      <ul class="mt-2 space-y-1">
        <li
          v-for="binding in bindings"
          :key="binding.testCaseId"
          class="flex items-center gap-2 rounded-panel border border-separator-soft bg-content px-2.5 py-1.5"
        >
          <span
            class="grid size-6 shrink-0 place-items-center rounded-control"
            :class="binding.type === 'WEB' ? 'bg-accent/10 text-accent' : 'bg-neutral/10 text-label-secondary'"
          >
            <Icon :as="FlaskConical" size="inline" />
          </span>
          <span class="min-w-0 flex-1 truncate text-caption text-label" :title="binding.name">
            {{ binding.name }}
          </span>
          <Badge :tone="binding.type === 'WEB' ? 'accent' : 'neutral'">
            {{ binding.type === 'WEB' ? 'Web' : '接口' }}
          </Badge>
          <TestCaseRunStatusBadge
            v-if="latestByCase.get(binding.testCaseId)"
            :status="latestByCase.get(binding.testCaseId)!.status"
          />
          <span v-else-if="!binding.enabled" class="text-caption text-label-tertiary">已停用</span>
          <span v-else class="text-caption text-label-tertiary">
            {{ status === 'DONE' ? '等待执行' : '完成后运行' }}
          </span>
        </li>
      </ul>
      <p v-if="summaryText" class="mt-1.5 text-caption text-label-tertiary">{{ summaryText }}</p>
      <Button v-if="runs.length > 0" size="sm" class="mt-2" @click="runsOpen = true">
        查看测试结果
      </Button>
    </template>

    <TaskTestCaseDialog
      v-model:open="dialogOpen"
      :task-id="taskId"
      :project-id="projectId"
      :editable="editable"
      @saved="onSaved"
    />
    <TestCaseRunsDialog v-model:open="runsOpen" :runs="runs" title="任务测试结果" />
  </div>
</template>
