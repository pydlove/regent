import { get } from '@/api/http'
import type {
  GitCommitDetail,
  GitCommitPage,
  GitCommitStatus,
  GitCommitSummary,
} from '@/types/api'

export type GitCommitFilters = {
  projectId?: number
  taskId?: number
  status?: GitCommitStatus
  keyword?: string
  from?: string
  to?: string
  page?: number
  size?: number
}

export const getTaskGitCommits = (taskId: number) =>
  get<GitCommitSummary[]>(`/tasks/${taskId}/git-commits`)

export const getGitCommits = (filters: GitCommitFilters = {}) => {
  const query = new URLSearchParams()
  if (filters.projectId) query.set('projectId', String(filters.projectId))
  if (filters.taskId) query.set('taskId', String(filters.taskId))
  if (filters.status) query.set('status', filters.status)
  if (filters.keyword?.trim()) query.set('keyword', filters.keyword.trim())
  if (filters.from) query.set('from', filters.from)
  if (filters.to) query.set('to', filters.to)
  query.set('page', String(filters.page ?? 1))
  query.set('size', String(filters.size ?? 20))
  return get<GitCommitPage>(`/git-commits?${query.toString()}`)
}

export const getGitCommitDetail = (id: number) =>
  get<GitCommitDetail>(`/git-commits/${id}`)
