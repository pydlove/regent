import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'

import { ApiError } from '@/api/http'
import { listProjects } from '@/api/projects'
import { cancelTask, dispatchTask, getTask, getTaskStats, listTasks, retryTask } from '@/api/tasks'
import type { Project, TaskDetail, TaskSummary } from '@/types/api'
import type { TaskStatus } from '@/types/task'

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

export const useTaskStore = defineStore('tasks', () => {
  const items = ref<TaskSummary[]>([])
  const total = ref(0)
  const totalPages = ref(0)
  const page = ref(1)
  const pageSize = ref(20)

  const statusFilter = ref<TaskStatus | 'ALL'>('ALL')
  /** null 表示全部项目。工具栏的筛选与新建任务弹框共用 projects 这一份列表 */
  const projectId = ref<number | null>(null)
  const projects = ref<Project[]>([])
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
        projectId: projectId.value,
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
    // 正在筛选的项目不在了（被删、或者换了库）就退回「全部」：
    // 留着一个查不到东西的条件，界面会一直空着，而且看不出为什么
    if (projectId.value !== null && !loaded.some((project) => project.id === projectId.value)) {
      projectId.value = null
    }
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

  const clearSelection = () => {
    selected.value = null
    detail.value = null
  }

  /** 返回 null 表示成功，否则是给用户看的失败原因。 */
  const retry = async (id: number): Promise<string | null> => {
    try {
      await retryTask(id)
      await Promise.all([load(), loadStats()])
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
      // 详情用列表里那一行重新取：状态已经从 PENDING 变成 RUNNING，本地改字段会让界面与库分叉
      const fresh = items.value.find((row) => row.id === id)
      if (fresh) await select(fresh)
      return null
    } catch (e) {
      return messageOf(e)
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

  // 筛选条件一变就回第 1 页：停在原页码上会得到一张空表，而且看不出为什么空
  watch([statusFilter, projectId, keyword], () => {
    page.value = 1
    void load()
  })
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
    projectId,
    projects,
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
    clearSelection,
    retry,
    dispatch,
    cancel,
  }
})
