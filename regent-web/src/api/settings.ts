import { get, postJson, postJsonWithTimeout, putJson } from '@/api/http'
import type {
  CodexRuntime,
  CodexInstallMethod,
  CodexInstallResult,
  ExecutionSettings,
  GitCommitSettings,
  RelayInstallResult,
  RelaySettings,
} from '@/types/api'

export const getExecutionSettings = () => get<ExecutionSettings>('/execution/settings')

export const updateExecutionSettings = (maxConcurrentTasks: number) =>
  putJson<ExecutionSettings>('/execution/settings', { maxConcurrentTasks })

export const checkCodexRuntime = () => get<CodexRuntime>('/execution/codex')

export const installCodex = (method: CodexInstallMethod) =>
  postJsonWithTimeout<CodexInstallResult>(
    '/execution/codex/install',
    { method },
    11 * 60 * 1000,
  )

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

export const getGitCommitSettings = () =>
  get<GitCommitSettings>('/settings/git-commit')

export const updateGitCommitSettings = (settings: GitCommitSettings) =>
  putJson<GitCommitSettings>('/settings/git-commit', settings)
