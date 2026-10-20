<script setup lang="ts">
import {
  Apple,
  Download,
  ExternalLink,
  MonitorDown,
  PackageOpen,
  RefreshCw,
} from '@lucide/vue'
import { computed, onMounted, ref } from 'vue'

import {
  CC_SWITCH_RELEASES_URL,
  CC_SWITCH_SITE_URL,
  detectCcSwitchPlatform,
  getCcSwitchRelease,
  type CcSwitchAsset,
  type CcSwitchRelease,
} from '@/api/ccSwitch'
import { buttonVariants } from '@/components/ui/button/button.variants'
import Icon from '@/components/ui/icon/Icon.vue'
import IconButton from '@/components/ui/icon-button/IconButton.vue'
import { formatBytes } from '@/utils/format'

const loading = ref(false)
const error = ref<string | null>(null)
const release = ref<CcSwitchRelease | null>(null)
const platform = detectCcSwitchPlatform(navigator.userAgent)

const platformName = computed(() => ({
  macos: 'macOS',
  windows: 'Windows',
  linux: 'Linux',
})[platform])

const quickAsset = computed<CcSwitchAsset | null>(() => {
  if (!release.value) return null
  if (platform === 'windows') {
    return release.value.assets.windowsInstaller ?? release.value.assets.windowsPortable
  }
  if (platform === 'linux') return release.value.assets.linuxAppImage
  return release.value.assets.macos
})

const quickLabel = computed(() =>
  quickAsset.value ? `快捷下载 ${platformName.value} 版` : '前往官方下载',
)

const publishedDate = computed(() =>
  release.value?.publishedAt ? release.value.publishedAt.slice(0, 10) : null,
)

async function load(force = false) {
  if (loading.value) return
  loading.value = true
  error.value = null
  try {
    release.value = await getCcSwitchRelease(force)
  } catch {
    error.value = '暂时无法读取最新版本，仍可通过官网或 GitHub Releases 下载。'
  } finally {
    loading.value = false
  }
}

onMounted(() => void load())
</script>

