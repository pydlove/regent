import { get, postJson, putJson } from '@/api/http'
import type { PluginDetail, PluginSummary, TaskPluginBinding } from '@/types/api'

export type SavePluginInput = {
  name: string
  description: string
  tags: string[]
  filename: string
  content: string
  enabled: boolean
}

export const listPlugins = () => get<PluginSummary[]>('/plugins')

export const getPlugin = (id: number) => get<PluginDetail>(`/plugins/${id}`)

export const createPlugin = (input: SavePluginInput) =>
  postJson<PluginDetail>('/plugins', input)

export const updatePlugin = (id: number, input: SavePluginInput) =>
  putJson<PluginDetail>(`/plugins/${id}`, input)

export const setPluginEnabled = (id: number, enabled: boolean) =>
  putJson<PluginDetail>(`/plugins/${id}/enabled`, { enabled })

export const listTaskPlugins = (taskId: number) =>
  get<TaskPluginBinding[]>(`/tasks/${taskId}/plugins`)

export const bindTaskPlugins = (taskId: number, pluginIds: number[]) =>
  putJson<TaskPluginBinding[]>(`/tasks/${taskId}/plugins`, { pluginIds })
