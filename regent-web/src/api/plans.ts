import { get, postJson } from '@/api/http'
import type { PlanDetail, PlanRunDetail, PlanRunSummary, PlanSummary } from '@/types/api'

export type CreatePlanInput = {
  name: string
  taskIds: number[]
  /** ISO-8601 UTC；为空时后端立即安排到下一拍扫描 */
  scheduledAt?: string | null
}

export const listPlans = () => get<PlanSummary[]>('/plans')

export const getPlan = (id: number) => get<PlanDetail>(`/plans/${id}`)

export const getPlanRun = (id: number) => get<PlanRunDetail>(`/plan-runs/${id}`)

export const createPlan = (input: CreatePlanInput) => postJson<PlanSummary>('/plans', input)

/** 人工立即执行；计划原定时时间不再等待。 */
export const runPlan = (id: number) => postJson<PlanRunSummary>(`/plans/${id}/run`, {})
