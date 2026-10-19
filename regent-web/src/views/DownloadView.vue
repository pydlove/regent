<script setup lang="ts">
import {
  Apple,
  ArrowLeft,
  ArrowRight,
  Check,
  Clock3,
  Download,
  MonitorDown,
  ShieldCheck,
  Sparkles,
} from '@lucide/vue'
import { computed, onMounted, ref } from 'vue'

import { getLatestRelease } from '@/api/release'
import { buttonVariants } from '@/components/ui/button/button.variants'
import Icon from '@/components/ui/icon/Icon.vue'
import SegmentedControl from '@/components/ui/segmented-control/SegmentedControl.vue'
import { CURRENT_RELEASE } from '@/content/releases'
import { useTheme } from '@/composables/useTheme'
import { useAuthStore } from '@/stores/useAuthStore'
import type { ReleaseManifest } from '@/types/release'
import {
  isWindowsClient,
  preferredDownloadOrder,
  recommendedDownloadPlatform,
} from '@/utils/downloadPlatform'
import { RouterLink } from 'vue-router'

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

const backTo = computed(() => (auth.isAuthenticated ? '/tasks' : '/login'))
const backLabel = computed(() => (auth.isAuthenticated ? '返回工作台' : '返回登录'))
const helpTo = computed(() =>
  auth.isAuthenticated ? '/help' : { name: 'login', query: { redirect: '/help' } },
)

const latestRelease = ref<ReleaseManifest | null>(null)
const loadingRelease = ref(true)
const windowsClient = isWindowsClient(navigator.userAgent)
const recommendedPlatform = recommendedDownloadPlatform(windowsClient)
const downloadOrder = preferredDownloadOrder(windowsClient)
const displayVersion = computed(
  () => latestRelease.value?.version || CURRENT_RELEASE.version,
)
const displayDate = computed(
  () => latestRelease.value?.releasedAt?.slice(0, 10) || CURRENT_RELEASE.releasedAt,
)
const macosFilename = computed(
  () => latestRelease.value?.downloads.macos?.filename || 'Regent-macOS.dmg',
)
const windowsFilename = computed(
  () => latestRelease.value?.downloads.windows?.filename || 'Regent-Windows-x64.zip',
)
const downloadHeading = computed(() =>
  windowsClient
    ? '把 Regent 装到你的 Windows 电脑上'
    : '把 Regent 装到你的电脑上',
)

const downloads = computed(() => {
  const items = [
    {
      id: 'macos' as const,
      name: 'macOS',
      icon: Apple,
      subtitle: 'Apple Silicon 与 Intel',
      requirements: 'macOS 13 或更高版本',
      format: latestRelease.value?.downloads.macos?.filename
        ? `磁盘映像 · ${latestRelease.value.downloads.macos.filename}`
        : loadingRelease.value
          ? '正在读取发布信息…'
          : '安装包暂未发布',
      url: latestRelease.value?.downloads.macos?.url
        || import.meta.env.VITE_DOWNLOAD_MACOS_URL
        || null,
      tone: 'bg-label text-content',
      recommended: recommendedPlatform === 'macos',
    },
    {
      id: 'windows' as const,
      name: 'Windows',
      icon: MonitorDown,
      subtitle: 'Windows 10 / 11 · 64 位便携版',
      requirements: 'Windows 10 或 Windows 11',
      format: latestRelease.value?.downloads.windows?.filename
        ? `${latestRelease.value.downloads.windows.filename.endsWith('.zip') ? '便携版' : '安装程序'} · ${latestRelease.value.downloads.windows.filename}`
        : loadingRelease.value
          ? '正在读取发布信息…'
          : '安装包暂未发布',
      url: latestRelease.value?.downloads.windows?.url
        || import.meta.env.VITE_DOWNLOAD_WINDOWS_URL
        || null,
      tone: 'bg-accent-soft text-accent',
      recommended: recommendedPlatform === 'windows',
    },
  ]
  return [...items].sort(
    (left, right) => downloadOrder.indexOf(left.id) - downloadOrder.indexOf(right.id),
  )
})

onMounted(async () => {
  try {
    latestRelease.value = await getLatestRelease()
  } catch {
    // 发布清单不可用时保留版本信息，但下载按钮明确显示资源尚未发布。
  } finally {
    loadingRelease.value = false
  }
})

const features = computed(() => [
  {
    title: '独立窗口',
    description: windowsClient
      ? '解压后运行 Regent.exe，不需要安装浏览器插件。'
      : '安装或解压后直接打开工作台。',
  },
  { title: '跟随系统', description: '自动使用 macOS 或 Windows 的深浅色外观。' },
  { title: '同一套数据', description: '桌面端与浏览器版共用账号和任务数据。' },
])
</script>

