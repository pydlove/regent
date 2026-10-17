import { get, postJson, putJson } from '@/api/http'
import type { ExecutionSettings, RelayInstallResult, RelaySettings } from '@/types/api'

export const getExecutionSettings = () => get<ExecutionSettings>('/execution/settings')

export const updateExecutionSettings = (maxConcurrentTasks: number) =>
  putJson<ExecutionSettings>('/execution/settings', { maxConcurrentTasks })

export const getRelaySettings = () => get<RelaySettings>('/settings/relay')

export const updateRelaySettings = (
  serverUrl: string,
  monitoringEnabled: boolean,
  serverIp: string,
  sshUsername: string,
  installDirectory: string,
) =>
  putJson<RelaySettings>('/settings/relay', {
    serverUrl,
    monitoringEnabled,
    serverIp,
    sshUsername,
    installDirectory,
  })

export const installRelay = (password: string) =>
  postJson<RelayInstallResult>('/settings/relay/install', { password })
