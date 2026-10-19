<script setup lang="ts">
import {
  AlertTriangle,
  Blocks,
  Bot,
  Check,
  ChevronLeft,
  Copy,
  ImagePlus,
  Pencil,
  Play,
  Plus,
  RefreshCw,
  RotateCcw,
  ScrollText,
  Trash2,
  X,
  XCircle,
} from '@lucide/vue'
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ACCEPTED_IMAGE_TYPES, listTaskAttachments, uploadTaskAttachment } from '@/api/attachments'
import { getDecision } from '@/api/decisions'
import { ApiError } from '@/api/http'
import { listTaskPlugins } from '@/api/plugins'
import DecisionLevelBadge from '@/components/business/DecisionLevelBadge.vue'
import DecisionReviewDialog from '@/components/business/DecisionReviewDialog.vue'
import ImageAttachmentGrid from '@/components/business/ImageAttachmentGrid.vue'
import ImageAttachmentsDialog from '@/components/business/ImageAttachmentsDialog.vue'
import NewTaskDialog from '@/components/business/NewTaskDialog.vue'
import RunDetailDialog from '@/components/business/RunDetailDialog.vue'
import RunStatusBadge from '@/components/business/RunStatusBadge.vue'
import TaskEditDialog from '@/components/business/TaskEditDialog.vue'
import TaskPluginDialog from '@/components/business/TaskPluginDialog.vue'
import TaskTestCasePanel from '@/components/business/TaskTestCasePanel.vue'
import TaskStatusBadge from '@/components/business/TaskStatusBadge.vue'
import WorkItemSwitcher from '@/components/business/WorkItemSwitcher.vue'
import Badge from '@/components/ui/badge/Badge.vue'
import Button from '@/components/ui/button/Button.vue'
import Checkbox from '@/components/ui/checkbox/Checkbox.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import EmptyState from '@/components/ui/empty-state/EmptyState.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import IconButton from '@/components/ui/icon-button/IconButton.vue'
import Input from '@/components/ui/input/Input.vue'
import ListToolbar from '@/components/ui/list-toolbar/ListToolbar.vue'
import Pagination from '@/components/ui/pagination/Pagination.vue'
import Select from '@/components/ui/select/Select.vue'
import Table from '@/components/ui/table/Table.vue'
import type { TableColumn } from '@/components/ui/table/table.types'
import Tooltip from '@/components/ui/tooltip/Tooltip.vue'
import { useCodexInstallDialog } from '@/composables/useCodexInstallDialog'
import { useToast } from '@/composables/useToast'
import { useDecisionStore } from '@/stores/useDecisionStore'
import { useExecutionRuntimeStore } from '@/stores/useExecutionRuntimeStore'
import { useRunStore } from '@/stores/useRunStore'
import { useTaskStore } from '@/stores/useTaskStore'
import type {
  DecisionSummary,
  ImageAttachment,
  TaskAttachment,
  TaskBatchDeleteFailure,
  TaskBatchDispatchFailure,
  TaskPluginBinding,
} from '@/types/api'
import {
  canBatchDispatch,
  canCancel,
  canDelete,
  canDispatch,
  canEdit,
  canReject,
  canResume,
  canRetry,
  retryActionLabel,
  type DecisionLevel,
  type TaskSource,
  type TaskStatus,
} from '@/types/task'
import {
  formatDateTime,
  formatDuration,
  formatDurationMillis,
  formatRelative,
  formatTokens,
} from '@/utils/format'
import { parseTaskIdQuery } from '@/utils/taskNavigation'
import BugView from '@/views/BugsView.vue'

/*
 * 视图不判断状态。按钮能不能点一律问 types/task.ts 里的守卫，
 * 守卫用的又是状态机本身 —— 状态机改了不用回来改模板。
 */

const store = useTaskStore()
const decisionStore = useDecisionStore()
const executionRuntime = useExecutionRuntimeStore()
const runStore = useRunStore()
const toast = useToast()
const { openDialog: openCodexInstallDialog } = useCodexInstallDialog()
const route = useRoute()
const router = useRouter()
const bugView = computed(() => route.query.view === 'bugs')
const routeTaskId = computed(() => parseTaskIdQuery(route.query.taskId))
let routeAppliedFilter = false

const sourceMeta: Record<
  TaskSource,
  { label: string; tone: 'neutral' | 'accent' | 'warning'; text: string }
> = {
  MANUAL: { label: '人工', tone: 'neutral', text: 'text-label-tertiary' },
  REQUIREMENT: { label: '需求', tone: 'accent', text: 'text-accent' },
  BUG_FIX: { label: 'Bug 修复', tone: 'warning', text: 'text-warning' },
}

const columns = computed<TableColumn[]>(() => [
  { key: 'selection', label: '', width: '36px' },
  // 编号并进标题列：它只是定位符，不值得单独占掉 56px 的标题宽度
  { key: 'title', label: '任务' },
  { key: 'source', label: '类型', width: '72px', hideBelow: 'xl' },
  // 选定具体项目后，项目名对每一行都一样，不再占列表宽度
  ...(store.projectId === null
    ? [{ key: 'projectName', label: '项目', width: '116px', hideBelow: 'xl' as const }]
    : []),
  { key: 'level', label: '等级', width: '60px', hideBelow: 'md' },
  { key: 'status', label: '状态', width: '88px' },
  { key: 'updated', label: '更新', width: '80px', hideBelow: '2xl' },
  { key: 'duration', label: '耗时', align: 'right', width: '76px', hideBelow: 'xl' },
  { key: 'tokens', label: 'Token', align: 'right', width: '68px', hideBelow: 'xl' },
])

const statusOptions = [
  { value: 'ALL', label: '全部状态' },
  { value: 'PENDING', label: '待执行' },
  { value: 'QUEUED', label: '排队中' },
  { value: 'RUNNING', label: '执行中' },
  { value: 'BLOCKED', label: '待批准' },
  { value: 'DONE', label: '已完成' },
  { value: 'FAILED', label: '失败' },
  { value: 'CANCELLED', label: '已终止' },
]

const levelOptions = [
  { value: 'ALL', label: '全部等级' },
  { value: 'L0', label: 'L0 完全自主' },
  { value: 'L1', label: 'L1 自主留痕' },
  { value: 'L2', label: 'L2 需人工批准' },
  { value: 'L3', label: 'L3 强阻塞' },
]

// Select 的值是 string，换算只放在视图边界，避免把字符串漏进 store 的类型
const statusFilter = computed({
  get: () => store.statusFilter,
  set: (value: string) => {
    store.statusFilter = value as TaskStatus | 'ALL'
  },
})

const levelFilter = computed({
  get: () => store.levelFilter,
  set: (value: string) => {
    store.levelFilter = value as DecisionLevel | 'ALL'
  },
})

