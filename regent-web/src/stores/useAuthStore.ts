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
import type { AuthUser } from '@/types/api'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<AuthUser | null>(null)
  const initialized = ref(false)
  const submitting = ref(false)
  const sendingCode = ref(false)

  const isAuthenticated = computed(() => user.value !== null)

  const loadCurrentUser = async (): Promise<boolean> => {
    try {
      user.value = await getCurrentUser()
    } catch {
      user.value = null
    } finally {
      initialized.value = true
    }
    return user.value !== null
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
