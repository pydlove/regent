import { deleteRequest, get, post, postJson, putJson } from '@/api/http'
import type {
  PageResponse,
  DailyTokenUsagePoint,
  HourlyTokenUsagePoint,
  TaskBatchDeleteResult,
  TaskBatchDispatchResult,
  TaskAdjustment,
  TaskDetail,
  TaskRunSummary,
  TaskSummary,
  TaskTokenUsage,
  TokenUsageRange,
  TaskTrendPoint,
} from '@/types/api'
import type { DecisionLevel, TaskStatus } from '@/types/task'

export type TaskListParams = {
  status?: TaskStatus | 'ALL'
  /** 任务最新决策的生效等级；'ALL' 表示不筛选 */
  level?: DecisionLevel | 'ALL'
  /** 项目筛选；不传表示全部项目 */
  projectId?: number | null
  /** 精确任务号；用于从决策等页面直接定位单条任务 */
  taskId?: number | null
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
    taskId: params.taskId,
    keyword: params.keyword,
    page: params.page,
    size: params.size,
  })

export const getTask = (id: number) => get<TaskDetail>(`/tasks/${id}`)

/** 近 N 天收场趋势（含今天），后端逐日补零；传 projectId 时只取该项目。 */
export const getTaskTrend = (days = 7, projectId: number | null = null) =>
  get<TaskTrendPoint[]>('/tasks/trend', { days, projectId })

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

export type AddTaskAdjustmentInput =
  | { sourceTaskId: number; content?: never }
  | { sourceTaskId?: never; content: string }

/** 给执行中的任务追加调整方向；来源任务与临时文本由联合类型保证二选一。 */
export const addTaskAdjustment = (id: number, input: AddTaskAdjustmentInput) =>
  postJson<TaskAdjustment>(`/tasks/${id}/adjustments`, input)

/** 调整历史按创建顺序返回，APPLIED 项会带实际下发到的运行记录。 */
export const listTaskAdjustments = (id: number) =>
  get<TaskAdjustment[]>(`/tasks/${id}/adjustments`)

export const getTaskStats = (projectId: number | null = null) =>
  get<Record<TaskStatus, number>>('/tasks/stats', { projectId })

/** 已结算 token 汇总；不传 projectId 时统计全部项目。 */
export const getTaskTokenUsage = (projectId: number | null = null) =>
  get<TaskTokenUsage>('/tasks/token-usage', { projectId })

/** 按日期查看已结算 Token；日期范围和切天口径都由后端统一处理。 */
export const getDailyTokenUsage = (
  range: TokenUsageRange,
  projectId: number | null = null,
) => get<DailyTokenUsagePoint[]>('/tasks/token-usage/daily', { range, projectId })

/** 当天与滚动 24 小时按整点查看 Token；其它范围应走日聚合接口。 */
export const getHourlyTokenUsage = (
  range: TokenUsageRange,
  projectId: number | null = null,
) => get<HourlyTokenUsagePoint[]>('/tasks/token-usage/hourly', { range, projectId })

/** 失败重试或已终止任务重新执行，统一回到 PENDING。 */
export const retryTask = (id: number) => post<TaskDetail>(`/tasks/${id}/retry`)

export const cancelTask = (id: number) => post<TaskDetail>(`/tasks/${id}/cancel`)

/** 删除待执行、排队中或已终止任务。其他状态由后端领域守卫拒绝。 */
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