/*
 * 搜索框加 250ms 防抖。后端分页之后每敲一个字就是一次请求，
 * 不做防抖的话「登录」两个字会打出五次查询，而前四次的结果都会被立刻覆盖掉。
 */
const keywordInput = ref(store.keyword)
let keywordTimer: number | undefined
watch(keywordInput, (value) => {
  window.clearTimeout(keywordTimer)
  keywordTimer = window.setTimeout(() => {
    store.keyword = value
  }, 250)
})

watch(
  routeTaskId,
  (taskId) => {
    if (taskId === null) {
      if (store.taskIdFilter !== null) {
        store.clearTaskFocus()
        store.clearSelection()
        routeAppliedFilter = true
      } else {
        routeAppliedFilter = false
      }
      return
    }
    store.clearSelection()
    store.focusTask(taskId)
    keywordInput.value = ''
    routeAppliedFilter = true
  },
  { immediate: true },
)

const selected = computed(() => store.selected)
const codexThreadId = computed(() => store.detail?.threadId ?? null)
const codexSessionStatus = computed(() => {
  if (!store.detail) return { label: '读取中', tone: 'neutral' as const }
  return codexThreadId.value
    ? { label: '已建立', tone: 'success' as const }
    : { label: '尚未建立', tone: 'neutral' as const }
})
const focusedTask = computed(() => {
  const taskId = store.taskIdFilter
  return taskId === null ? null : store.items.find((row) => row.id === taskId) ?? null
})
watch(
  focusedTask,
  (task) => {
    if (task && store.selected?.id !== task.id) void store.select(task)
  },
  { immediate: true },
)
const promptOpen = ref(false)
const editOpen = ref(false)

const openEdit = () => {
  if (!selected.value || !store.detail || !canEdit(selected.value.status)) return
  editOpen.value = true
}

/*
 * 批量选择只覆盖当前页允许删除的待执行、排队中或已终止任务。翻页或筛选后清空，避免用户看不到的行仍被选中；
 * 派发或删除结束后成功项移除，失败项保留，下一次点击可以直接重试同一批失败任务。
 */
const selectedIds = ref<Set<number>>(new Set())
const selectableRows = computed(() => store.items.filter((row) => canDelete(row.status)))
const selectedCount = computed(() => selectedIds.value.size)
const copiedThreadId = ref(false)
let copyThreadIdTimer: number | undefined

const copyThreadId = async () => {
  const threadId = codexThreadId.value
  if (!threadId) return

  let copied = false
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(threadId)
      copied = true
    }
  } catch {
    // 非 HTTPS 或浏览器拒绝剪贴板权限时，退回选中文本框复制
  }

  if (!copied) {
    const field = document.createElement('textarea')
    try {
      field.value = threadId
      field.setAttribute('readonly', '')
      field.className = 'fixed left-[-9999px] top-0'
      document.body.appendChild(field)
      field.select()
      copied = document.execCommand('copy')
    } catch {
      copied = false
    } finally {
      field.remove()
    }
  }

  if (!copied) {
    toast.warning('会话 ID 没有复制成功', '可以直接选中会话 ID 手动复制')
    return
  }

  copiedThreadId.value = true
  window.clearTimeout(copyThreadIdTimer)
  copyThreadIdTimer = window.setTimeout(() => {
    copiedThreadId.value = false
  }, 1600)
  toast.success('Codex 会话 ID 已复制')
}

const hasListFilters = computed(
  () =>
    store.statusFilter !== 'ALL' ||
    store.levelFilter !== 'ALL' ||
    store.keyword.trim() !== '' ||
    store.taskIdFilter !== null,
)
const dispatchableSelectedIds = computed(() =>
  store.items
    .filter((row) => selectedIds.value.has(row.id) && canBatchDispatch(row.status))
    .map((row) => row.id),
)
const selectionState = computed<boolean | 'indeterminate'>(() => {
  if (selectedCount.value === 0 || selectableRows.value.length === 0) return false
  return selectableRows.value.every((row) => selectedIds.value.has(row.id))
    ? true
    : 'indeterminate'
})

const isRowSelected = (id: number) => selectedIds.value.has(id)

const clearRowSelection = () => {
  selectedIds.value = new Set()
}

const setRowSelected = (id: number, checked: boolean) => {
  const next = new Set(selectedIds.value)
  if (checked) next.add(id)
  else next.delete(id)
  selectedIds.value = next
}

const toggleAllSelectable = (checked: boolean | 'indeterminate') => {
  if (checked === 'indeterminate') return
  selectedIds.value = checked
    ? new Set(selectableRows.value.map((row) => row.id))
    : new Set()
}

watch(
  () => store.items,
  (rows) => {
    const available = new Set(rows.filter((row) => canDelete(row.status)).map((row) => row.id))
    const retained = [...selectedIds.value].filter((id) => available.has(id))
    if (retained.length !== selectedIds.value.size) {
      selectedIds.value = new Set(retained)
    }
  },
)

watch(
  () => [
    store.page,
    store.pageSize,
    store.statusFilter,
    store.levelFilter,
    store.projectId,
    store.keyword,
  ],
  () => {
    selectedIds.value = new Set()
  },
)

/** 当前选中任务正阻塞在哪个决策上。列表项只有决策单号，问题与建议要单独取。 */
const decision = ref<DecisionSummary | null>(null)
const decisionLoading = ref(false)
const decisionError = ref<string | null>(null)
watch(
  () => store.selected?.decisionId,
  async (id) => {
    decision.value = null
    decisionError.value = null
    decisionLoading.value = id !== undefined && id !== null
    if (id === undefined || id === null) return
    try {
      const loaded = await getDecision(id)
      if (store.selected?.decisionId === id) decision.value = loaded
    } catch {
      if (store.selected?.decisionId === id) {
        decisionError.value = '决策详情读取失败，请稍后重试'
      }
    } finally {
      if (store.selected?.decisionId === id) decisionLoading.value = false
    }
  },
  { immediate: true },
)

/*
 * 详情里的决策卡只能展示「当前任务自己的、仍在待批」的那一条。
 * decision 是异步取回来的独立状态；只看它是否非空，会在切换任务或审批完成后
 * 把上一条决策继续渲染在当前任务下面。
 */
const activeDecision = computed(() => {
  const task = selected.value
  const current = decision.value
  if (!task || !current) return null
  if (task.status !== 'BLOCKED' || task.decisionId !== current.id) return null
  if (current.taskId !== task.id || current.status !== 'PENDING') return null
  return current
})

const reviewOpen = ref(false)
const reviewMode = ref<'approve' | 'reject'>('approve')

onMounted(() => {
  if (!routeAppliedFilter) void store.load()
  void store.loadStats()
})

const dismissTaskFilter = () => {
  store.clearTaskFocus()
  store.clearSelection()
  const query = { ...route.query }
  delete query.taskId
  void router.replace({ name: 'tasks', query })
}

