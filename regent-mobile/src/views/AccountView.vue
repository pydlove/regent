<script setup lang="ts">
import {
  Clock3,
  KeyRound,
  LockKeyhole,
  LogOut,
  ScanFace,
  ShieldCheck,
  Smartphone,
} from '@lucide/vue'
import { computed } from 'vue'

import { describeInstallationId } from '@/services/installation'
import type { BiometryState } from '@/services/biometrics'
import type { MobileSession } from '@/types'

const props = defineProps<{
  session: MobileSession
  avatarSrc: string
  biometry: BiometryState
  busy: boolean
}>()

defineEmits<{
  switchAccount: []
  lock: []
}>()

const expiry = computed(() =>
  new Intl.DateTimeFormat('zh-CN', {
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(props.session.refreshExpiresAt)))
</script>

<template>
  <section class="view-stack">
    <div class="profile-panel panel">
      <span class="profile-avatar">
        <img v-if="avatarSrc" :src="avatarSrc" :alt="session.user.displayName" />
        <template v-else>{{ session.user.displayName.slice(0, 1) }}</template>
      </span>
      <div>
        <p class="eyebrow">当前账号</p>
        <h2>{{ session.user.displayName }}</h2>
        <p>{{ session.user.email || `@${session.user.username}` }}</p>
      </div>
      <ShieldCheck :size="22" class="profile-shield" aria-hidden="true" />
    </div>

    <section class="settings-group">
      <h2>安全状态</h2>
      <div class="settings-list">
        <div class="setting-row">
          <span class="setting-icon"><ScanFace :size="18" aria-hidden="true" /></span>
          <div><strong>设备解锁</strong><p>{{ biometry.label }}</p></div>
          <span
            class="status-badge"
            :class="biometry.isAvailable ? 'green' : 'blue'"
          >
            {{ biometry.native ? (biometry.isAvailable ? '已配置' : '系统回退') : '调试' }}
          </span>
        </div>
        <div class="setting-row">
          <span class="setting-icon"><KeyRound :size="18" aria-hidden="true" /></span>
          <div><strong>登录会话</strong><p>手机独立 Bearer Token</p></div>
          <span class="status-badge blue">已加密</span>
        </div>
        <div class="setting-row">
          <span class="setting-icon"><Clock3 :size="18" aria-hidden="true" /></span>
          <div><strong>会话有效期</strong><p>刷新令牌至 {{ expiry }}</p></div>
        </div>
      </div>
    </section>

    <section class="settings-group">
      <h2>本机实例</h2>
      <div class="settings-list">
        <div class="setting-row">
          <span class="setting-icon"><Smartphone :size="18" aria-hidden="true" /></span>
          <div><strong>安装标识</strong><p><code>{{ describeInstallationId(session.installationId) }}</code></p></div>
        </div>
      </div>
    </section>

    <button
      v-if="biometry.native"
      class="secondary-button full-row"
      type="button"
      @click="$emit('lock')"
    >
      <LockKeyhole :size="17" aria-hidden="true" />
      锁定应用
    </button>
    <button class="danger-button full-row" type="button" :disabled="busy" @click="$emit('switchAccount')">
      <LogOut :size="17" aria-hidden="true" />
      退出并切换账号
    </button>
    <p class="caption center">切换账号会清除本机任务、项目与设备缓存。</p>
    <p class="caption center version-line">Regent Mobile · V1.0.5 · P0</p>
  </section>
</template>
