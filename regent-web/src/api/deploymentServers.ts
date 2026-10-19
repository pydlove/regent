import { deleteRequest, get, postJson, putJson } from '@/api/http'
import type { DeploymentAuthType, DeploymentServer } from '@/types/api'

export type SaveDeploymentServerInput = {
  projectId: number
  name: string
  host: string
  port: number
  username: string
  authType: DeploymentAuthType
  defaultRemoteDirectory: string
  password: string
}

export const listDeploymentServers = (projectId?: number | null) =>
  get<DeploymentServer[]>('/deployment-servers', { projectId })

export const getDeploymentServer = (id: number) =>
  get<DeploymentServer>(`/deployment-servers/${id}`)

export const createDeploymentServer = (input: SaveDeploymentServerInput) =>
  postJson<DeploymentServer>('/deployment-servers', input)

export const updateDeploymentServer = (id: number, input: SaveDeploymentServerInput) =>
  putJson<DeploymentServer>(`/deployment-servers/${id}`, input)

export const deleteDeploymentServer = (id: number) =>
  deleteRequest(`/deployment-servers/${id}`)
