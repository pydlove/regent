<script setup lang="ts">
import { ImagePlus, TriangleAlert, X } from '@lucide/vue'
import { computed, ref, watch } from 'vue'

import { ACCEPTED_IMAGE_TYPES, uploadBugAttachment } from '@/api/attachments'
import { ApiError } from '@/api/http'
import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import DialogField from '@/components/ui/dialog/DialogField.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import Input from '@/components/ui/input/Input.vue'
import Textarea from '@/components/ui/input/Textarea.vue'
import Progress from '@/components/ui/progress/Progress.vue'
import Select from '@/components/ui/select/Select.vue'
import { usePendingImages } from '@/composables/usePendingImages'
import { useToast } from '@/composables/useToast'
import { useBugStore } from '@/stores/useBugStore'
import { useTaskStore } from '@/stores/useTaskStore'
import type { BugSeverity } from '@/types/bug'
import { formatBytes } from '@/utils/format'

const emit = defineEmits<{
  created: [{ id: number; title: string }]
  uploaded: [{ id: number }]
}>()

const open = defineModel<boolean>('open', { default: false })
const bugStore = useBugStore()
const taskStore = useTaskStore()
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

const projectId = ref<string>()
const title = ref('')
const severity = ref<BugSeverity>('MINOR')
const reproduction = ref('')
const filePath = ref('')
const detail = ref('')
const error = ref<string | null>(null)

type Phase = 'editing' | 'creating' | 'created'
const phase = ref<Phase>('editing')
const createdBugId = ref<number | null>(null)

const projectItems = computed(() =>
  taskStore.projects.map((project) => ({
    value: String(project.id),
    label: project.enabled ? `${project.name}（${project.code}）` : `${project.name}（已暂停）`,
  })),
)

const severityItems = [
  { value: 'BLOCKER', label: '阻塞：主流程不可用' },
  { value: 'MAJOR', label: '严重：核心功能受损' },
  { value: 'MINOR', label: '一般：有替代路径' },
  { value: 'TRIVIAL', label: '轻微：不影响使用' },
]

const canSubmit = computed(
  () =>
    phase.value === 'editing' &&
    projectId.value !== undefined &&
    title.value.trim().length > 0,
)

const uploadLabel = computed(() => {
  if (createdBugId.value === null) return '录入中…'
  if (pending.value.length === 0) return '处理中…'
  const finished = pending.value.filter(
    (item) => item.status === 'done' || item.status === 'failed',
  ).length
  return `上传图片 ${Math.min(finished + 1, pending.value.length)}/${pending.value.length}…`
})

function messageOf(e: unknown): string {
  if (e instanceof ApiError) return e.message
  return '无法连接后端，请确认服务已启动（默认 127.0.0.1:8787）'
}

watch(open, async (value) => {
  if (!value) return
  projectId.value =
    taskStore.projectId === null ? undefined : String(taskStore.projectId)
  title.value = ''
  severity.value = 'MINOR'
  reproduction.value = ''
  filePath.value = ''
  detail.value = ''
  error.value = null
  phase.value = 'editing'
  createdBugId.value = null
  resetImages()
  try {
    await taskStore.loadProjects()
    if (projectId.value === undefined && taskStore.projects.length === 1) {
      projectId.value = String(taskStore.projects[0].id)
    }
  } catch (e) {
    error.value = messageOf(e)
  }
})

const close = () => {
  open.value = false
  phase.value = 'editing'
  createdBugId.value = null
  resetImages()
}

const uploadAttached = async (bugId: number) => {
  const uploaded = await uploadPending((file, onProgress) =>
    uploadBugAttachment(bugId, file, { onProgress }),
  )
  if (uploaded > 0) emit('uploaded', { id: bugId })
  return uploaded
}

const submit = async () => {
  if (!canSubmit.value || projectId.value === undefined) return
  error.value = null
  phase.value = 'creating'
  try {
    const bug = await bugStore.createManual({
      projectId: Number(projectId.value),
      title: title.value.trim(),
      severity: severity.value,
      reproduction: reproduction.value.trim(),
      filePath: filePath.value.trim(),
      detail: detail.value.trim(),
    })
    createdBugId.value = bug.id
    emit('created', { id: bug.id, title: bug.title })

    const attached = await uploadAttached(bug.id)
    if (failed.value.length === 0) {
      const total = pending.value.length
      close()
      toast.success(
        `Bug #${bug.id} 已录入`,
        total > 0 ? `${attached} 张图片已附上` : '已进入待处理列表',
      )
      return
    }

    phase.value = 'created'
    toast.warning(
      `${failed.value.length} 张图没传上去`,
      'Bug 已创建，图片可以重试或稍后在详情里补',
    )
  } catch (e) {
    phase.value = 'editing'
    error.value = messageOf(e)
  }
}

