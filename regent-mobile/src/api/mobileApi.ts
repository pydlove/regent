import { Capacitor } from '@capacitor/core'

import type {
  DashboardData,
  DecisionSummary,
  DevicePresence,
  AuthUser,
  MobileAuthResponse,
  MobileSession,
  PageResponse,
  Project,
  TaskStats,
  TaskSummary,
} from '@/types'
import { toMobileApiPath } from '@/services/apiPath'

const REQUEST_TIMEOUT_MS = 15_000

export class ApiError extends Error {
  readonly status: number
  readonly code: string

  constructor(status: number, code: string, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

function apiBase(): string {
  const configured = (import.meta.env.VITE_API_BASE_URL ?? '').trim().replace(/\/+$/, '')
  if (configured) return configured
  if (Capacitor.isNativePlatform()) {
    throw new ApiError(
      0,
      'API_BASE_MISSING',
      '原生安装包未配置 VITE_API_BASE_URL，请配置可访问的 HTTPS 服务地址',
    )
  }
  return ''
}

function url(path: string, params?: Record<string, string | number | null | undefined>): string {
  const normalized = path.startsWith('/') ? path : `/${path}`
  const query = params
    ? new URLSearchParams(
        Object.entries(params)
          .filter(([, value]) => value !== null && value !== undefined && value !== '')
          .map(([key, value]) => [key, String(value)]),
      ).toString()
    : ''
  return `${apiBase()}/api${normalized}${query ? `?${query}` : ''}`
}

async function parseError(response: Response): Promise<ApiError> {
  try {
    const body = await response.json() as { code?: string; message?: string }
    return new ApiError(
      response.status,
      body.code ?? 'UNKNOWN',
      body.message ?? `请求失败（HTTP ${response.status}）`,
    )
  } catch {
    return new ApiError(response.status, 'UNKNOWN', `请求失败（HTTP ${response.status}）`)
  }
}

async function fetchWithTimeout(
  input: string,
  init: RequestInit,
  timeoutMs = REQUEST_TIMEOUT_MS,
): Promise<Response> {
  const controller = new AbortController()
  const timer = globalThis.setTimeout(() => controller.abort(), timeoutMs)
  try {
    return await fetch(input, { ...init, signal: controller.signal })
  } catch (error) {
    if (controller.signal.aborted) {
      throw new ApiError(0, 'TIMEOUT', '请求超时，请检查网络后重试')
    }
    throw error
  } finally {
    globalThis.clearTimeout(timer)
  }
}

type SessionHooks = {
  getSession: () => MobileSession | null
  saveSession: (session: MobileSession) => Promise<void>
  clearSession: () => Promise<void>
}

export class MobileApi {
  private refreshInFlight: Promise<MobileSession> | null = null

  constructor(private readonly hooks: SessionHooks) {}

  async login(
    username: string,
    password: string,
    installationId: string,
  ): Promise<MobileSession> {
    const response = await fetchWithTimeout(url('/mobile/auth/login'), {
      method: 'POST',
      headers: this.headers(),
      body: JSON.stringify({ username, password, installationId }),
    })
    if (!response.ok) throw await parseError(response)
    const auth = await response.json() as MobileAuthResponse
    const session = { ...auth, installationId }
    await this.hooks.saveSession(session)
    return session
  }

  async refresh(): Promise<MobileSession> {
    if (this.refreshInFlight) return this.refreshInFlight
    const current = this.hooks.getSession()
    if (!current) throw new ApiError(401, 'UNAUTHORIZED', '登录已失效，请重新登录')
    this.refreshInFlight = (async () => {
      const response = await fetchWithTimeout(url('/mobile/auth/refresh'), {
        method: 'POST',
        headers: this.headers(),
        body: JSON.stringify({ refreshToken: current.refreshToken }),
      })
      if (!response.ok) {
        await this.hooks.clearSession()
        throw await parseError(response)
      }
      const auth = await response.json() as MobileAuthResponse
      const session = { ...auth, installationId: current.installationId }
      await this.hooks.saveSession(session)
      return session
    })()
    try {
      return await this.refreshInFlight
    } finally {
      this.refreshInFlight = null
    }
  }

  async logout(): Promise<void> {
    const session = this.hooks.getSession()
    if (!session) return
    try {
      await fetchWithTimeout(url('/mobile/auth/logout'), {
        method: 'POST',
        headers: this.headers(session.accessToken),
      })
    } catch {
      // 本地仍必须清理；服务端会话也会由访问令牌到期或下次登录替换。
    } finally {
      await this.hooks.clearSession()
    }
  }

  async loadCurrentUser(): Promise<AuthUser> {
    return this.authorized<AuthUser>('/auth/me')
  }

  async loadAvatar(avatarUrl: string): Promise<Blob> {
    const response = await this.authorizedResponse(toMobileApiPath(avatarUrl))
    return response.blob()
  }

  async loadDashboard(): Promise<DashboardData> {
    const [device, projects, stats, tasks, decisions] = await Promise.all([
      this.getDevice(),
      this.authorized<Project[]>('/projects'),
      this.authorized<TaskStats>('/tasks/stats'),
      this.authorized<PageResponse<TaskSummary>>('/tasks', {
        status: 'RUNNING',
        page: 1,
        size: 5,
      }),
      this.authorized<PageResponse<DecisionSummary>>('/decisions', {
        scope: 'PENDING',
        page: 1,
        size: 5,
      }),
    ])
    return {
      device,
      projects,
      stats,
      tasks: tasks.items,
      decisions: decisions.items,
      loadedAt: new Date().toISOString(),
    }
  }

  private async getDevice(): Promise<DevicePresence | null> {
    const response = await this.authorizedResponse('/devices/current')
    if (response.status === 204) return null
    return response.json() as Promise<DevicePresence>
  }

  private async authorized<T>(
    path: string,
    params?: Record<string, string | number | null | undefined>,
  ): Promise<T> {
    const response = await this.authorizedResponse(path, params)
    return response.json() as Promise<T>
  }

  private async authorizedResponse(
    path: string,
    params?: Record<string, string | number | null | undefined>,
    retried = false,
  ): Promise<Response> {
    const session = this.hooks.getSession()
    if (!session) throw new ApiError(401, 'UNAUTHORIZED', '登录已失效，请重新登录')
    const response = await fetchWithTimeout(url(path, params), {
      headers: this.headers(session.accessToken),
    })
    if (response.status !== 401) {
      if (!response.ok) throw await parseError(response)
      return response
    }
    if (retried) {
      await this.hooks.clearSession()
      throw await parseError(response)
    }
    await this.refresh()
    return this.authorizedResponse(path, params, true)
  }

  private headers(accessToken?: string): Record<string, string> {
    const headers: Record<string, string> = {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'X-Regent-Relay-Request': '1',
    }
    if (accessToken) headers.Authorization = `Bearer ${accessToken}`
    return headers
  }
}
