const LOCAL_AGENT_URL = 'http://127.0.0.1:12601'

export type DeviceCodexStatus = {
  available: boolean
  executablePath: string | null
  version: string | null
  message: string
  checkedAt: string
}

export type DeviceAgentStatus = {
  paired: boolean
  serverUrl: string | null
  hostname: string
  platform: string
  executor: string
  codex: DeviceCodexStatus
}

export type DeviceAgentJob = {
  id: string
  taskId: number
  projectCode: string
  status: 'running' | 'success' | 'failed' | 'blocked'
  message: string
  runId?: number
  startedAt: string
  updatedAt: string
}

type DesktopBridge = {
  deviceAgentStatus?: () => Promise<DeviceAgentStatus>
  runTaskOnDevice?: (payload: { taskId: number; projectCode: string }) => Promise<DeviceAgentJob>
  deviceJob?: (jobId: string) => Promise<DeviceAgentJob>
}

declare global {
  interface Window {
    regentDesktop?: DesktopBridge
  }
}

async function localRequest<T>(pathname: string, init?: RequestInit): Promise<T> {
  const controller = new AbortController()
  const timer = globalThis.setTimeout(() => controller.abort(), 5000)
  try {
    const response = await fetch(`${LOCAL_AGENT_URL}${pathname}`, {
      ...init,
      headers: {
        Accept: 'application/json',
        ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
        ...(init?.headers || {}),
      },
      signal: controller.signal,
    })
    const body = await response.json().catch(() => ({}))
    if (!response.ok) {
      throw new Error(
        typeof body.error === 'string'
          ? body.error
          : `设备代理请求失败（HTTP ${response.status}）`,
      )
    }
    return body as T
  } catch (error) {
    if (error instanceof Error && error.name === 'AbortError') {
      throw new Error('当前设备未连接 Regent 设备代理')
    }
    throw error instanceof Error
      ? error
      : new Error('当前设备未连接 Regent 设备代理')
  } finally {
    globalThis.clearTimeout(timer)
  }
}

export async function getDeviceAgentStatus(): Promise<DeviceAgentStatus> {
  if (window.regentDesktop?.deviceAgentStatus) {
    return window.regentDesktop.deviceAgentStatus()
  }
  return localRequest<DeviceAgentStatus>('/status')
}

export async function pairDeviceAgent(serverUrl: string, token: string): Promise<DeviceAgentStatus> {
  return localRequest<DeviceAgentStatus>('/pair', {
    method: 'POST',
    body: JSON.stringify({ serverUrl, token }),
  })
}

export async function runTaskOnDevice(taskId: number, projectCode: string): Promise<DeviceAgentJob> {
  if (window.regentDesktop?.runTaskOnDevice) {
    return window.regentDesktop.runTaskOnDevice({ taskId, projectCode })
  }
  return localRequest<DeviceAgentJob>('/run-task', {
    method: 'POST',
    body: JSON.stringify({ taskId, projectCode }),
  })
}

export async function getDeviceAgentJob(jobId: string): Promise<DeviceAgentJob> {
  if (window.regentDesktop?.deviceJob) {
    return window.regentDesktop.deviceJob(jobId)
  }
  return localRequest<DeviceAgentJob>(`/jobs/${encodeURIComponent(jobId)}`)
}
