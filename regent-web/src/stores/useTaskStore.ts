import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'

import { ApiError } from '@/api/http'
import { listProjects } from '@/api/projects'
import {
  cancelTask,
  deleteTask,
  deleteTasks,
  dispatchTask,
  dispatchTasks,
  getTask,
  getTaskStats,
  listTasks,
  retryTask,
  updateTask,
  type UpdateTaskInput,
} from '@/api/tasks'
import type {
  Project,
  TaskBatchDeleteResult,
  TaskBatchDispatchResult,
  TaskDetail,
  TaskSummary,
} from '@/types/api'
import type { DecisionLevel, TaskStatus } from '@/types/task'
import {
  readProjectSelection,
  resolveProjectSelection,
  writeProjectSelection,
} from '@/utils/projectSelectionStorage'

/*
 * 任务列表的数据源。真实数据来自后端 /api/tasks，分页、筛选、搜索全部下推到 SQL ——
 * 前端这一层只负责「记住用户选了什么」和「把结果放进来」。
 *
 * 两个刻意的取舍：
 *   1. 分页状态放在 store 里而不是视图里。工具栏的筛选一变就要回第 1 页，
 *      这条规则写在这里只写一次；放在视图里的话，换个页面就得再写一遍。
 *   2. 状态迁移（重试 / 终止）不是本地改字段，而是调接口后**重新拉取**。
 *      本地改字段看起来更快，但一旦后端因为并发或守卫拒绝，界面就会与库里的状态分叉，
 *      而分叉之后用户看到的是「刚刚点成功了」但刷新就变回去 —— 最难解释的一种 bug。
 */

const EMPTY_COUNTS: Record<TaskStatus, number> = {
  PENDING: 0,
  QUEUED: 0,
  RUNNING: 0,
  BLOCKED: 0,
  DONE: 0,
  FAILED: 0,
  CANCELLED: 0,
}

function messageOf(error: unknown): string {
  if (error instanceof ApiError) return error.message
  return '无法连接后端，请确认服务已启动（默认 127.0.0.1:8787）'
}

export type BatchDispatchOutcome =
  | { ok: true; result: TaskBatchDispatchResult }
  | { ok: false; message: string }

export type BatchDeleteOutcome =
  | { ok: true; result: TaskBatchDeleteResult }
  | { ok: false; message: string }

