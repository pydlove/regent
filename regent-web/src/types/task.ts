/*
 * 前端的领域词汇。刻意与后端 ARCHITECTURE.md 2.3 的 TaskStatus 状态机逐字对齐：
 *
 *   PENDING ──queue──▶ QUEUED ──dispatch──▶ RUNNING ──success──▶ DONE
 *      │                                      │
 *      └────────────dispatch────────────────┘
 *                                             ├──needDecision──▶ BLOCKED ──approved──▶ RUNNING (resume)
 *                                             │                     │
 *                                             │                     └──rejected──▶ CANCELLED
 *                                             └──error──▶ FAILED ──retry──▶ PENDING
 *                                                                       CANCELLED ──重新执行──▶ PENDING
 *
 * 对齐的意义不是好看：状态名一旦两边不一致，接真实接口时就要在每个边界做翻译，
 * 而翻译层正是「前端显示的和后端存的不是一回事」这类 bug 的温床。
 *
 * 词汇与守卫放在这里，**数据形状**放 types/api.ts —— 后者是后端契约的镜像，
 * 会随接口变化；前者是领域概念，只在状态机改动时才变。
 */

export type DecisionLevel = 'L0' | 'L1' | 'L2' | 'L3'

export type TaskStatus = 'PENDING' | 'QUEUED' | 'RUNNING' | 'BLOCKED' | 'DONE' | 'FAILED' | 'CANCELLED'

export type TaskSource = 'REQUIREMENT' | 'BUG_FIX' | 'MANUAL'

/*
 * 单次尝试的结局。对应后端的 RunStatus：
 *
 *   RUNNING ──出参说 DONE──▶ SUCCESS
 *      │
 *      ├──出参说 FAILED／出参读不懂／进程崩了──▶ FAILED
 *      ├──出参说 BLOCKED──▶ BLOCKED（任务挂起等人，本次尝试到此为止）
 *      ├──平台重启导致进程消失──▶ INTERRUPTED（不是业务失败，下次启动续跑）
 *      └──超过墙钟上限被杀──▶ TIMEOUT
 *
 * 它和 TaskStatus 是两件事：任务的状态是「现在能不能干活」，尝试的结局是
 * 「这一次花了多少、改了什么、结论是什么」。同一个任务可以有多次失败的尝试而状态一直是 RUNNING。
 */
export type RunStatus = 'RUNNING' | 'SUCCESS' | 'FAILED' | 'BLOCKED' | 'INTERRUPTED' | 'TIMEOUT'

/** 还在跑：运行记录面板据此决定要不要自动刷新。 */
export const runInProgress = (s: RunStatus) => s === 'RUNNING'

/** 没成：失败与超时在界面上都算「这一次白跑了」，只有原因不同。 */
export const runIsFailure = (s: RunStatus) => s === 'FAILED' || s === 'TIMEOUT'

/** 不再自行流转：DONE 不可重开；CANCELLED 只允许人工重新执行 */
export const TERMINAL_STATUSES: readonly TaskStatus[] = ['DONE', 'CANCELLED']

/*
 * 守卫。这几个函数是 UI 里「按钮能不能点」的唯一依据 —— 不要在视图里写
 * `status === 'BLOCKED'` 这种判断，那样状态机就散落到各个模板里了。
 * 后端对应的是 Task 聚合里 requireState(...) 的那几个方法；点下去被拒绝也没关系，
 * 因为真正的裁决权在后端，前端守卫只负责别把明显不可用的按钮摆出来。
 */
export const canDispatch = (s: TaskStatus) => s === 'PENDING'
export const canBlock = (s: TaskStatus) => s === 'RUNNING'
export const canResume = (s: TaskStatus) => s === 'BLOCKED'
export const canReject = (s: TaskStatus) => s === 'BLOCKED'
export const canCancel = (s: TaskStatus) => !TERMINAL_STATUSES.includes(s)
export const canRetry = (s: TaskStatus) => s === 'FAILED' || s === 'CANCELLED'
export const retryActionLabel = (s: TaskStatus) => (s === 'CANCELLED' ? '重新执行' : '重试')
export const canDelete = (s: TaskStatus) =>
  s === 'PENDING' || s === 'QUEUED' || s === 'CANCELLED'
export const canBatchDispatch = (s: TaskStatus) => s === 'PENDING' || s === 'QUEUED'
export const canEdit = (s: TaskStatus) => s !== 'DONE'
