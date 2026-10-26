import { deleteRequest, get, postForm, postJson, putJson } from '@/api/http'
import type {
  DesignAttachment,
  DesignModule,
  Initiative,
  InitiativeInput,
  InitiativePlan,
  InitiativePlanInput,
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
  input: { comment?: string } = {},
) => postJson<Initiative>(`/initiatives/${id}/approve`, input)

export const rejectInitiative = (
  id: number,
  input: { comment?: string } = {},
) => postJson<Initiative>(`/initiatives/${id}/reject`, input)

export const saveInitiativePlan = (id: number, input: InitiativePlanInput) =>
  putJson<InitiativePlan>(`/initiatives/${id}/plan`, input)

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

export type DesignModuleInput = {
  name: string
  description: string | null
  assigneeId: number
}

export const createDesignModule = (deliveryId: number, input: DesignModuleInput) =>
  postJson<DesignModule>(`/requirement-deliveries/${deliveryId}/modules`, input)

export const updateDesignModule = (
  deliveryId: number,
  moduleId: number,
  input: DesignModuleInput,
) => putJson<DesignModule>(
  `/requirement-deliveries/${deliveryId}/modules/${moduleId}`,
  input,
)

export const saveModuleDesign = (
  deliveryId: number,
  moduleId: number,
  input: { designSummary: string; designContent: string },
) => putJson<DesignModule>(
  `/requirement-deliveries/${deliveryId}/modules/${moduleId}/design`,
  input,
)

export const submitDesignModule = (deliveryId: number, moduleId: number) =>
  postJson<DesignModule>(
    `/requirement-deliveries/${deliveryId}/modules/${moduleId}/submit`,
    {},
  )

export const reopenDesignModule = (deliveryId: number, moduleId: number) =>
  postJson<DesignModule>(
    `/requirement-deliveries/${deliveryId}/modules/${moduleId}/reopen`,
    {},
  )

export const deleteDesignModule = (deliveryId: number, moduleId: number) =>
  deleteRequest(`/requirement-deliveries/${deliveryId}/modules/${moduleId}`)

export const uploadDesignAttachment = (
  deliveryId: number,
  file: File,
  moduleId: number | null = null,
) => {
  const form = new FormData()
  form.append('file', file)
  const query = moduleId === null ? '' : `?moduleId=${moduleId}`
  return postForm<DesignAttachment>(
    `/requirement-deliveries/${deliveryId}/design-attachments${query}`,
    form,
  )
}

export const listReleases = (projectId: number | null = null) =>
  get<Release[]>('/releases', { projectId })

export const createRelease = (input: ReleaseInput) =>
  postJson<Release>('/releases', input)

export const actRelease = (id: number, action: ReleaseAction, comment = '') =>
  postJson<Release>(`/releases/${id}/actions`, { action, comment })
