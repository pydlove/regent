<script setup lang="ts">
import { CornerDownRight, GitBranch, ImagePlus, TriangleAlert, X } from '@lucide/vue'
import { computed, ref, watch } from 'vue'

import { ACCEPTED_IMAGE_TYPES, uploadTaskAttachment } from '@/api/attachments'
import { ApiError } from '@/api/http'
import { createTaskContinuation } from '@/api/tasks'
import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import DialogField from '@/components/ui/dialog/DialogField.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import Input from '@/components/ui/input/Input.vue'
import Textarea from '@/components/ui/input/Textarea.vue'
import Progress from '@/components/ui/progress/Progress.vue'
import { usePendingImages, type PendingImage } from '@/composables/usePendingImages'
import { useToast } from '@/composables/useToast'
import type { TaskDetail } from '@/types/api'
import { formatBytes } from '@/utils/format'

const props = defineProps<{
  task: TaskDetail | null
}>()

const emit = defineEmits<{
  created: [task: TaskDetail]
  uploaded: [payload: { id: number }]
}>()

const open = defineModel<boolean>('open', { default: false })
const toast = useToast()
const prompt = ref('')
const priority = ref('0')
const saving = ref(false)
const error = ref<string | null>(null)
const {
  pending,
  dragging,
  fileInput,
  pendingBytes,
  onPick,
  onPaste,
  onDrop,
  onDragLeave,
  removeImage,
  reset,
  uploadAll,
} = usePendingImages()

const canSubmit = computed(() => !saving.value && prompt.value.trim().length > 0)

watch(open, (value) => {
  if (!value) {
    reset()
    return
  }
  reset()
  prompt.value = ''
  priority.value = String(props.task?.priority ?? 0)
  saving.value = false
  error.value = null
})

const submit = async () => {
  const task = props.task
  if (!task || !canSubmit.value) return

  const images = [...pending.value]
  saving.value = true
  error.value = null
  try {
    const created = await createTaskContinuation(task.id, {
      prompt: prompt.value.trim(),
      priority: Number(priority.value) || 0,
    })
    emit('created', created)
    open.value = false
    reset()
    if (images.length > 0) void uploadImages(created.id, images)
  } catch (cause) {
    error.value = cause instanceof ApiError
      ? cause.message
      : '后续任务没有创建成功，请稍后重试'
  } finally {
    saving.value = false
  }
}

const uploadImages = async (
  taskId: number,
  images: PendingImage[],
) => {
  const attached = await uploadAll(
    (file, onProgress) => uploadTaskAttachment(taskId, file, { onProgress }),
    images,
  )
  const failed = images.length - attached
  if (attached > 0) {
    emit('uploaded', { id: taskId })
    toast.success(`${attached} 张图已附上`, `#${taskId} 派发时会一并交给 Codex`)
  }
  if (failed > 0) {
    toast.warning(`${failed} 张图没传上去`, '任务已创建，图片可以在右侧详情里补')
  }
}
</script>

