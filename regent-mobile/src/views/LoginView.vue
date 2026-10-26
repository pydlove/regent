<script setup lang="ts">
import { Eye, EyeOff, KeyRound, LockKeyhole, ScanFace } from '@lucide/vue'
import { ref } from 'vue'

import AppMark from '@/components/AppMark.vue'

defineProps<{
  busy: boolean
  error: string
  installationLabel: string
  biometryLabel: string
  native: boolean
}>()

const emit = defineEmits<{
  login: [username: string, password: string]
}>()

const username = ref('')
const password = ref('')
const showPassword = ref(false)

function submit() {
  emit('login', username.value, password.value)
}
</script>

<template>
  <main class="auth-layout">
    <section class="auth-brand">
      <AppMark />
      <div>
        <p class="eyebrow">Regent Mobile</p>
        <h1>Regent</h1>
        <p class="auth-slogan">电脑不在身边，AI 任务随时随地开工</p>
      </div>
    </section>

    <form class="panel auth-panel" novalidate @submit.prevent="submit">
      <div class="panel-heading">
        <div>
          <p class="eyebrow">安全登录</p>
          <h2>继续你的 AI 任务</h2>
        </div>
        <span class="security-mark" :title="biometryLabel">
          <ScanFace :size="19" aria-hidden="true" />
        </span>
      </div>

      <label class="field">
        <span>账号</span>
        <span class="input-shell">
          <KeyRound :size="17" aria-hidden="true" />
          <input
            v-model="username"
            autocomplete="username"
            autocapitalize="none"
            placeholder="用户名或邮箱"
            type="text"
          />
        </span>
      </label>

      <label class="field">
        <span>密码</span>
        <span class="input-shell">
          <LockKeyhole :size="17" aria-hidden="true" />
          <input
            v-model="password"
            :type="showPassword ? 'text' : 'password'"
            autocomplete="current-password"
            placeholder="输入登录密码"
          />
          <button
            class="input-action"
            type="button"
            :aria-label="showPassword ? '隐藏密码' : '显示密码'"
            @click="showPassword = !showPassword"
          >
            <EyeOff v-if="showPassword" :size="17" aria-hidden="true" />
            <Eye v-else :size="17" aria-hidden="true" />
          </button>
        </span>
      </label>

      <p v-if="error" class="form-error" role="alert">{{ error }}</p>

      <button class="primary-button auth-submit" type="submit" :disabled="busy">
        <span>{{ busy ? '正在验证…' : '安全登录' }}</span>
        <span class="button-spinner" :class="{ visible: busy }"></span>
      </button>

      <div class="auth-footnote">
        <span>安装实例</span>
        <code>{{ installationLabel }}</code>
      </div>
      <p class="caption">
        {{ native ? `敏感操作将使用 ${biometryLabel}` : 'Web 调试模式不会持久化长期令牌' }}
      </p>
    </form>
  </main>
</template>