<template>
  <div class="download-stage min-h-screen overflow-hidden px-4 py-8 sm:px-6 lg:px-8">
    <div class="mx-auto w-full max-w-[1040px]">
      <header class="flex items-center justify-between gap-4">
        <RouterLink class="flex min-w-0 items-center gap-2.5" to="/download">
          <span
            aria-hidden="true"
            class="relative grid size-8 place-items-center rounded-[8px] bg-label text-content shadow-control"
          >
            <span class="translate-y-px text-[15px] font-emphasis leading-none">R</span>
            <span class="absolute bottom-1 right-1 size-1.5 rounded-full bg-accent ring-2 ring-label" />
          </span>
          <span class="download-brand-copy hidden flex-col sm:flex">
            <span class="text-body font-emphasis leading-none text-label">Regent AI</span>
            <span class="mt-1 text-[10px] leading-none text-label-secondary">桌面客户端</span>
          </span>
        </RouterLink>

        <div class="flex items-center gap-3">
          <SegmentedControl v-model="themeModel" :items="themeOptions" size="sm" />
          <RouterLink
            :to="backTo"
            class="inline-flex items-center gap-1 text-caption text-label-secondary transition-colors duration-100 ease-apple hover:text-label"
          >
            <Icon :as="ArrowLeft" size="inline" />{{ backLabel }}
          </RouterLink>
        </div>
      </header>

      <main class="mt-8 overflow-hidden rounded-card border border-separator bg-content shadow-window">
        <div class="download-content p-6 sm:p-10 lg:p-12">
          <div class="max-w-2xl">
            <div class="flex items-center gap-2">
              <span class="inline-flex items-center gap-1 rounded-badge bg-accent-soft px-1.5 py-px text-caption font-emphasis text-accent">
                桌面客户端
              </span>
              <span class="tnum text-caption text-label-tertiary">{{ displayVersion }}</span>
            </div>
            <h1 class="download-title mt-4 text-[32px] font-emphasis leading-[1.15] tracking-[-0.03em] text-label sm:text-[38px]">
              {{ downloadHeading }}
            </h1>
            <p class="mt-3 max-w-xl text-body leading-[1.7] text-label-secondary">
              {{ windowsClient
                ? '下载 Windows 便携版，解压后运行 Regent.exe。桌面端和浏览器版使用同一个账号。'
                : '选择你的系统，下载安装或解压后打开工作台。桌面端和浏览器版使用同一个账号。' }}
            </p>
          </div>

          <div class="download-platform-grid mt-8 grid gap-4 md:grid-cols-2">
            <article
              v-for="platform in downloads"
              :key="platform.id"
              :class="[
                'flex min-w-0 flex-col rounded-panel border bg-window p-5 shadow-control',
                platform.recommended
                  ? 'download-recommended-card'
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
                  v-if="platform.recommended"
                  class="ml-auto inline-flex shrink-0 items-center rounded-badge bg-accent-soft px-1.5 py-px text-caption font-emphasis text-accent"
                >
                  当前系统
                </span>
              </div>

              <dl class="mt-6 space-y-2 border-t border-separator pt-4">
                <div class="flex items-center gap-3">
                  <dt class="w-[72px] shrink-0 text-caption text-label-tertiary">系统要求</dt>
                  <dd class="text-caption text-label">{{ platform.requirements }}</dd>
                </div>
                <div class="flex items-center gap-3">
                  <dt class="w-[72px] shrink-0 text-caption text-label-tertiary">安装包</dt>
                  <dd class="min-w-0 truncate text-caption text-label" :title="platform.format">
                    {{ platform.format }}
                  </dd>
                </div>
                <div class="flex items-center gap-3">
                  <dt class="w-[72px] shrink-0 text-caption text-label-tertiary">版本</dt>
                  <dd class="tnum text-caption text-label">{{ displayVersion }}</dd>
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
                下载 {{ platform.name }} 版
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
                {{ loadingRelease ? '正在读取发布信息…' : '安装包暂未发布' }}
              </button>
            </article>
          </div>

          <div class="download-feature-grid mt-8 grid gap-3 border-t border-separator pt-6 sm:grid-cols-3">
            <div v-for="feature in features" :key="feature.title" class="flex items-start gap-2.5">
              <span class="mt-0.5 grid size-5 shrink-0 place-items-center rounded-full bg-success-soft text-success">
                <Icon :as="Check" size="inline" />
              </span>
              <div>
                <p class="text-caption font-emphasis text-label">{{ feature.title }}</p>
                <p class="mt-0.5 text-caption text-label-tertiary">{{ feature.description }}</p>
              </div>
            </div>
          </div>

          <div class="mt-6 flex flex-wrap items-start gap-2 rounded-panel bg-info-surface px-3 py-2.5">
            <Icon :as="ShieldCheck" size="inline" class="mt-0.5 text-accent" />
            <p class="text-caption leading-relaxed text-label-secondary">
              安装包由当前 Regent 服务提供。尚未发布对应系统安装包时，可直接使用浏览器版；
              登录账号、项目与任务数据完全一致。
            </p>
            <p class="w-full pl-6 text-caption leading-relaxed text-label-tertiary">
              {{ windowsClient
                ? 'Windows 便携版未做商业代码签名；若 SmartScreen 提示，请选择“更多信息”后点击“仍要运行”。'
                : 'macOS 当前为未公证版本：首次安装后请在 Finder 中右键 Regent，选择“打开”，再确认一次。' }}
            </p>
            <RouterLink
              :to="backTo"
              :class="buttonVariants({ variant: 'default', size: 'sm' })"
              class="download-web-button shrink-0 sm:ml-auto"
            >
              直接使用 Web 版
            </RouterLink>
          </div>

          <details class="mt-4 rounded-panel border border-separator-soft bg-window px-4 py-3">
            <summary class="cursor-pointer text-caption font-emphasis text-label">
              {{ windowsClient ? 'Windows 安装说明' : 'macOS 安装说明' }}
            </summary>
            <div
              v-if="windowsClient"
              class="mt-3 space-y-3 text-caption leading-relaxed text-label-secondary"
            >
              <ol class="list-decimal space-y-1.5 pl-5">
                <li>
                  下载并解压
                  <code class="font-mono text-label">{{ windowsFilename }}</code>。
                </li>
                <li>
                  把解压后的目录放到固定位置，双击
                  <code class="font-mono text-label">Regent.exe</code>
                  启动。
                </li>
                <li>
                  首次运行若出现 Windows SmartScreen，点击“更多信息”，再选择“仍要运行”。
                </li>
                <li>需要开机启动时，可右键 Regent.exe 创建桌面快捷方式并放入启动目录。</li>
              </ol>
              <p>
                首次启动填写 Regent 服务地址。本机使用
                <code class="font-mono text-label">http://127.0.0.1:12580</code>，公网使用实际部署地址。
              </p>
              <p>
                后续切换地址：菜单栏选择“文件 → 切换服务地址…”。便携版不会写入系统级安装记录。
              </p>
            </div>
            <div v-else class="mt-3 space-y-3 text-caption leading-relaxed text-label-secondary">
              <ol class="list-decimal space-y-1.5 pl-5">
                <li>
                  下载并双击
                  <code class="font-mono text-label">{{ macosFilename }}</code>。
                </li>
                <li>
                  把 <code class="font-mono text-label">Regent.app</code>
                  拖进“应用程序”，不要从 DMG 中直接运行。
                </li>
                <li>
                  首次打开时右键 <code class="font-mono text-label">Regent.app</code>，
                  选择“打开”，再确认一次。
                </li>
                <li>如果仍被系统拦截，在终端执行下面的命令。</li>
              </ol>
              <pre class="overflow-x-auto rounded-control bg-content px-3 py-2 font-mono text-[11px] text-label">xattr -cr /Applications/Regent.app