const resetListFilters = () => {
  window.clearTimeout(keywordTimer)
  keywordInput.value = ''
  store.statusFilter = 'ALL'
  store.levelFilter = 'ALL'
  store.keyword = ''
  if (store.taskIdFilter !== null) dismissTaskFilter()
}

const openReview = (mode: 'approve' | 'reject') => {
  if (!activeDecision.value) {
    toast.warning('决策详情还没读取到', decisionError.value ?? '请等加载完成后再试')
    return
  }
  reviewMode.value = mode
  reviewOpen.value = true
}

const onReviewed = async (payload: { mode: 'approve' | 'reject'; note: string }) => {
  const current = activeDecision.value
  const task = selected.value
  if (!current || !task) return

  const failure = await decisionStore.review(current.id, payload.mode, payload.note)
  if (failure) {
    toast.warning(`决策 #${current.id} 没能${payload.mode === 'approve' ? '批准' : '驳回'}`, failure)
    return
  }

  // 决议会改任务状态，所以列表、统计、右侧详情、决策卡四处都要重拉 ——
  // 少刷一处就会出现「列表说执行中、侧栏说待批准」这种自相矛盾的首屏
  await Promise.all([store.load(), store.loadStats()])
  const fresh = store.items.find((row) => row.id === task.id)
  if (fresh) await store.select(fresh)
  decision.value = await getDecision(current.id).catch(() => null)
  await loadAttachments(task.id)

  if (payload.mode === 'approve') {
    toast.success(`#${task.id} 已批准`, '任务已放行；已有执行会话时会从断点续跑')
  } else {
    toast.show(`#${task.id} 已驳回，任务终止`, { description: '决策已留痕' })
  }
}

const onApprovalUploaded = async (payload: { taskId: number }) => {
  if (store.selected?.id === payload.taskId) await loadAttachments(payload.taskId)
}

const retry = async () => {
  const task = selected.value
  if (!task) return
  const failure = await store.retry(task.id)
  if (failure) {
    toast.warning(`#${task.id} ${retryActionLabel(task.status)}被拒绝`, failure)
    return
  }
  toast.show(`#${task.id} 已回到待执行`, { description: '执行器下一轮会重新接手' })
}

const dispatchingTask = ref(false)

const requireCodexReady = async (): Promise<boolean> => {
  if (!executionRuntime.status) {
    await executionRuntime.check()
  }
  const status = executionRuntime.status
  if (status && !status.available) {
    openCodexInstallDialog()
    toast.warning(
      'Codex 未就绪，暂时不能派发',
      '请先到「设置 → 执行」安装或配置 Codex，然后重新检测',
    )
    return false
  }
  return true
}

const dispatch = async () => {
  const task = selected.value
  if (!task || dispatchingTask.value) return
  if (!(await requireCodexReady())) return
  dispatchingTask.value = true
  try {
    const failure = await store.dispatch(task.id)
    if (failure) {
      toast.warning(`#${task.id} 派发被拒绝`, failure)
      return
    }
    if (store.selected?.status === 'QUEUED') {
      toast.show(`#${task.id} 已进入排队`, { description: '执行位空出来后会自动开始，无需再次点击' })
      return
    }
    // 认领成功只说明「codex 已经起跑」，结果还在路上，所以随后把运行记录刷出来
    await runStore.load(task.id)
    toast.show(`#${task.id} 已派发给 codex`, { description: 'codex 在本机后台执行，任务状态和运行记录每 3 秒自动刷新' })
  } finally {
    dispatchingTask.value = false
  }
}

/** 派发 = 平台在本机拉起 codex CLI。当前只有这一个内部执行器，按钮与提示都要让人知道这一点 */
const dispatchHint = '在本机拉起 codex CLI 执行；派发目前只支持 codex'

const dispatchingSelected = ref(false)

const batchFailureDescription = (
  failures: Array<TaskBatchDeleteFailure | TaskBatchDispatchFailure>,
  action: '未派发' | '未删除',
) => {
  const shown = failures
    .slice(0, 2)
    .map((failure) => `#${failure.taskId}：${failure.message}`)
    .join('；')
  return failures.length > 2 ? `${shown}；另有 ${failures.length - 2} 个${action}` : shown
}

const dispatchSelected = async () => {
  const ids = dispatchableSelectedIds.value
  if (ids.length === 0 || dispatchingSelected.value) return
  if (!(await requireCodexReady())) return

  const selectedTaskId = selected.value?.id ?? null
  dispatchingSelected.value = true
  const outcome = await store.dispatchMany(ids)
  dispatchingSelected.value = false

  if (!outcome.ok) {
    toast.warning('批量派发请求失败', outcome.message)
    return
  }

  const dispatched = new Set(outcome.result.dispatchedTaskIds)
  selectedIds.value = new Set(ids.filter((id) => !dispatched.has(id)))
  if (selectedTaskId !== null && dispatched.has(selectedTaskId)) {
    await runStore.load(selectedTaskId)
  }

  const successCount = outcome.result.dispatchedTaskIds.length
  const failureCount = outcome.result.failures.length
  if (failureCount === 0) {
    toast.success(`已提交 ${successCount} 个任务`, '取得执行位的任务会立即起跑，其余按顺序排队等待')
  } else if (successCount === 0) {
    toast.warning('没有任务被派发', batchFailureDescription(outcome.result.failures, '未派发'))
  } else {
    toast.warning(
      `已派发 ${successCount} 个，${failureCount} 个未派发`,
      batchFailureDescription(outcome.result.failures, '未派发'),
    )
  }
}

const deleting = ref(false)
const deleteOpen = ref(false)
const deleteTargetIds = ref<number[]>([])

const deleteDialogTitle = computed(() =>
  deleteTargetIds.value.length === 1
    ? `删除任务 #${deleteTargetIds.value[0]}？`
    : `删除 ${deleteTargetIds.value.length} 个任务？`,
)
const deleteDialogDescription = computed(() =>
  deleteTargetIds.value.length === 1
    ? '任务会从平台列表中移除，无法在平台上恢复。'
    : '选中的待执行、排队中或已终止任务会从平台列表中移除，无法在平台上恢复。',
)

const requestDelete = (ids: number[]) => {
  const uniqueIds = [...new Set(ids)]
  if (uniqueIds.length === 0) return
  deleteTargetIds.value = uniqueIds
  deleteOpen.value = true
}

