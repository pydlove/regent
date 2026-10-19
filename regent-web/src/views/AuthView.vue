<script setup lang="ts">
import { Download, LogIn, Mail, UserPlus } from '@lucide/vue'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { ApiError } from '@/api/http'
import Button from '@/components/ui/button/Button.vue'
import Checkbox from '@/components/ui/checkbox/Checkbox.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import Input from '@/components/ui/input/Input.vue'
import SegmentedControl from '@/components/ui/segmented-control/SegmentedControl.vue'
import { useTheme } from '@/composables/useTheme'
import { useAuthStore } from '@/stores/useAuthStore'

const props = defineProps<{ mode: 'login' | 'register' }>()

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { resolved, setTheme } = useTheme()

/*
 * 登录页是用户见到的第一屏，必须给一个可用的外观开关：管理界面里有工具栏，
 * 这一页没有，否则系统深色用户进来只能看到一套不属于自己的配色。
 */
const themeModel = computed({
  get: () => resolved.value,
  set: (value: string) => setTheme(value as 'light' | 'dark'),
})
const themeOptions = [
  { value: 'light', label: '浅色' },
  { value: 'dark', label: '深色' },
]

/*
 * 封面图约定放在 public/auth/hero.jpg。文件还没就位时切到同目录的 SVG 占位图，
 * 这样页面不会出现破图，也方便后续直接替换生成图而不改代码。
 */
const HERO_FALLBACK = '/auth/hero.svg'
const heroArt = ref('/auth/hero.jpg')

function useFallbackHero(): void {
  if (heroArt.value !== HERO_FALLBACK) heroArt.value = HERO_FALLBACK
}

