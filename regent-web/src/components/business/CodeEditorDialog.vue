<script setup lang="ts">
import { LoaderCircle } from '@lucide/vue'
import { computed, ref, watch } from 'vue'

import { getAvailableCodeEditors, openProjectCode } from '@/api/codeEditors'
import { ApiError } from '@/api/http'
import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import { useToast } from '@/composables/useToast'
import type { CodeEditor } from '@/types/api'
import cursorIcon from '@/assets/code-editors/cursor.svg'
import intellijIcon from '@/assets/code-editors/intellij-idea.svg'
import vsCodeIcon from '@/assets/code-editors/vs-code.svg'

const props = withDefaults(defineProps<{
  projectId: number
  relativePath?: string | null
  line?: number | null
}>(), {
  relativePath: null,
  line: null,
})
const open = defineModel<boolean>('open', { default: false })
const toast = useToast()
const editors = ref<CodeEditor[]>([])
const loading = ref(false)
const openingId = ref<CodeEditor['id'] | null>(null)
const error = ref<string | null>(null)

const targetLabel = computed(() =>
  props.relativePath
    ? `${props.relativePath}${props.line ? `:${props.line}` : ''}`
    : '整个项目目录',
)

function messageOf(cause: unknown): string {
  return cause instanceof ApiError ? cause.message : '无法调用本机代码编辑器'
}

function editorIcon(editor: CodeEditor): string {
  return {
    VS_CODE: vsCodeIcon,
    CURSOR: cursorIcon,
    INTELLIJ_IDEA: intellijIcon,
  }[editor.id]
}

async function load() {
  loading.value = true
  error.value = null
  try {
    editors.value = await getAvailableCodeEditors(props.projectId)
  } catch (cause) {
    error.value = messageOf(cause)
    editors.value = []
  } finally {
    loading.value = false
  }
}

async function choose(editor: CodeEditor) {
  if (openingId.value) return
  openingId.value = editor.id
  error.value = null
  try {
    await openProjectCode(
      props.projectId,
      editor.id,
      props.relativePath ?? null,
      editor.supportsFileNavigation ? props.line ?? null : null,
    )
    toast.success(`已用 ${editor.name} 打开代码`)
    open.value = false
  } catch (cause) {
    error.value = messageOf(cause)
  } finally {
    openingId.value = null
  }
}

watch(
  () => [open.value, props.projectId, props.relativePath, props.line] as const,
  ([isOpen]) => {
    if (isOpen) void load()
  },
)
</script>

<template>
  <Dialog
    v-model:open="open"
    title="打开项目代码"
    description="选择本机已安装的编辑器"
    size="wide"
  >
    <p class="rounded-panel bg-window px-3 py-2 font-mono text-caption text-label-secondary">
      {{ targetLabel }}
    </p>

    <div v-if="loading" class="flex h-40 items-center justify-center gap-2 text-caption text-label-tertiary">
      <LoaderCircle :size="16" class="animate-spin" />正在检测本机编辑器…
    </div>
    <div v-else-if="editors.length > 0" class="mt-3 space-y-2">
      <button
        v-for="editor in editors"
        :key="editor.id"
        type="button"
        class="flex w-full items-center gap-3 rounded-panel border border-separator-soft bg-content px-3 py-3 text-left transition-colors hover:bg-hover"
        :disabled="openingId !== null"
        @click="choose(editor)"
      >
        <span class="relative grid size-10 shrink-0 place-items-center rounded-panel bg-white shadow-sm">
          <img :src="editorIcon(editor)" :alt="`${editor.name} 图标`" class="size-6" />
          <span v-if="openingId === editor.id" class="absolute inset-0 grid place-items-center rounded-panel bg-white/80">
            <Icon :as="LoaderCircle" size="inline" class="animate-spin text-accent" />
          </span>
        </span>
        <span class="min-w-0 flex-1">
          <span class="block text-body font-emphasis text-label">{{ editor.name }}</span>
          <span class="mt-0.5 block text-caption text-label-tertiary">
            {{ editor.supportsFileNavigation ? '可定位到当前文件与行' : '打开项目目录' }}
          </span>
        </span>
        <span class="text-caption text-accent">打开</span>
      </button>
    </div>
    <div v-else class="mt-3 rounded-panel border border-warning/20 bg-warning/5 px-3 py-3">
      <p class="text-caption font-emphasis text-label">没有检测到可用的代码编辑器</p>
      <p class="mt-1 text-caption text-label-tertiary">
        安装 VS Code、Cursor 或 IntelliJ IDEA 后重新打开本窗口。
      </p>
    </div>

    <p v-if="error" class="mt-3 text-caption text-danger">{{ error }}</p>

    <template #footer>
      <Button size="sm" :disabled="loading || openingId !== null" @click="load">重新检测</Button>
      <Button size="sm" @click="open = false">关闭</Button>
    </template>
  </Dialog>
</template>
