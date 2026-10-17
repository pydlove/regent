<script setup lang="ts">
import { LogIn, Mail, UserPlus } from '@lucide/vue'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { ApiError } from '@/api/http'
import Button from '@/components/ui/button/Button.vue'
import Checkbox from '@/components/ui/checkbox/Checkbox.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import Input from '@/components/ui/input/Input.vue'
import { useAuthStore } from '@/stores/useAuthStore'

const props = defineProps<{ mode: 'login' | 'register' }>()

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const identifier = ref('')
const email = ref('')
const emailCode = ref('')
const password = ref('')
const confirmPassword = ref('')
const rememberMe = ref(true)
const error = ref<string | null>(null)
const notice = ref<string | null>(null)
const resendSeconds = ref(0)
let countdownTimer: number | undefined

const isRegister = computed(() => props.mode === 'register')
const registered = computed(() => route.query.registered === '1')
const submitLabel = computed(() => (isRegister.value ? '创建账号' : '登录'))
const codeButtonLabel = computed(() => {
  if (resendSeconds.value > 0) return `${resendSeconds.value}s 后重发`
  return auth.sendingCode ? '发送中…' : '获取验证码'
})

watch(
  () => props.mode,
  () => {
    error.value = null
    notice.value = null
    password.value = ''
    confirmPassword.value = ''
    emailCode.value = ''
    const queryUsername = route.query.username
    if (typeof queryUsername === 'string') identifier.value = queryUsername
  },
  { immediate: true },
)

onBeforeUnmount(stopCountdown)

function messageOf(value: unknown): string {
  if (value instanceof ApiError) return value.message
  return '无法连接后端，请确认服务已启动'
}

function looksLikeEmail(value: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim())
}

function stopCountdown(): void {
  if (countdownTimer !== undefined) {
    window.clearInterval(countdownTimer)
    countdownTimer = undefined
  }
}

function startCountdown(): void {
  stopCountdown()
  resendSeconds.value = 60
  countdownTimer = window.setInterval(() => {
    resendSeconds.value -= 1
    if (resendSeconds.value <= 0) stopCountdown()
  }, 1000)
}

async function sendCode(): Promise<void> {
  error.value = null
  notice.value = null
  if (!looksLikeEmail(email.value)) {
    error.value = '请输入正确的邮箱地址'
    return
  }
  try {
    const target = email.value.trim()
    await auth.sendRegistrationCode(target)
    startCountdown()
    notice.value = `验证码已发送至 ${target}，5 分钟内有效。`
  } catch (value) {
    error.value = messageOf(value)
  }
}

async function submit(): Promise<void> {
  error.value = null
  notice.value = null
  if (isRegister.value) {
    if (!looksLikeEmail(email.value)) {
      error.value = '请输入正确的邮箱地址'
      return
    }
    if (!/^\d{6}$/.test(emailCode.value.trim())) {
      error.value = '请输入 6 位邮箱验证码'
      return
    }
    if (password.value !== confirmPassword.value) {
      error.value = '两次输入的密码不一致'
      return
    }
  }

  try {
    if (isRegister.value) {
      const user = await auth.register({
        email: email.value.trim(),
        emailCode: emailCode.value.trim(),
        password: password.value,
        confirmPassword: confirmPassword.value,
      })
      await router.push({
        name: 'login',
        query: { registered: '1', username: user.email ?? user.username },
      })
      return
    }

    await auth.login(identifier.value.trim(), password.value, rememberMe.value)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    await router.push(redirect.startsWith('/') && !redirect.startsWith('//') ? redirect : '/tasks')
  } catch (value) {
    error.value = messageOf(value)
  }
}
</script>

