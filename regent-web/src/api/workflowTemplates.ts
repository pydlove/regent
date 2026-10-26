import { get } from '@/api/http'

export type WorkflowTemplateStage = {
  code: string
  name: string
  order: number
  scope: 'VERSION' | 'REQUIREMENT'
  approverRole: string | null
  description: string | null
}

export type WorkflowTemplate = {
  id: number
  code: string
  name: string
  versionNo: number
  displayName: string
  description: string | null
  stages: WorkflowTemplateStage[]
}

export const listWorkflowTemplates = () =>
  get<WorkflowTemplate[]>('/workflow-templates')
