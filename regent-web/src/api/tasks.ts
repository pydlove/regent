import { deleteRequest, get, post, postJson, putJson } from '@/api/http'
import type {
  PageResponse,
  TaskBatchDeleteResult,
  TaskBatchDispatchResult,
  TaskDetail,
  TaskRunSummary,
  TaskSummary,
  TaskTokenUsage,
  TaskTrendPoint,
} from '@/types/api'
import type { DecisionLevel, TaskStatus } from '@/types/task'

export type TaskListParams = {
  status?: TaskStatus | 'ALL'
  /** 任务最新决策的生效等级；'ALL' 表示不筛选 */
  level?: DecisionLevel | 'ALL'
  /** 项目筛选；不传表示全部项目 */
  projectId?: number | null
  keyword?: string
  page?: number
  size?: number
}

export const listTasks = (params: TaskListParams = {}) =>
  get<PageResponse<TaskSummary>>('/tasks', {
    // 'ALL' 与「全部项目」在界面上是选项，后端用「不传」表达同一件事，转换在这一层完成
    status: params.status === 'ALL' ? undefined : params.status,
    level: params.level === 'ALL' ? undefined : params.level,
    projectId: params.projectId,
    keyword: params.keyword,
    page: params.page,
    size: params.size,
  })

export const getTask = (id: number) => get<TaskDetail>(`/tasks/${id}`)

/** 近 N 天收场趋势（含今天），后端逐日补零，前端直接画。 */
export const getTaskTrend = (days = 7) => get<TaskTrendPoint[]>('/tasks/trend', { days })

export type CreateTaskInput = {
  projectId: number
  prompt: string
  /** 排队优先级，数值越大越先派发；不传按 0 处理 */
  priority?: number
}

/**
 * 人工录入任务（来源 MANUAL）。返回的是新建任务本身，前端据此直接选中新行。
 *
 * 不传标题：标题由后端从指令首行推导（剥掉 Markdown 记号、超长截断）。
 * 让人填一个「其实只是指令摘要」的标题，等于让他抄一遍自己刚写的话。
 */
export const createTask = (input: CreateTaskInput) => postJson<TaskDetail>('/tasks', input)

export type UpdateTaskInput = {
  prompt: string
  /** 排队优先级，数值越大越先派发 */
  priority: number
}

/** 编辑指令与优先级；已完成任务由后端领域守卫拒绝。 */
export const updateTask = (id: number, input: UpdateTaskInput) =>
  putJson<TaskDetail>(`/tasks/${id}`, input)

export const getTaskStats = () => get<Record<TaskStatus, number>>('/tasks/stats')

/** 全平台已结算 token 汇总，供工作台展示。 */
export const getTaskTokenUsage = () => get<TaskTokenUsage>('/tasks/token-usage')

/** 失败重试或已终止任务重新执行，统一回到 PENDING。 */
export const retryTask = (id: number) => post<TaskDetail>(`/tasks/${id}/retry`)

export const cancelTask = (id: number) => post<TaskDetail>(`/tasks/${id}/cancel`)

/** 删除待执行任务。其他状态由后端领域守卫拒绝。 */
export const deleteTask = (id: number) => deleteRequest(`/tasks/${id}`)

/** 批量删除。单个任务失败不会阻断其他任务，结果里逐项返回。 */
export const deleteTasks = (taskIds: number[]) =>
  postJson<TaskBatchDeleteResult>('/tasks/batch-delete', { taskIds })

/** 派发执行。返回的是「已认领」的状态，真正的执行在后台跑，结果进运行记录。 */
export const dispatchTask = (id: number) => post<TaskDetail>(`/tasks/${id}/dispatch`)

/** 批量派发。单个任务失败不会阻断其他任务，结果里逐项返回。 */
export const dispatchTasks = (taskIds: number[]) =>
  postJson<TaskBatchDispatchResult>('/tasks/dispatch', { taskIds })

/** 运行记录：每次尝试一行，最新的在前。 */
export const listTaskRuns = (id: number) => get<TaskRunSummary[]>(`/tasks/${id}/runs`)
