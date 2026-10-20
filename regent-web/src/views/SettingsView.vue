<script setup lang="ts">
import {
  CircleUserRound,
  Download,
  Gauge,
  LayoutPanelLeft,
  LogOut,
  Palette,
  RadioTower,
  RefreshCw,
} from '@lucide/vue'
import type { Component } from 'vue'
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ApiError } from '@/api/http'
import {
  getExecutionSettings,
  getRelaySettings,
  installRelay as installRelayOnServer,
  updateExecutionSettings,
  updateRelaySettings,
} from '@/api/settings'
import CcSwitchDownloadCard from '@/components/business/CcSwitchDownloadCard.vue'
import Avatar from '@/components/ui/avatar/Avatar.vue'
import Button from '@/components/ui/button/Button.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import IconButton from '@/components/ui/icon-button/IconButton.vue'
import Input from '@/components/ui/input/Input.vue'
import SegmentedControl from '@/components/ui/segmented-control/SegmentedControl.vue'
import StatusDot from '@/components/ui/status-dot/StatusDot.vue'
import Switch from '@/components/ui/switch/Switch.vue'
import Tooltip from '@/components/ui/tooltip/Tooltip.vue'
import { useCodexInstallDialog } from '@/composables/useCodexInstallDialog'
import { useTheme, type ThemePreference } from '@/composables/useTheme'
import { useToast } from '@/composables/useToast'
import { useAuthStore } from '@/stores/useAuthStore'
import { useExecutionRuntimeStore } from '@/stores/useExecutionRuntimeStore'
import type {
  RelayInstallResult,
  RelaySettings,
  RelayState,
  RelayStatus,
} from '@/types/api'
import BaselineView from '@/views/BaselineView.vue'

type SettingsSection = 'appearance' | 'baseline' | 'execution' | 'relay' | 'account'
type SectionItem = {
  id: SettingsSection
  label: string
  icon: Component
  tone: string
}

const sections: SectionItem[] = [
  { id: 'appearance', label: '外观', icon: Palette, tone: 'bg-accent' },
  { id: 'baseline', label: '样式基准', icon: LayoutPanelLeft, tone: 'bg-accent' },
  { id: 'execution', label: '执行', icon: Gauge, tone: 'bg-warning' },
  { id: 'relay', label: '中继', icon: RadioTower, tone: 'bg-success' },
  { id: 'account', label: '账户', icon: CircleUserRound, tone: 'bg-neutral' },
]

const route = useRoute()
const router = useRouter()
const toast = useToast()
const auth = useAuthStore()
const executionRuntime = useExecutionRuntimeStore()
const { openDialog: openCodexInstallDialog } = useCodexInstallDialog()
const { preference, setTheme } = useTheme()

const sectionParam = computed(() => {
  const value = route.params.section
  return Array.isArray(value) ? value[0] : value
})
const activeSection = computed<SettingsSection>(() => {
  const match = sections.find((section) => section.id === sectionParam.value)
  return match?.id ?? 'appearance'
})

const selectSection = (section: SettingsSection) => {
  if (activeSection.value === section) return
  void router.push({ name: 'settings', params: { section } })
}

const themeModel = computed({
  get: () => preference.value,
  set: (value: string) => setTheme(value as ThemePreference),
})
const themeOptions = [
  { value: 'light', label: '浅色' },
  { value: 'dark', label: '深色' },
  { value: 'system', label: '跟随系统' },
]

const loggingOut = ref(false)

async function logout() {
  if (loggingOut.value) return
  loggingOut.value = true
  try {
    await auth.logout()
    await router.push({ name: 'login' })
  } finally {
    loggingOut.value = false
  }
}

const executionLimit = ref('1')
const savedExecutionLimit = ref('1')
const runningTaskCount = ref(0)
const executionLoading = ref(false)
const executionSaving = ref(false)
const executionLoaded = ref(false)
const executionError = ref<string | null>(null)
const codexRuntime = computed(() => executionRuntime.status)
const codexChecking = computed(() => executionRuntime.loading)
const codexError = computed(() => executionRuntime.error)

