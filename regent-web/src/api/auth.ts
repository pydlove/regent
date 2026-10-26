import {
  apiResourceUrl,
  get,
  postForm,
  postJson,
  postJsonNoContent,
  postNoContent,
  putJson,
} from '@/api/http'
import type { AuthUser } from '@/types/api'

export type RegisterInput = {
  email: string
  emailCode: string
  password: string
  confirmPassword: string
}

export type LoginInput = {
  username: string
  password: string
  rememberMe: boolean
}

function resolveAuthUser(user: AuthUser): AuthUser {
  const avatarUrl = user.avatarUrl ? apiResourceUrl(user.avatarUrl) : null
  return avatarUrl === user.avatarUrl ? user : { ...user, avatarUrl }
}

export const getCurrentUser = async () =>
  resolveAuthUser(await get<AuthUser>('/auth/me'))

export const updateProfile = async (displayName: string) =>
  resolveAuthUser(await putJson<AuthUser>('/profile', { displayName }))

export const uploadProfileAvatar = async (file: File) => {
  const form = new FormData()
  form.append('file', file)
  return resolveAuthUser(await postForm<AuthUser>('/profile/avatar', form))
}

export const register = async (input: RegisterInput) =>
  resolveAuthUser(await postJson<AuthUser>('/auth/register', input))

export const sendRegistrationCode = (email: string) =>
  postJsonNoContent('/auth/email-codes', { email })

export const login = async (input: LoginInput) =>
  resolveAuthUser(await postJson<AuthUser>('/auth/login', input))

export const logout = () => postNoContent('/auth/logout')

export const changePassword = async (input: {
  currentPassword: string
  newPassword: string
  confirmPassword: string
}) => resolveAuthUser(await postJson<AuthUser>('/auth/change-password', input))

export type DeviceSession = {
  token: string
  expiresAt: string
}

/** 为当前设备代理换取长期 Bearer 会话。令牌只保存在本机代理的安全配置中。 */
export const createDeviceSession = () => postJson<DeviceSession>('/auth/device-session', {})
