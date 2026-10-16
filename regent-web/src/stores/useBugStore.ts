import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import {
  canMarkFixed,
  canRequestFix,
  canVerify,
  canWontFix,
  isResolved,
  type Bug,
  type BugSeverity,
} from '@/types/bug'

/*
 * 缺陷单。和 useTaskStore 同一套写法：迁移收在方法里，方法开头先过守卫。
 *
 * 后端还没有缺陷查询接口，所以这里**从空列表开始**，页面如实呈现空态 ——
 * 不用种子数据充数：界面上出现的东西必须真的存在。等 Bug 查询接口落地后，
 * 在这里加一个 load() 把它接上，下面的写路径方法原样可用。
 *
 * 「派生修复任务」这一步刻意不放在这里 —— 它要同时改 Bug 和 Task 两个聚合，
 * 属于应用服务的编排（见 AGENTS.md 硬约束 3）。所以这里只实现 requestFix 这半个动作，
 * 由调用方先向 Task 侧要一个新任务，再把 id 交进来。
 */

const clock = () => new Date().toTimeString().slice(0, 8)

export const useBugStore = defineStore('bugs', () => {
  const bugs = ref<Bug[]>([])

  /** 尚需人管的总数：含「待验证」——那一档正等着人来验，藏起来等于没人验。 */
  const openBugs = computed(() => bugs.value.filter((b) => !isResolved(b.status)))

  const byId = (id: number) => {
    const bug = bugs.value.find((b) => b.id === id)
    if (!bug) throw new Error(`缺陷单不存在：${id}`)
    return bug
  }

  /** 守卫不过就抛，对应后端的 requireState(...)。视图先用 types/bug.ts 的 can* 判断再调。 */
  const require = (ok: boolean, message: string) => {
    if (!ok) throw new Error(message)
  }

  /** 报缺陷。接后端后由执行器调（BugReported）；列表为空时序号从 1 起。 */
  const report = (title: string, severity: BugSeverity, sourceTaskId: number, reproduction: string) => {
    const id = bugs.value.reduce((max, b) => Math.max(max, b.id), 0) + 1
    const bug: Bug = {
      id,
      title,
      severity,
      status: 'OPEN',
      sourceTaskId,
      fixTaskId: null,
      reproduction,
      reportedAt: clock(),
    }
    bugs.value = [bug, ...bugs.value]
    return bug
  }

  /** 记录已派生的修复任务。任务本身由调用方向 Task 侧创建，这里只回填外键。 */
  const requestFix = (id: number, fixTaskId: number) => {
    const bug = byId(id)
    require(canRequestFix(bug.status), '只有未处理的缺陷可以派发修复任务')
    bug.status = 'FIXING'
    bug.fixTaskId = fixTaskId
  }

  /** 修复任务跑完 ≠ 问题没了，所以只到 FIXED，还要人验证过才 CLOSED。 */
  const markFixed = (id: number) => {
    const bug = byId(id)
    require(canMarkFixed(bug.status), '只有修复中的缺陷可以标记为已修复')
    bug.status = 'FIXED'
  }

  const verify = (id: number) => {
    const bug = byId(id)
    require(canVerify(bug.status), '只有已修复的缺陷可以验证关闭')
    bug.status = 'CLOSED'
  }

  const wontFix = (id: number, reason: string) => {
    const bug = byId(id)
    require(canWontFix(bug.status), '已关闭的缺陷不能重复处置')
    bug.status = 'WONTFIX'
    bug.wontFixReason = reason
  }

  return { bugs, openBugs, byId, report, requestFix, markFixed, verify, wontFix }
})
