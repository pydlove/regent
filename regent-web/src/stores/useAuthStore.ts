import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import {
  getCurrentUser,
  login as loginRequest,
  logout as logoutRequest,
  register as registerRequest,
  sendRegistrationCode as sendRegistrationCodeRequest,
  updateProfile as updateProfileRequest,
  uploadProfileAvatar,
  type RegisterInput,
} from '@/api/auth'
import { ApiError } from '@/api/http'
import type { AuthUser } from '@/types/api'
import {
  AUTH_RECOVERY_DELAY_MS,
  AUTH_RECOVERY_MAX_ATTEMPTS,
  shouldRetryAuth,
} from '@/utils/authRecovery'

const sleep = (milliseconds: number) =>
  new Promise<void>((resolve) => globalThis.setTimeout(resolve, milliseconds))

function announceStartupStatus(message: string): void {
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent('regent:startup-status', { detail: message }))
  }
}

export const useAuthStore = defineStore('auth', () => {
  const user = ref<AuthUser | null>(null)
  const initialized = ref(false)
  const submitting = ref(false)
  const sendingCode = ref(false)

  const isAuthenticated = computed(() => user.value !== null)

  const loadCurrentUser = async (): Promise<boolean> => {
    for (let attempt = 1; attempt <= AUTH_RECOVERY_MAX_ATTEMPTS; attempt++) {
      try {
        user.value = await getCurrentUser()
        initialized.value = true
        return true
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) {
          user.value = null
          initialized.value = true
          return false
        }
        if (!shouldRetryAuth(error) || attempt === AUTH_RECOVERY_MAX_ATTEMPTS) {
          user.value = null
          initialized.value = true
          return false
        }
        announceStartupStatus(`服务正在重启，正在恢复登录…（${attempt}/${AUTH_RECOVERY_MAX_ATTEMPTS}）`)
        await sleep(AUTH_RECOVERY_DELAY_MS)
      }
    }
    initialized.value = true
    return false
  }

  const login = async (
    identifier: string,
    password: string,
    rememberMe = true,
  ): Promise<void> => {
    submitting.value = true
    try {
      user.value = await loginRequest({ username: identifier, password, rememberMe })
      initialized.value = true
    } finally {
      submitting.value = false
    }
  }

  const sendRegistrationCode = async (email: string): Promise<void> => {
    sendingCode.value = true
    try {
      await sendRegistrationCodeRequest(email)
    } finally {
      sendingCode.value = false
    }
  }

  const register = async (input: RegisterInput): Promise<AuthUser> => {
    submitting.value = true
    try {
      return await registerRequest(input)
    } finally {
      submitting.value = false
    }
  }

  const saveProfile = async (displayName: string, avatar: File | null): Promise<void> => {
    let updated = await updateProfileRequest(displayName)
    user.value = updated
    if (avatar) {
      updated = await uploadProfileAvatar(avatar)
      user.value = updated
    }
  }

  const logout = async (): Promise<void> => {
    try {
      await logoutRequest()
    } finally {
      clear()
    }
  }

  const clear = (): void => {
    user.value = null
    initialized.value = true
  }

  return {
    user,
    initialized,
    submitting,
    sendingCode,
    isAuthenticated,
    loadCurrentUser,
    login,
    sendRegistrationCode,
    register,
    saveProfile,
    logout,
    clear,
  }
})