const heroPoints = [
  { label: '分级决策', hint: 'L0–L3 自动分流' },
  { label: '断点续跑', hint: '批准后接着执行' },
  { label: '全程留痕', hint: '每一步都可回看' },
]

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
  <div
    class="auth-stage relative flex min-h-screen items-center justify-center overflow-hidden px-4 py-8 sm:px-6 lg:px-8"
  >
    <main
      class="auth-window relative grid w-full max-w-[1120px] grid-cols-[minmax(0,1fr)] overflow-hidden rounded-card border border-separator bg-content shadow-window lg:grid-cols-[1.04fr_0.96fr]"
    >
      <!-- 桌面端封面：深色画面 + 白色文字，和右侧表单形成明确的明暗分栏 -->
      <aside class="relative hidden min-h-[680px] min-w-0 overflow-hidden lg:flex lg:flex-col">
        <img
          :src="heroArt"
          alt=""
          aria-hidden="true"
          class="auth-visual-art"
          @error="useFallbackHero"
        />
        <div class="auth-visual-scrim" aria-hidden="true" />

        <div class="relative z-10 flex h-full flex-col px-10 py-9 xl:px-12 xl:py-10">
          <div class="flex items-center gap-3">
            <span
              aria-hidden="true"
              class="relative grid size-9 place-items-center rounded-[9px] bg-white/12 text-white ring-1 ring-inset ring-white/20 backdrop-blur-md"
            >
              <span class="translate-y-px text-[17px] font-emphasis leading-none">R</span>
              <span
                class="absolute bottom-1.5 right-1.5 size-1.5 rounded-full bg-accent ring-2 ring-black/30"
              />
            </span>
            <div>
              <p class="text-title font-emphasis leading-none text-white">Regent AI</p>
              <p class="mt-1.5 text-caption text-white/55">任务执行平台</p>
            </div>
          </div>

          <div class="mt-auto max-w-[430px]">
            <div class="mb-5 flex items-center gap-2.5">
              <span class="h-px w-7 bg-white/30" aria-hidden="true" />
              <span class="text-[10px] font-emphasis uppercase tracking-[0.18em] text-white/55">
                AI Execution Platform
              </span>
            </div>
            <h2
              class="text-[38px] font-emphasis leading-[1.1] tracking-[-0.035em] text-white xl:text-[42px]"
            >
              把复杂工作，交给<br />一条可靠的执行链。
            </h2>
            <p class="mt-5 max-w-[370px] text-body leading-[1.75] text-white/60">
              从需求录入到执行完成，AI 负责推进与记录；需要判断的地方，按分级交还给你。
            </p>

            <div class="mt-10 grid grid-cols-3 gap-4 border-t border-white/15 pt-5">
              <div v-for="point in heroPoints" :key="point.label">
                <p class="text-caption font-emphasis text-white/85">{{ point.label }}</p>
                <p class="mt-1 text-caption text-white/40">{{ point.hint }}</p>
              </div>
            </div>
          </div>
        </div>
      </aside>

      <section class="auth-form-pane relative flex min-h-[680px] min-w-0 flex-col bg-content">
        <!-- 手机端没有左侧分栏，用一段短封面保留品牌氛围 -->
        <div class="relative h-[188px] overflow-hidden lg:hidden">
          <img
            :src="heroArt"
            alt=""
            aria-hidden="true"
            class="auth-visual-art"
            @error="useFallbackHero"
          />
          <div class="auth-visual-scrim auth-visual-scrim-mobile" aria-hidden="true" />
          <div class="relative z-10 flex h-full flex-col justify-between px-6 py-5">
            <div class="flex items-center gap-2.5">
              <span
                aria-hidden="true"
                class="relative grid size-8 place-items-center rounded-[8px] bg-white/12 text-white ring-1 ring-inset ring-white/20 backdrop-blur-md"
              >
                <span class="translate-y-px text-[15px] font-emphasis leading-none">R</span>
                <span class="absolute bottom-1 right-1 size-1.5 rounded-full bg-accent ring-2 ring-black/30" />
              </span>
              <div>
                <p class="text-body font-emphasis leading-none text-white">Regent AI</p>
                <p class="mt-1 text-caption text-white/55">任务执行平台</p>
              </div>
            </div>
            <p class="max-w-[250px] text-[13px] leading-5 text-white/60">
              让 AI 推进任务，把判断留给人。
            </p>
          </div>
        </div>

        <div
          class="auth-form-content relative z-10 flex min-w-0 flex-1 flex-col justify-center px-6 py-9 sm:px-10 lg:px-12 xl:px-14"
        >
          <div class="mx-auto w-full min-w-0 max-w-[360px]">
            <div class="mb-8 flex items-center justify-between gap-3">
              <RouterLink
                class="inline-flex items-center gap-1.5 text-caption text-label-secondary transition-colors duration-100 ease-apple hover:text-label"
                to="/download"
              >
                <Icon :as="Download" size="inline" />下载客户端
              </RouterLink>
              <SegmentedControl v-model="themeModel" :items="themeOptions" size="sm" />
            </div>

            <div>
              <h1 class="text-display font-emphasis leading-[34px] tracking-[-0.03em] text-label">
                {{ isRegister ? '创建账号' : '欢迎回来' }}
              </h1>
              <p class="mt-2 text-body leading-[1.7] text-label-secondary">
                {{
                  isRegister
                    ? '使用邮箱验证码完成注册，立即开始。'
                    : '登录后继续推进任务，关键决策仍由你拍板。'
                }}
              </p>
            </div>

            <p
              v-if="!isRegister && registered"
              class="mt-5 rounded-control border border-success/25 bg-success/8 px-3 py-2 text-caption text-label"
              role="status"
            >
              账号已创建，请使用注册邮箱登录。
            </p>

            <form class="mt-8 space-y-4" @submit.prevent="submit">
          <label v-if="!isRegister" class="block">
            <span class="mb-1.5 block text-caption font-emphasis text-label-secondary">
              邮箱或用户名
            </span>
            <Input
              v-model="identifier"
              size="xl"
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
              size="xl"
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
                  size="xl"
                  inputmode="numeric"
                  maxlength="6"
                  autocomplete="one-time-code"
                  placeholder="6 位数字"
                  :invalid="Boolean(error)"
                />
              </div>
              <Button
                size="xl"
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
              size="xl"
              :autocomplete="isRegister ? 'new-password' : 'current-password'"
              :invalid="Boolean(error)"
            />
            <span v-if="isRegister" class="mt-1 block text-caption text-label-tertiary">
              至少 8 个字符
            </span>
          </label>

              <div v-if="!isRegister" class="pt-0.5">
                <Checkbox v-model="rememberMe" label="在此设备保持登录" />
              </div>

          <label v-if="isRegister" class="block">
            <span class="mb-1.5 block text-caption font-emphasis text-label-secondary">
              确认密码
            </span>
            <Input
              v-model="confirmPassword"
              type="password"
              size="xl"
              autocomplete="new-password"
              :invalid="Boolean(error)"
            />
          </label>

          <p
            v-if="notice"
            class="rounded-control border border-accent/20 bg-accent/6 px-3 py-2 text-caption text-label"
            role="status"
          >
            {{ notice }}
          </p>
          <p
            v-if="error"
            class="flex items-start gap-2 rounded-control border border-danger/20 bg-danger/6 px-3 py-2 text-caption text-danger"
            role="alert"
          >
            <span class="mt-[3px] size-1.5 shrink-0 rounded-full bg-danger" aria-hidden="true" />
            <span>{{ error }}</span>
          </p>

          <Button
            type="submit"
            variant="primary"
            size="xl"
            block
            :disabled="auth.submitting"
          >
            <Icon :as="isRegister ? UserPlus : LogIn" size="inline" />
            {{ auth.submitting ? '请稍候…' : submitLabel }}
          </Button>
        </form>

        <div
          class="mt-7 flex items-center justify-center gap-1 border-t border-separator pt-5 text-caption text-label-secondary"
        >
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
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped>
/* 页面底色只做极轻的冷色光晕，主体仍然靠发丝线和大面积留白分层。 */
.auth-stage {
  background:
    radial-gradient(
      ellipse 62% 52% at 8% 0%,
      color-mix(in srgb, var(--sys-blue) 11%, transparent),
      transparent 72%
    ),
    radial-gradient(
      ellipse 52% 44% at 96% 100%,
      color-mix(in srgb, var(--sys-green) 7%, transparent),
      transparent 74%
    ),
    var(--window-bg);
}