export const useTaskStore = defineStore('tasks', () => {
  const items = ref<TaskSummary[]>([])
  const total = ref(0)
  const totalPages = ref(0)
  const page = ref(1)
  const pageSize = ref(20)

  const statusFilter = ref<TaskStatus | 'ALL'>('ALL')
  const levelFilter = ref<DecisionLevel | 'ALL'>('ALL')
  /** null 表示全部项目。工具栏的筛选与新建任务弹框共用 projects 这一份列表 */
  const projectId = ref<number | null>(readProjectSelection())
  const projects = ref<Project[]>([])
  /** 精确任务号筛选；由决策页跳转等外部入口设置 */
  const taskIdFilter = ref<number | null>(null)
  const keyword = ref('')

  const loading = ref(false)
  const error = ref<string | null>(null)

  const counts = ref<Record<TaskStatus, number>>({ ...EMPTY_COUNTS })
  const taskTotal = computed(() => Object.values(counts.value).reduce((sum, n) => sum + n, 0))

  /** 选中行的详情。列表项没有 prompt，点开一行时单独取一次。 */
  const selected = ref<TaskSummary | null>(null)
  const detail = ref<TaskDetail | null>(null)

  const load = async () => {
    loading.value = true
    error.value = null
    try {
      const result = await listTasks({
        status: statusFilter.value,
        level: levelFilter.value,
        projectId: projectId.value,
        taskId: taskIdFilter.value,
        keyword: keyword.value,
        page: page.value,
        size: pageSize.value,
      })
      items.value = result.items
      total.value = result.total
      totalPages.value = result.totalPages
      // 总数变少时当前页可能越界（筛掉、终止），夹回最后一页而不是留一张空表
      if (result.totalPages > 0 && page.value > result.totalPages) {
        page.value = result.totalPages
      }
    } catch (e) {
      error.value = messageOf(e)
      items.value = []
      total.value = 0
      totalPages.value = 0
    } finally {
      loading.value = false
    }
  }

  /** 项目列表。失败时向上抛，由调用方决定怎么提示 —— 同一个请求在两个位置的失败语义不一样。 */
  const loadProjects = async () => {
    const loaded = await listProjects()
    projects.value = loaded
    // 启动时下拉可能先于列表返回而瞬时空置选中值，项目列表到手后按持久化 ID 再恢复一次；
    // 只有项目确实不在当前列表里（被删或换了库）才退回「全部」。
    const resolved = resolveProjectSelection(
      projectId.value,
      readProjectSelection(),
      loaded.map((project) => project.id),
    )
    projectId.value = resolved
    // 列表为空或项目已失效时，显式清掉旧持久化值；正常的同值赋值不会触发上面的 watch。
    writeProjectSelection(resolved)
  }

  const loadStats = async () => {
    try {
      counts.value = { ...EMPTY_COUNTS, ...(await getTaskStats()) }
    } catch {
      // 角标是锦上添花，取不到就维持上一次的值，不打扰用户
    }
  }

  const select = async (row: TaskSummary) => {
    selected.value = row
    detail.value = null
    try {
      detail.value = await getTask(row.id)
    } catch (e) {
      error.value = messageOf(e)
    }
  }

  /** 列表与统计一起重拉；选中行还在结果里时同步摘要和详情，不清空右侧面板。 */
  const refresh = async () => {
    const selectedId = selected.value?.id ?? null
    await Promise.all([load(), loadStats()])
    if (selectedId === null || selected.value?.id !== selectedId) return

    const fresh = items.value.find((row) => row.id === selectedId)
    if (!fresh) {
      clearSelection()
      return
    }

    selected.value = fresh
    try {
      const freshDetail = await getTask(fresh.id)
      if (selected.value?.id === fresh.id) detail.value = freshDetail
    } catch (e) {
      if (selected.value?.id === fresh.id) error.value = messageOf(e)
    }
  }

  const clearSelection = () => {
    selected.value = null
    detail.value = null
  }

  /** 精确聚焦一条任务：清掉可能把它挡住的筛选，并回到第一页。 */
  const focusTask = (taskId: number) => {
    statusFilter.value = 'ALL'
    levelFilter.value = 'ALL'
    projectId.value = null
    keyword.value = ''
    taskIdFilter.value = taskId
    page.value = 1
  }

  const clearTaskFocus = () => {
    taskIdFilter.value = null
    page.value = 1
  }

  /** 返回 null 表示成功，否则是给用户看的失败原因。 */
  const retry = async (id: number): Promise<string | null> => {
    try {
      await retryTask(id)
      await refresh()
      return null
    } catch (e) {
      return messageOf(e)
    }
  }

  /** 派发执行。返回的是「已认领」，真正的结果要等执行记录出来。 */
  const dispatch = async (id: number): Promise<string | null> => {
    try {
      await dispatchTask(id)
      await Promise.all([load(), loadStats()])
      // 详情用列表里那一行重新取：状态可能变成 QUEUED / RUNNING，本地改字段会让界面与库分叉
      const fresh = items.value.find((row) => row.id === id)
      if (fresh) await select(fresh)
      return null
    } catch (e) {
      return messageOf(e)
    }
  }

  /** 批量派发结果只用于界面提示；成功和失败都已写入后端，随后统一重拉列表与统计。 */
  const dispatchMany = async (ids: number[]): Promise<BatchDispatchOutcome> => {
    const selectedId = selected.value?.id ?? null
    try {
      const result = await dispatchTasks(ids)
      await Promise.all([load(), loadStats()])
      if (
        selectedId !== null &&
        selected.value?.id === selectedId &&
        result.dispatchedTaskIds.includes(selectedId)
      ) {
        const fresh = items.value.find((row) => row.id === selectedId)
        if (fresh) await select(fresh)
      }
      return { ok: true, result }
    } catch (e) {
      return { ok: false, message: messageOf(e) }
    }
  }

  const cancel = async (id: number): Promise<string | null> => {
    try {
      await cancelTask(id)
      await Promise.all([load(), loadStats()])
      return null
    } catch (e) {
      return messageOf(e)
    }
  }

  /** 编辑任务内容。成功后统一重拉，确保标题、优先级排序与详情同步收敛。 */
  const edit = async (id: number, input: UpdateTaskInput): Promise<string | null> => {
    try {
      await updateTask(id, input)
      await refresh()
      return null
    } catch (e) {
      return messageOf(e)
    }
  }

  /** 删除单个任务。成功后如果删的是当前选中行，关闭右侧详情。 */
  const remove = async (id: number): Promise<string | null> => {
    try {
      await deleteTask(id)
      const selectedId = selected.value?.id ?? null
      await Promise.all([load(), loadStats()])
      if (selectedId === id) clearSelection()
      return null
    } catch (e) {
      return messageOf(e)
    }
  }

  /** 批量删除结果用于提示；后端已逐项处理，随后统一重拉列表与统计。 */
  const removeMany = async (ids: number[]): Promise<BatchDeleteOutcome> => {
    const selectedId = selected.value?.id ?? null
    try {
      const result = await deleteTasks(ids)
      await Promise.all([load(), loadStats()])
      if (selectedId !== null && result.deletedTaskIds.includes(selectedId)) {
        clearSelection()
      }
      return { ok: true, result }
    } catch (e) {
      return { ok: false, message: messageOf(e) }
    }
  }

  // 筛选条件一变就回第 1 页：停在原页码上会得到一张空表，而且看不出为什么空
  watch([statusFilter, levelFilter, projectId, taskIdFilter, keyword], () => {
    page.value = 1
    void load()
  })
  watch(
    projectId,
    (value) => {
      // 首屏项目列表还没回来时，下拉可能发出一次瞬时空值；不能拿它覆盖持久化选择。
      if (projects.value.length === 0) return
      writeProjectSelection(value)
    },
    { flush: 'sync' },
  )
  watch([page, pageSize], () => {
    void load()
  })

  return {
    items,
    total,
    totalPages,
    page,
    pageSize,
    statusFilter,
    levelFilter,
    projectId,
    projects,
    taskIdFilter,
    keyword,
    loading,
    error,
    counts,
    taskTotal,
    selected,
    detail,
    load,
    loadProjects,
    loadStats,
    select,
    refresh,
    clearSelection,
    focusTask,
    clearTaskFocus,
    retry,
    dispatch,
    dispatchMany,
    cancel,
    edit,
    remove,
    removeMany,
  }
})
