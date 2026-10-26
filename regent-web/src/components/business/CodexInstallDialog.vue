<script setup lang="ts">
import { RefreshCw, Terminal, TriangleAlert } from '@lucide/vue'
import { computed, ref, watch } from 'vue'

import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import IconButton from '@/components/ui/icon-button/IconButton.vue'
import StatusDot from '@/components/ui/status-dot/StatusDot.vue'
import { useCodexInstallDialog } from '@/composables/useCodexInstallDialog'
import { useToast } from '@/composables/useToast'
import { useExecutionRuntimeStore } from '@/stores/useExecutionRuntimeStore'

const { open } = useCodexInstallDialog()
const runtime = useExecutionRuntimeStore()
const toast = useToast()

const error = ref<string | null>(null)

const statusLabel = computed(() => {
  if (runtime.loading) return '检测中'
  if (!runtime.status) return '未检测'
  return runtime.status.available ? '已就绪' : '未安装或不可用'
})
const statusDot = computed(() => {
  if (runtime.loading) return 'bg-accent'
  if (!runtime.status) return 'bg-neutral'
  return runtime.status.available ? 'bg-success' : 'bg-danger'
})
watch(open, (value) => {
  if (!value) return
  error.value = null
  if (!runtime.status) void runtime.check()
})

async function copy(command: string) {
  error.value = null
  try {
    if (!navigator.clipboard?.writeText) {
      throw new Error('当前浏览器不允许访问剪贴板')
    }
    await navigator.clipboard.writeText(command)
    toast.success('命令已复制', '请在当前设备终端执行')
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '命令复制失败'
  }
}

async function recheck() {
  await runtime.check(true)
  if (runtime.status?.available) {
    toast.success('Codex 已就绪', runtime.status.version ?? '可执行文件检测通过')
  }
}
</script>

<template>
  <Dialog
    v-model:open="open"
    layout="form"
    size="large"
    title="安装或更新 Codex"
    description="Regent 本身不内置模型，任务执行和项目摘要生成都依赖本机 Codex CLI。"
    :icon="Terminal"
  >
    <div class="space-y-3">
      <div class="flex items-start gap-3 rounded-panel border border-warning/30 bg-warning/8 p-3">
        <Icon :as="TriangleAlert" size="large" class="mt-0.5 shrink-0 text-warning" />
        <div class="min-w-0">
          <p class="text-body font-emphasis text-label">必须先安装 Codex，才能在平台上干活</p>
          <p class="mt-1 text-caption leading-5 text-label-secondary">
            未安装时可以注册、创建项目和录入任务，但任务不能派发，项目摘要也不能生成。
          </p>
        </div>
      </div>

      <div class="rounded-panel border border-separator-soft bg-content p-3">
        <div class="flex flex-wrap items-center gap-2">
          <span class="text-caption font-emphasis text-label-secondary">当前状态</span>
          <StatusDot :dot="statusDot">{{ statusLabel }}</StatusDot>
          <span v-if="runtime.status?.version" class="ml-auto font-mono text-caption text-label-tertiary">
            {{ runtime.status.version }}
          </span>
        </div>
        <p v-if="runtime.status?.message" class="mt-1.5 text-caption text-label-secondary">
          {{ runtime.status.message }}
        </p>
        <p v-if="runtime.status?.executablePath" class="mt-1 break-all font-mono text-caption text-label-tertiary">
          {{ runtime.status.executablePath }}
        </p>
      </div>

      <div class="rounded-panel border border-warning/30 bg-warning/8 p-3 text-caption leading-5 text-label-secondary">
        npm 和 GitHub 位于国外。国内网络通常需要代理或“魔法”才能稳定安装；公司网络也可能拦截下载。
      </div>

      <div class="grid gap-3 sm:grid-cols-2">
        <div class="rounded-panel border border-separator-soft bg-content p-3">
          <div class="flex items-center justify-between gap-2">
            <p class="text-body font-emphasis text-label">npm 安装</p>
            <span class="text-caption text-label-tertiary">推荐</span>
          </div>
          <p class="mt-1 text-caption text-label-secondary">需要 Node.js 18+。</p>
          <code class="mt-2 block break-all rounded-control bg-window px-2 py-1.5 font-mono text-caption text-label">
            npm install -g @openai/codex
          </code>
          <Button class="mt-3" variant="primary" block @click="copy('npm install -g @openai/codex')">
            复制安装命令
          </Button>
        </div>

        <div class="rounded-panel border border-separator-soft bg-content p-3">
          <p class="text-body font-emphasis text-label">Homebrew 安装</p>
          <p class="mt-1 text-caption text-label-secondary">需要 macOS Homebrew。</p>
          <code class="mt-2 block break-all rounded-control bg-window px-2 py-1.5 font-mono text-caption text-label">
            brew install codex
          </code>
          <Button class="mt-3" block @click="copy('brew install codex')">
            复制安装命令
          </Button>
        </div>
      </div>

      <p class="text-caption text-label-tertiary">
        安装通常需要 1–5 分钟。安装完成后必须在终端能直接执行
        <span class="font-mono">codex --version</span>；安装在非标准目录时，请在后端配置
        <span class="font-mono">regent.execution.codex-command</span> 的绝对路径。
      </p>

      <div v-if="error" class="rounded-panel border border-danger/30 bg-danger/8 p-3">
        <p class="text-caption text-danger">{{ error }}</p>
      </div>
    </div>

    <template #footer>
      <IconButton
        label="重新检测 Codex"
        :disabled="runtime.loading"
        @click="recheck"
      >
        <Icon :as="RefreshCw" size="regular" :class="{ 'animate-spin': runtime.loading }" />
      </IconButton>
      <Button @click="open = false">关闭</Button>
    </template>
  </Dialog>
</template>
