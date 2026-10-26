import { get, postJson } from '@/api/http'

export type WorkflowTemplateStage = {
  code: string
  name: string
  order: number
  scope: 'VERSION' | 'REQUIREMENT'
  approverRole: string | null
  description: string | null
}

export type WorkflowTemplateStatus = 'PUBLISHED' | 'SUPERSEDED' | 'ARCHIVED'

export type WorkflowTemplate = {
  id: number
  code: string
  name: string
  versionNo: number
  displayName: string
  description: string | null
  status: WorkflowTemplateStatus
  stages: WorkflowTemplateStage[]
}

export type WorkflowTemplateStageInput = {
  code?: string | null
  name: string
  scope: 'VERSION' | 'REQUIREMENT'
  approverRole: string | null
  description: string | null
}

export type WorkflowTemplateSaveInput = {
  name: string
  description: string | null
  stages: WorkflowTemplateStageInput[]
}

export const listWorkflowTemplates = () =>
  get<WorkflowTemplate[]>('/workflow-templates')

export const createWorkflowTemplate = (
  organizationId: number,
  input: WorkflowTemplateSaveInput,
) =>
  postJson<WorkflowTemplate>(
    `/workflow-templates?organizationId=${organizationId}`,
    input,
  )

export const createWorkflowTemplateVersion = (
  organizationId: number,
  templateId: number,
  input: WorkflowTemplateSaveInput,
) =>
  postJson<WorkflowTemplate>(
    `/workflow-templates/${templateId}/versions?organizationId=${organizationId}`,
    input,
  )

export const archiveWorkflowTemplate = (
  organizationId: number,
  templateId: number,
) =>
  postJson<WorkflowTemplate>(
    `/workflow-templates/${templateId}/archive?organizationId=${organizationId}`,
    {},
  )
