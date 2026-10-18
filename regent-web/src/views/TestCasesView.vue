<script setup lang="ts">
import {
  FlaskConical,
  Pencil,
  Play,
  Plus,
  RefreshCw,
  ScrollText,
  Search,
  Trash2,
  X,
} from '@lucide/vue'
import { computed, onUnmounted, ref, watch } from 'vue'

import { ApiError } from '@/api/http'
import {
  deleteTestCase,
  listTestCaseRuns,
  listTestCases,
  runTestCase,
  runTestCases,
  setTestCaseEnabled,
} from '@/api/testCases'
import TestCaseDialog from '@/components/business/TestCaseDialog.vue'
import TestCaseRunsDialog from '@/components/business/TestCaseRunsDialog.vue'
import TestCaseRunStatusBadge from '@/components/business/TestCaseRunStatusBadge.vue'
import Badge from '@/components/ui/badge/Badge.vue'
import Button from '@/components/ui/button/Button.vue'
import Checkbox from '@/components/ui/checkbox/Checkbox.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import EmptyState from '@/components/ui/empty-state/EmptyState.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import IconButton from '@/components/ui/icon-button/IconButton.vue'
import Input from '@/components/ui/input/Input.vue'
import ListToolbar from '@/components/ui/list-toolbar/ListToolbar.vue'
import SegmentedControl from '@/components/ui/segmented-control/SegmentedControl.vue'
import Select from '@/components/ui/select/Select.vue'
import Switch from '@/components/ui/switch/Switch.vue'
import Tooltip from '@/components/ui/tooltip/Tooltip.vue'
import { useToast } from '@/composables/useToast'
import { useTaskStore } from '@/stores/useTaskStore'
import type { TestCaseRunSummary, TestCaseSummary } from '@/types/api'
import { formatDateTime } from '@/utils/format'

type EnabledFilter = 'all' | 'enabled' | 'disabled'