const confirmDelete = async () => {
  const ids = deleteTargetIds.value
  if (ids.length === 0 || deleting.value) return

  deleting.value = true
  if (ids.length === 1) {
    const failure = await store.remove(ids[0])
    deleting.value = false
    if (failure) {
      toast.warning(`#${ids[0]} 删除被拒绝`, failure)
      return
    }
    selectedIds.value = new Set([...selectedIds.value].filter((id) => id !== ids[0]))
    deleteOpen.value = false
    toast.success(`#${ids[0]} 已删除`, '任务已从平台列表移除')
    return
  }

  const outcome = await store.removeMany(ids)
  deleting.value = false
  if (!outcome.ok) {
    toast.warning('批量删除请求失败', outcome.message)
    return
  }

  const deleted = new Set(outcome.result.deletedTaskIds)
  selectedIds.value = new Set([...selectedIds.value].filter((id) => !deleted.has(id)))
  deleteOpen.value = false

  const successCount = outcome.result.deletedTaskIds.length
  const failureCount = outcome.result.failures.length
  if (failureCount === 0) {
    toast.success(`已删除 ${successCount} 个任务`, '任务已从平台列表移除')
  } else if (successCount === 0) {
    toast.warning('没有任务被删除', batchFailureDescription(outcome.result.failures, '未删除'))
  } else {
    toast.warning(
      `已删除 ${successCount} 个，${failureCount} 个未删除`,
      batchFailureDescription(outcome.result.failures, '未删除'),
    )
  }
}

const cancelOpen = ref(false)
const canceling = ref(false)
const cancelTargetId = ref<number | null>(null)

const cancelDialogTitle = computed(() =>
  cancelTargetId.value === null ? '终止任务？' : `终止任务 #${cancelTargetId.value}？`,
)

const requestCancel = () => {
  const task = selected.value
  if (!task || !canCancel(task.status)) return
  cancelTargetId.value = task.id
  cancelOpen.value = true
}

const confirmCancel = async () => {
  const id = cancelTargetId.value
  if (id === null || canceling.value) return

  canceling.value = true
  const failure = await store.cancel(id)
  canceling.value = false
  if (failure) {
    toast.warning(`#${id} 终止被拒绝`, failure)
    return
  }
  cancelOpen.value = false
  toast.show(`#${id} 已终止`, { description: '任务不会再有后续动作' })
}

/*
 * 新建任务。建完之后要把列表翻回「能看到它」的位置：列表按优先级与 ID 降序，
 * 新任务一定在第一页第一行，但筛选条件或页码会把它挡在外面 —— 那看起来就像创建失败了。
 */
const createOpen = ref(false)

const onTaskCreated = async (payload: { id: number; title: string }) => {
  // 只清理会把新任务藏起来的筛选；项目是用户在顶栏明确选择的长期上下文，不能顺手重置。
  // 当前选中项目时，新建任务本来就落在这个项目；选择「全部项目」时新任务同样可见。
  store.statusFilter = 'ALL'
  store.levelFilter = 'ALL'
  store.keyword = ''
  keywordInput.value = ''
  store.page = 1
  if (store.taskIdFilter !== null) {
    store.clearTaskFocus()
    const query = { ...route.query }
    delete query.taskId
    void router.replace({ name: 'tasks', query })
  }
  // 上面几个赋值会顺带触发 store 的 watch（同一份查询），但这里要等的是「请求已完成」，
  // 所以显式再拉一次：多一次本地 GET，换的是「创建成功立刻能看到并选中」这件确定的事
  await store.load()
  await store.loadStats()
  const fresh = store.items.find((row) => row.id === payload.id)
  if (fresh) await store.select(fresh)
  // 弹框是先建任务再传图，这里按 0 张读一次；传完它会再发 uploaded 事件让我们重拉
  await loadAttachments(payload.id)
  toast.success(`#${payload.id} 已创建`, '在待执行队列里，点「派发给 codex」才会真的跑')
}

/*
 * 附件。跟着选中行走，切一行重拉一次。
 *
 * 放在视图里而不是任务 store 里：只有这一个地方用它，进了 store 反而要跟着列表翻页、
 * 筛选一起失效重算，而那几处跟附件没关系。
 */
const attachments = ref<TaskAttachment[]>([])
const taskPlugins = ref<TaskPluginBinding[]>([])
const pluginDialogOpen = ref(false)
const viewerOpen = ref(false)
const viewerId = ref<number | null>(null)

const loadAttachments = async (id: number) => {
  try {
    attachments.value = await listTaskAttachments(id)
  } catch {
    // 附件是附加信息：取不到就当作没有，不拿一条红字把整个详情面板顶掉
    attachments.value = []
  }
}

const loadTaskPlugins = async (id: number) => {
  try {
    taskPlugins.value = await listTaskPlugins(id)
  } catch {
    taskPlugins.value = []
  }
}

const canEditPlugins = computed(
  () =>
    selected.value !== null &&
    (selected.value.status === 'PENDING' ||
      selected.value.status === 'QUEUED' ||
      selected.value.status === 'FAILED' ||
      selected.value.status === 'CANCELLED'),
)

const onPluginsSaved = (bindings: TaskPluginBinding[]) => {
  taskPlugins.value = bindings
}

const openViewer = (attachment: ImageAttachment) => {
  viewerId.value = attachment.id
  viewerOpen.value = true
}

/** 补图。上传接口挂在任务下，所以这里对任何状态的任务都成立 —— 图片在「下次」执行开局才读 */
const attachInput = ref<HTMLInputElement | null>(null)
const attaching = ref(false)

const attachFiles = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  // 清掉 value：连着选同一个文件两次时 change 不会再触发，看起来像「点了没反应」
  input.value = ''
  const task = selected.value
  if (!task || files.length === 0) return

  attaching.value = true
  let uploaded = 0
  for (const file of files) {
    try {
      await uploadTaskAttachment(task.id, file)
      uploaded += 1
    } catch (e) {
      toast.warning(
        `「${file.name}」没传上去`,
        e instanceof ApiError ? e.message : '无法连接后端，请确认服务已启动',
      )
    }
  }
  attaching.value = false
  if (uploaded > 0) {
    await loadAttachments(task.id)
    toast.success(`${uploaded} 张图已附上`, `#${task.id} 下次执行时会一并交给 codex`)
  }
}

/** 弹框传完图（含失败后重试成功）时刷新当前选中行的缩略图 */
const onUploaded = (payload: { id: number }) => {
  if (store.selected?.id === payload.id) void loadAttachments(payload.id)
}

/*
 * 任务状态来自列表查询，运行记录自己的轮询刷新不到它。手动刷新与自动轮询共用这里，
 * 保证列表、统计和右侧详情按同一份后端状态收敛。
 */
const refreshingTasks = ref(false)
const refreshTasks = async () => {
  if (refreshingTasks.value) return
  refreshingTasks.value = true
  try {
    await store.refresh()
  } finally {
    refreshingTasks.value = false
  }
}

/*
 * 运行记录跟着选中行走：切一行重拉一次，同时把上一条的弹框关掉 ——
 * 弹框里显示的是「某一次尝试」，换了任务还留着它，只会让人对错上下文。
 */
let runsTimer: number | undefined
const stopRunsTimer = () => {
  window.clearInterval(runsTimer)
  runsTimer = undefined
}

const refreshRuns = async () => {
  const task = selected.value
  if (task) await runStore.load(task.id)
}

