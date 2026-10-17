import { listBugs } from '@/api/bugs'
import { listDecisions } from '@/api/decisions'
import { getTaskStats, getTaskTokenUsage, getTaskTrend, listTasks } from '@/api/tasks'
import type { Bug } from '@/types/bug'
import type { DecisionSummary, TaskSummary, TaskTokenUsage, TaskTrendPoint } from '@/types/api'
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
}

/**
 * 看板只从各上下文已有的查询能力取数，不在前端复制业务判断。
 * 页面需要同时回答「平台整体怎么样」和「现在先处理什么」，所以统计与最近事项并行发出。
 */
export async function getDashboardSnapshot(): Promise<DashboardSnapshot> {
  const [tasks, tokenUsage, trend, pending, pendingL2, pendingL3, openBugs, running, queued] =
    await Promise.all([
      getTaskStats(),
      getTaskTokenUsage(),
      getTaskTrend(7),
      listDecisions({ scope: 'PENDING', page: 1, size: 4 }),
      listDecisions({ scope: 'PENDING', level: 'L2', page: 1, size: 1 }),
      listDecisions({ scope: 'PENDING', level: 'L3', page: 1, size: 1 }),
      listBugs({ scope: 'OPEN', page: 1, size: 4 }),
      listTasks({ status: 'RUNNING', page: 1, size: 4 }),
      listTasks({ status: 'QUEUED', page: 1, size: 4 }),
    ])

  return {
    tasks,
    tokenUsage,
    trend,
    pendingDecisions: pending.total,
    pendingL2Decisions: pendingL2.total,
    pendingL3Decisions: pendingL3.total,
    openBugs: openBugs.total,
    activeTasks: [...running.items, ...queued.items].slice(0, 5),
    pendingDecisionItems: pending.items,
    openBugItems: openBugs.items,
  }
}
