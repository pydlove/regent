/*
 * 缺陷单。词汇对齐 ARCHITECTURE.md 的 bug 限界上下文：聚合根 `Bug`、值对象 `Severity`、
 * 复现步骤 `Reproduction`，以及 BugReported / BugFixTaskRequested 两个事件。
 *
 * 状态机（Bug 是支撑域，故意做得比 Task 简单：它没有「回到上一步」这种需求）：
 *
 *   OPEN ──requestFix──▶ FIXING ──fixDone──▶ FIXED ──verify──▶ CLOSED
 *     │                    │                   │
 *     └──────────── wontFix ──────────────────┘         ▶ WONTFIX
 *
 * 「已修复」和「已关闭」必须分开：FIXED 只说明修复任务跑完了，还不能证明问题真的没了；
 * 合成一个状态，就等于让执行器自己宣布自己合格。
 */

export type BugSeverity = 'BLOCKER' | 'MAJOR' | 'MINOR' | 'TRIVIAL'

export type BugStatus = 'OPEN' | 'FIXING' | 'FIXED' | 'CLOSED' | 'WONTFIX'

/** 终态：不再有任何操作可做 */
export const BUG_TERMINAL_STATUSES: readonly BugStatus[] = ['CLOSED', 'WONTFIX']

/*
 * 「已解决」= 进了终态。
 * FIXED（待验证）不算解决 —— 它正等人验，是最不该被默认列表藏起来的一档。
 * 列表筛选和侧边栏角标必须用同一个判断，否则角标说 3 条、点进去看到 2 条。
 */
export const isResolved = (s: BugStatus) => BUG_TERMINAL_STATUSES.includes(s)

export type Bug = {
  id: number
  title: string
  severity: BugSeverity
  status: BugStatus
  /** 缺陷是在哪条任务里发现的。只存 ID，不持有任务对象 */
  sourceTaskId: number
  /** 派生出来的修复任务，没有则为 null */
  fixTaskId: number | null
  /** 复现步骤。缺陷单没有这个字段，就等于让人凭空猜 */
  reproduction: string
  reportedAt: string
  /** 判为不修复时留的理由 */
  wontFixReason?: string
}

/*
 * 守卫。和 Task 一样，这是 UI 里「按钮能不能点」的唯一依据，
 * 视图不许自己写 `status === 'OPEN'`。
 */
export const canRequestFix = (s: BugStatus) => s === 'OPEN'
export const canMarkFixed = (s: BugStatus) => s === 'FIXING'
export const canVerify = (s: BugStatus) => s === 'FIXED'
export const canWontFix = (s: BugStatus) => !BUG_TERMINAL_STATUSES.includes(s)