const numericLimit = computed(() => Number(executionLimit.value))
const executionValid = computed(
  () => Number.isInteger(numericLimit.value) && numericLimit.value >= 1 && numericLimit.value <= 64,
)
const executionDirty = computed(
  () => executionLoaded.value && executionLimit.value !== savedExecutionLimit.value,
)
const codexStatusLabel = computed(() => {
  if (codexChecking.value) return '检测中'
  if (!codexRuntime.value) return '未检测'
  return codexRuntime.value.available ? '可用' : '不可用'
})
const codexStatusDot = computed(() => {
  if (codexChecking.value) return 'bg-accent'
  if (!codexRuntime.value) return 'bg-neutral'
  return codexRuntime.value.available ? 'bg-success' : 'bg-danger'
})
const codexCheckedAt = computed(() => {
  if (!codexRuntime.value) return ''
  return new Date(codexRuntime.value.checkedAt).toLocaleString('zh-CN')
})

function messageOf(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback
}

async function loadExecutionSettings() {
  executionLoading.value = true
  executionError.value = null
  try {
    const settings = await getExecutionSettings()
    executionLimit.value = String(settings.maxConcurrentTasks)
    savedExecutionLimit.value = executionLimit.value
    runningTaskCount.value = settings.runningTaskCount
    executionLoaded.value = true
  } catch (error) {
    executionError.value = messageOf(error, '无法读取执行设置')
  } finally {
    executionLoading.value = false
  }
}

async function saveExecutionSettings() {
  if (!executionValid.value || executionSaving.value) return
  executionSaving.value = true
  executionError.value = null
  try {
    const settings = await updateExecutionSettings(numericLimit.value)
    executionLimit.value = String(settings.maxConcurrentTasks)
    savedExecutionLimit.value = executionLimit.value
    runningTaskCount.value = settings.runningTaskCount
    toast.success('执行设置已保存', `并发上限为 ${settings.maxConcurrentTasks}`)
  } catch (error) {
    executionError.value = messageOf(error, '无法保存执行设置')
  } finally {
    executionSaving.value = false
  }
}

async function detectCodex() {
  await executionRuntime.check(true)
}

const relayForm = reactive({
  serverUrl: '',
  monitoringEnabled: false,
  serverIp: '',
  sshUsername: 'root',
  installDirectory: '/root/app/regent-relay',
  password: '',
})
const savedRelayForm = reactive({
  serverUrl: '',
  monitoringEnabled: false,
  serverIp: '',
  sshUsername: 'root',
  installDirectory: '/root/app/regent-relay',
})
const relayStatus = ref<RelayStatus | null>(null)
const relayLoading = ref(false)
const relaySaving = ref(false)
const relayInstalling = ref(false)
const relayRefreshing = ref(false)
const relayLoaded = ref(false)
const relayError = ref<string | null>(null)
const relayInstallResult = ref<RelayInstallResult | null>(null)

const relayPagePaths = new Set([
  '/dashboard',
  '/tasks',
  '/decisions',
  '/projects',
  '/environments',
  '/bugs',
])

function isRelayPagePath(pathname: string): boolean {
  const path = pathname.replace(/\/+$/, '') || '/'
  return relayPagePaths.has(path) || path === '/settings' || path.startsWith('/settings/')
}

function inspectRelayServerUrl(rawValue: string) {
  const raw = rawValue.trim()
  if (!raw) {
    return {
      valid: !relayForm.monitoringEnabled,
      normalized: '',
      pagePath: '',
    }
  }
  try {
    const url = new URL(raw)
    const validBase =
      (url.protocol === 'http:' || url.protocol === 'https:') &&
      !url.username &&
      !url.password &&
      !url.search &&
      !url.hash
    const rootPath = url.pathname === '/' || url.pathname === ''
    const pagePath = validBase && !rootPath && isRelayPagePath(url.pathname)
    return {
      valid: validBase && (rootPath || pagePath),
      normalized: validBase && (rootPath || pagePath) ? url.origin : '',
      pagePath: pagePath ? url.pathname.replace(/\/+$/, '') : '',
    }
  } catch {
    return {
      valid: false,
      normalized: '',
      pagePath: '',
    }
  }
}

