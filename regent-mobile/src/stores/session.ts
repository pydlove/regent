import { App } from '@capacitor/app'
import { Capacitor } from '@capacitor/core'
import { reactive } from 'vue'

import { ApiError, MobileApi } from '@/api/mobileApi'
import { dashboardCache } from '@/services/accountCache'
import {
  authenticateDevice,
  getBiometryState,
  type BiometryState,
} from '@/services/biometrics'
import { getOrCreateInstallationId } from '@/services/installation'
import {
  SESSION_KEY,
  clearAccountCaches,
  getSecureItem,
  removeSecureItem,
  setSecureItem,
} from '@/services/secureStorage'
import type { DashboardData, MobileSession } from '@/types'

export type SessionPhase = 'booting' | 'login' | 'locked' | 'authenticated'

const initialBiometry: BiometryState = {
  isAvailable: false,
  strongBiometryIsAvailable: false,
  biometryType: 0,
  biometryTypes: [],
  deviceIsSecure: false,
  reason: '正在检查安全能力',
  code: '' as BiometryState['code'],
  label: '检查中',
  native: false,
}

const state = reactive({
  phase: 'booting' as SessionPhase,
  session: null as MobileSession | null,
  installationId: '',
  biometry: initialBiometry,
  dashboard: null as DashboardData | null,
  avatarSrc: '',
  loadingDashboard: false,
  busy: false,
  error: '',
  dashboardError: '',
})

let api: MobileApi
let lifecycleRegistered = false
let avatarObjectUrl: string | null = null
let loadedAvatarUrl: string | null = null

function clearAvatarSource(): void {
  if (avatarObjectUrl && typeof URL.revokeObjectURL === 'function') {
    URL.revokeObjectURL(avatarObjectUrl)
  }
  avatarObjectUrl = null
  loadedAvatarUrl = null
  state.avatarSrc = ''
}

function clearInMemoryAccountState(): void {
  clearAvatarSource()
  state.session = null
  state.dashboard = null
  state.dashboardError = ''
  dashboardCache.clear()
  clearAccountCaches()
}

async function clearSession(): Promise<void> {
  clearInMemoryAccountState()
  await removeSecureItem(SESSION_KEY)
}

async function persistSession(session: MobileSession): Promise<void> {
  state.session = session
  await setSecureItem(SESSION_KEY, JSON.stringify(session))
}

function parseSession(raw: string | null, installationId: string): MobileSession | null {
  if (!raw) return null
  try {
    const value = JSON.parse(raw) as Partial<MobileSession>
    if (
      !value.user
      || typeof value.user.id !== 'number'
      || typeof value.accessToken !== 'string'
      || typeof value.refreshToken !== 'string'
      || typeof value.accessExpiresAt !== 'string'
      || typeof value.refreshExpiresAt !== 'string'
      || value.installationId !== installationId
      || new Date(value.refreshExpiresAt).getTime() <= Date.now()
    ) {
      return null
    }
    return value as MobileSession
  } catch {
    return null
  }
}

function createApi(): MobileApi {
  return new MobileApi({
    getSession: () => state.session,
    saveSession: persistSession,
    clearSession,
  })
}

function friendlyError(error: unknown): string {
  if (error instanceof ApiError) return error.message
  if (error instanceof Error) return error.message
  return '操作失败，请稍后重试'
}

async function syncAvatar(): Promise<void> {
  const session = state.session
  const avatarUrl = session?.user.avatarUrl ?? null
  if (!session || !avatarUrl) {
    clearAvatarSource()
    return
  }
  if (avatarObjectUrl && loadedAvatarUrl === avatarUrl) return
  const expectedUserId = session.user.id
  const blob = await api.loadAvatar(avatarUrl)
  if (
    state.session?.user.id !== expectedUserId
    || state.session.user.avatarUrl !== avatarUrl
    || typeof URL.createObjectURL !== 'function'
  ) {
    return
  }
  const next = URL.createObjectURL(blob)
  if (avatarObjectUrl && typeof URL.revokeObjectURL === 'function') {
    URL.revokeObjectURL(avatarObjectUrl)
  }
  avatarObjectUrl = next
  loadedAvatarUrl = avatarUrl
  state.avatarSrc = next
}

