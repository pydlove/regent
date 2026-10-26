<script setup lang="ts">
import { Check, GitBranch, LoaderCircle, RefreshCw } from '@lucide/vue'
import { computed, ref, watch } from 'vue'

import { commitTaskReview, getTaskReviewGitCommit } from '@/api/reviews'
import { ApiError } from '@/api/http'
import Badge from '@/components/ui/badge/Badge.vue'
import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import Textarea from '@/components/ui/input/Textarea.vue'
import { useToast } from '@/composables/useToast'
import type { ReviewGitCommit } from '@/types/api'

const props = defineProps<{
  taskId: number
  revision: string
}>()
const emit = defineEmits<{
  committed: [result: ReviewGitCommit]
}>()
const open = defineModel<boolean>('open', { default: false })
const toast = useToast()

const preview = ref<ReviewGitCommit | null>(null)
const message = ref('')
const loading = ref(false)
const submitting = ref(false)
const error = ref<string | null>(null)

const alreadyCommitted = computed(() => Boolean(preview.value?.commitSha))
const canSubmit = computed(
  () =>
    Boolean(preview.value?.canCommit)
    && message.value.trim().length > 0
    && !loading.value
    && !submitting.value,
)

function messageOf(cause: unknown): string {
  return cause instanceof ApiError ? cause.message : 'Git 提交请求失败，请稍后重试'
}

async function load(resetMessage = true) {
  if (!open.value || loading.value) return
  loading.value = true
  error.value = null
  try {
    const result = await getTaskReviewGitCommit(props.taskId)
    preview.value = result
    if (resetMessage) message.value = result.suggestedMessage
  } catch (cause) {
    error.value = messageOf(cause)
  } finally {
    loading.value = false
  }
}

function regenerateMessage() {
  if (!preview.value) return
  void load(true)
}

async function submit() {
  if (!canSubmit.value) return
  submitting.value = true
  error.value = null
  try {
    const result = await commitTaskReview(props.taskId, message.value.trim())
    preview.value = result
    emit('committed', result)
    toast.success('Git 提交已创建', result.commitSha.slice(0, 12))
    open.value = false
  } catch (cause) {
    error.value = messageOf(cause)
  } finally {
    submitting.value = false
  }
}

watch(
  () => [open.value, props.taskId, props.revision] as const,
  ([isOpen]) => {
    if (isOpen) {
      preview.value = null
      message.value = ''
      void load()
    }
  },
)
</script>

<template>
  <Dialog
    v-model:open="open"
    size="wide"
    title="提交 Git"
    description="确认自动生成的提交说明和文件范围后创建本地提交"
    :icon="GitBranch"
  >
    <div v-if="loading" class="flex h-48 items-center justify-center gap-2 text-caption text-label-tertiary">
      <LoaderCircle :size="16" class="animate-spin" />
      正在核对审阅结论与 Git 工作区…
    </div>

    <div v-else-if="preview" class="space-y-4">
      <div class="flex flex-wrap items-center gap-x-4 gap-y-2 rounded-panel bg-window px-3 py-2 text-caption">
        <span class="text-label-secondary">{{ preview.projectName }}</span>
        <Badge v-if="preview.branchName" tone="neutral">
          <Icon :as="GitBranch" size="inline" />
          {{ preview.branchName }}
        </Badge>
        <span class="font-mono text-label-tertiary">{{ preview.headCommit.slice(0, 12) }}</span>
        <Badge v-if="preview.ticket" tone="neutral">关联 Bug {{ preview.ticket }}</Badge>
        <span v-else class="text-label-tertiary">无关联 Bug</span>
        <span class="ml-auto text-label-secondary">{{ preview.fileCount }} 个审阅文件</span>
      </div>

      <div
        v-if="preview.reason"
        class="rounded-panel border px-3 py-2 text-caption"
        :class="alreadyCommitted
          ? 'border-success/20 bg-success/5 text-success'
          : 'border-warning/25 bg-warning/5 text-warning'"
      >
        {{ preview.reason }}
      </div>

      <div v-if="alreadyCommitted" class="rounded-panel bg-content px-3 py-3">
        <p class="text-caption font-emphasis text-label">提交已完成</p>
        <p class="mt-1 break-all font-mono text-caption text-label-tertiary">{{ preview.commitSha }}</p>
      </div>

      <template v-else-if="preview.canCommit">
        <div>
          <div class="flex items-center gap-2">
            <label for="git-commit-message" class="text-caption font-emphasis text-label">提交说明</label>
            <Button
              size="sm"
              class="ml-auto"
              :disabled="submitting"
              @click="regenerateMessage"
            >
              <Icon :as="RefreshCw" size="inline" />重新生成
            </Button>
          </div>
          <Textarea
            id="git-commit-message"
            v-model="message"
            class="mt-2 font-mono"
            :rows="5"
            :disabled="!preview.canCommit || submitting"
            placeholder="提交说明将自动生成"
          />
          <p class="mt-1.5 text-caption text-label-tertiary">
            已按提交规范自动生成，可直接提交或微调。
          </p>
          <p v-if="preview.validationHint" class="mt-1 text-caption text-label-tertiary">
            {{ preview.validationHint }}
          </p>
        </div>

        <div>
          <p class="text-caption font-emphasis text-label">本次提交文件</p>
          <div class="scroll-area mt-1.5 max-h-48 overflow-y-auto rounded-panel bg-window p-2">
            <p
              v-for="path in preview.filePaths"
              :key="path"
              class="break-all py-0.5 font-mono text-caption text-label-secondary"
            >
              {{ path }}
            </p>
          </div>
        </div>
      </template>

      <p v-if="error" class="text-caption text-danger">{{ error }}</p>
    </div>

    <template #footer>
      <Button size="sm" :disabled="submitting" @click="open = false">关闭</Button>
      <Button
        v-if="preview?.canCommit"
        variant="primary"
        size="sm"
        :disabled="!canSubmit"
        @click="submit"
      >
        <Icon :as="submitting ? LoaderCircle : Check" size="inline" :class="{ 'animate-spin': submitting }" />
        {{ submitting ? '提交中…' : '提交 Git' }}
      </Button>
    </template>
  </Dialog>
</template>
