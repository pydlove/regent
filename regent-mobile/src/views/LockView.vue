<script setup lang="ts">
import { LockKeyhole, ScanFace, UserRoundX } from '@lucide/vue'

import AppMark from '@/components/AppMark.vue'

defineProps<{
  displayName: string
  biometryLabel: string
  busy: boolean
  error: string
}>()

defineEmits<{
  unlock: []
  switchAccount: []
}>()
</script>

<template>
  <main class="lock-layout">
    <div class="lock-brand">
      <AppMark />
      <p class="eyebrow">Regent Mobile</p>
      <h1>已锁定</h1>
      <p class="auth-slogan">电脑不在身边，AI 任务随时随地开工</p>
    </div>

    <section class="panel lock-panel">
      <span class="lock-orbit"><LockKeyhole :size="28" aria-hidden="true" /></span>
      <h2>{{ displayName }}，欢迎回来</h2>
      <p>使用 {{ biometryLabel }} 解锁本机保存的会话。</p>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <button class="primary-button" type="button" :disabled="busy" @click="$emit('unlock')">
        <ScanFace :size="18" aria-hidden="true" />
        {{ busy ? '正在验证…' : `使用${biometryLabel}解锁` }}
      </button>
      <button class="secondary-button" type="button" :disabled="busy" @click="$emit('switchAccount')">
        <UserRoundX :size="17" aria-hidden="true" />
        切换账号
      </button>
    </section>
  </main>
</template>
