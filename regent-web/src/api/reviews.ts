import { get, patchJson, post, postJson } from '@/api/http'
import type {
  ReviewDecisionAction,
  ReviewDecisionType,
  ReviewDiff,
  ReviewFindingStatus,
  ReviewGitCommit,
  ReviewSnapshot,
  ReviewUnitDecisionStatus,
  TaskReview,
} from '@/types/api'

export const getTaskReview = (taskId: number) =>
  get<TaskReview>(`/tasks/${taskId}/review`)

export const generateTaskReview = (taskId: number) =>
  post<TaskReview>(`/tasks/${taskId}/review/generate`)

export const regenerateTaskReview = (taskId: number) =>
  post<TaskReview>(`/tasks/${taskId}/review/regenerate`)

export const markReviewUnitViewed = (taskId: number, unitId: number) =>
  post<ReviewSnapshot>(`/tasks/${taskId}/review/units/${unitId}/viewed`)

export const decideReviewUnit = (
  taskId: number,
  unitId: number,
  decision: ReviewUnitDecisionStatus,
  comment: string | null,
) => postJson<ReviewSnapshot>(`/tasks/${taskId}/review/units/${unitId}/decision`, {
  decision,
  comment,
})

export const updateReviewFinding = (
  taskId: number,
  findingId: number,
  status: ReviewFindingStatus,
) => patchJson<ReviewSnapshot>(`/tasks/${taskId}/review/findings/${findingId}`, { status })

export const decideTaskReview = (
  taskId: number,
  decision: ReviewDecisionType,
  comment: string | null,
) => postJson<ReviewDecisionAction>(`/tasks/${taskId}/review/decisions`, { decision, comment })

export const getTaskReviewDiff = (taskId: number) =>
  get<ReviewDiff>(`/tasks/${taskId}/review/diff`)

export const getTaskReviewGitCommit = (taskId: number) =>
  get<ReviewGitCommit>(`/tasks/${taskId}/review/git-commit`)

export const commitTaskReview = (taskId: number, message: string) =>
  postJson<ReviewGitCommit>(`/tasks/${taskId}/review/git-commit`, { message })