open /Applications/Regent.app</pre>
              <p>
                首次启动填写 Regent 服务地址。本机使用
                <code class="font-mono text-label">http://127.0.0.1:12580</code>，公网使用实际部署地址。
              </p>
              <p>
                后续切换地址：菜单栏选择“文件 → 切换服务地址…”。详细教程也包含在 DMG 的
                <code class="font-mono text-label">安装说明.txt</code> 中。
              </p>
            </div>
          </details>
        </div>
      </main>

      <footer class="mt-6 flex flex-wrap items-center justify-between gap-3 px-1 text-caption text-label-tertiary">
        <RouterLink class="inline-flex items-center gap-1 transition-colors hover:text-label" :to="backTo">
          <Icon :as="ArrowLeft" size="inline" />{{ backLabel }}
        </RouterLink>
        <p>
          安装遇到问题？<RouterLink class="font-emphasis text-accent hover:underline" :to="helpTo">查看使用说明</RouterLink>
        </p>
        <span class="inline-flex items-center gap-1">
          <Icon :as="Sparkles" size="inline" />当前版本 {{ CURRENT_RELEASE.label }} · {{ displayDate }}
        </span>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.download-stage {
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

.download-recommended-card {
  border-color: rgba(0, 122, 255, 0.35);
  box-shadow:
    0 0 0 1px rgba(0, 122, 255, 0.1),
    var(--fx-shadow-control);
}

.download-brand-copy {
  display: none;
}

@media (min-width: 40rem) {
  .download-stage {
    padding-inline: 1.5rem;
  }

  .download-brand-copy {
    display: flex;
  }

  .download-content {
    padding: 2.5rem;
  }

  .download-title {
    font-size: 38px;
  }

  .download-feature-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .download-web-button {
    margin-left: auto;
  }
}

@media (min-width: 64rem) {
  .download-stage {
    padding-inline: 2rem;
  }

  .download-content {
    padding: 3rem;
  }

}

@media (min-width: 48rem) {
  .download-platform-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

</style>
