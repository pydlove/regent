<script setup lang="ts">
import {
  Archive,
  BookOpen,
  Copy,
  FileText,
  History,
  Library,
  Pencil,
  Plus,
  Power,
  RefreshCw,
} from '@lucide/vue'
import { computed, onMounted, ref, watch } from 'vue'

import {
  archiveCodeStandard,
  listCodeStandards,
  setCodeStandardEnabled,
} from '@/api/codeStandards'
import { listFileLibrary } from '@/api/documents'
import { ApiError } from '@/api/http'
import CodeStandardCopyDialog from '@/components/business/CodeStandardCopyDialog.vue'
import CodeStandardEditorDialog from '@/components/business/CodeStandardEditorDialog.vue'
import CodeStandardHistoryDialog from '@/components/business/CodeStandardHistoryDialog.vue'
import Badge from '@/components/ui/badge/Badge.vue'
import Button from '@/components/ui/button/Button.vue'
import EmptyState from '@/components/ui/empty-state/EmptyState.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import IconButton from '@/components/ui/icon-button/IconButton.vue'
import Tooltip from '@/components/ui/tooltip/Tooltip.vue'
import { useToast } from '@/composables/useToast'
import { useTaskStore } from '@/stores/useTaskStore'
import type { CodeStandard, LibraryDocument } from '@/types/api'
import { formatBytes, formatDateTime } from '@/utils/format'

type LibrarySection = 'standards' | 'general'

const store = useTaskStore()
const toast = useToast()
const section = ref<LibrarySection>('standards')
const loading = ref(false)
const documents = ref<LibraryDocument[]>([])
const standards = ref<CodeStandard[]>([])
const error = ref<string | null>(null)

const editorOpen = ref(false)
const editingStandard = ref<CodeStandard | null>(null)
const historyOpen = ref(false)
const historyStandard = ref<CodeStandard | null>(null)
const copyOpen = ref(false)

const projectId = computed(() =>
  store.projectId !== null
    && store.projects.some((project) => project.id === store.projectId)
    ? store.projectId
    : null,
)
const sectionItems = computed(() => [
  {
    value: 'standards' as const,
    label: `开发规范${standards.value.length ? ` (${standards.value.length})` : ''}`,
  },
  {
    value: 'general' as const,
    label: `通用文件${documents.value.length ? ` (${documents.value.length})` : ''}`,
  },
])
const currentItems = computed(() => (section.value === 'standards' ? standards.value : documents.value))
const scopeLabels: Record<string, string> = {
  GENERAL: '通用',
  FRONTEND: '前端',
  BACKEND: '后端',
  TESTING: '测试',
  SECURITY: '安全',
  DATABASE: '数据库',
  DEPLOYMENT: '部署',
}

const load = async () => {
  const id = projectId.value
  if (id === null) {
    documents.value = []
    standards.value = []
    return
  }
  loading.value = true
  error.value = null
  try {
    const [nextDocuments, nextStandards] = await Promise.all([
      listFileLibrary(id, 'GENERAL'),
      listCodeStandards(id),
    ])
    documents.value = nextDocuments
    standards.value = nextStandards
  } catch (e) {
    documents.value = []
    standards.value = []
    error.value = e instanceof ApiError ? e.message : '文件库读取失败'
    toast.warning('文件库读取失败', error.value)
  } finally {
    loading.value = false
  }
}

watch(projectId, () => void load())
onMounted(async () => {
  try {
    await store.loadProjects()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '项目列表读取失败'
  }
  await load()
})

const openCreate = () => {
  editingStandard.value = null
  editorOpen.value = true
}

const openEdit = (standard: CodeStandard) => {
  editingStandard.value = standard
  editorOpen.value = true
}

const openHistory = (standard: CodeStandard) => {
  historyStandard.value = standard
  historyOpen.value = true
}

const toggleEnabled = async (standard: CodeStandard) => {
  try {
    await setCodeStandardEnabled(standard.id, !standard.enabled)
    await load()
    toast.success(
      standard.enabled ? `「${standard.name}」已停用` : `「${standard.name}」已启用`,
      standard.enabled ? '新任务不再默认引用' : '新任务会按默认引用设置自动关联',
    )
  } catch (e) {
    toast.warning('规范状态更新失败', e instanceof ApiError ? e.message : '请稍后重试')
  }
}