<template>
  <section class="overflow-hidden rounded-panel bg-content shadow-panel">
    <div class="flex items-start gap-4 px-4 py-4">
      <span class="grid size-10 shrink-0 place-items-center rounded-panel bg-accent-soft text-accent">
        <Icon :as="Download" size="large" />
      </span>
      <div class="min-w-0 flex-1">
        <div class="flex flex-wrap items-center gap-2">
          <span class="text-body font-emphasis text-label">CC Switch</span>
          <span class="rounded-badge bg-accent-soft px-1.5 py-px text-caption font-emphasis text-accent">
            推荐工具
          </span>
          <span v-if="release" class="tnum text-caption text-label-tertiary">
            v{{ release.version }}
          </span>
        </div>
        <p class="mt-1 text-caption leading-5 text-label-secondary">
          统一管理 Codex、Claude Code 等工具的供应商配置、MCP 与 Skills，减少手工修改配置文件。
        </p>
        <p v-if="publishedDate" class="tnum mt-1 text-caption text-label-tertiary">
          官方发布于 {{ publishedDate }}
        </p>
      </div>
      <IconButton
        label="刷新 CC Switch 版本信息"
        size="sm"
        :disabled="loading"
        @click="load(true)"
      >
        <Icon :as="RefreshCw" size="inline" :class="{ 'animate-spin': loading }" />
      </IconButton>
    </div>

    <div class="flex flex-wrap items-center gap-3 border-t border-separator-soft bg-window/45 px-4 py-3">
      <div class="min-w-0">
        <p class="text-caption font-emphasis text-label">当前系统：{{ platformName }}</p>
        <p class="mt-0.5 text-caption text-label-tertiary">
          下载地址来自 CC Switch 官方 GitHub Releases，Regent 不代管安装包。
        </p>
      </div>
      <a
        :href="quickAsset?.url || CC_SWITCH_SITE_URL"
        target="_blank"
        rel="noreferrer"
        :class="[
          buttonVariants({ variant: quickAsset ? 'primary' : 'default', size: 'sm' }),
          'ml-auto',
        ]"
      >
        <Icon :as="Download" size="inline" />
        {{ loading ? '正在读取最新版本…' : quickLabel }}
      </a>
    </div>

    <p
      v-if="error"
      class="border-t border-separator-soft px-4 py-2 text-caption text-warning"
    >
      {{ error }}
    </p>

    <div v-if="release" class="border-t border-separator-soft">
      <div class="grid grid-cols-[130px_minmax(0,1fr)_auto] items-center gap-4 px-4 py-3 max-sm:grid-cols-1 max-sm:gap-2">
        <div class="flex items-center gap-2">
          <Icon :as="Apple" size="regular" />
          <span class="text-body font-emphasis text-label">macOS</span>
        </div>
        <div class="min-w-0 text-caption text-label-secondary">
          Universal 安装包，支持 Apple Silicon 与 Intel。
          <span class="mt-1 block font-mono text-label-tertiary">brew install --cask cc-switch</span>
        </div>
        <a
          v-if="release.assets.macos"
          :href="release.assets.macos.url"
          target="_blank"
          rel="noreferrer"
          :class="buttonVariants({ size: 'sm', variant: platform === 'macos' ? 'primary' : 'default' })"
        >
          下载 DMG
          <span class="tnum text-caption opacity-70">{{ formatBytes(release.assets.macos.sizeBytes) }}</span>
        </a>
        <a v-else :href="CC_SWITCH_RELEASES_URL" target="_blank" rel="noreferrer" :class="buttonVariants({ size: 'sm' })">
          查看 Releases
        </a>
      </div>

      <div class="h-px bg-separator-soft" />
      <div class="grid grid-cols-[130px_minmax(0,1fr)_auto] items-center gap-4 px-4 py-3 max-sm:grid-cols-1 max-sm:gap-2">
        <div class="flex items-center gap-2">
          <Icon :as="MonitorDown" size="regular" />
          <span class="text-body font-emphasis text-label">Windows</span>
        </div>
        <div class="text-caption text-label-secondary">Windows 10 及以上，支持安装版或便携版。</div>
        <div class="flex flex-wrap items-center justify-end gap-2 max-sm:justify-start">
          <a
            v-if="release.assets.windowsInstaller"
            :href="release.assets.windowsInstaller.url"
            target="_blank"
            rel="noreferrer"
            :class="buttonVariants({ size: 'sm', variant: platform === 'windows' ? 'primary' : 'default' })"
          >
            下载安装包
          </a>
          <a
            v-if="release.assets.windowsPortable"
            :href="release.assets.windowsPortable.url"
            target="_blank"
            rel="noreferrer"
            :class="buttonVariants({ size: 'sm' })"
          >
            便携版
          </a>
          <a
            v-if="!release.assets.windowsInstaller && !release.assets.windowsPortable"
            :href="CC_SWITCH_RELEASES_URL"
            target="_blank"
            rel="noreferrer"
            :class="buttonVariants({ size: 'sm' })"
          >
            查看 Releases
          </a>
        </div>
      </div>

      <div class="h-px bg-separator-soft" />
      <div class="grid grid-cols-[130px_minmax(0,1fr)_auto] items-center gap-4 px-4 py-3 max-sm:grid-cols-1 max-sm:gap-2">
        <div class="flex items-center gap-2">
          <Icon :as="PackageOpen" size="regular" />
          <span class="text-body font-emphasis text-label">Linux</span>
        </div>
        <div class="text-caption text-label-secondary">x86_64 AppImage；其他发行包请查看 Releases。</div>
        <a
          :href="release.assets.linuxAppImage?.url || CC_SWITCH_RELEASES_URL"
          target="_blank"
          rel="noreferrer"
          :class="buttonVariants({ size: 'sm', variant: platform === 'linux' && release.assets.linuxAppImage ? 'primary' : 'default' })"
        >
          {{ release.assets.linuxAppImage ? '下载 AppImage' : '查看 Releases' }}
        </a>
      </div>
    </div>

    <div class="flex flex-wrap items-center gap-4 border-t border-separator-soft px-4 py-2.5">
      <a
        :href="CC_SWITCH_SITE_URL"
        target="_blank"
        rel="noreferrer"
        class="inline-flex items-center gap-1 text-caption text-label-secondary transition-colors duration-100 ease-apple hover:text-label"
      >
        官方网站
        <Icon :as="ExternalLink" size="inline" />
      </a>
      <a
        :href="CC_SWITCH_RELEASES_URL"
        target="_blank"
        rel="noreferrer"
        class="inline-flex items-center gap-1 text-caption text-label-secondary transition-colors duration-100 ease-apple hover:text-label"
      >
        GitHub Releases
        <Icon :as="ExternalLink" size="inline" />
      </a>
      <span class="ml-auto text-caption text-label-tertiary">第三方开源工具</span>
    </div>
  </section>
</template>
