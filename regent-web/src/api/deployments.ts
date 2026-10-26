import { deleteRequest, get, postJson, postJsonWithTimeout, putJson } from '@/api/http'
import type {
  DeploymentDetail,
  DeploymentExecutionMode,
  DeploymentRunDetail,
  DeploymentRunSummary,
  DeploymentScriptGeneration,
  DeploymentScriptValidation,
  DeploymentSummary,
} from '@/types/api'

export type SaveDeploymentInput = {
  projectId: number
  projectIds: number[]
  serverId: number
  name: string
  remoteDirectory: string
  scriptFilename: string
  scriptContent: string
  executionMode: DeploymentExecutionMode
  projectScriptPath: string | null
}

export const listDeployments = (projectId?: number | null) =>
  get<DeploymentSummary[]>('/deployments', { projectId })

export const getDeployment = (id: number) => get<DeploymentDetail>(`/deployments/${id}`)

export const validateDeploymentScript = (scriptContent: string) =>
  postJson<DeploymentScriptValidation>('/deployments/script-validation', { scriptContent })

export type GenerateDeploymentScriptInput = {
  requestId: string
  projectId: number
  serverId: number
  workspacePath: string
  remoteDirectory: string
}

const SCRIPT_GENERATION_TIMEOUT_MS = 4 * 60 * 1000

export const generateDeploymentScript = (input: GenerateDeploymentScriptInput) =>
  postJsonWithTimeout<DeploymentScriptGeneration>(
    '/deployments/script-generation',
    input,
    SCRIPT_GENERATION_TIMEOUT_MS,
  )

export const cancelDeploymentScriptGeneration = (requestId: string) =>
  postJson<boolean>(
    `/deployments/script-generation/${encodeURIComponent(requestId)}/cancel`,
    {},
  )

export const createDeployment = (input: SaveDeploymentInput) =>
  postJson<DeploymentDetail>('/deployments', input)

export const updateDeployment = (id: number, input: SaveDeploymentInput) =>
  putJson<DeploymentDetail>(`/deployments/${id}`, input)

export const deleteDeployment = (id: number) => deleteRequest(`/deployments/${id}`)

export const runDeployment = (id: number, projectId: number, taskId: number | null = null) =>
  postJson<DeploymentRunSummary>(`/deployments/${id}/run`, { projectId, taskId })

export const stopDeployment = (id: number) =>
  postJson<DeploymentRunSummary>(`/deployments/${id}/stop`, {})

export const listDeploymentRuns = (id: number, limit = 30) =>
  get<DeploymentRunSummary[]>(`/deployments/${id}/runs`, { limit })

export const getDeploymentRun = (id: number) =>
  get<DeploymentRunDetail>(`/deployment-runs/${id}`)