const relayUrlState = computed(() => inspectRelayServerUrl(relayForm.serverUrl))
const relayUrlValid = computed(() => relayUrlState.value.valid)
const relayServerIpValid = computed(
  () =>
    relayForm.serverIp.trim().length > 0 &&
    !/\s/.test(relayForm.serverIp) &&
    /^[A-Za-z0-9.-]+$/.test(relayForm.serverIp),
)
const relayUsernameValid = computed(() => /^[A-Za-z_][A-Za-z0-9_-]{0,31}$/.test(relayForm.sshUsername))
const relayDirectoryValid = computed(() => {
  const value = relayForm.installDirectory.trim()
  return /^\/[A-Za-z0-9._/-]+$/.test(value) &&
    value !== '/' &&
    !value.includes('//') &&
    !value.includes('/./') &&
    !value.includes('/../') &&
    !value.endsWith('/.') &&
    !value.endsWith('/..') &&
    !value.endsWith('/')
})
const relayConnectionValid = computed(
  () => relayServerIpValid.value && relayUsernameValid.value && relayDirectoryValid.value,
)
const relayDirty = computed(
  () =>
    relayLoaded.value &&
    (relayForm.serverUrl.trim() !== savedRelayForm.serverUrl ||
      relayForm.monitoringEnabled !== savedRelayForm.monitoringEnabled ||
      relayForm.serverIp.trim() !== savedRelayForm.serverIp ||
      relayForm.sshUsername.trim() !== savedRelayForm.sshUsername ||
      relayForm.installDirectory.trim() !== savedRelayForm.installDirectory),
)
const relayCanSave = computed(
  () =>
    relayLoaded.value &&
    relayUrlValid.value &&
    relayConnectionValid.value &&
    relayDirty.value &&
    !relaySaving.value &&
    !relayInstalling.value,
)
const relayCanInstall = computed(
  () =>
    relayLoaded.value &&
    relayUrlValid.value &&
    relayConnectionValid.value &&
    !relaySaving.value &&
    !relayInstalling.value,
)

function applyRelaySettings(settings: RelaySettings) {
  relayForm.serverUrl = settings.serverUrl
  relayForm.monitoringEnabled = settings.monitoringEnabled
  relayForm.serverIp = settings.serverIp
  relayForm.sshUsername = settings.sshUsername
  relayForm.installDirectory = settings.installDirectory
  relayForm.password = ''
  savedRelayForm.serverUrl = settings.serverUrl
  savedRelayForm.monitoringEnabled = settings.monitoringEnabled
  savedRelayForm.serverIp = settings.serverIp
  savedRelayForm.sshUsername = settings.sshUsername
  savedRelayForm.installDirectory = settings.installDirectory
  relayStatus.value = settings.status
  relayLoaded.value = true
}

async function loadRelaySettings() {
  relayLoading.value = true
  relayError.value = null
  try {
    applyRelaySettings(await getRelaySettings())
  } catch (error) {
    relayError.value = messageOf(error, '无法读取中继设置')
  } finally {
    relayLoading.value = false
  }
}

async function refreshRelayStatus() {
  if (relayRefreshing.value || !relayLoaded.value) return
  relayRefreshing.value = true
  try {
    const settings = await getRelaySettings()
    relayStatus.value = settings.status
    relayError.value = null
  } catch (error) {
    relayError.value = messageOf(error, '无法刷新中继状态')
  } finally {
    relayRefreshing.value = false
  }
}

async function saveRelaySettings() {
  if (!relayCanSave.value) return
  relaySaving.value = true
  relayError.value = null
  try {
    const settings = await updateRelaySettings(
      relayUrlState.value.normalized,
      relayForm.monitoringEnabled,
      relayForm.serverIp.trim(),
      relayForm.sshUsername.trim(),
      relayForm.installDirectory.trim(),
    )
    applyRelaySettings(settings)
    toast.success('中继设置已保存', settings.monitoringEnabled ? '正在监听中继状态' : '状态监听已关闭')
    syncRelayPolling()
  } catch (error) {
    relayError.value = messageOf(error, '无法保存中继设置')
  } finally {
    relaySaving.value = false
  }
}

async function installRelay() {
  if (!relayCanInstall.value) return
  relayInstalling.value = true
  relayError.value = null
  relayInstallResult.value = null
  if (!relayForm.serverUrl.trim()) {
    relayForm.serverUrl = `http://${relayForm.serverIp.trim()}:18787`
    relayForm.monitoringEnabled = true
  }
  const password = relayForm.password
  try {
    if (relayDirty.value) {
      applyRelaySettings(
        await updateRelaySettings(
          relayUrlState.value.normalized,
          relayForm.monitoringEnabled,
          relayForm.serverIp.trim(),
          relayForm.sshUsername.trim(),
          relayForm.installDirectory.trim(),
        ),
      )
    }
    const result = await installRelayOnServer(password)
    relayInstallResult.value = result
    if (result.success) {
      toast.success('中继器安装完成', result.installDirectory)
      await refreshRelayStatus()
    }
  } catch (error) {
    relayError.value = messageOf(error, '无法安装中继器')
  } finally {
    relayForm.password = ''
    relayInstalling.value = false
  }
}

