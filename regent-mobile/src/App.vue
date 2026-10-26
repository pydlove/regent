<script setup lang="ts">
import {
  BadgeCheck,
  CircleUserRound,
  House,
  ListChecks,
} from '@lucide/vue'
import { Capacitor } from '@capacitor/core'
import { computed, onMounted, ref } from 'vue'

import AppMark from '@/components/AppMark.vue'
import { useMobileSession } from '@/stores/session'
import AccountView from '@/views/AccountView.vue'
import DecisionsView from '@/views/DecisionsView.vue'
import LockView from '@/views/LockView.vue'
import LoginView from '@/views/LoginView.vue'
import OverviewView from '@/views/OverviewView.vue'
import TasksView from '@/views/TasksView.vue'

type TabKey = 'home' | 'tasks' | 'decisions' | 'account'

const {
  state,
  bootstrap,
  login,
  unlock,
  switchAccount,
  refresh,
  lock,
} = useMobileSession()

const activeTab = ref<TabKey>('home')
const native = Capacitor.isNativePlatform()
const platform = Capacitor.getPlatform()

const titles: Record<TabKey, { eyebrow: string; title: string }> = {
  home: { eyebrow: '远程任务控制台', title: '概览' },
  tasks: { eyebrow: '执行队列', title: '任务' },
  decisions: { eyebrow: '人工决策', title: '决策' },
  account: { eyebrow: '账号与安全', title: '我的' },
}

const pageTitle = computed(() => titles[activeTab.value])
const displayInitial = computed(() =>
  state.session?.user.displayName.slice(0, 1).toUpperCase() ?? 'R')

const installationLabel = computed(() => {
  const value = state.installationId
  if (!value) return '正在生成'
  return value.length <= 18 ? value : `${value.slice(0, 10)}…${value.slice(-6)}`
})

async function handleSwitchAccount() {
  await switchAccount()
  activeTab.value = 'home'
}

function handleLock() {
  lock()
  activeTab.value = 'home'
}

onMounted(() => {
  void bootstrap()
})
</script>

<template>
  <div class="app-root" :class="`platform-${platform}`">
    <div class="device-surface">
      <div v-if="state.phase === 'booting'" class="boot-screen">
        <AppMark />
        <p class="eyebrow">Regent Mobile</p>
        <h1>正在恢复安全会话</h1>
        <span class="boot-line"></span>
      </div>

      <LoginView
        v-else-if="state.phase === 'login'"
        :busy="state.busy"
        :error="state.error"
        :installation-label="installationLabel"
        :biometry-label="state.biometry.label"
        :native="native"
        @login="login"
      />

      <LockView
        v-else-if="state.phase === 'locked' && state.session"
        :display-name="state.session.user.displayName"
        :biometry-label="state.biometry.label"
        :busy="state.busy"
        :error="state.error"
        @unlock="unlock"
        @switch-account="handleSwitchAccount"
      />

      <template v-else-if="state.phase === 'authenticated' && state.session">
        <header class="app-header">
          <div class="app-header-copy">
            <p class="eyebrow">{{ pageTitle.eyebrow }}</p>
            <h1>{{ pageTitle.title }}</h1>
          </div>
          <button
            class="account-chip"
            type="button"
            aria-label="打开账号与安全"
            @click="activeTab = 'account'"
          >
            <span>
              <img v-if="state.avatarSrc" :src="state.avatarSrc" :alt="state.session.user.displayName" />
              <template v-else>{{ displayInitial }}</template>
            </span>
            <strong>{{ state.session.user.displayName }}</strong>
          </button>
        </header>

        <main class="app-content">
          <OverviewView
            v-if="activeTab === 'home'"
            :dashboard="state.dashboard"
            :loading="state.loadingDashboard"
            :error="state.dashboardError"
            @refresh="refresh"
            @show-tasks="activeTab = 'tasks'"
            @show-decisions="activeTab = 'decisions'"
          />
          <TasksView
            v-else-if="activeTab === 'tasks'"
            :dashboard="state.dashboard"
            :loading="state.loadingDashboard"
            @refresh="refresh"
          />
          <DecisionsView
            v-else-if="activeTab === 'decisions'"
            :dashboard="state.dashboard"
            :loading="state.loadingDashboard"
            @refresh="refresh"
          />
          <AccountView
            v-else
            :session="state.session"
            :avatar-src="state.avatarSrc"
            :biometry="state.biometry"
            :busy="state.busy"
            @switch-account="handleSwitchAccount"
            @lock="handleLock"
          />
        </main>

        <nav class="bottom-nav" aria-label="主导航">
          <button
            type="button"
            :class="{ selected: activeTab === 'home' }"
            aria-label="概览"
            @click="activeTab = 'home'"
          >
            <House :size="20" aria-hidden="true" />
            <span>概览</span>
          </button>
          <button
            type="button"
            :class="{ selected: activeTab === 'tasks' }"
            aria-label="任务"
            @click="activeTab = 'tasks'"
          >
            <ListChecks :size="20" aria-hidden="true" />
            <span>任务</span>
          </button>
          <button
            type="button"
            :class="{ selected: activeTab === 'decisions' }"
            aria-label="决策"
            @click="activeTab = 'decisions'"
          >
            <BadgeCheck :size="20" aria-hidden="true" />
            <span>决策</span>
          </button>
          <button
            type="button"
            :class="{ selected: activeTab === 'account' }"
            aria-label="我的"
            @click="activeTab = 'account'"
          >
            <CircleUserRound :size="20" aria-hidden="true" />
            <span>我的</span>
          </button>
        </nav>
      </template>
    </div>
  </div>
</template>