const runDialogOpen = ref(false)
const openRun = async (runId: number) => {
  runDialogOpen.value = true
  await runStore.openRun(runId)
}

watch(
  () => store.selected?.id,
  (id) => {
    stopRunsTimer()
    window.clearTimeout(copyThreadIdTimer)
    copiedThreadId.value = false
    promptOpen.value = false
    editOpen.value = false
    runDialogOpen.value = false
    runStore.reset()
    // 看图弹框里显示的是「某一张图」，换了任务还留着它，只会让人对错上下文
    viewerOpen.value = false
    viewerId.value = null
    attachments.value = []
    taskPlugins.value = []
    pluginDialogOpen.value = false
    if (id !== undefined) {
      void runStore.load(id)
      void loadAttachments(id)
      void loadTaskPlugins(id)
    }
  },
  { immediate: true },
)

// 只在「有记录还在跑」时开表：没在跑的时候还每 3 秒打一次后端，只是白耗电
watch(
  () => runStore.hasRunning,
  (running) => {
    stopRunsTimer()
    if (running) runsTimer = window.setInterval(() => void runStore.refreshQuietly(), 3000)
  },
)

/*
 * 当前页只要还有执行中或排队中的任务就轮询列表；状态落定后 computed 自动变 false，
 * 下一次状态变化时清掉定时器，不会让后台持续空转。
 */
const hasActiveTasks = computed(
  () =>
    !bugView.value &&
    (selected.value?.status === 'QUEUED' ||
      selected.value?.status === 'RUNNING' ||
      store.items.some((row) => row.status === 'QUEUED' || row.status === 'RUNNING')),
)
let tasksTimer: number | undefined
const stopTasksTimer = () => {
  window.clearInterval(tasksTimer)
  tasksTimer = undefined
}
watch(
  hasActiveTasks,
  (running) => {
    stopTasksTimer()
    if (running) tasksTimer = window.setInterval(() => void refreshTasks(), 3000)
  },
  { immediate: true },
)

onUnmounted(() => {
  stopRunsTimer()
  stopTasksTimer()
  window.clearTimeout(copyThreadIdTimer)
})
</script>