const relayPresentation = computed(() => {
  const state: RelayState = relayStatus.value?.state ?? 'UNCONFIGURED'
  switch (state) {
    case 'ONLINE':
      return {
        title: '中继可用',
        detail: '服务器与本地连接器均在线',
        dot: 'bg-success',
        text: 'text-success',
      }
    case 'DEGRADED':
      return {
        title: '连接器离线',
        detail: '中继服务器可达，但本地连接器尚未连接',
        dot: 'bg-warning',
        text: 'text-warning',
      }
    case 'OFFLINE':
      return {
        title: '服务器不可达',
        detail: '无法读取中继健康状态',
        dot: 'bg-danger',
        text: 'text-danger',
      }
    case 'DISABLED':
      return {
        title: '监听已关闭',
        detail: '服务器地址已保存，但当前不检查状态',
        dot: 'bg-neutral',
        text: 'text-label-secondary',
      }
    default:
      return {
        title: '尚未配置',
        detail: '填写中继服务器地址后即可监听状态',
        dot: 'bg-neutral',
        text: 'text-label-secondary',
      }
  }
})

const connectorText = computed(() => {
  const value = relayStatus.value?.connectorOnline
  if (value === true) return '在线'
  if (value === false) return '离线'
  return '未知'
})

const checkedAtText = computed(() => {
  const value = relayStatus.value?.checkedAt
  if (!value) return '尚未检查'
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(new Date(value))
})

let relayPollTimer: number | undefined

function stopRelayPolling() {
  if (relayPollTimer !== undefined) {
    window.clearInterval(relayPollTimer)
    relayPollTimer = undefined
  }
}

function syncRelayPolling() {
  stopRelayPolling()
  if (
    activeSection.value === 'relay' &&
    relayLoaded.value &&
    savedRelayForm.monitoringEnabled &&
    document.visibilityState === 'visible'
  ) {
    relayPollTimer = window.setInterval(() => void refreshRelayStatus(), 8000)
  }
}

function handleVisibilityChange() {
  if (document.visibilityState === 'visible' && activeSection.value === 'relay') {
    void refreshRelayStatus()
  }
  syncRelayPolling()
}

watch(activeSection, (section) => {
  if (section === 'relay' && !relayLoaded.value) {
    void loadRelaySettings()
  }
  syncRelayPolling()
})

watch(
  () => savedRelayForm.monitoringEnabled,
  () => syncRelayPolling(),
)

onMounted(() => {
  void loadExecutionSettings()
  if (activeSection.value === 'relay') {
    void loadRelaySettings()
  }
  document.addEventListener('visibilitychange', handleVisibilityChange)
  syncRelayPolling()
})

