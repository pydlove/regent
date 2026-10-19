import { deleteRequest, get, postJson, putJson } from '@/api/http'
import type {
  DeploymentDetail,
  DeploymentRunDetail,
  DeploymentRunSummary,
  DeploymentSummary,
} from '@/types/api'

export type SaveDeploymentInput = {
  projectId: number
  name: string
  host: string
  port: number
  username: string
  authType: string
  remoteDirectory: string
  scriptFilename: string
  scriptContent: string
  password: string
}

export const listDeployments = (projectId?: number | null) =>
  get<DeploymentSummary[]>('/deployments', { projectId })

export const getDeployment = (id: number) => get<DeploymentDetail>(`/deployments/${id}`)

export const createDeployment = (input: SaveDeploymentInput) =>
  postJson<DeploymentDetail>('/deployments', input)

export const updateDeployment = (id: number, input: SaveDeploymentInput) =>
  putJson<DeploymentDetail>(`/deployments/${id}`, input)

export const deleteDeployment = (id: number) => deleteRequest(`/deployments/${id}`)

export const runDeployment = (id: number) =>
  postJson<DeploymentRunSummary>(`/deployments/${id}/run`, {})

export const listDeploymentRuns = (id: number, limit = 30) =>
  get<DeploymentRunSummary[]>(`/deployments/${id}/runs`, { limit })

export const getDeploymentRun = (id: number) =>
  get<DeploymentRunDetail>(`/deployment-runs/${id}`)
