<script setup lang="ts">
import {
  Apple,
  ArrowLeft,
  ArrowRight,
  Clock3,
  Download,
  KeyRound,
  Monitor,
  ScanFace,
  ShieldCheck,
  Smartphone,
  Sparkles,
} from '@lucide/vue'
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'

import { getLatestRelease } from '@/api/release'
import { buttonVariants } from '@/components/ui/button/button.variants'
import Icon from '@/components/ui/icon/Icon.vue'
import SegmentedControl from '@/components/ui/segmented-control/SegmentedControl.vue'
import { useTheme } from '@/composables/useTheme'
import { useAuthStore } from '@/stores/useAuthStore'
import type { ReleaseManifest } from '@/types/release'
import { appPath } from '@/utils/appBase'
import {
  detectDesktopOperatingSystem,
  detectMobileDownloadPlatform,
  type MobileDownloadPlatform,
} from '@/utils/mobileDownloadPlatform'

const auth = useAuthStore()
const { resolved, setTheme } = useTheme()
const themeModel = computed({
  get: () => resolved.value,
  set: (value: string) => setTheme(value as 'light' | 'dark'),
})
const themeOptions = [
  { value: 'light', label: '浅色' },
  { value: 'dark', label: '深色' },
]

const latestRelease = ref<ReleaseManifest | null>(null)
const loadingRelease = ref(true)
const currentPlatform = detectMobileDownloadPlatform(
  navigator.userAgent,
  navigator.maxTouchPoints,
)
const desktopOperatingSystem = detectDesktopOperatingSystem(
  navigator.userAgent,
  navigator.maxTouchPoints,
)

const backTo = computed(() => (auth.isAuthenticated ? '/dashboard' : '/login'))
const backLabel = computed(() => (auth.isAuthenticated ? '返回工作台' : '返回登录'))
const macUsesWeb = computed(() => desktopOperatingSystem === 'macos')
const webEntryLabel = computed(() => (auth.isAuthenticated ? '进入 Web 工作台' : '进入 Web 版'))
const androidDebugPackage = computed(() =>
  Boolean(latestRelease.value?.downloads.android?.filename?.endsWith('debug.apk')))

const downloads = computed(() => {
  const android = latestRelease.value?.downloads.android
  const ios = latestRelease.value?.downloads.ios
  const items = [
    {
      id: 'ios' as const,
      name: 'iPhone / iPad',
      icon: Apple,
      subtitle: 'iOS 15 及以上',
      requirements: 'iOS 15+，通过 TestFlight 或 App Store 安装',
      format: formatLabel(ios?.filename, loadingRelease.value, 'TestFlight 内测邀请'),
      url: publicAssetUrl(
        ios?.url || import.meta.env.VITE_DOWNLOAD_IOS_URL,
      ),
      tone: 'bg-label text-content',
    },
    {
      id: 'android' as const,
      name: 'Android',
      icon: Smartphone,
      subtitle: 'Android 9 及以上',
      requirements: 'Android 9+，下载 APK 或通过应用商店安装',
      format: formatLabel(android?.filename, loadingRelease.value, '签名 APK'),
      url: publicAssetUrl(
        android?.url || import.meta.env.VITE_DOWNLOAD_ANDROID_URL,
      ),
      tone: 'bg-success-soft text-success',
    },
  ]
  return items.sort((left, right) => {
    if (left.id === currentPlatform) return -1
    if (right.id === currentPlatform) return 1
    return 0
  })
})

const features = [
  {
    icon: Smartphone,
    title: '电脑不在身边',
    description: '随时查看 AI 任务进度与电脑在线状态。',
  },
  {
    icon: ShieldCheck,
    title: '账号不串号',
    description: '账号、手机安装、执行电脑和任务多层隔离。',
  },
  {
    icon: ScanFace,
    title: '本机安全解锁',
    description: '面容 ID、指纹或系统密码保护移动端会话。',
  },
]