watch(
  activeSection,
  (section) => {
    if (section === 'execution' && !codexRuntime.value && !codexChecking.value) {
      void detectCodex()
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  stopRelayPolling()
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})
</script>

<template>
  <div class="flex h-full min-h-0 bg-window max-lg:flex-col">
    <aside
      class="w-[200px] shrink-0 p-3 max-lg:w-full max-lg:overflow-x-auto max-lg:border-b max-lg:border-separator max-lg:p-2"
    >
      <div class="flex flex-col gap-1 max-lg:flex-row">
        <button
          v-for="section in sections"
          :key="section.id"
          type="button"
          :aria-current="activeSection === section.id ? 'page' : undefined"
          :class="[
            'group flex h-row shrink-0 items-center gap-2 rounded-control px-2 text-left text-body transition-colors duration-[120ms] ease-apple',
            activeSection === section.id
              ? 'bg-accent text-white shadow-control'
              : 'text-label hover:bg-hover',
          ]"
          @click="selectSection(section.id)"
        >
          <span
            :class="[
              'grid size-5 shrink-0 place-items-center rounded-[5px] text-white',
              activeSection === section.id ? 'bg-white/20' : section.tone,
            ]"
          >
            <Icon :as="section.icon" size="inline" />
          </span>
          <span class="truncate">{{ section.label }}</span>
        </button>
      </div>
    </aside>

    <section class="scroll-area min-w-0 flex-1 overflow-y-auto">
      <BaselineView v-if="activeSection === 'baseline'" />
      <div v-else class="mx-auto w-full max-w-[720px] px-8 py-7 max-sm:px-5 max-sm:py-6">
        <template v-if="activeSection === 'appearance'">
          <header class="mb-6">
            <h1 class="text-metric font-emphasis text-label">外观</h1>
            <p class="mt-1 text-body text-label-secondary">选择 Regent 在浅色与深色环境中的显示方式。</p>
          </header>

          <div class="overflow-hidden rounded-panel shadow-panel">
            <div
              class="flex items-center gap-6 bg-content px-4 py-3 max-sm:flex-col max-sm:items-start max-sm:gap-3"
            >
              <div class="min-w-0">
                <div class="text-body font-emphasis text-label">外观模式</div>
                <div class="mt-0.5 text-caption text-label-secondary">
                  跟随系统时会自动响应 macOS 的深浅色切换。
                </div>
              </div>
              <SegmentedControl
                v-model="themeModel"
                :items="themeOptions"
                size="lg"
                class="ml-auto max-sm:ml-0"
              />
            </div>
          </div>
        </template>

        <template v-else-if="activeSection === 'execution'">
          <header class="mb-6">
            <h1 class="text-metric font-emphasis text-label">执行</h1>
            <p class="mt-1 text-body text-label-secondary">
              检查本机 Codex 执行器，并控制平台同时运行的任务数量。
            </p>
          </header>

          <section class="mb-4 overflow-hidden rounded-panel bg-content shadow-panel">
            <div class="flex items-start gap-4 px-4 py-4">
              <div class="min-w-0 flex-1">
                <div class="flex flex-wrap items-center gap-2">
                  <span class="text-body font-emphasis text-label">Codex 执行器</span>
                  <StatusDot :dot="codexStatusDot">{{ codexStatusLabel }}</StatusDot>
                </div>
                <p class="mt-1 text-caption text-label-secondary">
                  检测本机是否能找到并运行 codex。只执行版本命令，不调用模型。
                </p>
              </div>
              <div class="flex shrink-0 items-center gap-2">
                <Button size="sm" @click="openCodexInstallDialog">安装 / 更新</Button>
                <Button size="sm" :disabled="codexChecking" @click="detectCodex">
                  <Icon
                    :as="RefreshCw"
                    size="inline"
                    :class="{ 'animate-spin': codexChecking }"
                  />
                  {{ codexChecking ? '检测中…' : '重新检测' }}
                </Button>
              </div>
            </div>

            <template v-if="codexRuntime">
              <div class="h-px bg-separator-soft" />
              <dl class="grid gap-2 px-4 py-3 text-caption sm:grid-cols-2">
                <div class="flex min-w-0 items-center gap-2">
                  <dt class="shrink-0 text-label-secondary">配置命令</dt>
                  <dd class="ml-auto truncate font-mono text-label">
                    {{ codexRuntime.configuredCommand }}
                  </dd>
                </div>
                <div class="flex min-w-0 items-center gap-2">
                  <dt class="shrink-0 text-label-secondary">版本</dt>
                  <dd class="ml-auto truncate text-label">{{ codexRuntime.version ?? '未返回' }}</dd>
                </div>
                <div class="flex min-w-0 items-center gap-2 sm:col-span-2">
                  <dt class="shrink-0 text-label-secondary">实际路径</dt>
                  <dd class="ml-auto truncate font-mono text-label">
                    {{ codexRuntime.executablePath ?? '未找到' }}
                  </dd>
                </div>
                <div class="flex min-w-0 items-center gap-2 sm:col-span-2">
                  <dt class="shrink-0 text-label-secondary">检测结果</dt>
                  <dd
                    class="ml-auto text-right"
                    :class="codexRuntime.available ? 'text-label-secondary' : 'text-danger'"
                  >
                    {{ codexRuntime.message }}
                  </dd>
                </div>
              </dl>
              <div
                v-if="!codexRuntime.available"
                class="border-t border-separator-soft bg-warning/5 px-4 py-3"
              >
                <p class="text-caption font-emphasis text-label">必须安装 Codex 才能执行任务</p>
                <p class="mt-2 text-caption text-label-tertiary">
                  npm 和 GitHub 位于国外，国内网络可能需要代理或“魔法”才能完成安装。
                </p>
                <Button class="mt-2" size="sm" @click="openCodexInstallDialog">
                  查看安装方式
                </Button>
              </div>
              <p class="border-t border-separator-soft px-4 py-2 text-caption text-label-tertiary">
                最近检测：{{ codexCheckedAt }}。可用只代表可执行文件能启动，不代表 Codex 已完成登录。
              </p>
            </template>
            <p v-if="codexError" class="border-t border-separator-soft px-4 py-2 text-caption text-danger">
              {{ codexError }}
            </p>
          </section>

          <CcSwitchDownloadCard class="mt-4 mb-4" />

          <div class="overflow-hidden rounded-panel bg-content shadow-panel">
            <div
              class="grid grid-cols-[180px_minmax(0,1fr)] items-center gap-6 px-4 py-3 max-sm:grid-cols-1 max-sm:gap-2"
            >
              <div>
                <div class="text-body font-emphasis text-label">并发上限</div>
                <div class="mt-0.5 text-caption text-label-secondary">范围 1–64</div>
              </div>
              <div class="flex items-center gap-2">
                <div class="w-24 shrink-0">
                  <Input
                    v-model="executionLimit"
                    type="number"
                    size="lg"
                    placeholder="1"
                    :disabled="executionLoading || executionSaving"
                    :invalid="!executionValid"
                  />
                </div>
                <span class="shrink-0 whitespace-nowrap text-caption text-label-secondary">
                  个任务
                </span>
              </div>
            </div>
            <div class="h-px bg-separator-soft" />
            <div class="flex items-center gap-6 px-4 py-3">
              <div class="min-w-0">
                <div class="text-body font-emphasis text-label">当前运行</div>
                <div class="mt-0.5 text-caption text-label-secondary">
                  调低上限不会终止已经开始的任务。
                </div>
              </div>
              <span class="tnum ml-auto text-body font-emphasis text-label">
                {{ executionLoading ? '—' : runningTaskCount }} /
                {{ executionValid ? numericLimit : '—' }}
              </span>
            </div>
          </div>

          <p v-if="executionError" class="mt-3 text-caption text-danger">{{ executionError }}</p>
          <div class="mt-5 flex justify-end">
            <Button
              variant="primary"
              size="lg"
              :disabled="executionLoading || executionSaving || !executionValid || !executionDirty"
              @click="saveExecutionSettings"
            >
              {{ executionSaving ? '保存中…' : '保存' }}
            </Button>
          </div>
        </template>

        <template v-else-if="activeSection === 'relay'">
          <header class="mb-6">
            <h1 class="text-metric font-emphasis text-label">中继</h1>
            <p class="mt-1 text-body text-label-secondary">
              配置公网中继服务器，自动安装中继器，并监听服务器与本地连接器是否保持连通。
            </p>
          </header>

          <div class="mb-3 flex items-center px-1">
            <span class="text-caption font-emphasis text-label-secondary">当前状态</span>
            <Tooltip content="刷新状态">
              <IconButton
                label="刷新中继状态"
                size="sm"
                class="ml-auto"
                :disabled="relayLoading || relayRefreshing || !relayLoaded || relayDirty"
                @click="refreshRelayStatus"
              >
                <Icon :as="RefreshCw" size="inline" />
              </IconButton>
            </Tooltip>
          </div>

          <div class="overflow-hidden rounded-panel bg-content shadow-panel">
            <div class="flex items-start gap-3 px-4 py-3">
              <StatusDot :dot="relayPresentation.dot" class="mt-0.5">
                <span :class="['font-emphasis', relayPresentation.text]">
                  {{ relayPresentation.title }}
                </span>
              </StatusDot>
              <div class="ml-auto min-w-0 text-right">
                <div class="text-caption text-label-secondary">{{ relayPresentation.detail }}</div>
                <div class="mt-0.5 text-caption text-label-tertiary">
                  {{ relayStatus?.message || '等待状态' }}
                </div>
              </div>
            </div>
            <div class="h-px bg-separator-soft" />
            <div
              class="grid grid-cols-3 divide-x divide-separator-soft max-sm:grid-cols-1 max-sm:divide-x-0 max-sm:divide-y"
            >
              <div class="px-4 py-3">
                <div class="text-caption text-label-secondary">本地连接器</div>
                <div class="mt-1 text-body font-emphasis text-label">{{ connectorText }}</div>
              </div>
              <div class="px-4 py-3">
                <div class="text-caption text-label-secondary">请求队列</div>
                <div class="tnum mt-1 text-body font-emphasis text-label">
                  {{ relayStatus?.queueDepth ?? '—' }}
                </div>
              </div>
              <div class="px-4 py-3">
                <div class="text-caption text-label-secondary">上次检查</div>
                <div class="mt-1 text-body font-emphasis text-label">{{ checkedAtText }}</div>
              </div>
            </div>
          </div>

          <div class="mt-5 overflow-hidden rounded-panel bg-content shadow-panel">
            <div class="flex items-center gap-6 px-4 py-3">
              <div class="min-w-0">
                <div class="text-body font-emphasis text-label">监听状态</div>
                <div class="mt-0.5 text-caption text-label-secondary">
                  设置页可见时每 8 秒检查一次。
                </div>
              </div>
              <Switch
                v-model="relayForm.monitoringEnabled"
                class="ml-auto"
                :disabled="relayLoading || relaySaving || relayInstalling"
              />
            </div>
            <div class="h-px bg-separator-soft" />
            <div
              class="grid grid-cols-[180px_minmax(0,1fr)] items-start gap-6 px-4 py-3 max-sm:grid-cols-1 max-sm:gap-2"
            >
              <div>
                <div class="text-body font-emphasis text-label">服务器地址</div>
                <div class="mt-0.5 text-caption text-label-secondary">
                  站点根地址，支持 HTTP 与 HTTPS
                </div>
              </div>
              <div class="min-w-0">
                <Input
                  v-model="relayForm.serverUrl"
                  size="lg"
                  placeholder="https://regent.example.com"
                  :disabled="relayLoading || relaySaving || relayInstalling"
                  :invalid="!relayUrlValid"
                />
                <p class="mt-1.5 text-caption text-label-tertiary">
                  连接器共享密钥仍只保存在本机部署配置中，不会发送到浏览器或数据库。
                </p>
                <p
                  v-if="relayUrlState.pagePath"
                  class="mt-1.5 text-caption text-warning"
                >
                  检测到页面地址 {{ relayUrlState.pagePath }}，保存时将使用站点根地址
                  {{ relayUrlState.normalized }}。
                </p>
                <p v-else-if="!relayUrlValid" class="mt-1.5 text-caption text-danger">
                  请填写不含路径、参数和登录信息的 HTTP(S) 根地址。
                </p>
              </div>
            </div>
            <div class="h-px bg-separator-soft" />
            <div
              class="grid grid-cols-[180px_minmax(0,1fr)] items-start gap-6 px-4 py-3 max-sm:grid-cols-1 max-sm:gap-2"
            >
              <div>
                <div class="text-body font-emphasis text-label">服务器 IP</div>
                <div class="mt-0.5 text-caption text-label-secondary">安装前必填</div>
              </div>
              <div class="min-w-0">
                <Input
                  v-model="relayForm.serverIp"
                  size="lg"
                  placeholder="203.0.113.10"
                  :disabled="relayLoading || relaySaving || relayInstalling"
                  :invalid="relayForm.serverIp.length > 0 && !relayServerIpValid"
                />
                <p
                  v-if="relayForm.serverIp.length > 0 && !relayServerIpValid"
                  class="mt-1.5 text-caption text-danger"
                >
                  请填写不含空格和路径的服务器 IP 或主机名。
                </p>
                <p
                  v-else-if="relayForm.serverIp.length === 0"
                  class="mt-1.5 text-caption text-label-tertiary"
                >
                  填写公网服务器地址后即可自动安装。
                </p>
              </div>
            </div>
            <div class="h-px bg-separator-soft" />
            <div
              class="grid grid-cols-[180px_minmax(0,1fr)] items-start gap-6 px-4 py-3 max-sm:grid-cols-1 max-sm:gap-2"
            >
              <div>
                <div class="text-body font-emphasis text-label">SSH 用户名</div>
                <div class="mt-0.5 text-caption text-label-secondary">需具备服务器写入权限</div>
              </div>
              <Input
                v-model="relayForm.sshUsername"
                size="lg"
                placeholder="root"
                :disabled="relayLoading || relaySaving || relayInstalling"
                :invalid="!relayUsernameValid"
                class="max-w-64"
              />
            </div>
            <div class="h-px bg-separator-soft" />
            <div
              class="grid grid-cols-[180px_minmax(0,1fr)] items-start gap-6 px-4 py-3 max-sm:grid-cols-1 max-sm:gap-2"
            >
              <div>
                <div class="text-body font-emphasis text-label">SSH 密码</div>
                <div class="mt-0.5 text-caption text-label-secondary">只在本次安装中使用</div>
              </div>
              <div class="min-w-0">
                <Input
                  v-model="relayForm.password"
                  type="password"
                  size="lg"
                  placeholder="留空则使用本机 SSH 密钥"
                  :disabled="relayLoading || relaySaving || relayInstalling"
                  autocomplete="current-password"
                />
                <p class="mt-1.5 text-caption text-label-tertiary">
                  密码不会保存到数据库，也不会在安装完成后保留在页面中。
                </p>
              </div>
            </div>
            <div class="h-px bg-separator-soft" />
            <div
              class="grid grid-cols-[180px_minmax(0,1fr)] items-start gap-6 px-4 py-3 max-sm:grid-cols-1 max-sm:gap-2"
            >
              <div>
                <div class="text-body font-emphasis text-label">安装目录</div>
                <div class="mt-0.5 text-caption text-label-secondary">服务器上的绝对路径</div>
              </div>
              <div class="min-w-0">
                <Input
                  v-model="relayForm.installDirectory"
                  size="lg"
                  placeholder="/root/app/regent-relay"
                  :disabled="relayLoading || relaySaving || relayInstalling"
                  :invalid="!relayDirectoryValid"
                  class="font-mono"
                />
                <p
                  v-if="!relayDirectoryValid"
                  class="mt-1.5 text-caption text-danger"
                >
                  请输入不以 / 结尾的安全绝对路径。
                </p>
                <p v-else class="mt-1.5 text-caption text-label-tertiary">
                  中继程序、配置、脚本和日志都安装在该目录下。
                </p>
              </div>
            </div>
          </div>

          <div
            v-if="relayInstallResult"
            class="mt-3 rounded-panel bg-content px-4 py-3 shadow-panel"
          >
            <StatusDot :dot="relayInstallResult.success ? 'bg-success' : 'bg-danger'">
              <span class="font-emphasis text-label">
                {{ relayInstallResult.success ? '安装完成' : '安装失败' }}
              </span>
            </StatusDot>
            <div class="mt-2 break-all font-mono text-caption text-label-secondary">
              {{ relayInstallResult.installDirectory }}
            </div>
            <pre
              v-if="!relayInstallResult.success"
              class="mt-2 max-h-48 overflow-auto whitespace-pre-wrap break-all rounded-control bg-field p-2 font-mono text-caption text-label-secondary"
            >{{ relayInstallResult.message }}</pre>
          </div>

          <p v-if="relayError" class="mt-3 text-caption text-danger">{{ relayError }}</p>
          <div class="mt-5 flex items-center justify-end gap-2">
            <span v-if="relayDirty" class="mr-auto text-caption text-label-secondary">有未保存的更改</span>
            <Button
              variant="default"
              size="lg"
              :disabled="!relayCanInstall"
              @click="installRelay"
            >
              <Icon :as="Download" size="inline" class="mr-1.5" />
              {{ relayInstalling ? '安装中…' : '安装中继器' }}
            </Button>
            <Button
              variant="primary"
              size="lg"
              :disabled="!relayCanSave"
              @click="saveRelaySettings"
            >
              {{ relaySaving ? '保存中…' : '保存并检测' }}
            </Button>
          </div>
        </template>

        <template v-else>
          <header class="mb-6">
            <h1 class="text-metric font-emphasis text-label">账户</h1>
            <p class="mt-1 text-body text-label-secondary">查看当前登录账户并管理会话。</p>
          </header>

          <div class="overflow-hidden rounded-panel bg-content shadow-panel">
            <div class="flex items-center gap-3 px-4 py-3">
              <Avatar
                :src="auth.user?.avatarUrl ?? ''"
                :name="auth.user?.displayName || auth.user?.username || 'U'"
                size="profile"
              />
              <div class="min-w-0">
                <div class="truncate text-body font-emphasis text-label">
                  {{ auth.user?.displayName || auth.user?.username }}
                </div>
                <div class="mt-0.5 truncate text-caption text-label-secondary">
                  {{ auth.user?.email ?? (auth.user ? `@${auth.user.username}` : '') }}
                </div>
              </div>
            </div>
            <div class="h-px bg-separator-soft" />
            <div
              class="flex items-center gap-6 px-4 py-3 max-sm:flex-col max-sm:items-start max-sm:gap-3"
            >
              <div class="min-w-0">
                <div class="text-body font-emphasis text-label">退出登录</div>
                <div class="mt-0.5 text-caption text-label-secondary">
                  退出当前账户，再次访问时需要重新登录。
                </div>
              </div>
              <Button
                size="lg"
                class="ml-auto max-sm:ml-0"
                :disabled="loggingOut"
                @click="logout"
              >
                <Icon :as="LogOut" size="inline" />
                {{ loggingOut ? '正在退出…' : '退出登录' }}
              </Button>
            </div>
          </div>
        </template>
      </div>
    </section>
  </div>
</template>
