import { get, postJson, postJsonNoContent, postNoContent } from '@/api/http'
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
}

export const getCurrentUser = () => get<AuthUser>('/auth/me')

export const register = (input: RegisterInput) =>
  postJson<AuthUser>('/auth/register', input)

export const sendRegistrationCode = (email: string) =>
  postJsonNoContent('/auth/email-codes', { email })

export const login = (input: LoginInput) =>
  postJson<AuthUser>('/auth/login', input)

export const logout = () => postNoContent('/auth/logout')
