<script setup lang="ts">
import { Code2, GitBranch, LoaderCircle } from '@lucide/vue'
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { getGitCommitDetail } from '@/api/gitCommits'
import { ApiError } from '@/api/http'
import GitCommitStatusBadge from '@/components/business/GitCommitStatusBadge.vue'
import CodeEditorDialog from '@/components/business/CodeEditorDialog.vue'
import Button from '@/components/ui/button/Button.vue'
import CopyButton from '@/components/ui/copy-button/CopyButton.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import { formatDateTime } from '@/utils/format'
import type { GitCommitDetail } from '@/types/api'

const props = defineProps<{ commitId: number | null }>()
const open = defineModel<boolean>('open', { default: false })
const router = useRouter()
const detail = ref<GitCommitDetail | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
const codeEditorOpen = ref(false)

function messageOf(cause: unknown): string {
  return cause instanceof ApiError ? cause.message : '无法读取 Git 提交详情'
}

async function load() {
  if (!props.commitId) return
  loading.value = true
  error.value = null
  try {
    detail.value = await getGitCommitDetail(props.commitId)
  } catch (cause) {
    error.value = messageOf(cause)
  } finally {
    loading.value = false
  }
}

function openTask() {
  if (!detail.value) return
  open.value = false
  void router.push({ path: '/tasks', query: { taskId: String(detail.value.summary.taskId) } })
}

watch(
  () => [open.value, props.commitId] as const,
  ([isOpen]) => {
    if (isOpen) {
      detail.value = null
      void load()
    }
  },
)
</script>

<template>
  <Dialog
    v-model:open="open"
    size="wide"
    title="Git 提交详情"
    description="提交动作、审阅来源和文件范围的审计记录"
    :icon="GitBranch"
  >
    <div v-if="loading" class="flex h-48 items-center justify-center gap-2 text-caption text-label-tertiary">
      <LoaderCircle :size="16" class="animate-spin" />正在读取提交记录…
    </div>
    <p v-else-if="error" class="text-caption text-danger">{{ error }}</p>

    <div v-else-if="detail" class="space-y-4">
      <div class="flex flex-wrap items-center gap-2">
        <GitCommitStatusBadge :status="detail.summary.status" />
        <span class="text-caption text-label-tertiary">
          {{ formatDateTime(detail.summary.startedAt) }}
        </span>
        <span class="ml-auto text-caption text-label-secondary">
          {{ detail.summary.operatorName }}
        </span>
      </div>

      <div class="rounded-panel bg-window px-3 py-3">
        <p class="text-caption font-emphasis text-label-secondary">提交说明</p>
        <p class="mt-1 whitespace-pre-wrap text-body text-label">{{ detail.summary.message }}</p>
        <div class="mt-2 flex items-center gap-2">
          <CopyButton :text="detail.summary.message" label="复制提交说明" success-message="提交说明已复制" />
          <span v-if="detail.summary.ticket" class="font-mono text-caption text-label-tertiary">
            Bug {{ detail.summary.ticket }}
          </span>
        </div>
      </div>

      <dl class="grid gap-3 rounded-panel bg-content px-3 py-3 text-caption sm:grid-cols-2">
        <div><dt class="text-label-tertiary">任务</dt><dd class="mt-0.5 text-label">#{{ detail.summary.taskId }} · {{ detail.summary.taskTitle }}</dd></div>
        <div><dt class="text-label-tertiary">项目</dt><dd class="mt-0.5 text-label">{{ detail.summary.projectName }}</dd></div>
        <div><dt class="text-label-tertiary">分支</dt><dd class="mt-0.5 font-mono text-label">{{ detail.summary.branchName }}</dd></div>
        <div>
          <dt class="text-label-tertiary">Commit</dt>
          <dd class="mt-0.5 flex items-center gap-1 font-mono text-label">
            <span class="break-all">{{ detail.summary.commitSha ?? '—' }}</span>
            <CopyButton v-if="detail.summary.commitSha" :text="detail.summary.commitSha" label="复制 Commit SHA" success-message="Commit SHA 已复制" />
          </dd>
        </div>
        <div class="sm:col-span-2"><dt class="text-label-tertiary">仓库</dt><dd class="mt-0.5 break-all font-mono text-label">{{ detail.repositoryPath }}</dd></div>
      </dl>

      <div>
        <p class="text-caption font-emphasis text-label">提交文件（{{ detail.filePaths.length }}）</p>
        <div class="scroll-area mt-1.5 max-h-56 overflow-y-auto rounded-panel bg-window p-2">
          <p v-for="path in detail.filePaths" :key="path" class="break-all py-0.5 font-mono text-caption text-label-secondary">
            {{ path }}
          </p>
        </div>
      </div>

      <div v-if="detail.failureReason" class="rounded-panel border border-danger/20 bg-danger/5 px-3 py-2">
        <p class="text-caption font-emphasis text-danger">失败原因</p>
        <p class="mt-1 whitespace-pre-wrap text-caption text-label">{{ detail.failureReason }}</p>
      </div>
    </div>

    <template #footer>
      <Button v-if="detail" size="sm" @click="codeEditorOpen = true">
        <Icon :as="Code2" size="inline" />打开项目代码
      </Button>
      <Button size="sm" :disabled="!detail" @click="openTask">
        <Icon :as="GitBranch" size="inline" />打开任务
      </Button>
      <Button size="sm" @click="open = false">关闭</Button>
    </template>
  </Dialog>

  <CodeEditorDialog
    v-if="detail"
    v-model:open="codeEditorOpen"
    :project-id="detail.summary.projectId"
  />
</template>
