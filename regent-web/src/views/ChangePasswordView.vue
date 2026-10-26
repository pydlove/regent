<script setup lang="ts">
import { KeyRound, ShieldCheck } from '@lucide/vue'
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { changePassword } from '@/api/auth'
import { ApiError } from '@/api/http'
import Button from '@/components/ui/button/Button.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import Input from '@/components/ui/input/Input.vue'
import { useAuthStore } from '@/stores/useAuthStore'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const error = ref<string | null>(null)
const saving = ref(false)

const accountLabel = computed(() => auth.user?.email || auth.user?.username || '当前账号')
const redirect = computed(() => {
  const value = typeof route.query.redirect === 'string' ? route.query.redirect : ''
  return value.startsWith('/') && !value.startsWith('//') ? value : '/tasks'
})

function messageOf(value: unknown): string {
  if (value instanceof ApiError) return value.message
  return value instanceof Error ? value.message : '密码修改失败'
}

async function submit() {
  if (saving.value) return
  error.value = null
  if (newPassword.value.length < 8) {
    error.value = '新密码至少需要 8 个字符'
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    error.value = '两次输入的新密码不一致'
    return
  }
  saving.value = true
  try {
    await changePassword({
      currentPassword: currentPassword.value,
      newPassword: newPassword.value,
      confirmPassword: confirmPassword.value,
    })
    await auth.refreshCurrentUser()
    await router.replace(redirect.value)
  } catch (value) {
    error.value = messageOf(value)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="auth-stage relative flex min-h-screen items-center justify-center px-4 py-8">
    <main class="w-full max-w-[480px] rounded-card border border-separator bg-content p-6 shadow-window sm:p-8">
      <div class="flex items-center gap-3">
        <span class="grid size-10 place-items-center rounded-panel bg-warning/10 text-warning">
          <Icon :as="KeyRound" size="large" />
        </span>
        <div>
          <p class="text-title font-emphasis text-label">修改初始密码</p>
          <p class="mt-1 text-caption text-label-tertiary">{{ accountLabel }}</p>
        </div>
      </div>

      <div class="mt-5 rounded-panel border border-warning/20 bg-warning/5 p-3">
        <div class="flex items-center gap-2 text-caption font-emphasis text-warning">
          <Icon :as="ShieldCheck" size="inline" />首次登录必须修改密码
        </div>
        <p class="mt-1 text-caption text-label-tertiary">
          当前使用的是管理员生成的临时密码。修改完成后才能继续使用系统。
        </p>
      </div>

      <div class="mt-5 flex flex-col gap-3">
        <label class="text-caption text-label-secondary">
          当前密码
          <Input v-model="currentPassword" class="mt-1" type="password" autocomplete="current-password" />
        </label>
        <label class="text-caption text-label-secondary">
          新密码
          <Input v-model="newPassword" class="mt-1" type="password" autocomplete="new-password" placeholder="至少 8 个字符" />
        </label>
        <label class="text-caption text-label-secondary">
          确认新密码
          <Input v-model="confirmPassword" class="mt-1" type="password" autocomplete="new-password" />
        </label>
      </div>

      <p v-if="error" class="mt-3 rounded-control bg-danger/5 px-3 py-2 text-caption text-danger">
        {{ error }}
      </p>

      <Button
        class="mt-5"
        variant="primary"
        block
        :disabled="saving || !currentPassword || !newPassword || !confirmPassword"
        @click="submit"
      >
        {{ saving ? '保存中…' : '修改密码并继续' }}
      </Button>
    </main>
  </div>
</template>
