import { computed, onMounted, onUnmounted, ref } from 'vue'

import { getLatestRelease } from '@/api/release'
import { CURRENT_RELEASE } from '@/content/releases'
import type { ReleaseManifest } from '@/types/release'

const CHECK_INTERVAL = 30 * 60 * 1000

/**
 * 浏览器版更新检查。
 *
 * <p>服务端发布新的 latest.json 后，前端 JS 仍是旧版本；发现版本号变化时只提示刷新，
 * 不自动 reload，避免打断正在编辑的任务。桌面壳的更新由 Tauri updater 单独负责。
 */
export function useWebUpdate() {
  const latest = ref<ReleaseManifest | null>(null)
  const checking = ref(false)
  const dismissed = ref(false)

  const hasUpdate = computed(() => {
    const version = latest.value?.version
    return Boolean(version && version !== CURRENT_RELEASE.version && !dismissed.value)
  })

  const check = async () => {
    if (checking.value) return
    checking.value = true
    try {
      latest.value = await getLatestRelease()
    } catch {
      // 更新检查失败不打扰用户；下一次切回页面或定时器会重试。
    } finally {
      checking.value = false
    }
  }

  const dismiss = () => {
    dismissed.value = true
  }

  let timer: number | undefined
  const onFocus = () => void check()

  onMounted(() => {
    void check()
    timer = window.setInterval(() => void check(), CHECK_INTERVAL)
    window.addEventListener('focus', onFocus)
  })
  onUnmounted(() => {
    window.clearInterval(timer)
    window.removeEventListener('focus', onFocus)
  })

  return { latest, checking, hasUpdate, check, dismiss }
}
