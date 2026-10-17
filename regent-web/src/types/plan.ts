/** 计划生命周期。计划只执行一次，不会在完成后自动重开。 */
export type PlanStatus = 'SCHEDULED' | 'RUNNING' | 'COMPLETED'

/** 一次计划执行的整体结果。 */
export type PlanRunStatus = 'RUNNING' | 'COMPLETED' | 'PARTIAL' | 'FAILED'

/** 计划内单条任务在本轮执行中的状态。 */
export type PlanTaskStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'BLOCKED'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'CANCELLED'
  | 'SKIPPED'

export type PlanTrigger = 'SCHEDULED' | 'MANUAL'

export const planStatusLabel = (status: PlanStatus) =>
  status === 'SCHEDULED' ? '待执行' : status === 'RUNNING' ? '执行中' : '已完成'

export const planTaskStatusLabel = (status: PlanTaskStatus | null) => {
  if (status === null) return '尚未执行'
  return {
    PENDING: '待派发',
    RUNNING: '执行中',
    BLOCKED: '等待决策',
    SUCCEEDED: '成功',
    FAILED: '失败',
    CANCELLED: '已终止',
    SKIPPED: '已跳过',
  }[status]
}

export const planRunStatusLabel = (status: PlanRunStatus) => {
  return {
    RUNNING: '执行中',
    COMPLETED: '全部成功',
    PARTIAL: '部分成功',
    FAILED: '未成功',
  }[status]
}
