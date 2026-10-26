import { apiUrl } from '@/api/http'
import { appServerUrl } from '@/utils/appBase'

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
  deviceId: string
  serverUrl: string | null
  hostname: string
  platform: string
  executor: string
  clientRelease?: string | null
  shellRelease?: string | null
  codex: DeviceCodexStatus
  online?: boolean
}

export type DeviceAgentStatusSource = 'desktop' | 'local' | 'server'

export type DeviceAgentStatusSnapshot = {
  status: DeviceAgentStatus
  source: DeviceAgentStatusSource
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
  filePath?: (file: File) => string | null
  deviceAgentStatus?: () => Promise<DeviceAgentStatus>
  runTaskOnDevice?: (payload: { taskId: number; projectCode: string }) => Promise<DeviceAgentJob>
  deviceJob?: (jobId: string) => Promise<DeviceAgentJob>
}

/** Electron 能拿到文件真实路径；普通浏览器只返回空，由调用方退化处理。 */
export function selectedFilePath(file: File): string | null {
  try {
    const fromDesktop = window.regentDesktop?.filePath?.(file)
    if (fromDesktop) return fromDesktop
  } catch {
    // 旧客户端没有这个桥接方法时继续使用浏览器兼容路径。
  }
  const legacy = (file as File & { path?: string }).path
  return typeof legacy === 'string' && legacy.trim() ? legacy : null
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

export async function getDeviceAgentStatusSnapshot(): Promise<DeviceAgentStatusSnapshot> {
  if (window.regentDesktop?.deviceAgentStatus) {
    return {
      status: await window.regentDesktop.deviceAgentStatus(),
      source: 'desktop',
    }
  }
  const access = await localNetworkAccess()
  const canRequestAccess = access !== 'prompt' || navigator.userActivation?.isActive === true
  if (access !== 'denied' && access !== 'insecure' && canRequestAccess) {
    try {
      return {
        status: await localRequest<DeviceAgentStatus>('/status'),
        source: 'local',
      }
    } catch {
      // 浏览器不允许直连时改用服务器中继里的已配对设备。
    }
  }
  const response = await fetchCurrentDevice()
  if (response.status === 204) {
    throw new Error(accessMessage(access))
  }
  if (!response.ok) {
    throw new Error(`当前设备状态读取失败（HTTP ${response.status}）`)
  }
  const current = await response.json() as Omit<DeviceAgentStatus, 'paired' | 'serverUrl'>
  return {
    status: {
      ...current,
      paired: current.online === true,
      serverUrl: appServerUrl(),
    },
    source: 'server',
  }
}

export async function getDeviceAgentStatus(): Promise<DeviceAgentStatus> {
  return (await getDeviceAgentStatusSnapshot()).status
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
  try {
    return await localRequest<DeviceAgentJob>('/run-task', {
      method: 'POST',
      body: JSON.stringify({ taskId, projectCode }),
    })
  } catch (error) {
    if (!isLocalAgentUnavailable(error)) throw error
    const status = await getDeviceAgentStatus()
    const response = await fetch(
      apiUrl(`/devices/${encodeURIComponent(status.deviceId)}/tasks/${taskId}/dispatch`),
      {
        method: 'POST',
        headers: {
          Accept: 'application/json',
          'X-Regent-Relay-Request': '1',
        },
      },
    )
    if (!response.ok) {
      const body = await response.json().catch(() => ({}))
      throw new Error(
        typeof body.message === 'string'
          ? body.message
          : `任务发送到当前设备失败（HTTP ${response.status}）`,
      )
    }
    return {
      id: `server-${taskId}-${Date.now()}`,
      taskId,
      projectCode,
      status: 'running',
      message: '任务已发送到当前设备代理',
      startedAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    }
  }
}

export async function getDeviceAgentJob(jobId: string): Promise<DeviceAgentJob> {
  if (window.regentDesktop?.deviceJob) {
    return window.regentDesktop.deviceJob(jobId)
  }
  return localRequest<DeviceAgentJob>(`/jobs/${encodeURIComponent(jobId)}`)
}

function isLocalAgentUnavailable(error: unknown): boolean {
  if (!(error instanceof Error)) return false
  return (
    error.message.includes('Failed to fetch')
    || error.message.includes('未连接 Regent 设备代理')
    || error.name === 'AbortError'
  )
}

async function fetchCurrentDevice(): Promise<Response> {
  try {
    return await fetch(apiUrl('/devices/current'), {
      headers: {
        Accept: 'application/json',
        'X-Regent-Relay-Request': '1',
      },
    })
  } catch {
    throw new Error('无法读取当前设备状态，请检查 Regent 服务与中继连接')
  }
}

type LocalNetworkAccess = 'granted' | 'prompt' | 'denied' | 'insecure' | 'unknown'

async function localNetworkAccess(): Promise<LocalNetworkAccess> {
  if (!window.isSecureContext) return 'insecure'
  if (!navigator.permissions?.query) return 'unknown'
  try {
    const permission = await navigator.permissions.query({
      name: 'local-network-access',
    } as unknown as PermissionDescriptor)
    return permission.state
  } catch {
    return 'unknown'
  }
}

function accessMessage(access: LocalNetworkAccess): string {
  if (access === 'insecure') {
    return '当前页面不是 HTTPS，浏览器不能检测本机 Codex；请使用 Regent 桌面客户端，或从 HTTPS 地址打开'
  }
  if (access === 'denied') {
    return '浏览器已禁止访问本机设备，请点击地址栏左侧站点设置，允许“本地网络访问”后重新检测'
  }
  if (access === 'prompt') {
    return '请点击“重新检测”，并在浏览器提示中允许访问本地网络'
  }
  return '当前设备未连接 Regent 设备代理'
}