function publicAssetUrl(url: string | undefined): string | null {
  if (!url) return null
  if (/^https?:\/\//i.test(url)) return url
  return appPath(url)
}

function formatLabel(
  filename: string | undefined,
  loading: boolean,
  fallback: string,
): string {
  if (filename) return filename
  return loading ? '正在读取发布信息…' : fallback
}

function buttonLabel(id: MobileDownloadPlatform): string {
  if (id === 'ios') return '下载 iOS 版'
  if (id === 'android') return '下载 Android 版'
  return '下载'
}

onMounted(async () => {
  // 从工作台内部路由跳转时，窗口可能保留上一个页面滚动位置。
  window.scrollTo({ left: 0, top: 0 })
  try {
    latestRelease.value = await getLatestRelease()
  } catch {
    // 发布清单不可用时保留环境变量入口，都没有则明确显示未发布。
  } finally {
    loadingRelease.value = false
  }
})
</script>

<template>
  <div class="mobile-download-stage min-h-screen overflow-hidden px-4 py-8 sm:px-6 lg:px-8">
    <div class="mx-auto w-full max-w-[980px]">
      <header class="flex items-center justify-between gap-4">
        <RouterLink class="flex min-w-0 items-center gap-2.5" :to="{ name: 'mobile-download' }">
          <span
            aria-hidden="true"
            class="relative grid size-8 place-items-center rounded-[8px] bg-label text-content shadow-control"
          >
            <span class="translate-y-px text-[15px] font-emphasis leading-none">R</span>
            <span class="absolute bottom-1 right-1 size-1.5 rounded-full bg-accent ring-2 ring-label" />
          </span>
          <span class="hidden flex-col sm:flex">
            <span class="text-body font-emphasis leading-none text-label">Regent AI</span>
            <span class="mt-1 text-[10px] leading-none text-label-secondary">手机 App</span>
          </span>
        </RouterLink>

        <div class="flex items-center gap-3">
          <div class="hidden sm:block">
            <SegmentedControl v-model="themeModel" :items="themeOptions" size="sm" />
          </div>
          <RouterLink
            :to="backTo"
            class="inline-flex min-h-control items-center gap-1 text-caption text-label-secondary transition-colors duration-100 ease-apple hover:text-label"
          >
            <Icon :as="ArrowLeft" size="inline" />{{ backLabel }}
          </RouterLink>
        </div>
      </header>

      <main class="mt-8 overflow-hidden rounded-card border border-separator bg-content shadow-window">
        <div class="p-6 sm:p-10 lg:p-12">
          <div class="max-w-2xl">
            <div class="flex flex-wrap items-center gap-2">
              <span class="inline-flex items-center gap-1 rounded-badge bg-accent-soft px-1.5 py-px text-caption font-emphasis text-accent">
                手机 App
              </span>
              <span class="inline-flex items-center gap-1 rounded-badge bg-success-soft px-1.5 py-px text-caption font-emphasis text-success">
                P0 内测
              </span>
              <span class="tnum text-caption text-label-tertiary">V1.0.5</span>
            </div>
            <h1 class="mt-4 text-[32px] font-emphasis leading-[1.15] tracking-[-0.03em] text-label sm:text-[38px]">
              电脑不在身边，AI 任务随时随地开工
            </h1>
            <p class="mt-3 max-w-xl text-body leading-[1.7] text-label-secondary">
              选择你的手机系统安装 Regent。任务仍在已授权的执行电脑上运行，
              手机负责远程下发、监控和关键决策。
            </p>
          </div>

          <section
            v-if="macUsesWeb"
            class="mt-6 flex flex-col gap-3 rounded-panel border border-accent/25 bg-accent-soft/70 p-4 sm:flex-row sm:items-center"
          >
            <span class="grid size-10 shrink-0 place-items-center rounded-panel bg-content text-accent shadow-control">
              <Icon :as="Monitor" size="large" />
            </span>
            <div class="min-w-0 flex-1">
              <p class="text-body font-emphasis text-label">当前是 Mac，直接使用 Web 版即可</p>
              <p class="mt-1 text-caption leading-relaxed text-label-secondary">
                Mac 不需要安装手机 App。网页版与移动端使用同一账号、任务和决策数据。
              </p>
            </div>
            <RouterLink
              :to="backTo"
              :class="buttonVariants({ variant: 'primary', size: 'lg' })"
              class="w-full shrink-0 sm:w-auto"
            >
              {{ webEntryLabel }}
              <Icon :as="ArrowRight" size="inline" />
            </RouterLink>
          </section>

          <div class="mt-8 grid gap-4 md:grid-cols-2">
            <article
              v-for="platform in downloads"
              :key="platform.id"
              :class="[
                'flex min-w-0 flex-col rounded-panel border bg-window p-5 shadow-control',
                platform.id === currentPlatform
                  ? 'border-accent/35 ring-1 ring-accent/10'
                  : 'border-separator-soft',
              ]"
            >
              <div class="flex items-start gap-3">
                <span :class="['grid size-11 shrink-0 place-items-center rounded-panel', platform.tone]">
                  <Icon :as="platform.icon" size="xlarge" />
                </span>
                <div class="min-w-0">
                  <h2 class="text-title font-emphasis text-label">{{ platform.name }}</h2>
                  <p class="mt-1 text-caption text-label-secondary">{{ platform.subtitle }}</p>
                </div>
                <span
                  v-if="platform.id === currentPlatform"
                  class="ml-auto shrink-0 rounded-badge bg-accent-soft px-1.5 py-px text-caption font-emphasis text-accent"
                >
                  当前设备
                </span>
              </div>

              <dl class="mt-6 space-y-2 border-t border-separator pt-4">
                <div class="flex items-start gap-3">
                  <dt class="w-[72px] shrink-0 text-caption text-label-tertiary">系统要求</dt>
                  <dd class="min-w-0 text-caption leading-relaxed text-label">{{ platform.requirements }}</dd>
                </div>
                <div class="flex items-center gap-3">
                  <dt class="w-[72px] shrink-0 text-caption text-label-tertiary">安装包</dt>
                  <dd class="min-w-0 truncate text-caption text-label" :title="platform.format">
                    {{ platform.format }}
                  </dd>
                </div>
                <div class="flex items-center gap-3">
                  <dt class="w-[72px] shrink-0 text-caption text-label-tertiary">版本</dt>
                  <dd class="tnum text-caption text-label">V1.0.5 · P0</dd>
                </div>
              </dl>

              <a
                v-if="platform.url"
                :href="platform.url"
                target="_blank"
                rel="noreferrer"
                :class="buttonVariants({ variant: 'primary', size: 'lg' })"
                class="mt-6 w-full"
              >
                <Icon :as="Download" size="inline" />
                {{ buttonLabel(platform.id) }}
                <Icon :as="ArrowRight" size="inline" class="ml-auto opacity-70" />
              </a>
              <button
                v-else
                type="button"
                disabled
                :class="buttonVariants({ variant: 'primary', size: 'lg' })"
                class="mt-6 w-full"
              >
                <Icon :as="Clock3" size="inline" />
                {{ loadingRelease ? '正在读取发布信息…' : '内测包暂未发布' }}
              </button>
            </article>
          </div>

          <div class="mt-8 grid gap-3 border-t border-separator pt-6 sm:grid-cols-3">
            <div v-for="feature in features" :key="feature.title" class="flex items-start gap-2.5">
              <span class="mt-0.5 grid size-6 shrink-0 place-items-center rounded-[7px] bg-accent-soft text-accent">
                <Icon :as="feature.icon" size="inline" />
              </span>
              <div>
                <p class="text-caption font-emphasis text-label">{{ feature.title }}</p>
                <p class="mt-0.5 text-caption leading-relaxed text-label-tertiary">
                  {{ feature.description }}
                </p>
              </div>
            </div>
          </div>

          <div class="mt-6 grid gap-4 rounded-panel border border-separator-soft bg-window p-4 sm:grid-cols-2">
            <div>
              <p class="flex items-center gap-2 text-caption font-emphasis text-label">
                <Icon :as="Smartphone" size="inline" class="text-success" />Android 安装
              </p>
              <ol class="mt-2 list-decimal space-y-1 pl-5 text-caption leading-relaxed text-label-secondary">
                <li>下载官方签名 APK。</li>
                <li>允许浏览器或文件管理器安装未知应用。</li>
                <li>打开 APK 完成安装，再使用 Regent 账号登录。</li>
              </ol>
            </div>
            <div>
              <p class="flex items-center gap-2 text-caption font-emphasis text-label">
                <Icon :as="Apple" size="inline" class="text-accent" />iPhone 安装
              </p>
              <ol class="mt-2 list-decimal space-y-1 pl-5 text-caption leading-relaxed text-label-secondary">
                <li>先安装 Apple TestFlight。</li>
                <li>打开本页 iOS 内测邀请并接受测试。</li>
                <li>在 TestFlight 中安装 Regent，正式版通过 App Store 更新。</li>
              </ol>
            </div>
          </div>

          <div class="mt-4 flex flex-wrap items-start gap-2 rounded-panel bg-info-surface px-3 py-2.5">
            <Icon :as="ShieldCheck" size="inline" class="mt-0.5 text-accent" />
            <p class="min-w-0 flex-1 text-caption leading-relaxed text-label-secondary">
              只从当前 Regent 官方域名进入下载页。Android 安装前请核对签名来源；
              iOS 不提供企业证书直装，也不要求关闭系统安全保护。
            </p>
            <span class="inline-flex items-center gap-1 text-caption text-label-tertiary">
              <Icon :as="KeyRound" size="inline" />
              令牌只保存到系统安全存储
            </span>
          </div>

          <p
            v-if="androidDebugPackage"
            class="mt-3 rounded-panel border border-warning/25 bg-warning/10 px-3 py-2 text-caption leading-relaxed text-warning"
          >
            当前 Android 安装包使用内测调试签名，仅用于 P0 验证；正式分发前会替换为独立发布签名。
          </p>

          <details class="mt-4 rounded-panel border border-separator-soft bg-window px-4 py-3">
            <summary class="cursor-pointer text-caption font-emphasis text-label">内测说明</summary>
            <div class="mt-3 space-y-2 text-caption leading-relaxed text-label-secondary">
              <p>
                当前下载页随 V1.0.5 P0 一起提供。发布 Android APK 或 iOS TestFlight 链接后，
                按钮会自动变为可下载状态。
              </p>
              <p>
                手机 App 必须连接该 Regent 服务可访问的 HTTPS 地址；账号、项目和任务数据不会复制到手机端。
              </p>
            </div>
          </details>
        </div>
      </main>

      <footer class="mt-6 flex flex-wrap items-center justify-between gap-3 px-1 text-caption text-label-tertiary">
        <RouterLink class="inline-flex items-center gap-1 transition-colors hover:text-label" :to="backTo">
          <Icon :as="ArrowLeft" size="inline" />{{ backLabel }}
        </RouterLink>
        <RouterLink class="inline-flex items-center gap-1 transition-colors hover:text-label" :to="{ name: 'download' }">
          桌面客户端<Icon :as="ArrowRight" size="inline" />
        </RouterLink>
        <span class="inline-flex items-center gap-1">
          <Icon :as="Sparkles" size="inline" />Regent Mobile · V1.0.5 · P0
        </span>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.mobile-download-stage {
  background:
    radial-gradient(
      ellipse 64% 52% at 8% 0%,
      rgba(0, 122, 255, 0.1),
      transparent 72%
    ),
    radial-gradient(
      ellipse 52% 44% at 96% 100%,
      rgba(52, 199, 89, 0.06),
      transparent 74%
    ),
    var(--window-bg);
}
</style>