.auth-visual-art {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center;
  animation: auth-art-in 1400ms var(--fx-ease) both;
}

/* 深色压层保证封面上的白色文字在任意生成图上都能读清。 */
.auth-visual-scrim {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(
      180deg,
      rgb(5 8 13 / 0.5) 0%,
      rgb(5 8 13 / 0.12) 40%,
      rgb(5 8 13 / 0.84) 100%
    ),
    radial-gradient(120% 85% at 82% 0%, rgb(0 122 255 / 0.16), transparent 62%);
}

.auth-visual-scrim-mobile {
  background: linear-gradient(180deg, rgb(5 8 13 / 0.28) 0%, rgb(5 8 13 / 0.76) 100%);
}

/* 表单区右上角留一层几乎看不见的冷光，深色模式下才不会变成一块死板的面板。 */
.auth-form-pane::before {
  position: absolute;
  inset: 0;
  background: radial-gradient(
    72% 48% at 100% 0%,
    color-mix(in srgb, var(--sys-blue) 5%, transparent),
    transparent 70%
  );
  content: '';
  pointer-events: none;
}

.auth-form-content {
  animation: auth-form-in 520ms 80ms var(--fx-ease) both;
}

@keyframes auth-art-in {
  from {
    opacity: 0;
    transform: scale(1.035);
  }
  to {
    opacity: 1;
    transform: scale(1.01);
  }
}

@keyframes auth-form-in {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 手机端封面比桌面分栏矮，主体要落在画面偏上的位置，不能被裁成一条模糊色带。 */
@media (max-width: 1023px) {
  .auth-visual-art {
    object-position: center 42%;
  }
}
</style>