const archive = async (standard: CodeStandard) => {
  if (!window.confirm(`归档开发规范「${standard.name}」？历史任务仍会保留已绑定版本。`)) return
  try {
    await archiveCodeStandard(standard.id)
    await load()
    toast.success(`「${standard.name}」已归档`, '归档不会删除历史文件和任务快照')
  } catch (e) {
    toast.warning('规范归档失败', e instanceof ApiError ? e.message : '请稍后重试')
  }
}

const download = (url: string | null) => {
  if (url) window.open(url, '_blank')
}
</script>

<template>
  <section class="flex h-full min-h-0 flex-col bg-canvas">
    <header class="flex min-h-11 flex-wrap items-center gap-2 border-b border-separator px-3 py-2 sm:gap-3 sm:px-4 sm:py-0">
      <Icon :as="Library" size="regular" class="text-label-secondary" />
      <div>
        <h2 class="text-body font-emphasis text-label">文件库</h2>
        <p class="text-caption text-label-tertiary">项目文件资产统一归档；开发规范会自动关联到任务</p>
      </div>
      <div class="ml-auto flex flex-wrap items-center justify-end gap-2">
        <Button
          v-if="section === 'standards'"
          size="sm"
          :disabled="projectId === null"
          @click="copyOpen = true"
        >
          <Icon :as="Copy" size="inline" /><span class="hidden sm:inline">复制其他项目</span>
        </Button>
        <Button
          v-if="section === 'standards'"
          variant="primary"
          size="sm"
          :disabled="projectId === null"
          @click="openCreate"
        >
          <Icon :as="Plus" size="inline" /><span class="hidden sm:inline">上传开发规范</span>
        </Button>
        <Button size="sm" :disabled="loading || projectId === null" @click="load">
          <Icon :as="RefreshCw" size="inline" :class="{ 'animate-spin': loading }" />
          <span class="hidden sm:inline">刷新</span>
        </Button>
      </div>
    </header>

    <div class="flex min-h-0 flex-1">
      <aside class="w-32 shrink-0 border-r border-separator bg-window p-2 sm:w-44">
        <button
          v-for="item in sectionItems"
          :key="item.value"
          type="button"
          class="flex min-h-9 w-full items-center gap-2 rounded-control px-2.5 text-left text-caption transition-colors"
          :class="
            section === item.value
              ? 'bg-accent/10 font-emphasis text-accent'
              : 'text-label-secondary hover:bg-hover hover:text-label'
          "
          @click="section = item.value"
        >
          <Icon :as="item.value === 'standards' ? BookOpen : FileText" size="inline" />
          <span class="truncate">{{ item.label }}</span>
        </button>
      </aside>

      <div class="min-h-0 flex-1 overflow-y-auto p-4">
        <p v-if="loading && currentItems.length === 0" class="text-caption text-label-tertiary">
          正在读取文件库…
        </p>
        <p v-else-if="projectId === null" class="text-caption text-label-tertiary">
          请先在页面顶部选择具体项目。
        </p>
        <p v-else-if="error" class="text-caption text-danger">{{ error }}</p>

        <EmptyState
          v-else-if="section === 'standards' && standards.length === 0"
          title="还没有开发规范"
          description="上传 Markdown、文本或结构化文本规范，新建任务时会自动引用默认项。"
        >
          <Button size="sm" variant="primary" @click="openCreate">
            <Icon :as="Plus" size="inline" />上传第一条规范
          </Button>
        </EmptyState>

        <EmptyState
          v-else-if="section === 'general' && documents.length === 0"
          title="通用文件库还是空的"
          description="新建任务上传文档时勾选保存到文件库，以后就能直接复用。"
        />

        <div v-else-if="section === 'standards'" class="mx-auto max-w-5xl space-y-2">
          <article
            v-for="standard in standards"
            :key="standard.id"
            class="rounded-panel border border-separator-soft bg-content px-3 py-3"
          >
            <div class="flex items-start gap-3">
              <span class="flex size-9 shrink-0 items-center justify-center rounded-control bg-surface text-label-secondary">
                <Icon :as="BookOpen" size="regular" />
              </span>
              <div class="min-w-0 flex-1">
                <div class="flex flex-wrap items-center gap-2">
                  <h3 class="truncate text-body font-emphasis text-label">{{ standard.name }}</h3>
                  <Badge>{{ scopeLabels[standard.scope] ?? standard.scope }}</Badge>
                  <Badge tone="accent">v{{ standard.currentVersion }}</Badge>
                  <Badge v-if="standard.defaultApply" tone="success">默认引用</Badge>
                  <Badge v-if="!standard.enabled" tone="warning">已停用</Badge>
                  <Badge v-if="standard.sourceProjectId">项目副本</Badge>
                </div>
                <p class="mt-1 line-clamp-2 text-caption text-label-secondary">
                  {{ standard.description || '未填写说明' }}
                </p>
                <p class="mt-1 text-caption text-label-tertiary">
                  {{ standard.filename }} · {{ formatBytes(standard.sizeBytes) }} ·
                  {{ formatDateTime(standard.updatedAt) }}
                </p>
              </div>
              <div class="flex shrink-0 flex-wrap justify-end gap-1">
                <Tooltip content="下载当前版本">
                  <IconButton
                    v-if="standard.contentUrl"
                    label="下载开发规范"
                    size="sm"
                    @click="download(standard.contentUrl)"
                  >
                    <Icon :as="FileText" size="inline" />
                  </IconButton>
                </Tooltip>
                <Tooltip content="版本记录">
                  <IconButton label="查看版本记录" size="sm" @click="openHistory(standard)">
                    <Icon :as="History" size="inline" />
                  </IconButton>
                </Tooltip>
                <Tooltip content="编辑元数据">
                  <IconButton label="编辑开发规范" size="sm" @click="openEdit(standard)">
                    <Icon :as="Pencil" size="inline" />
                  </IconButton>
                </Tooltip>
                <Tooltip :content="standard.enabled ? '停用' : '启用'">
                  <IconButton label="切换开发规范状态" size="sm" @click="toggleEnabled(standard)">
                    <Icon :as="Power" size="inline" />
                  </IconButton>
                </Tooltip>
                <Tooltip content="归档">
                  <IconButton label="归档开发规范" size="sm" @click="archive(standard)">
                    <Icon :as="Archive" size="inline" />
                  </IconButton>
                </Tooltip>
              </div>
            </div>
          </article>
        </div>

        <div v-else class="mx-auto max-w-4xl space-y-1.5">
          <a
            v-for="document in documents"
            :key="document.id"
            :href="document.contentUrl"
            class="flex min-h-12 items-center gap-3 rounded-panel border border-separator-soft bg-content px-3 py-2 transition-colors hover:border-accent/50"
            :title="`下载 ${document.filename}`"
          >
            <span class="flex size-8 shrink-0 items-center justify-center rounded-control bg-surface text-label-secondary">
              <Icon :as="FileText" size="regular" />
            </span>
            <span class="min-w-0 flex-1">
              <span class="block truncate text-body text-label">{{ document.filename }}</span>
              <span class="mt-0.5 block text-caption text-label-tertiary">
                {{ formatBytes(document.sizeBytes) }} · {{ formatDateTime(document.createdAt) }}
              </span>
            </span>
            <span v-if="document.sourceTaskId" class="tnum text-caption text-label-tertiary">
              任务 #{{ document.sourceTaskId }}
            </span>
          </a>
        </div>
      </div>
    </div>

    <CodeStandardEditorDialog
      v-model:open="editorOpen"
      :project-id="projectId"
      :standard="editingStandard"
      @saved="load"
    />
    <CodeStandardHistoryDialog
      v-model:open="historyOpen"
      :standard="historyStandard"
      @changed="load"
    />
    <CodeStandardCopyDialog
      v-model:open="copyOpen"
      :target-project-id="projectId"
      @copied="load"
    />
  </section>
</template>
