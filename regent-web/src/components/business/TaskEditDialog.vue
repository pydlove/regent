<script setup lang="ts">
import { ImagePlus, TriangleAlert, X } from '@lucide/vue'
import { computed, ref, watch } from 'vue'

import { ACCEPTED_IMAGE_TYPES, uploadTaskAttachment } from '@/api/attachments'
import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import DialogField from '@/components/ui/dialog/DialogField.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import Input from '@/components/ui/input/Input.vue'
import Textarea from '@/components/ui/input/Textarea.vue'
import Progress from '@/components/ui/progress/Progress.vue'
import ImageAttachmentGrid from '@/components/business/ImageAttachmentGrid.vue'
import { usePendingImages } from '@/composables/usePendingImages'
import { useToast } from '@/composables/useToast'
import { useTaskStore } from '@/stores/useTaskStore'
import type { ImageAttachment, TaskAttachment, TaskDetail } from '@/types/api'
import { formatBytes } from '@/utils/format'

const props = withDefaults(
  defineProps<{
    task: TaskDetail | null
    attachments?: TaskAttachment[]
  }>(),
  { attachments: () => [] },
)

const emit = defineEmits<{
  uploaded: []
  preview: [attachment: ImageAttachment]
}>()

const open = defineModel<boolean>('open', { default: false })
const store = useTaskStore()
const toast = useToast()
const {
  pending,
  dragging,
  fileInput,
  failed,
  pendingBytes,
  onPick,
  onPaste,
  onDrop,
  onDragLeave,
  removeImage,
  reset: resetImages,
  uploadAll: uploadPending,
} = usePendingImages()

const prompt = ref('')
const priority = ref('0')
const error = ref<string | null>(null)
const saving = ref(false)

watch(open, (value) => {
  if (!value) {
    resetImages()
    return
  }
  if (!props.task) return
  prompt.value = props.task.prompt
  priority.value = String(props.task.priority)
  error.value = null
  saving.value = false
  resetImages()
})

const changed = computed(
  () =>
    props.task !== null &&
    (prompt.value.trim() !== props.task.prompt ||
      Number(priority.value) !== props.task.priority ||
      pending.value.length > 0),
)

const canSave = computed(
  () => !saving.value && prompt.value.trim().length > 0 && changed.value,
)

const textChanged = (task: TaskDetail) =>
  prompt.value.trim() !== task.prompt || Number(priority.value) !== task.priority

const save = async () => {
  const task = props.task
  if (!task || !canSave.value) return

  saving.value = true
  error.value = null

  if (textChanged(task)) {
    const failure = await store.edit(task.id, {
      prompt: prompt.value.trim(),
      priority: Number(priority.value) || 0,
    })
    if (failure) {
      error.value = failure
      saving.value = false
      return
    }
  }

  const uploaded = await uploadPending((file, onProgress) =>
    uploadTaskAttachment(task.id, file, { onProgress }),
  )
  if (uploaded > 0) emit('uploaded')

  if (failed.value.length > 0) {
    error.value = `${failed.value.length} 张图片没传上去，请重试`
    saving.value = false
    return
  }

  open.value = false
  saving.value = false
  toast.success(
    `#${task.id} 已更新`,
    uploaded > 0 ? `${uploaded} 张图片已附上，下次执行会一并交给 codex` : '标题已按新的指令首行重新生成',
  )
}
</script>

<template>
  <Dialog
    v-model:open="open"
    layout="form"
    size="large"
    title="编辑任务"
    :description="task ? `#${task.id} 的指令、图片与优先级` : ''"
  >
    <div
      class="flex flex-col gap-3"
      :class="dragging ? 'rounded-panel ring-2 ring-accent/50' : ''"
      @paste="onPaste"
      @dragover.prevent="dragging = true"
      @dragleave="onDragLeave"
      @drop.prevent="onDrop"
    >
      <DialogField label="指令" hint="第一行会被拿去当任务标题，修改后会同步更新">
        <Textarea
          v-model="prompt"
          :rows="10"
          :disabled="saving"
          placeholder="交给执行器的完整指令：要改哪里、验收标准是什么"
        />
      </DialogField>

      <DialogField
        label="图片"
        hint="支持选择、拖放与 ⌘V / Ctrl+V 粘贴；新增图片会在下次执行时交给 codex"
      >
        <div v-if="attachments.length > 0" class="mb-2">
          <p class="mb-1 tnum text-caption text-label-tertiary">已有 {{ attachments.length }} 张</p>
          <ImageAttachmentGrid :attachments="attachments" size="sm" @select="emit('preview', $event)" />
        </div>

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
      </DialogField>

      <DialogField label="优先级" hint="数值越大越先派发">
        <div class="w-20">
          <Input v-model="priority" type="number" :disabled="saving" />
        </div>
      </DialogField>

      <p v-if="error" class="text-caption text-danger">{{ error }}</p>
    </div>

    <template #footer>
      <Button :disabled="saving" @click="open = false">取消</Button>
      <Button variant="primary" :disabled="!canSave" @click="save">
        {{ saving ? '保存中…' : '保存修改' }}
      </Button>
    </template>
  </Dialog>
</template>