async function syncProfile(): Promise<void> {
  const current = state.session
  if (!current) return
  const user = await api.loadCurrentUser()
  if (state.session?.user.id !== current.user.id) return
  await persistSession({ ...state.session, user })
  await syncAvatar()
}

function registerLifecycle(): void {
  if (lifecycleRegistered || !Capacitor.isNativePlatform()) return
  lifecycleRegistered = true
  void App.addListener('appStateChange', ({ isActive }) => {
    if (!isActive && state.phase === 'authenticated' && state.session) {
      state.phase = 'locked'
      state.dashboard = null
    }
  })
}

async function loadDashboard(): Promise<void> {
  const session = state.session
  if (!session) return
  const expectedUserId = session.user.id
  const cached = dashboardCache.get(expectedUserId)
  if (cached) state.dashboard = cached
  state.loadingDashboard = true
  state.dashboardError = ''
  try {
    await syncProfile()
    const next = await api.loadDashboard()
    if (state.session?.user.id !== expectedUserId) return
    dashboardCache.set(expectedUserId, next)
    state.dashboard = next
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      clearInMemoryAccountState()
      await removeSecureItem(SESSION_KEY)
      state.phase = 'login'
      state.error = '登录已失效，请重新登录'
      return
    }
    state.dashboardError = friendlyError(error)
  } finally {
    if (state.session?.user.id === expectedUserId) {
      state.loadingDashboard = false
    }
  }
}

async function bootstrap(): Promise<void> {
  try {
    state.phase = 'booting'
    state.error = ''
    state.installationId = getOrCreateInstallationId()
    api = createApi()
    const [biometry, stored] = await Promise.all([
      getBiometryState().catch(() => initialBiometry),
      getSecureItem(SESSION_KEY),
    ])
    state.biometry = biometry
    const session = parseSession(stored, state.installationId)
    if (!session) {
      if (stored) await removeSecureItem(SESSION_KEY)
      state.phase = 'login'
      return
    }
    state.session = session
    if (Capacitor.isNativePlatform()) {
      state.phase = 'locked'
      return
    }
    state.phase = 'authenticated'
    registerLifecycle()
    await loadDashboard()
  } catch (error) {
    state.error = friendlyError(error)
    state.phase = 'login'
  }
}

async function login(username: string, password: string): Promise<void> {
  if (!username.trim() || !password) {
    state.error = '请输入账号和密码'
    return
  }
  state.busy = true
  state.error = ''
  try {
    const session = await api.login(
      username.trim(),
      password,
      state.installationId,
    )
    state.session = session
    state.phase = 'authenticated'
    registerLifecycle()
    await loadDashboard()
  } catch (error) {
    state.error = friendlyError(error)
  } finally {
    state.busy = false
  }
}

async function unlock(): Promise<void> {
  if (!state.session) {
    state.phase = 'login'
    return
  }
  state.busy = true
  state.error = ''
  try {
    await authenticateDevice(`解锁 ${state.session.user.displayName} 的 Regent`)
    state.phase = 'authenticated'
    await loadDashboard()
  } catch (error) {
    state.error = friendlyError(error)
  } finally {
    state.busy = false
  }
}

async function switchAccount(): Promise<void> {
  state.busy = true
  state.error = ''
  try {
    await api.logout()
  } finally {
    clearInMemoryAccountState()
    await removeSecureItem(SESSION_KEY)
    state.phase = 'login'
    state.busy = false
  }
}

async function refresh(): Promise<void> {
  if (!state.session) return
  await loadDashboard()
}

function lock(): void {
  if (!state.session || !Capacitor.isNativePlatform()) return
  state.phase = 'locked'
  state.dashboard = null
}

export function useMobileSession() {
  return {
    state,
    bootstrap,
    login,
    unlock,
    switchAccount,
    refresh,
    lock,
  }
}
