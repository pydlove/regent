import { get, post, postJson } from '@/api/http'
import type { Bug, BugSeverity } from '@/types/bug'
import type { PageResponse } from '@/types/api'

export type BugScope = 'ALL' | 'OPEN' | 'RESOLVED'

export type BugListParams = {
  severity?: BugSeverity | 'ALL'
  scope?: BugScope
  projectId?: number | null
  keyword?: string
  page?: number
  size?: number
}

export const listBugs = (params: BugListParams = {}) =>
  get<PageResponse<Bug>>('/bugs', {
    severity: params.severity === 'ALL' ? undefined : params.severity,
    scope: params.scope,
    projectId: params.projectId,
    keyword: params.keyword,
    page: params.page,
    size: params.size,
  })

export type CreateBugInput = {
  projectId: number
  title: string
  severity: BugSeverity
  reproduction?: string
  filePath?: string
  detail?: string
}

export const createBug = (input: CreateBugInput) => postJson<Bug>('/bugs', input)

export const requestBugFix = (bugId: number) => post<Bug>(`/bugs/${bugId}/fix-task`)

export const markBugFixed = (bugId: number) => post<Bug>(`/bugs/${bugId}/mark-fixed`)

export const verifyBug = (bugId: number) => post<Bug>(`/bugs/${bugId}/verify`)

export const wontFixBug = (bugId: number, reason: string) =>
  postJson<Bug>(`/bugs/${bugId}/wont-fix`, { reason })
