import { listBugs } from '@/api/bugs'
import { listDecisions } from '@/api/decisions'
import { getTaskStats, getTaskTokenUsage, getTaskTrend, listTasks } from '@/api/tasks'
import type { Bug } from '@/types/bug'
import type {
  DecisionSummary,
  PageResponse,
  TaskSummary,
  TaskTokenUsage,
  TaskTrendPoint,
} from '@/types/api'
import type { TaskStatus } from '@/types/task'

export type DashboardSnapshot = {
  tasks: Record<TaskStatus, number>
  tokenUsage: TaskTokenUsage
  pendingDecisions: number
  pendingL2Decisions: number
  pendingL3Decisions: number
  openBugs: number
  trend: TaskTrendPoint[]
  activeTasks: TaskSummary[]
  pendingDecisionItems: DecisionSummary[]
  openBugItems: Bug[]
  /** 读取失败的区块。页面照常展示其余数据，同时把缺失范围说清楚。 */
  unavailableSections: string[]
}

const EMPTY_TASKS: Record<TaskStatus, number> = {
  PENDING: 0,
  QUEUED: 0,
  RUNNING: 0,
  BLOCKED: 0,
  DONE: 0,
  FAILED: 0,
  CANCELLED: 0,
}

const EMPTY_TOKEN_USAGE: TaskTokenUsage = {
  input: 0,
  cachedInput: 0,
  output: 0,
  total: 0,
}

function emptyPage<T>(): PageResponse<T> {
  return { items: [], total: 0, page: 1, size: 0, totalPages: 0 }
}

type SectionResult<T> = {
  value: T
  failure: string | null
  error: unknown
}

async function loadSection<T>(
  label: string,
  loader: () => Promise<T>,
  fallback: T,
): Promise<SectionResult<T>> {
  try {
    return { value: await loader(), failure: null, error: null }
  } catch (error) {
    return { value: fallback, failure: label, error }
  }
}

/**
 * 工作台只从各上下文已有的查询能力取数，不在前端复制业务判断。
 * 页面需要同时回答「平台整体怎么样」和「现在先处理什么」，所以统计与最近事项并行发出。
 *
 * <p>九个请求是并列的展示数据，不应该因为其中一个超时就让整个首页永远停在骨架屏：
 * 单项失败时用空值占位，并在页面上列出缺失区块；只有全部失败才交给页面的错误态处理。
 * projectId 是整份快照的统一范围，避免同一张工作台上混进多个项目的数据。
 */
export async function getDashboardSnapshot(
  projectId: number | null = null,
): Promise<DashboardSnapshot> {
  const [
    tasks,
    tokenUsage,
    trend,
    pending,
    pendingL2,
    pendingL3,
    openBugs,
    running,
    queued,
  ] = await Promise.all([
    loadSection('任务统计', () => getTaskStats(projectId), EMPTY_TASKS),
    loadSection('Token 消耗', () => getTaskTokenUsage(projectId), EMPTY_TOKEN_USAGE),
    loadSection('执行趋势', () => getTaskTrend(7, projectId), []),
    loadSection('待批决策', () => listDecisions({
      scope: 'PENDING',
      projectId,
      page: 1,
      size: 4,
    }),
      emptyPage<DecisionSummary>()),
    loadSection('L2 决策', () => listDecisions({
      scope: 'PENDING',
      level: 'L2',
      projectId,
      page: 1,
      size: 1,
    }),
      emptyPage<DecisionSummary>()),
    loadSection('L3 决策', () => listDecisions({
      scope: 'PENDING',
      level: 'L3',
      projectId,
      page: 1,
      size: 1,
    }),
      emptyPage<DecisionSummary>()),
    loadSection('开放缺陷', () => listBugs({ scope: 'OPEN', projectId, page: 1, size: 4 }),
      emptyPage<Bug>()),
    loadSection('运行任务', () => listTasks({ status: 'RUNNING', projectId, page: 1, size: 4 }),
      emptyPage<TaskSummary>()),
    loadSection('排队任务', () => listTasks({ status: 'QUEUED', projectId, page: 1, size: 4 }),
      emptyPage<TaskSummary>()),
  ])

  const sections = [
    tasks,
    tokenUsage,
    trend,
    pending,
    pendingL2,
    pendingL3,
    openBugs,
    running,
    queued,
  ]
  const failures = sections.filter((section) => section.failure !== null)
  if (failures.length === sections.length) {
    throw failures[0].error
  }

  return {
    tasks: tasks.value,
    tokenUsage: tokenUsage.value,
    trend: trend.value,
    pendingDecisions: pending.value.total,
    pendingL2Decisions: pendingL2.value.total,
    pendingL3Decisions: pendingL3.value.total,
    openBugs: openBugs.value.total,
    activeTasks: [...running.value.items, ...queued.value.items].slice(0, 5),
    pendingDecisionItems: pending.value.items,
    openBugItems: openBugs.value.items,
    unavailableSections: failures.map((section) => section.failure as string),
  }
}
