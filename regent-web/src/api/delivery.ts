import { get, postJson, putJson } from '@/api/http'
import type {
  Initiative,
  InitiativeInput,
  Release,
  ReleaseAction,
  ReleaseInput,
  RequirementDelivery,
  RequirementDeliveryAction,
} from '@/types/delivery'

export const listInitiatives = (projectId: number | null = null) =>
  get<Initiative[]>('/initiatives', { projectId })

export const createInitiative = (input: InitiativeInput) =>
  postJson<Initiative>('/initiatives', input)

export const updateInitiative = (id: number, input: InitiativeInput) =>
  putJson<Initiative>(`/initiatives/${id}`, input)

export const submitInitiative = (id: number) =>
  postJson<Initiative>(`/initiatives/${id}/submit`, {})

export const approveInitiative = (
  id: number,
  input: { comment?: string; meetingMinutes?: string } = {},
) => postJson<Initiative>(`/initiatives/${id}/approve`, input)

export const rejectInitiative = (
  id: number,
  input: { comment?: string; meetingMinutes?: string } = {},
) => postJson<Initiative>(`/initiatives/${id}/reject`, input)

export const listRequirementDeliveries = (projectId: number | null = null) =>
  get<RequirementDelivery[]>('/requirement-deliveries', { projectId })

export const createRequirementDelivery = (initiativeId: number, requirementId: number) =>
  postJson<RequirementDelivery>('/requirement-deliveries', { initiativeId, requirementId })

export type InitiativeRequirementInput = {
  title: string
  rawText: string
  acceptanceText: string | null
  clarificationText: string | null
}

export const createInitiativeRequirement = (
  initiativeId: number,
  input: InitiativeRequirementInput,
) => postJson<RequirementDelivery>(`/initiatives/${initiativeId}/requirements`, input)

export const actRequirementDelivery = (
  id: number,
  input: {
    action: RequirementDeliveryAction
    comment?: string
    designSummary?: string
    designContent?: string
  },
) => postJson<RequirementDelivery>(`/requirement-deliveries/${id}/actions`, input)

export const listReleases = (projectId: number | null = null) =>
  get<Release[]>('/releases', { projectId })

export const createRelease = (input: ReleaseInput) =>
  postJson<Release>('/releases', input)

export const actRelease = (id: number, action: ReleaseAction, comment = '') =>
  postJson<Release>(`/releases/${id}/actions`, { action, comment })