<template>
  <Dialog
    v-model:open="open"
    layout="form"
    size="large"
    title="继续创建任务"
    :description="task ? `新任务会复用 #${task.id} 的 Codex 会话与分支` : ''"
    :icon="CornerDownRight"
  >
    <div v-if="task" class="flex flex-col gap-4">
      <div class="rounded-panel border border-accent/20 bg-accent/6 px-3 py-3">
        <p class="text-body font-emphasis text-label">{{ task.title }}</p>
        <p class="tnum mt-1 text-caption text-label-tertiary">
          #{{ task.id }} · Codex 会话已建立
        </p>
        <p v-if="task.branchName" class="mt-1 flex items-center gap-1 text-caption text-label-secondary">
          <Icon :as="GitBranch" size="inline" />
          <span class="break-all font-mono">{{ task.branchName }}</span>
        </p>
        <p class="mt-2 text-caption leading-5 text-label-secondary">
          新任务会沿用已有上下文，不会从头开始。为避免同一个会话被并发写入，
          来源任务完成、失败或终止后才能派发新任务。
        </p>
      </div>

      <DialogField label="新的开发内容" hint="只写这次要继续补充或修改的部分，不需要重抄来源任务">
        <Textarea
          v-model="prompt"
          :rows="9"
          :maxlength="12000"
          :disabled="saving"
          placeholder="例如：在现有实现上补齐权限校验、异常状态和对应测试"
          @paste="onPaste"
        />
        <p class="tnum mt-1 text-right text-caption text-label-tertiary">
          {{ prompt.length }} / 12000
        </p>
      </DialogField>

      <DialogField
        label="图片"
        hint="支持拖放与 Ctrl+V / ⌘V 粘贴；截图、设计稿会随新任务一起交给 Codex"
      >
        <div
          :class="[
            'rounded-panel border border-dashed p-2 transition-colors',
            dragging ? 'border-accent bg-accent/5' : 'border-separator',
          ]"
          @dragover.prevent="dragging = true"
          @dragleave="onDragLeave"
          @drop.prevent="onDrop"
        >
          <div class="flex flex-wrap items-start gap-2">
            <div v-for="item in pending" :key="item.localId" class="group relative size-20">
              <div class="size-full overflow-hidden rounded-panel border border-separator-soft bg-content">
                <img
                  :src="item.previewUrl"
                  :alt="item.file.name"
                  class="size-full object-cover"
                  :class="item.status === 'uploading' ? 'opacity-40' : ''"
                />
                <Progress
                  v-if="item.status === 'uploading'"
                  :value="item.progress"
                  class="absolute inset-x-0 bottom-0"
                />
                <span
                  v-if="item.status === 'failed'"
                  class="absolute inset-0 flex flex-col items-center justify-center gap-0.5 bg-danger/15 text-caption text-danger"
                  :title="item.error ?? undefined"
                >
                  <Icon :as="TriangleAlert" size="inline" />
                  没传上
                </span>
              </div>
              <button
                v-if="item.status !== 'uploading'"
                type="button"
                title="移除这张图"
                class="absolute -right-1.5 -top-1.5 hidden size-5 items-center justify-center rounded-full bg-label text-white shadow-control group-hover:flex focus-visible:flex focus-visible:outline-none"
                @click="removeImage(item)"
              >
                <Icon :as="X" size="inline" />
              </button>
            </div>

            <button
              type="button"
              class="flex size-20 flex-col items-center justify-center gap-1 rounded-panel border border-dashed border-separator text-label-secondary transition-colors duration-100 ease-apple hover:border-accent/60 hover:text-accent focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-accent/35"
              :disabled="saving"
              @click="fileInput?.click()"
            >
              <Icon :as="ImagePlus" size="large" />
              <span class="text-caption">添加图片</span>
            </button>

            <input
              ref="fileInput"
              type="file"
              :accept="ACCEPTED_IMAGE_TYPES.join(',')"
              multiple
              class="hidden"
              @change="onPick"
            />
          </div>
          <span v-if="pending.length > 0" class="tnum mt-1 block text-caption text-label-tertiary">
            已选 {{ pending.length }} 张 · {{ formatBytes(pendingBytes) }}
          </span>
        </div>
      </DialogField>

      <DialogField label="优先级" hint="数值越大越先派发">
        <div class="w-24">
          <Input v-model="priority" type="number" :disabled="saving" />
        </div>
      </DialogField>

      <p v-if="error" class="text-caption text-danger">{{ error }}</p>
    </div>

    <template #footer>
      <Button :disabled="saving" @click="open = false">取消</Button>
      <Button variant="primary" :disabled="!canSubmit" @click="submit">
        {{ saving ? '创建中…' : '创建后续任务' }}
      </Button>
    </template>
  </Dialog>
</template>