<template>
  <BugView v-if="bugView" />
  <div v-else class="flex h-full min-h-0">
    <div class="flex min-w-0 flex-1 flex-col">
      <ListToolbar>
        <template #title>
          <h1 class="text-body font-emphasis text-label">任务</h1>
          <WorkItemSwitcher value="TASKS" />
          <span class="tnum text-body text-label-tertiary">
            {{ store.total }} / {{ store.taskTotal }}
          </span>
        </template>
        <template #filters>
          <Input
            v-model="keywordInput"
            placeholder="搜索任务标题"
            size="lg"
            aria-label="搜索任务标题"
            class="min-w-0 flex-1 sm:w-52 sm:flex-none"
          />
          <Select v-model="statusFilter" :items="statusOptions" size="lg" class="w-32" />
          <Select v-model="levelFilter" :items="levelOptions" size="lg" class="w-36" />
          <button
            v-if="store.taskIdFilter !== null"
            type="button"
            class="inline-flex h-control items-center gap-1.5 rounded-full border border-accent/20 bg-accent/10 px-2.5 text-caption font-emphasis text-accent transition-colors duration-100 ease-apple hover:bg-accent/15"
            title="清除任务筛选"
            @click="dismissTaskFilter"
          >
            <span class="tnum">任务 #{{ store.taskIdFilter }}</span>
            <Icon :as="X" size="inline" />
          </button>
        </template>
        <template #actions>
          <Tooltip content="刷新任务列表">
            <IconButton
              label="刷新任务列表"
              size="lg"
              :disabled="refreshingTasks"
              @click="refreshTasks"
            >
              <Icon
                :as="RefreshCw"
                size="large"
                :class="refreshingTasks ? 'animate-spin' : ''"
              />
            </IconButton>
          </Tooltip>
          <Button variant="primary" size="lg" @click="createOpen = true">
            <Icon :as="Plus" size="inline" />新建任务
          </Button>
        </template>
      </ListToolbar>

      <!--
        批量操作只在真的选中了行时出现。常驻在工具栏里会让 1024–1440 这一段最常用的宽度
        同时挤着筛选、搜索、刷新、新建和两条长按钮；macOS 的列表也把选区动作放在上下文位置。
      -->
      <div
        v-if="selectedCount > 0"
        class="flex min-h-toolbar-compact shrink-0 flex-wrap items-center gap-x-3 gap-y-1.5 border-b border-separator bg-accent/6 px-3 py-1.5 lg:px-5"
        aria-live="polite"
      >
        <p class="text-caption text-label">
          已选择 <span class="tnum font-emphasis">{{ selectedCount }}</span> 项
        </p>
        <button
          type="button"
          class="inline-flex items-center gap-0.5 text-caption text-accent transition-colors duration-100 ease-apple hover:text-accent/80"
          @click="clearRowSelection"
        >
          <Icon :as="X" size="inline" />清除选择
        </button>
        <p class="hidden text-caption text-label-tertiary md:block">
          只有可删除的任务会进入选区
        </p>
        <div class="ml-auto flex items-center gap-2">
          <Tooltip v-if="dispatchableSelectedIds.length > 0" :content="dispatchHint">
            <Button
              variant="primary"
              size="sm"
              :disabled="dispatchingSelected"
              @click="dispatchSelected"
            >
              <Icon :as="Play" size="inline" />
              <template v-if="dispatchingSelected">派发中…</template>
              <template v-else>
                <span class="sm:hidden">派发 {{ dispatchableSelectedIds.length }} 个</span>
                <span class="hidden sm:inline">
                  派发给 codex {{ dispatchableSelectedIds.length }} 个
                </span>
              </template>
            </Button>
          </Tooltip>
          <Button
            variant="danger"
            size="sm"
            :disabled="deleting"
            @click="requestDelete([...selectedIds])"
          >
            <Icon :as="Trash2" size="inline" />删除 {{ selectedCount }} 个
          </Button>
        </div>
      </div>

      <div class="scroll-area min-h-0 flex-1 overflow-auto">
        <Table
          :columns="columns"
          :rows="store.items"
          :selected-id="selected?.id ?? null"
          :checked-ids="[...selectedIds]"
          touch-rows
          :show-empty="false"
          @select="store.select"
        >
          <template #header="{ column }">
            <Checkbox
              v-if="column.key === 'selection'"
              :model-value="selectionState"
              :disabled="selectableRows.length === 0"
              aria-label="选择当前页全部可删除任务"
              @update:model-value="toggleAllSelectable"
            />
            <span v-else>{{ column.label }}</span>
          </template>
          <template #cell="{ row, column }">
            <div v-if="column.key === 'selection'" class="flex items-center" @click.stop>
              <Checkbox
                :model-value="isRowSelected(row.id)"
                :disabled="!canDelete(row.status)"
                :aria-label="`选择任务 #${row.id}`"
                @update:model-value="setRowSelected(row.id, $event === true)"
              />
            </div>
            <span
              v-else-if="column.key === 'title'"
              class="flex min-w-0 items-center gap-2"
            >
              <span
                class="tnum shrink-0 text-caption text-label-tertiary group-data-[selected=true]:text-white/60"
              >
                #{{ row.id }}
              </span>
              <span
                class="truncate font-emphasis text-label group-data-[selected=true]:text-white"
                :title="row.title"
              >
                {{ row.title }}
              </span>
            </span>
            <span
              v-else-if="column.key === 'source'"
              :class="[
                'truncate text-caption',
                sourceMeta[row.source as TaskSource].text,
                'group-data-[selected=true]:text-white/70',
              ]"
            >
              {{ sourceMeta[row.source as TaskSource].label }}
            </span>
            <span
              v-else-if="column.key === 'projectName'"
              class="truncate text-label-secondary group-data-[selected=true]:text-white/70"
              :title="row.projectName"
            >
              {{ row.projectName }}
            </span>
            <DecisionLevelBadge
              v-else-if="column.key === 'level'"
              :level="row.level"
              compact
            />
            <TaskStatusBadge
              v-else-if="column.key === 'status'"
              :status="row.status"
              variant="dot"
            />
            <span
              v-else-if="column.key === 'updated'"
              class="tnum text-caption text-label-tertiary group-data-[selected=true]:text-white/70"
              :title="formatDateTime(row.updatedAt)"
            >
              {{ formatRelative(row.updatedAt) }}
            </span>
            <span
              v-else-if="column.key === 'duration'"
              class="tnum text-label group-data-[selected=true]:text-white"
            >
              {{ formatDurationMillis(row.executionMillis) }}
            </span>
            <span
              v-else-if="column.key === 'tokens'"
              class="tnum text-label group-data-[selected=true]:text-white"
            >
              {{ formatTokens(row.tokens) }}
            </span>
          </template>
        </Table>

        <EmptyState
          v-if="store.loading && store.items.length === 0"
          title="正在加载…"
          description="从后端读取任务列表"
        />
        <EmptyState
          v-else-if="store.error"
          title="加载失败"
          :description="store.error"
        />
        <EmptyState
          v-else-if="store.total === 0"
          :title="
            store.taskIdFilter !== null
              ? `没有找到任务 #${store.taskIdFilter}`
              : hasListFilters
                ? '没有匹配的任务'
                : store.projectId !== null
                  ? '这个项目还没有任务'
                  : '还没有任务'
          "
          :description="
            store.taskIdFilter !== null
              ? '这个任务可能已被删除，或当前账号没有读取权限'
              : hasListFilters
                ? '调整状态、等级或关键词，或清除筛选后再看。'
                : store.projectId !== null
                  ? '新建任务会落到当前项目，也可以切到「全部项目」查看已有任务。'
                  : '录入第一条需求，AI 会从待执行队列开始接手。'
          "
        >
          <div class="flex flex-wrap items-center justify-center gap-2">
            <Button
              v-if="store.projectId !== null"
              size="sm"
              @click="store.projectId = null"
            >
              查看全部项目
            </Button>
            <Button v-if="hasListFilters" size="sm" @click="resetListFilters">
              清除筛选
            </Button>
          </div>
        </EmptyState>
      </div>

      <Pagination v-model:page="store.page" v-model:page-size="store.pageSize" :total="store.total" />
    </div>

    <!--
      详情面板：桌面是右栏（288px）；手机（<lg）改成整屏覆盖层 ——
      390px 屏幕放不下「列表 + 288px 侧栏」并排，打开详情就让列表让位，看完点「返回列表」收回。
      详情正文单独滚动，操作条固定在面板底部，长记录不会再把关键按钮顶出首屏。
    -->
    <aside
      :class="[
        'h-full min-h-0 bg-window',
        selected
          ? 'fixed inset-0 z-40 flex w-full flex-col overflow-hidden border-separator lg:static lg:inset-auto lg:z-auto lg:w-72 lg:shrink-0 lg:border-l'
          : 'hidden lg:flex lg:w-72 lg:shrink-0 lg:flex-col lg:border-l lg:border-separator',
      ]"
    >
      <template v-if="selected">
        <button
          type="button"
          class="mx-4 mt-4 inline-flex items-center gap-0.5 text-caption text-accent lg:hidden"
          @click="store.clearSelection()"
        >
          <Icon :as="ChevronLeft" size="inline" />返回列表
        </button>
        <div class="scroll-area min-h-0 flex-1 overflow-y-auto p-4">
          <div class="flex items-center gap-2">
            <span class="tnum text-caption text-label-tertiary">#{{ selected.id }}</span>
            <DecisionLevelBadge :level="selected.level" />
            <Badge :tone="sourceMeta[selected.source].tone">
              {{ sourceMeta[selected.source].label }}
            </Badge>
            <TaskStatusBadge :status="selected.status" class="ml-auto" />
          </div>
          <h2 class="mt-2 text-body font-emphasis text-label">{{ selected.title }}</h2>

          <div v-if="store.detail" class="mt-2">
            <div class="flex items-center gap-2">
              <p class="text-caption font-emphasis text-label-secondary">指令</p>
              <button
                type="button"
                class="ml-auto text-caption text-accent transition-colors duration-100 ease-apple hover:text-accent/80"
                @click="promptOpen = true"
              >
                查看详情
              </button>
            </div>
            <p class="mt-1 line-clamp-4 whitespace-pre-wrap text-caption text-label-secondary">
              {{ store.detail.prompt }}
            </p>
          </div>

          <dl class="mt-4 space-y-2">
            <div class="flex items-center gap-2">
              <dt class="whitespace-nowrap text-caption text-label-secondary">项目</dt>
              <dd class="ml-auto text-caption text-label">{{ selected.projectName }}</dd>
            </div>
            <div class="flex items-center gap-2">
              <dt class="whitespace-nowrap text-caption text-label-secondary">优先级</dt>
              <dd class="tnum ml-auto text-caption text-label">{{ selected.priority }}</dd>
            </div>
            <div class="flex items-center gap-2">
              <dt class="whitespace-nowrap text-caption text-label-secondary">分支</dt>
              <dd class="ml-auto text-caption text-label">{{ selected.branchName ?? '项目目录' }}</dd>
            </div>
            <div class="flex items-center gap-2">
              <dt class="whitespace-nowrap text-caption text-label-secondary">耗时</dt>
              <dd class="tnum ml-auto text-caption text-label">
                {{ formatDurationMillis(selected.executionMillis) }}
              </dd>
            </div>
            <div class="flex items-center gap-2">
              <dt class="whitespace-nowrap text-caption text-label-secondary">Token</dt>
              <dd class="tnum ml-auto text-caption text-label">{{ formatTokens(selected.tokens) }}</dd>
            </div>
            <div class="flex items-center gap-2">
              <dt class="whitespace-nowrap text-caption text-label-secondary">尝试次数</dt>
              <dd class="tnum ml-auto text-caption text-label">{{ selected.attemptCount }}</dd>
            </div>
            <div class="flex items-center gap-2">
              <dt class="whitespace-nowrap text-caption text-label-secondary">开始执行时间</dt>
              <dd class="tnum ml-auto whitespace-nowrap text-caption text-label">
                {{ formatDateTime(store.detail?.startedAt ?? selected.startedAt) }}
              </dd>
            </div>
            <div class="flex items-center gap-2">
              <dt class="whitespace-nowrap text-caption text-label-secondary">完成时间</dt>
              <dd class="tnum ml-auto whitespace-nowrap text-caption text-label">
                {{ formatDateTime(store.detail?.finishedAt ?? selected.finishedAt) }}
              </dd>
            </div>
            <div class="flex items-center gap-2">
              <dt class="whitespace-nowrap text-caption text-label-secondary">创建时间</dt>
              <dd class="tnum ml-auto whitespace-nowrap text-caption text-label">
                {{ formatDateTime(store.detail?.createdAt ?? selected.createdAt) }}
              </dd>
            </div>
          </dl>

        <div class="mt-4 rounded-panel border border-separator-soft bg-content p-3">
          <div class="flex items-center gap-2">
            <p class="text-caption font-emphasis text-label-secondary">Codex 会话</p>
            <Badge :tone="codexSessionStatus.tone">
              {{ codexSessionStatus.label }}
            </Badge>
            <Tooltip
              v-if="codexThreadId"
              :content="copiedThreadId ? '已复制' : '复制会话 ID'"
            >
              <IconButton
                :label="copiedThreadId ? '会话 ID 已复制' : '复制 Codex 会话 ID'"
                size="sm"
                class="ml-auto"
                @click="copyThreadId"
              >
                <Icon :as="copiedThreadId ? Check : Copy" size="inline" />
              </IconButton>
            </Tooltip>
          </div>

          <template v-if="codexThreadId">
            <div class="mt-2 rounded-panel bg-window px-2.5 py-2">
              <p class="select-all break-all font-mono text-caption leading-relaxed text-label">
                {{ codexThreadId }}
              </p>
            </div>
            <p class="mt-1.5 text-caption text-label-tertiary">
              后续批准继续或平台重启恢复时，会从这个会话断点续跑。
            </p>
          </template>
          <p v-else-if="store.detail" class="mt-1.5 text-caption text-label-tertiary">
            首次派发给 codex 后自动生成；之后的批准继续与重启恢复都会沿用它。
          </p>
          <p v-else class="mt-1.5 text-caption text-label-tertiary">正在读取会话信息…</p>
        </div>

        <div class="mt-4 border-t border-separator pt-3">
          <div class="flex items-center gap-2">
            <p class="text-caption font-emphasis text-label-secondary">插件</p>
            <span v-if="taskPlugins.length > 0" class="tnum text-caption text-label-tertiary">
              {{ taskPlugins.length }}
            </span>
            <Tooltip :content="canEditPlugins ? '选择可复用插件' : '执行开始后不能改绑'">
              <IconButton
                label="管理任务插件"
                size="sm"
                class="ml-auto"
                @click="pluginDialogOpen = true"
              >
                <Icon :as="Blocks" size="regular" />
              </IconButton>
            </Tooltip>
          </div>
          <div v-if="taskPlugins.length > 0" class="mt-2 flex flex-wrap gap-1.5">
            <Badge
              v-for="plugin in taskPlugins"
              :key="plugin.pluginId"
              :tone="plugin.enabled ? 'accent' : 'warning'"
              :title="plugin.filename"
            >
              {{ plugin.name }} · v{{ plugin.revision }}
            </Badge>
          </div>
          <p v-else class="mt-2 text-caption text-label-tertiary">
            没有绑定插件。已写好的组件可以固定版本复用，不必每次重新生成。
          </p>
        </div>

        <TaskTestCasePanel
          :task-id="selected.id"
          :project-id="selected.projectId"
          :status="selected.status"
        />

        <!-- 图片不只是留档：派发时它们会跟着指令一起交给 codex，所以放在详情里而不是另开一页 -->
        <div class="mt-4 border-t border-separator pt-3">
          <div class="flex items-center gap-2">
            <p class="text-caption font-emphasis text-label-secondary">图片</p>
            <span v-if="attachments.length > 0" class="tnum text-caption text-label-tertiary">
              {{ attachments.length }}
            </span>
            <Tooltip content="加一张图">
              <IconButton
                label="添加图片"
                size="sm"
                class="ml-auto"
                :disabled="attaching"
                @click="attachInput?.click()"
              >
                <Icon :as="ImagePlus" size="regular" />
              </IconButton>
            </Tooltip>
          </div>

          <ImageAttachmentGrid
            v-if="attachments.length > 0"
            :attachments="attachments"
            class="mt-2"
            @select="openViewer"
          />
          <p v-else class="mt-2 text-caption text-label-tertiary">
            还没有图片。有些事说不清 —— 截图和设计稿在派发时会一起交给 codex。
          </p>

          <input
            ref="attachInput"
            type="file"
            :accept="ACCEPTED_IMAGE_TYPES.join(',')"
            multiple
            class="hidden"
            @change="attachFiles"
          />
        </div>

        <div
          v-if="activeDecision"
          class="mt-4 rounded-panel border border-separator-soft bg-content p-2.5"
        >
          <p class="text-caption font-emphasis text-label-secondary">等待你的决定</p>
          <p class="mt-1 text-caption text-label">{{ activeDecision.question }}</p>
          <p v-if="activeDecision.matchedRuleName" class="mt-1 text-caption text-label-tertiary">
            命中规则「{{ activeDecision.matchedRuleName }}」
            <template v-if="activeDecision.levelEscalated">
              · AI 自评 {{ activeDecision.aiLevel }}，已升级到 {{ activeDecision.effectiveLevel }}
            </template>
          </p>
        </div>

        <div class="mt-4 border-t border-separator pt-3">
          <div class="flex items-center gap-2">
            <p class="text-caption font-emphasis text-label-secondary">运行记录</p>
            <span v-if="runStore.runs.length > 0" class="tnum text-caption text-label-tertiary">
              {{ runStore.runs.length }}
            </span>
            <Tooltip content="刷新">
              <IconButton
                label="刷新运行记录"
                size="sm"
                class="ml-auto"
                @click="refreshRuns"
              >
                <Icon :as="RefreshCw" size="regular" />
              </IconButton>
            </Tooltip>
          </div>

          <p v-if="runStore.error" class="mt-2 text-caption text-danger">{{ runStore.error }}</p>
          <p
            v-else-if="runStore.loading && runStore.runs.length === 0"
            class="mt-2 text-caption text-label-tertiary"
          >
            正在加载…
          </p>
          <p v-else-if="runStore.runs.length === 0" class="mt-2 text-caption text-label-tertiary">
            还没有跑过。
            <template v-if="canDispatch(selected.status)">点下面的「派发给 codex」让它跑起来。</template>
          </p>
          <ul v-else class="mt-2 space-y-1">
            <li v-for="run in runStore.runs" :key="run.id">
              <!-- 整行可点：一次尝试的详情（事件流、出参原文）在弹框里，288px 装不下日志 -->
              <button
                type="button"
                class="w-full rounded-panel border border-separator-soft bg-content px-2.5 py-1.5 text-left transition-colors duration-100 ease-apple hover:bg-hover"
                @click="openRun(run.id)"
              >
                <span class="flex items-center gap-2">
                  <span class="tnum text-caption text-label-tertiary">#{{ run.attemptNo }}</span>
                  <RunStatusBadge :status="run.status" />
                  <Tooltip
                    v-if="run.executor === 'agent'"
                    :content="`外部执行者：${run.agentName ?? '未署名'}`"
                  >
                    <Icon :as="Bot" size="inline" class="text-label-tertiary" />
                  </Tooltip>
                  <span class="tnum ml-auto text-caption text-label-tertiary">
                    {{ formatTokens(run.tokens) }}
                  </span>
                </span>
                <span class="mt-0.5 flex items-center gap-2">
                  <span class="truncate text-caption text-label-secondary">
                    {{ run.summary ?? '（没有摘要）' }}
                  </span>
                  <span class="tnum ml-auto shrink-0 text-caption text-label-tertiary">
                    {{ formatDuration(run.startedAt, run.finishedAt) }}
                  </span>
                </span>
              </button>
            </li>
          </ul>
          <p v-if="runStore.hasRunning" class="mt-1.5 text-caption text-label-tertiary">
            执行中 · 每 3 秒自动刷新
          </p>
        </div>

        </div>

        <div class="shrink-0 border-t border-separator bg-window p-3 lg:p-4">
          <div class="flex flex-wrap items-center gap-2">
            <Tooltip
              v-if="store.detail"
              :content="canEdit(selected.status) ? '编辑任务指令与优先级' : '已完成的任务不能编辑'"
            >
              <span class="inline-flex">
                <Button
                  size="sm"
                  :disabled="!canEdit(selected.status)"
                  @click="openEdit"
                >
                  <Icon :as="Pencil" size="inline" />编辑
                </Button>
              </span>
            </Tooltip>
            <Button
              v-if="canResume(selected.status)"
              variant="primary"
              size="sm"
              :disabled="decisionLoading"
              @click="openReview('approve')"
            >
              <Icon :as="Play" size="inline" />批准并继续
            </Button>
            <Tooltip v-if="canDispatch(selected.status)" :content="dispatchHint">
              <Button
                variant="primary"
                size="sm"
                :disabled="dispatchingTask"
                @click="dispatch"
              >
                <Icon :as="Play" size="inline" />{{ dispatchingTask ? '派发中…' : '派发给 codex' }}
              </Button>
            </Tooltip>
            <Button
              v-if="canReject(selected.status)"
              size="sm"
              :disabled="decisionLoading"
              @click="openReview('reject')"
            >
              <Icon :as="XCircle" size="inline" />驳回
            </Button>
            <Button v-if="canRetry(selected.status)" size="sm" @click="retry">
              <Icon :as="RotateCcw" size="inline" />{{ retryActionLabel(selected.status) }}
            </Button>
            <Button v-if="canCancel(selected.status)" size="sm" @click="requestCancel">终止</Button>
            <Button
              v-if="canDelete(selected.status)"
              variant="danger"
              size="sm"
              @click="requestDelete([selected.id])"
            >
              <Icon :as="Trash2" size="inline" />删除
            </Button>
            <Button v-if="runStore.runs.length > 0" size="sm" @click="openRun(runStore.runs[0].id)">
              打开日志
            </Button>
          </div>
        </div>
      </template>

      <p v-else class="p-4 text-caption text-label-tertiary">选中一行查看详情</p>
    </aside>

    <NewTaskDialog v-model:open="createOpen" @created="onTaskCreated" @uploaded="onUploaded" />

    <TaskEditDialog
      v-model:open="editOpen"
      :task="store.detail"
      :attachments="attachments"
      @uploaded="onUploaded({ id: selected?.id ?? 0 })"
      @preview="openViewer"
    />

    <TaskPluginDialog
      v-if="selected"
      v-model:open="pluginDialogOpen"
      :task-id="selected.id"
      :editable="canEditPlugins"
      @saved="onPluginsSaved"
    />

    <ImageAttachmentsDialog
      v-model:open="viewerOpen"
      v-model:selected-id="viewerId"
      :attachments="attachments"
    />

    <Dialog
      v-model:open="promptOpen"
      layout="log"
      title="任务指令"
      :description="selected ? `#${selected.id} 的完整原文` : ''"
      :icon="ScrollText"
    >
      <div
        class="max-h-[64vh] overflow-y-auto rounded-panel border border-separator-soft bg-content p-3"
      >
        <p class="whitespace-pre-wrap break-words text-caption text-label">
          {{ store.detail?.prompt }}
        </p>
      </div>
      <template #footer>
        <Button size="sm" @click="promptOpen = false">关闭</Button>
      </template>
    </Dialog>

    <Dialog
      v-model:open="deleteOpen"
      :title="deleteDialogTitle"
      :description="deleteDialogDescription"
      :icon="AlertTriangle"
      icon-tone="danger"
    >
      <template #footer>
        <Button size="sm" :disabled="deleting" @click="deleteOpen = false">取消</Button>
        <Button variant="danger" size="sm" :disabled="deleting" @click="confirmDelete">
          {{ deleting ? '删除中…' : '删除' }}
        </Button>
      </template>
    </Dialog>

    <Dialog
      v-model:open="cancelOpen"
      :title="cancelDialogTitle"
      description="任务会进入已终止状态，平台不再推进后续动作；运行记录和会话会保留，之后仍可重新执行。"
      :icon="AlertTriangle"
      icon-tone="danger"
    >
      <template #footer>
        <Button size="sm" :disabled="canceling" @click="cancelOpen = false">取消</Button>
        <Button variant="danger" size="sm" :disabled="canceling" @click="confirmCancel">
          {{ canceling ? '终止中…' : '确认终止' }}
        </Button>
      </template>
    </Dialog>

    <DecisionReviewDialog
      v-model:open="reviewOpen"
      :decision="activeDecision"
      :mode="reviewMode"
      @done="onReviewed"
      @uploaded="onApprovalUploaded"
      @preview="openViewer"
    />
    <RunDetailDialog
      v-model:open="runDialogOpen"
      :detail="runStore.detail"
      :loading="runStore.detailLoading"
      :error="runStore.detailError"
    />
  </div>
</template>
