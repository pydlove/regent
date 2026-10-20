import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import { createDeviceSession } from '@/api/auth'
import {
  getDeviceAgentStatus,
  pairDeviceAgent,
  type DeviceAgentStatus,
} from '@/api/deviceAgent'
import { ApiError } from '@/api/http'
import type { CodexInstallMethod, CodexInstallResult, CodexRuntime } from '@/types/api'

function messageOf(error: unknown): string {
  if (error instanceof ApiError) return error.message
  if (error instanceof Error) return error.message
  return '当前设备未连接 Regent 设备代理'
}

/**
 * 登录后的执行器就绪状态。
 *
 * <p>设置页要展示它，全站提示也要读它；各自检测会重复启动 `codex --version`，
 * 也会出现「设置页已恢复、顶部仍然报错」的分叉。状态放在 store 里，强制刷新由用户显式触发。
 */
export const useExecutionRuntimeStore = defineStore('executionRuntime', () => {
  const status = ref<CodexRuntime | null>(null)
  const device = ref<DeviceAgentStatus | null>(null)
  const loading = ref(false)
  const installing = ref(false)
  const error = ref<string | null>(null)
  let monitorTimer: ReturnType<typeof setInterval> | null = null

  const available = computed(() => status.value?.available ?? null)
  const unavailable = computed(
    () =>
      status.value !== null
      && !status.value.available
      && device.value?.paired === true,
  )

  const check = async (force = false): Promise<void> => {
    if (loading.value) return
    if (!force && status.value) return
    loading.value = true
    error.value = null
    try {
      let current = await getDeviceAgentStatus()
      if (!current.paired && !window.regentDesktop) {
        const grant = await createDeviceSession()
        current = await pairDeviceAgent(window.location.origin, grant.token)
      }
      device.value = current
      status.value = {
        available: current.paired && current.codex.available,
        configuredCommand: 'codex',
        executablePath: current.codex.executablePath,
        version: current.codex.version,
        message: current.paired
          ? current.codex.message
          : '当前设备代理尚未连接到 Regent 服务器，请先登录桌面客户端',
        checkedAt: current.codex.checkedAt,
      }
    } catch (e) {
      device.value = null
      status.value = null
      error.value = messageOf(e)
    } finally {
      loading.value = false
    }
  }

  const clear = () => {
    stopMonitoring()
    status.value = null
    device.value = null
    loading.value = false
    installing.value = false
    error.value = null
  }

  const install = async (method: CodexInstallMethod): Promise<CodexInstallResult> => {
    throw new Error(
      `请在当前设备终端执行 ${
        method === 'npm' ? 'npm install -g @openai/codex' : 'brew install codex'
      }，安装完成后回到设置页重新检测。`,
    )
  }

  const onVisibilityChange = () => {
    if (document.visibilityState === 'visible') {
      void check(true)
    }
  }

  const startMonitoring = (intervalMs = 30_000) => {
    if (monitorTimer !== null) return
    void check()
    monitorTimer = setInterval(() => {
      if (document.visibilityState !== 'hidden') {
        void check(true)
      }
    }, intervalMs)
    document.addEventListener('visibilitychange', onVisibilityChange)
  }

  const stopMonitoring = () => {
    if (monitorTimer !== null) {
      clearInterval(monitorTimer)
      monitorTimer = null
    }
    document.removeEventListener('visibilitychange', onVisibilityChange)
  }

  return {
    status,
    device,
    loading,
    installing,
    error,
    available,
    unavailable,
    check,
    install,
    startMonitoring,
    stopMonitoring,
    clear,
  }
})
