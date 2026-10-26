import type { PlanRunItem, PlanRunSummary } from '@/types/api'

const ITEM_ORDER: Record<PlanRunItem['status'], number> = {
  FAILED: 0,
  BLOCKED: 1,
  CANCELLED: 2,
  SKIPPED: 3,
  RUNNING: 4,
  PENDING: 5,
  SUCCEEDED: 6,
}

/** 先看需要处理的结果，再看成功的任务；同状态下按任务号稳定排序。 */
export function orderPlanRunItems(items: readonly PlanRunItem[]): PlanRunItem[] {
  return [...items].sort(
    (left, right) =>
      ITEM_ORDER[left.status] - ITEM_ORDER[right.status] || left.taskId - right.taskId,
  )
}

export function planRunHeadline(run: PlanRunSummary): string {
  if (run.status === 'RUNNING') return `${run.progressCount} / ${run.taskCount} 个任务已收场`
  if (run.status === 'COMPLETED') return `${run.taskCount} 个任务全部成功`
  if (run.status === 'PARTIAL') {
    const unsuccessful = run.failedCount + run.cancelledCount + run.skippedCount
    return `${run.succeededCount} 个成功，${unsuccessful} 个未成功`
  }
  return run.taskCount === 0 ? '本轮没有任务' : '本轮没有任务成功'
}
