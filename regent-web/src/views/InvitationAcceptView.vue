<script setup lang="ts">
import { Building2, Check, LogIn, Mail, UserPlus } from '@lucide/vue'
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { ApiError } from '@/api/http'
import {
  acceptOrganizationInvitation,
  previewOrganizationInvitation,
} from '@/api/organizations'
import Badge from '@/components/ui/badge/Badge.vue'
import Button from '@/components/ui/button/Button.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import Input from '@/components/ui/input/Input.vue'
import { useOrganizationStore } from '@/stores/useOrganizationStore'
import { useAuthStore } from '@/stores/useAuthStore'
import type { OrganizationInvitationPreview, OrganizationRole } from '@/types/organization'
import { formatDateTime } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const organizationStore = useOrganizationStore()

const token = computed(() => String(route.params.token ?? ''))
const preview = ref<OrganizationInvitationPreview | null>(null)
const loading = ref(true)
const submitting = ref(false)
const error = ref<string | null>(null)
const displayName = ref('')
const password = ref('')
const confirmPassword = ref('')

const roleLabels: Record<OrganizationRole, string> = {
  ORG_OWNER: '组织所有者',
  ORG_ADMIN: '组织管理员',
  PROJECT_MANAGER: '项目经理',
  ORG_MEMBER: '普通成员',
}
const signedInWithInvitedEmail = computed(
  () => !!auth.user?.email
    && auth.user.email.toLowerCase() === (preview.value?.email ?? '').toLowerCase(),
)
const loginTarget = computed(() => ({
  path: '/login',
  query: { redirect: route.fullPath },
}))

function messageOf(value: unknown): string {
  if (value instanceof ApiError) return value.message
  return value instanceof Error ? value.message : '邀请读取失败'
}

async function load() {
  loading.value = true
  error.value = null
  try {
    await auth.loadCurrentUser()
    preview.value = await previewOrganizationInvitation(token.value)
  } catch (value) {
    error.value = messageOf(value)
  } finally {
    loading.value = false
  }
}

async function accept() {
  const invitation = preview.value
  if (!invitation || submitting.value) return
  error.value = null
  if (!invitation.accountExists) {
    if (!displayName.value.trim()) {
      error.value = '请填写显示名称'
      return
    }
    if (password.value.length < 8) {
      error.value = '密码至少需要 8 个字符'
      return
    }
    if (password.value !== confirmPassword.value) {
      error.value = '两次输入的密码不一致'
      return
    }
  }
  submitting.value = true
  try {
    await acceptOrganizationInvitation(token.value, invitation.accountExists
      ? {}
      : { displayName: displayName.value.trim(), password: password.value })
    if (!invitation.accountExists) {
      await auth.login(invitation.email, password.value, true)
    }
    await organizationStore.load()
    await router.push(invitation.role === 'ORG_MEMBER' ? '/tasks' : '/organizations')
  } catch (value) {
    error.value = messageOf(value)
  } finally {
    submitting.value = false
  }
}

async function switchAccount() {
  await auth.logout()
  await router.push(loginTarget.value)
}

onMounted(load)
</script>

<template>
  <div class="auth-stage relative flex min-h-screen items-center justify-center px-4 py-8">
    <main class="w-full max-w-[480px] rounded-card border border-separator bg-content p-6 shadow-window sm:p-8">
      <div class="flex items-center gap-3">
        <span class="grid size-10 place-items-center rounded-panel bg-accent/10 text-accent">
          <Icon :as="Building2" size="large" />
        </span>
        <div>
          <p class="text-title font-emphasis text-label">加入组织</p>
          <p class="mt-1 text-caption text-label-tertiary">Regent AI 任务执行平台</p>
        </div>
      </div>

      <div v-if="loading" class="mt-8 text-caption text-label-tertiary">正在读取邀请…</div>

      <div v-else-if="error && !preview" class="mt-6 rounded-panel border border-danger/20 bg-danger/5 p-4">
        <p class="text-caption text-danger">{{ error }}</p>
        <RouterLink :to="loginTarget" class="mt-3 inline-block text-caption text-accent">
          返回登录
        </RouterLink>
      </div>

      <template v-else-if="preview">
        <div class="mt-6 rounded-panel border border-separator-soft bg-window p-4">
          <div class="flex flex-wrap items-center gap-2">
            <p class="text-body font-emphasis text-label">{{ preview.organizationName }}</p>
            <Badge tone="accent">{{ roleLabels[preview.role] }}</Badge>
          </div>
          <div class="mt-3 flex items-center gap-2 text-caption text-label-secondary">
            <Icon :as="Mail" size="inline" class="text-label-tertiary" />
            {{ preview.email }}
          </div>
          <p class="mt-2 text-[10px] text-label-tertiary">
            邀请有效至 {{ formatDateTime(preview.expiresAt) }}
          </p>
        </div>

        <template v-if="preview.accountExists">
          <div
            v-if="signedInWithInvitedEmail"
            class="mt-4 rounded-panel border border-success/20 bg-success/5 p-4"
          >
            <div class="flex items-center gap-2 text-caption font-emphasis text-success">
              <Icon :as="Check" size="inline" />已使用邀请邮箱登录
            </div>
            <p class="mt-1 text-caption text-label-tertiary">
              接受后会把当前账号加入组织，不会修改现有密码。
            </p>
          </div>
          <div v-else class="mt-4 rounded-panel border border-warning/20 bg-warning/5 p-4">
            <p class="text-caption text-label">
              该邮箱已有 Regent 账号，请先使用 {{ preview.email }} 登录后再接受邀请。
            </p>
            <div class="mt-3 flex flex-wrap gap-2">
              <RouterLink :to="loginTarget">
                <Button variant="primary" size="sm">
                  <Icon :as="LogIn" size="inline" />登录后接受
                </Button>
              </RouterLink>
              <Button v-if="auth.isAuthenticated" size="sm" @click="switchAccount">
                切换账号
              </Button>
            </div>
          </div>
        </template>

        <div v-else class="mt-5 flex flex-col gap-3">
          <label class="text-caption text-label-secondary">
            显示名称
            <Input v-model="displayName" class="mt-1" placeholder="你的姓名" />
          </label>
          <label class="text-caption text-label-secondary">
            设置密码
            <Input v-model="password" class="mt-1" type="password" placeholder="至少 8 个字符" />
          </label>
          <label class="text-caption text-label-secondary">
            确认密码
            <Input v-model="confirmPassword" class="mt-1" type="password" />
          </label>
        </div>

        <p v-if="error" class="mt-3 rounded-control bg-danger/5 px-3 py-2 text-caption text-danger">
          {{ error }}
        </p>

        <Button
          v-if="!preview.accountExists || signedInWithInvitedEmail"
          class="mt-5"
          variant="primary"
          block
          :disabled="submitting"
          @click="accept"
        >
          <Icon :as="UserPlus" size="inline" />
          {{ submitting ? '接受中…' : '接受邀请并加入组织' }}
        </Button>
      </template>
    </main>
  </div>
</template>
