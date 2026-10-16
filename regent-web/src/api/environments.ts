import { deleteRequest, get, postJson, putJson } from '@/api/http'
import type { EnvironmentEntry, EnvironmentScope } from '@/types/api'

export type SaveEnvironmentInput = {
  scope: EnvironmentScope
  projectId: number | null
  key: string
  value: string
  tags: string[]
  description: string
}

export const listEnvironments = () => get<EnvironmentEntry[]>('/environments')

export const createEnvironment = (input: SaveEnvironmentInput) =>
  postJson<EnvironmentEntry>('/environments', input)

export const updateEnvironment = (id: number, input: SaveEnvironmentInput) =>
  putJson<EnvironmentEntry>(`/environments/${id}`, input)

export const deleteEnvironment = (id: number) => deleteRequest(`/environments/${id}`)