const retry = async () => {
  if (phase.value !== 'created' || createdBugId.value === null) return
  phase.value = 'creating'
  await uploadAttached(createdBugId.value)
  if (failed.value.length === 0) {
    const id = createdBugId.value
    const total = pending.value.length
    close()
    toast.success(`${total} 张图片已附上`, `Bug #${id} 的复现证据已保存`)
    return
  }
  phase.value = 'created'
}

const dismiss = () => {
  if (failed.value.length > 0 && createdBugId.value !== null) {
    toast.warning(
      `${failed.value.length} 张图没传上去`,
      'Bug 已经创建，图片可以在右侧详情里补',
    )
  }
  close()
}
</script>

<template>
  <Dialog
    v-model:open="open"
    layout="form"
    size="large"
    title="录入 Bug"
    description="人工发现的缺陷。执行器也能在任务过程中自动上报，两者会进入同一张列表。"
  >
    <div
      class="max-h-[calc(100vh-13rem)] overflow-y-auto overscroll-contain pr-1"
      @paste="onPaste"
      @dragover.prevent="dragging = true"
      @dragleave="onDragLeave"
      @drop.prevent="onDrop"
    >
      <div
        class="flex flex-col gap-3"
        :class="dragging ? 'rounded-panel ring-2 ring-accent/50' : ''"
      >
        <DialogField label="项目" required>
          <Select v-model="projectId" :items="projectItems" placeholder="选择项目" block />
        </DialogField>
        <DialogField label="缺陷" required>
          <Input v-model="title" placeholder="一句话说明哪里坏了" class="w-full" />
        </DialogField>
        <DialogField label="严重级" required>
          <Select v-model="severity" :items="severityItems" block />
        </DialogField>
        <DialogField
          label="复现步骤"
          hint="可以直接按 ⌘V / Ctrl+V 粘贴截图，图片会附在这张 Bug 单上"
        >
          <Textarea
            v-model="reproduction"
            :rows="4"
            placeholder="怎么触发、期望什么、实际发生了什么；不知道可以留空"
          />
        </DialogField>

        <DialogField
          label="图片"
          hint="支持选择、拖放与 ⌘V / Ctrl+V 粘贴；PNG / JPEG / GIF / WebP，单张上限 10.0 MB"
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
              :disabled="phase !== 'editing'"
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

          <span
            v-if="pending.length > 0"
            class="tnum mt-1 block text-caption text-label-tertiary"
          >
            已选 {{ pending.length }} 张 · {{ formatBytes(pendingBytes) }}
          </span>
        </DialogField>

        <DialogField label="文件">
          <Input v-model="filePath" placeholder="src/...，没有定位到可以留空" class="w-full" />
        </DialogField>
        <DialogField label="补充说明">
          <Textarea v-model="detail" :rows="3" placeholder="影响范围、初步判断或相关上下文" />
        </DialogField>

        <p
          v-if="phase === 'created'"
          class="rounded-panel bg-warning/10 px-3 py-2 text-caption text-label-secondary"
        >
          Bug #{{ createdBugId }} 已经录入，没传上去的图片可以点「重试」；
          也可以先关掉，之后在右侧详情里补。
        </p>

        <p v-if="error" class="text-caption text-danger">{{ error }}</p>
      </div>
    </div>

    <template #footer>
      <Button v-if="phase === 'editing'" @click="close">取消</Button>
      <Button v-else-if="phase === 'created'" @click="dismiss">关闭</Button>

      <Button v-if="phase === 'editing'" variant="primary" :disabled="!canSubmit" @click="submit">
        录入
      </Button>
      <Button v-else-if="phase === 'creating'" variant="primary" disabled>
        {{ uploadLabel }}
      </Button>
      <Button v-else variant="primary" @click="retry">
        重试失败的 {{ failed.length }} 张
      </Button>
    </template>
  </Dialog>
</template>
