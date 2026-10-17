import { get, putJson } from '@/api/http'
import type { ExecutionSettings, RelaySettings } from '@/types/api'

export const getExecutionSettings = () => get<ExecutionSettings>('/execution/settings')

export const updateExecutionSettings = (maxConcurrentTasks: number) =>
  putJson<ExecutionSettings>('/execution/settings', { maxConcurrentTasks })

export const getRelaySettings = () => get<RelaySettings>('/settings/relay')

export const updateRelaySettings = (serverUrl: string, monitoringEnabled: boolean) =>
  putJson<RelaySettings>('/settings/relay', { serverUrl, monitoringEnabled })
