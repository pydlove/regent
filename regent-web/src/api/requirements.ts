import { deleteRequest, get, postJson, postJsonWithTimeout, putJson } from '@/api/http'
import type { Requirement, RequirementTaskDraft } from '@/types/api'

export type SaveRequirementInput = {
  projectId: number
  initiativeId?: number | null
  source?: 'MANUAL' | 'IMPORT' | 'AI'
  title: string
  rawText: string
  acceptanceText: string | null
  clarificationText: string | null
}

export const listRequirements = (projectId: number | null = null) =>
  get<Requirement[]>('/requirements', { projectId })

export const getRequirement = (id: number) =>
  get<Requirement>(`/requirements/${id}`)

export const createRequirement = (input: SaveRequirementInput) =>
  postJson<Requirement>('/requirements', input)

export const updateRequirement = (id: number, input: SaveRequirementInput) =>
  putJson<Requirement>(`/requirements/${id}`, input)

export const analyzeRequirement = (id: number) =>
  postJson<Requirement>(`/requirements/${id}/analyze`, {})

export const confirmRequirement = (id: number, tasks: RequirementTaskDraft[]) =>
  postJson<Requirement>(`/requirements/${id}/confirm`, tasks)

export const cancelRequirement = (id: number) =>
  postJson<Requirement>(`/requirements/${id}/cancel`, {})

export const assignRequirementVersion = (id: number, versionId: number | null) =>
  putJson<Requirement>(`/requirements/${id}/version`, { versionId })

export const reviewRequirement = (
  id: number,
  action: 'SUBMIT' | 'APPROVE' | 'REJECT',
  comment = '',
) => postJson<Requirement>(`/requirements/${id}/review`, { action, comment })

export type RequirementTrashItem = {
  id: number
  projectId: number
  versionId: number | null
  title: string
  rawText: string
  source: 'MANUAL' | 'IMPORT' | 'AI'
  deletedAt: string
  deletedBy: number | null
}

export const listRequirementTrash = (projectId: number) =>
  get<RequirementTrashItem[]>('/requirements/trash', { projectId })

export const deleteRequirement = (id: number) =>
  deleteRequest(`/requirements/${id}`)

export const restoreRequirement = (id: number) =>
  postJson<Requirement>(`/requirements/${id}/restore`, {})

export const generateRequirementsFromIdea = (
  projectId: number,
  initiativeId: number,
  idea: string,
) => postJsonWithTimeout<Requirement[]>('/requirements/ai-generate', {
  projectId,
  initiativeId,
  idea,
}, 210_000)
