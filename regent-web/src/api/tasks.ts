import { get, post, postJson } from '@/api/http'
import type { PageResponse, TaskDetail, TaskRunSummary, TaskSummary } from '@/types/api'
import type { TaskStatus } from '@/types/task'

export type TaskListParams = {
  status?: TaskStatus | 'ALL'
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
    projectId: params.projectId,
    keyword: params.keyword,
    page: params.page,
    size: params.size,
  })

export const getTask = (id: number) => get<TaskDetail>(`/tasks/${id}`)

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

export const getTaskStats = () => get<Record<TaskStatus, number>>('/tasks/stats')

export const retryTask = (id: number) => post<TaskDetail>(`/tasks/${id}/retry`)

export const cancelTask = (id: number) => post<TaskDetail>(`/tasks/${id}/cancel`)

/** 派发执行。返回的是「已认领」的状态，真正的执行在后台跑，结果进运行记录。 */
export const dispatchTask = (id: number) => post<TaskDetail>(`/tasks/${id}/dispatch`)

/** 运行记录：每次尝试一行，最新的在前。 */
export const listTaskRuns = (id: number) => get<TaskRunSummary[]>(`/tasks/${id}/runs`)
