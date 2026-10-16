import { get, postJson } from '@/api/http'
import type { DecisionSummary, PageResponse } from '@/types/api'
import type { DecisionLevel } from '@/types/task'

export type DecisionListParams = {
  level?: DecisionLevel | 'ALL'
  scope?: 'ALL' | 'PENDING' | 'RESOLVED'
  keyword?: string
  page?: number
  size?: number
}

export const listDecisions = (params: DecisionListParams = {}) =>
  get<PageResponse<DecisionSummary>>('/decisions', {
    level: params.level === 'ALL' ? undefined : params.level,
    scope: params.scope,
    keyword: params.keyword,
    page: params.page,
    size: params.size,
  })

export const getDecision = (id: number) => get<DecisionSummary>(`/decisions/${id}`)

/** 审批请求体。三个字段都可空：choice 空 = 采纳建议，token 空 = 控制台路径。 */
export type DecisionReviewPayload = {
  choice?: string
  note?: string
  token?: string
}

/** 批准。返回的是库里的最新状态，不是「你点了什么」—— 并发下这一步能立刻显出真实结论。 */
export const approveDecision = (id: number, payload: DecisionReviewPayload = {}) =>
  postJson<DecisionSummary>(`/decisions/${id}/approve`, payload)

/** 驳回。理由必填，空理由会被聚合守卫挡回来（400）。 */
export const rejectDecision = (id: number, payload: DecisionReviewPayload = {}) =>
  postJson<DecisionSummary>(`/decisions/${id}/reject`, payload)
