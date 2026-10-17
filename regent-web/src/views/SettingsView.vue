<script setup lang="ts">
import { Gauge, Palette, RadioTower, RefreshCw } from '@lucide/vue'
import type { Component } from 'vue'
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ApiError } from '@/api/http'
import {
  getExecutionSettings,
  getRelaySettings,
  updateExecutionSettings,
  updateRelaySettings,
} from '@/api/settings'
import Button from '@/components/ui/button/Button.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import IconButton from '@/components/ui/icon-button/IconButton.vue'
import Input from '@/components/ui/input/Input.vue'
import SegmentedControl from '@/components/ui/segmented-control/SegmentedControl.vue'
import StatusDot from '@/components/ui/status-dot/StatusDot.vue'
import Switch from '@/components/ui/switch/Switch.vue'
import Tooltip from '@/components/ui/tooltip/Tooltip.vue'
import { useTheme, type ThemePreference } from '@/composables/useTheme'
import { useToast } from '@/composables/useToast'
import type { RelaySettings, RelayState, RelayStatus } from '@/types/api'

type SettingsSection = 'appearance' | 'execution' | 'relay'
type SectionItem = {
  id: SettingsSection
  label: string
  icon: Component
  tone: string
}

const sections: SectionItem[] = [
  { id: 'appearance', label: '外观', icon: Palette, tone: 'bg-accent' },
  { id: 'execution', label: '执行', icon: Gauge, tone: 'bg-warning' },
  { id: 'relay', label: '中继', icon: RadioTower, tone: 'bg-success' },
]

const route = useRoute()
const router = useRouter()
const toast = useToast()
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

const executionLimit = ref('1')
const savedExecutionLimit = ref('1')
const runningTaskCount = ref(0)
const executionLoading = ref(false)
const executionSaving = ref(false)
const executionLoaded = ref(false)
const executionError = ref<string | null>(null)

const numericLimit = computed(() => Number(executionLimit.value))
const executionValid = computed(
  () => Number.isInteger(numericLimit.value) && numericLimit.value >= 1 && numericLimit.value <= 64,
)
const executionDirty = computed(
  () => executionLoaded.value && executionLimit.value !== savedExecutionLimit.value,
)

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

const relayForm = reactive({
  serverUrl: '',
  monitoringEnabled: false,
})
const savedRelayForm = reactive({
  serverUrl: '',
  monitoringEnabled: false,
})
const relayStatus = ref<RelayStatus | null>(null)
const relayLoading = ref(false)
const relaySaving = ref(false)
const relayRefreshing = ref(false)
const relayLoaded = ref(false)
const relayError = ref<string | null>(null)

const relayPagePaths = new Set([
  '/baseline',
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
const relayDirty = computed(
  () =>
    relayLoaded.value &&
    (relayForm.serverUrl.trim() !== savedRelayForm.serverUrl ||
      relayForm.monitoringEnabled !== savedRelayForm.monitoringEnabled),
)
const relayCanSave = computed(
  () => relayLoaded.value && relayUrlValid.value && relayDirty.value && !relaySaving.value,
)

function applyRelaySettings(settings: RelaySettings) {
  relayForm.serverUrl = settings.serverUrl
  relayForm.monitoringEnabled = settings.monitoringEnabled
  savedRelayForm.serverUrl = settings.serverUrl
  savedRelayForm.monitoringEnabled = settings.monitoringEnabled
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
      <div class="mx-auto w-full max-w-[720px] px-8 py-7 max-sm:px-5 max-sm:py-6">
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
              控制平台同时运行的任务数量，新设置立即影响任务领取。
            </p>
          </header>

          <div class="overflow-hidden rounded-panel bg-content shadow-panel">
            <div
              class="grid grid-cols-[180px_minmax(0,1fr)] items-center gap-6 px-4 py-3 max-sm:grid-cols-1 max-sm:gap-2"
            >
              <div>
                <div class="text-body font-emphasis text-label">并发上限</div>
                <div class="mt-0.5 text-caption text-label-secondary">范围 1–64</div>
              </div>
              <div class="flex items-center gap-2">
                <Input
                  v-model="executionLimit"
                  type="number"
                  size="lg"
                  placeholder="1"
                  :disabled="executionLoading || executionSaving"
                  :invalid="!executionValid"
                  class="w-24"
                />
                <span class="text-caption text-label-secondary">个任务</span>
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

        <template v-else>
          <header class="mb-6">
            <h1 class="text-metric font-emphasis text-label">中继</h1>
            <p class="mt-1 text-body text-label-secondary">
              配置公网中继服务器，并监听服务器与本地连接器是否保持连通。
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
              <Switch v-model="relayForm.monitoringEnabled" class="ml-auto" />
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
                  :disabled="relayLoading || relaySaving"
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
          </div>

          <p v-if="relayError" class="mt-3 text-caption text-danger">{{ relayError }}</p>
          <div class="mt-5 flex items-center justify-end gap-2">
            <span v-if="relayDirty" class="mr-auto text-caption text-label-secondary">有未保存的更改</span>
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
      </div>
    </section>
  </div>
</template>