<template>
  <div class="flex min-h-screen items-center justify-center bg-window px-4 py-10">
    <main class="w-full max-w-[380px]">
      <div class="mb-7 flex items-center justify-center gap-2.5">
        <span
          aria-hidden="true"
          class="relative grid size-9 place-items-center rounded-[8px] bg-label text-content shadow-control"
        >
          <span class="translate-y-px text-[18px] font-emphasis leading-none">R</span>
          <span class="absolute bottom-1 right-1 size-1.5 rounded-full bg-accent ring-2 ring-label" />
        </span>
        <div>
          <p class="text-title font-emphasis leading-none text-label">Regent AI</p>
          <p class="mt-1 text-caption text-label-secondary">任务执行平台</p>
        </div>
      </div>

      <section class="rounded-[8px] border border-separator bg-content p-6 shadow-panel">
        <div>
          <h1 class="text-title font-emphasis text-label">
            {{ isRegister ? '创建账号' : '登录' }}
          </h1>
          <p class="mt-1 text-caption text-label-secondary">
            {{ isRegister ? '通过邮箱验证码注册，注册后即可登录。' : '使用邮箱或用户名继续。' }}
          </p>
        </div>

        <p
          v-if="!isRegister && registered"
          class="mt-4 rounded-control border border-success/25 bg-success/8 px-2.5 py-2 text-caption text-label"
          role="status"
        >
          账号已创建，请使用注册邮箱登录。
        </p>

        <form class="mt-5 space-y-4" @submit.prevent="submit">
          <label v-if="!isRegister" class="block">
            <span class="mb-1.5 block text-caption font-emphasis text-label-secondary">
              邮箱或用户名
            </span>
            <Input
              v-model="identifier"
              size="lg"
              autocomplete="username"
              autocapitalize="none"
              spellcheck="false"
              :invalid="Boolean(error)"
            />
          </label>

          <label v-else class="block">
            <span class="mb-1.5 block text-caption font-emphasis text-label-secondary">邮箱</span>
            <Input
              v-model="email"
              type="email"
              size="lg"
              autocomplete="email"
              autocapitalize="none"
              spellcheck="false"
              placeholder="you@example.com"
              :invalid="Boolean(error)"
            />
          </label>

          <div v-if="isRegister" class="block">
            <span class="mb-1.5 block text-caption font-emphasis text-label-secondary">
              邮箱验证码
            </span>
            <div class="flex items-center gap-2">
              <div class="min-w-0 flex-1">
                <Input
                  v-model="emailCode"
                  size="lg"
                  inputmode="numeric"
                  maxlength="6"
                  autocomplete="one-time-code"
                  placeholder="6 位数字"
                  :invalid="Boolean(error)"
                />
              </div>
              <Button
                size="lg"
                class="w-[116px]"
                :disabled="auth.sendingCode || resendSeconds > 0"
                @click="sendCode"
              >
                <Icon :as="Mail" size="inline" />
                {{ codeButtonLabel }}
              </Button>
            </div>
          </div>

          <label class="block">
            <span class="mb-1.5 block text-caption font-emphasis text-label-secondary">密码</span>
            <Input
              v-model="password"
              type="password"
              size="lg"
              :autocomplete="isRegister ? 'new-password' : 'current-password'"
              :invalid="Boolean(error)"
            />
            <span v-if="isRegister" class="mt-1 block text-caption text-label-tertiary">
              至少 8 个字符
            </span>
          </label>

          <div v-if="!isRegister" class="flex items-start justify-between gap-4">
            <div>
              <Checkbox v-model="rememberMe" label="在此设备保持登录" />
              <p class="mt-1 pl-5 text-caption text-label-tertiary">
                正常使用期间长期有效，无需反复输入密码。
              </p>
            </div>
          </div>

          <label v-if="isRegister" class="block">
            <span class="mb-1.5 block text-caption font-emphasis text-label-secondary">
              确认密码
            </span>
            <Input
              v-model="confirmPassword"
              type="password"
              size="lg"
              autocomplete="new-password"
              :invalid="Boolean(error)"
            />
          </label>

          <p
            v-if="notice"
            class="rounded-control border border-accent/20 bg-accent/6 px-2.5 py-2 text-caption text-label"
            role="status"
          >
            {{ notice }}
          </p>
          <p v-if="error" class="text-caption text-danger" role="alert">{{ error }}</p>

          <Button
            type="submit"
            variant="primary"
            size="lg"
            block
            :disabled="auth.submitting"
          >
            <Icon :as="isRegister ? UserPlus : LogIn" size="inline" />
            {{ auth.submitting ? '请稍候…' : submitLabel }}
          </Button>
        </form>

        <div class="mt-5 border-t border-separator pt-4 text-center text-caption text-label-secondary">
          <template v-if="isRegister">
            已有账号？
            <RouterLink class="font-emphasis text-accent hover:underline" to="/login">
              返回登录
            </RouterLink>
          </template>
          <template v-else>
            还没有账号？
            <RouterLink class="font-emphasis text-accent hover:underline" to="/register">
              邮箱注册
            </RouterLink>
          </template>
        </div>
      </section>
    </main>
  </div>
</template>
