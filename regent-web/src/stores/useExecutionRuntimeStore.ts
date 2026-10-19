import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import { ApiError } from '@/api/http'
import { checkCodexRuntime, installCodex } from '@/api/settings'
import type { CodexInstallMethod, CodexInstallResult, CodexRuntime } from '@/types/api'

function messageOf(error: unknown): string {
  if (error instanceof ApiError) return error.message
  return '无法检测 Codex，请确认后端服务已启动'
}

/**
 * 登录后的执行器就绪状态。
 *
 * <p>设置页要展示它，全站提示也要读它；各自检测会重复启动 `codex --version`，
 * 也会出现「设置页已恢复、顶部仍然报错」的分叉。状态放在 store 里，强制刷新由用户显式触发。
 */
export const useExecutionRuntimeStore = defineStore('executionRuntime', () => {
  const status = ref<CodexRuntime | null>(null)
  const loading = ref(false)
  const installing = ref(false)
  const error = ref<string | null>(null)
  let monitorTimer: ReturnType<typeof setInterval> | null = null

  const available = computed(() => status.value?.available ?? null)
  const unavailable = computed(() => status.value !== null && !status.value.available)

  const check = async (force = false): Promise<void> => {
    if (loading.value) return
    if (!force && status.value) return
    loading.value = true
    error.value = null
    try {
      status.value = await checkCodexRuntime()
    } catch (e) {
      error.value = messageOf(e)
    } finally {
      loading.value = false
    }
  }

  const clear = () => {
    stopMonitoring()
    status.value = null
    loading.value = false
    installing.value = false
    error.value = null
  }

  const install = async (method: CodexInstallMethod): Promise<CodexInstallResult> => {
    if (installing.value) {
      throw new Error('已有安装任务正在进行')
    }
    installing.value = true
    error.value = null
    try {
      const result = await installCodex(method)
      status.value = result.runtime
      return result
    } finally {
      installing.value = false
    }
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
