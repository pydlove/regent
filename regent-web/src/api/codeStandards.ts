import { get, postForm, postJson, postNoContent, putJson } from '@/api/http'
import type {
  CodeStandard,
  CodeStandardCopyResult,
  CodeStandardScope,
  CodeStandardVersion,
  TaskCodeStandard,
} from '@/types/api'

export type CodeStandardInput = {
  projectId: number
  name: string
  description: string
  scope: CodeStandardScope
  enabled: boolean
  defaultApply: boolean
  sortOrder: number
}

export const listCodeStandards = (projectId: number) =>
  get<CodeStandard[]>(`/projects/${projectId}/code-standards`)

export const listCodeStandardVersions = (standardId: number) =>
  get<CodeStandardVersion[]>(`/code-standards/${standardId}/versions`)

export const listTaskCodeStandards = (taskId: number) =>
  get<TaskCodeStandard[]>(`/tasks/${taskId}/code-standards`)

export const setCodeStandardEnabled = (standardId: number, enabled: boolean) =>
  postJson<CodeStandard>(`/code-standards/${standardId}/enabled`, { enabled })

export const archiveCodeStandard = (standardId: number) =>
  postNoContent(`/code-standards/${standardId}/archive`)

export const updateCodeStandard = (standardId: number, input: CodeStandardInput) =>
  putJson<CodeStandard>(`/code-standards/${standardId}`, input)

export async function createCodeStandard(input: CodeStandardInput, file: File): Promise<CodeStandard> {
  const body = new FormData()
  body.append('file', file)
  body.append('name', input.name)
  body.append('description', input.description)
  body.append('scope', input.scope)
  body.append('enabled', String(input.enabled))
  body.append('defaultApply', String(input.defaultApply))
  body.append('sortOrder', String(input.sortOrder))
  return postForm<CodeStandard>(`/projects/${input.projectId}/code-standards`, body)
}

export async function uploadCodeStandardVersion(
  standardId: number,
  file: File,
): Promise<CodeStandard> {
  const body = new FormData()
  body.append('file', file)
  return postForm<CodeStandard>(`/code-standards/${standardId}/versions`, body)
}

export const copyCodeStandards = (
  targetProjectId: number,
  sourceProjectId: number,
  standardIds: number[],
  strategy: 'SKIP' | 'RENAME',
) => postJson<CodeStandardCopyResult>(`/projects/${targetProjectId}/code-standards/copy`, {
  sourceProjectId,
  standardIds,
  strategy,
})

