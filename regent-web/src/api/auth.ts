import { get, postForm, postJson, postJsonNoContent, postNoContent, putJson } from '@/api/http'
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

export const getCurrentUser = () => get<AuthUser>('/auth/me')

export const updateProfile = (displayName: string) =>
  putJson<AuthUser>('/profile', { displayName })

export const uploadProfileAvatar = (file: File) => {
  const form = new FormData()
  form.append('file', file)
  return postForm<AuthUser>('/profile/avatar', form)
}

export const register = (input: RegisterInput) =>
  postJson<AuthUser>('/auth/register', input)

export const sendRegistrationCode = (email: string) =>
  postJsonNoContent('/auth/email-codes', { email })

export const login = (input: LoginInput) =>
  postJson<AuthUser>('/auth/login', input)

export const logout = () => postNoContent('/auth/logout')

export type DeviceSession = {
  token: string
  expiresAt: string
}

/** 为当前设备代理换取长期 Bearer 会话。令牌只保存在本机代理的安全配置中。 */
export const createDeviceSession = () => postJson<DeviceSession>('/auth/device-session', {})