const store = useTaskStore()
const toast = useToast()
const testCases = ref<TestCaseSummary[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const keyword = ref('')
const typeFilter = ref('ALL')
const enabledFilter = ref<EnabledFilter>('all')
const toggling = ref(new Set<number>())
const selectedIds = ref<Set<number>>(new Set())
const executingIds = ref(new Set<number>())

const dialogOpen = ref(false)
const editing = ref<TestCaseSummary | null>(null)

const runsOpen = ref(false)
const runsLoading = ref(false)
const runsError = ref<string | null>(null)
const runs = ref<TestCaseRunSummary[]>([])
const runsTarget = ref<TestCaseSummary | null>(null)

const deleteOpen = ref(false)
const deleting = ref(false)
const deleteTarget = ref<TestCaseSummary | null>(null)

const typeItems = [
  { value: 'ALL', label: '全部类型' },
  { value: 'WEB', label: 'Web 测试' },
  { value: 'API', label: '接口测试' },
]
const enabledItems = computed(() => [
  { value: 'all', label: `全部 ${testCases.value.length}` },
  {
    value: 'enabled',
    label: `启用 ${testCases.value.filter((testCase) => testCase.enabled).length}`,
  },
  {
    value: 'disabled',
    label: `停用 ${testCases.value.filter((testCase) => !testCase.enabled).length}`,
  },
])
const enabledModel = computed({
  get: () => enabledFilter.value,
  set: (value: string) => {
    enabledFilter.value = value as EnabledFilter
  },
})
const filtered = computed(() => {
  const search = keyword.value.trim().toLowerCase()
  return testCases.value.filter((testCase) => {
    if (typeFilter.value !== 'ALL' && testCase.type !== typeFilter.value) return false
    if (enabledFilter.value === 'enabled' && !testCase.enabled) return false
    if (enabledFilter.value === 'disabled' && testCase.enabled) return false
    if (!search) return true
    return [
      testCase.name,
      testCase.projectName,
      testCase.description ?? '',
      testCase.command ?? '',
      testCase.scriptFilename ?? '',
    ].some((value) => value.toLowerCase().includes(search))
  })
})
const isRunActive = (testCase: TestCaseSummary) =>
  testCase.latestRunStatus === 'QUEUED' || testCase.latestRunStatus === 'RUNNING'
const canSelect = (testCase: TestCaseSummary) => testCase.enabled && !isRunActive(testCase)
const selectableCases = computed(() => filtered.value.filter(canSelect))
const selectedSet = computed(() => selectedIds.value)
const selectedCount = computed(() => selectedIds.value.size)
const selectionState = computed<boolean | 'indeterminate'>(() => {
  if (selectedCount.value === 0 || selectableCases.value.length === 0) return false
  return selectableCases.value.every((testCase) => selectedSet.value.has(testCase.id))
    ? true
    : 'indeterminate'
})
const hasActiveRuns = computed(
  () =>
    executingIds.value.size > 0 ||
    testCases.value.some(
      (testCase) =>
        testCase.latestRunStatus === 'QUEUED' || testCase.latestRunStatus === 'RUNNING',
    ),
)
let pollTimer: number | undefined
function messageOf(e: unknown): string {
  if (e instanceof ApiError) return e.message
  return '无法连接后端，请确认服务已启动（默认 127.0.0.1:8787）'
}

const load = async (quiet = false) => {
  if (!quiet) {
    loading.value = true
    error.value = null
  }
  try {
    testCases.value = await listTestCases(store.projectId)
  } catch (e) {
    if (!quiet) {
      error.value = messageOf(e)
      testCases.value = []
    }
  } finally {
    if (!quiet) loading.value = false
  }
}

watch(
  () => store.projectId,
  () => {
    selectedIds.value = new Set()
    void load()
  },
  { immediate: true },
)

watch([keyword, typeFilter, enabledFilter], () => {
  selectedIds.value = new Set()
})

const syncPollTimer = () => {
  window.clearInterval(pollTimer)
  pollTimer = undefined
  if (hasActiveRuns.value) {
    pollTimer = window.setInterval(() => void load(true), 2000)
  }
}

watch(hasActiveRuns, syncPollTimer, { immediate: true })
onUnmounted(() => window.clearInterval(pollTimer))

const openCreate = () => {
  editing.value = null
  dialogOpen.value = true
}

const openEdit = (testCase: TestCaseSummary) => {
  editing.value = testCase
  dialogOpen.value = true
}

const toggle = async (testCase: TestCaseSummary, enabled: boolean) => {
  if (toggling.value.has(testCase.id)) return
  toggling.value = new Set(toggling.value).add(testCase.id)
  try {
    const updated = await setTestCaseEnabled(testCase.id, enabled)
    testCases.value = testCases.value.map((item) =>
      item.id === updated.id ? { ...item, ...updated } : item,
    )
    if (!enabled) {
      const nextSelected = new Set(selectedIds.value)
      nextSelected.delete(testCase.id)
      selectedIds.value = nextSelected
    }
    toast.success(
      `测试用例「${testCase.name}」已${enabled ? '启用' : '停用'}`,
      enabled ? '可以绑定到任务' : '已绑定任务执行时会跳过它',
    )
  } catch (e) {
    toast.failure('测试用例状态修改失败', messageOf(e))
  } finally {
    const next = new Set(toggling.value)
    next.delete(testCase.id)
    toggling.value = next
  }
}

const updateLatestRun = (run: TestCaseRunSummary) => {
  testCases.value = testCases.value.map((testCase) =>
    testCase.id === run.testCaseId
      ? {
          ...testCase,
          latestRunStatus: run.status,
          latestRunAt: run.queuedAt,
        }
      : testCase,
  )
}

const setExecuting = (ids: number[], executing: boolean) => {
  const next = new Set(executingIds.value)
  for (const id of ids) {
    if (executing) next.add(id)
    else next.delete(id)
  }
  executingIds.value = next
}

const executeOne = async (testCase: TestCaseSummary) => {
  if (!testCase.enabled || executingIds.value.has(testCase.id)) return
  setExecuting([testCase.id], true)
  try {
    const run = await runTestCase(testCase.id)
    updateLatestRun(run)
    const nextSelected = new Set(selectedIds.value)
    nextSelected.delete(testCase.id)
    selectedIds.value = nextSelected
    toast.success(`测试用例「${testCase.name}」已开始执行`, '结果会在执行记录中持续更新')
  } catch (e) {
    toast.failure(`测试用例「${testCase.name}」未能执行`, messageOf(e))
  } finally {
    setExecuting([testCase.id], false)
  }
}

const toggleSelection = (testCase: TestCaseSummary, checked: boolean) => {
  if (!canSelect(testCase)) return
  const next = new Set(selectedIds.value)
  if (checked) next.add(testCase.id)
  else next.delete(testCase.id)
  selectedIds.value = next
}

const toggleAll = (checked: boolean | 'indeterminate') => {
  selectedIds.value =
    checked === true
      ? new Set(selectableCases.value.map((testCase) => testCase.id))
      : new Set()
}

const executeSelected = async () => {
  const ids = [...selectedIds.value]
  if (ids.length === 0) return
  setExecuting(ids, true)
  try {
    const result = await runTestCases(ids)
    for (const run of result.runs) updateLatestRun(run)
    const failedIds = new Set(result.failures.map((failure) => failure.testCaseId))
    selectedIds.value = new Set(ids.filter((id) => failedIds.has(id)))
    if (result.failures.length > 0) {
      toast.warning(
        `已提交 ${result.runs.length} 个，${result.failures.length} 个未执行`,
        result.failures
          .slice(0, 2)
          .map((failure) => `#${failure.testCaseId}：${failure.message}`)
          .join('；'),
      )
    } else {
      toast.success(`已提交 ${result.runs.length} 个测试用例`, '批量执行结果会在列表中自动更新')
    }
  } catch (e) {
    toast.failure('批量执行测试用例失败', messageOf(e))
  } finally {
    setExecuting(ids, false)
  }
}


const openRuns = async (testCase: TestCaseSummary) => {
  runsTarget.value = testCase
  runs.value = []
  runsError.value = null
  runsOpen.value = true
  runsLoading.value = true
  try {
    runs.value = await listTestCaseRuns(testCase.id)
  } catch (e) {
    runsError.value = messageOf(e)
  } finally {
    runsLoading.value = false
  }
}

const requestDelete = (testCase: TestCaseSummary) => {
  deleteTarget.value = testCase
  deleteOpen.value = true
}

const confirmDelete = async () => {
  const target = deleteTarget.value
  if (!target || deleting.value) return
  deleting.value = true
  try {
    await deleteTestCase(target.id)
    deleteOpen.value = false
    toast.success(`测试用例「${target.name}」已删除`, '任务历史中的执行记录仍会保留')
    await load()
  } catch (e) {
    toast.failure('测试用例删除失败', messageOf(e))
  } finally {
    deleting.value = false
    deleteTarget.value = null
  }
}

const clearFilters = () => {
  keyword.value = ''
  typeFilter.value = 'ALL'
  enabledFilter.value = 'all'
}
</script>

<template>
  <div class="flex h-full min-h-0 flex-col">
    <ListToolbar>
      <template #title>
        <h1 class="text-body font-emphasis text-label">测试用例</h1>
        <span class="tnum text-caption text-label-tertiary">
          {{ testCases.filter((item) => item.enabled).length }} 启用 / {{ testCases.length }} 全部
        </span>
      </template>
      <template #actions>
        <Button
          v-if="selectedCount > 0"
          size="lg"
          :disabled="executingIds.size > 0"
          @click="executeSelected"
        >
          <Icon :as="Play" size="inline" />执行选中 {{ selectedCount }} 个
        </Button>
        <Tooltip content="刷新">
          <IconButton label="刷新测试用例" size="lg" :disabled="loading" @click="load()">
            <Icon :as="RefreshCw" size="large" />
          </IconButton>
        </Tooltip>
        <Button variant="primary" size="lg" @click="openCreate">
          <Icon :as="Plus" size="inline" />新建测试用例
        </Button>
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
            placeholder="搜索名称、说明或文件"
            aria-label="搜索测试用例"
            class="pl-7 pr-7"
          />
          <button
            v-if="keyword"
            type="button"
            class="absolute right-1 top-1/2 flex size-5 -translate-y-1/2 items-center justify-center rounded-control text-label-tertiary hover:bg-hover hover:text-label"
            aria-label="清空测试用例搜索"
            @click="keyword = ''"
          >
            <Icon :as="X" size="inline" />
          </button>
        </div>
        <Select v-model="typeFilter" :items="typeItems" size="lg" class="w-28" />
        <SegmentedControl v-model="enabledModel" :items="enabledItems" size="lg" />
      </template>
    </ListToolbar>

    <div class="scroll-area min-h-0 flex-1 overflow-auto">
      <div
        v-if="!error && filtered.length > 0"
        class="mx-4 my-4 overflow-hidden rounded-panel border border-separator-soft bg-content shadow-panel"
      >
        <div
          class="grid h-8 select-none grid-cols-[24px_minmax(0,1fr)_72px_92px] items-center gap-3 border-b border-separator bg-sidebar px-3 text-caption font-emphasis text-label-secondary md:grid-cols-[24px_minmax(0,1fr)_90px_120px_150px_72px_92px] xl:grid-cols-[24px_minmax(0,1fr)_130px_100px_180px_150px_150px_72px_92px]"
        >
          <Checkbox
            :model-value="selectionState"
            :disabled="selectableCases.length === 0"
            aria-label="选择当前页全部可执行测试用例"
            @update:model-value="toggleAll"
          />
          <span>用例</span>
          <span class="hidden md:block">项目</span>
          <span class="hidden md:block">类型</span>
          <span class="hidden xl:block">执行内容</span>
          <span class="hidden md:block">最近结果</span>
          <span class="hidden xl:block">更新时间</span>
          <span>启用</span>
          <span />
        </div>

        <div
          v-for="testCase in filtered"
          :key="testCase.id"
          class="group grid min-h-16 grid-cols-[24px_minmax(0,1fr)_72px_92px] items-center gap-3 border-b border-separator-soft px-3 py-2 transition-colors duration-100 ease-apple last:border-b-0 hover:bg-hover md:grid-cols-[24px_minmax(0,1fr)_90px_120px_150px_72px_92px] xl:grid-cols-[24px_minmax(0,1fr)_130px_100px_180px_150px_150px_72px_92px]"
          @dblclick="openEdit(testCase)"
        >
          <Checkbox
            :model-value="selectedSet.has(testCase.id)"
            :disabled="!canSelect(testCase)"
            :aria-label="`选择测试用例 ${testCase.name}`"
            @update:model-value="toggleSelection(testCase, $event === true)"
          />
          <div class="flex min-w-0 items-center gap-2.5">
            <span
              class="flex size-8 shrink-0 items-center justify-center rounded-panel"
              :class="testCase.enabled ? 'bg-accent/10 text-accent' : 'bg-neutral/10 text-label-tertiary'"
            >
              <Icon :as="FlaskConical" size="regular" />
            </span>
            <div class="min-w-0">
              <p class="truncate text-body font-emphasis text-label">{{ testCase.name }}</p>
              <p class="mt-0.5 truncate text-caption text-label-secondary">
                {{ testCase.description || testCase.scriptFilename || testCase.command }}
              </p>
            </div>
          </div>

          <span
            class="hidden truncate text-caption text-label-secondary md:block"
            :title="testCase.projectName"
          >
            {{ testCase.projectName }}
          </span>
          <Badge
            class="hidden md:inline-flex"
            :tone="testCase.type === 'WEB' ? 'accent' : 'neutral'"
          >
            {{ testCase.type === 'WEB' ? 'Web' : '接口' }}
          </Badge>
          <div class="hidden min-w-0 xl:block">
            <p class="truncate font-mono text-caption text-label-secondary" :title="testCase.command ?? ''">
              {{ testCase.command || '使用默认执行方式' }}
            </p>
            <p v-if="testCase.scriptFilename" class="mt-0.5 truncate text-caption text-label-tertiary">
              {{ testCase.scriptFilename }}
            </p>
          </div>
          <div class="hidden md:block">
            <TestCaseRunStatusBadge
              v-if="testCase.latestRunStatus"
              :status="testCase.latestRunStatus"
            />
            <span v-else class="text-caption text-label-tertiary">未运行</span>
          </div>
          <span class="tnum hidden text-caption text-label-secondary xl:block">
            {{ formatDateTime(testCase.updatedAt) }}
          </span>
          <Switch
            :model-value="testCase.enabled"
            :disabled="toggling.has(testCase.id)"
            :aria-label="`切换测试用例 ${testCase.name} 状态`"
            @update:model-value="toggle(testCase, $event === true)"
          />
          <div class="flex justify-end gap-0.5">
            <Tooltip :content="!testCase.enabled ? '用例已停用' : isRunActive(testCase) ? '正在执行' : '立即执行'">
              <span class="inline-flex">
                <IconButton
                  label="立即执行测试用例"
                  size="sm"
                  :disabled="!testCase.enabled || isRunActive(testCase) || executingIds.has(testCase.id)"
                  @click.stop="executeOne(testCase)"
                >
                  <Icon :as="Play" size="inline" />
                </IconButton>
              </span>
            </Tooltip>
            <Tooltip content="查看执行记录">
              <IconButton label="查看测试执行记录" size="sm" @click.stop="openRuns(testCase)">
                <Icon :as="ScrollText" size="inline" />
              </IconButton>
            </Tooltip>
            <Tooltip content="编辑">
              <IconButton label="编辑测试用例" size="sm" @click.stop="openEdit(testCase)">
                <Icon :as="Pencil" size="inline" />
              </IconButton>
            </Tooltip>
            <Tooltip content="删除">
              <IconButton
                label="删除测试用例"
                size="sm"
                variant="ghost"
                @click.stop="requestDelete(testCase)"
              >
                <Icon :as="Trash2" size="inline" />
              </IconButton>
            </Tooltip>
          </div>
        </div>
      </div>

      <EmptyState
        v-if="loading && testCases.length === 0"
        title="正在加载…"
        description="从后端读取测试用例"
      />
      <EmptyState v-else-if="error" title="加载失败" :description="error" />
      <EmptyState
        v-else-if="testCases.length === 0"
        title="还没有测试用例"
        description="上传 Web 或接口测试脚本，可以立即执行，也可以在任务成功完成后自动运行。"
      />
      <EmptyState
        v-else-if="filtered.length === 0"
        title="没有匹配的测试用例"
        description="换个关键词，或调整类型与启停筛选"
      >
        <Button @click="clearFilters">清除筛选</Button>
      </EmptyState>
    </div>

    <TestCaseDialog
      v-model:open="dialogOpen"
      :test-case="editing"
      :projects="store.projects"
      :default-project-id="store.projectId"
      @saved="load()"
    />

    <TestCaseRunsDialog
      v-model:open="runsOpen"
      :runs="runs"
      :loading="runsLoading"
      :error="runsError"
      :title="runsTarget ? `${runsTarget.name} · 执行记录` : '测试执行记录'"
    />

    <Dialog
      v-model:open="deleteOpen"
      title="删除测试用例？"
      :description="deleteTarget ? `「${deleteTarget.name}」将不再出现在任务绑定中。` : ''"
      icon-tone="danger"
    >
      <p class="text-caption text-label-secondary">
        历史任务的测试执行记录会保留，已经绑定的任务完成时会跳过这条用例。
      </p>
      <template #footer>
        <Button :disabled="deleting" @click="deleteOpen = false">取消</Button>
        <Button variant="danger" :disabled="deleting" @click="confirmDelete">
          {{ deleting ? '删除中…' : '删除' }}
        </Button>
      </template>
    </Dialog>
  </div>
</template>
