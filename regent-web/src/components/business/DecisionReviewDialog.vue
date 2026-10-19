<script setup lang="ts">
import { AlertTriangle, Check, ImagePlus, TriangleAlert, X } from '@lucide/vue'
import { computed, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'

import { ACCEPTED_IMAGE_TYPES, listTaskAttachments, uploadTaskAttachment } from '@/api/attachments'
import DecisionLevelBadge from '@/components/business/DecisionLevelBadge.vue'
import ImageAttachmentGrid from '@/components/business/ImageAttachmentGrid.vue'
import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import Textarea from '@/components/ui/input/Textarea.vue'
import Progress from '@/components/ui/progress/Progress.vue'
import { usePendingImages } from '@/composables/usePendingImages'
import type { DecisionSummary, ImageAttachment, TaskAttachment } from '@/types/api'
import { formatBytes, formatRelative } from '@/utils/format'

/*
 * 审批对话框。查看、批准、驳回共用同一个组件：
 *   查看 —— 展示判断所需的完整事实；待批记录可直接切换到批准或驳回
 *   批准 —— 可补充建议与图片，确认后放行任务继续处理
 *   驳回 —— 可补充理由，理由会随决策留痕
 *
 * 三种模式都只读传进来的 decision，调用点只管 open / decision / mode 三个东西。
 */
const props = withDefaults(
  defineProps<{
    decision: DecisionSummary | null
    mode: 'view' | 'approve' | 'reject'
  }>(),
  { decision: null, mode: 'view' },
)

const emit = defineEmits<{
  done: [payload: { mode: 'approve' | 'reject'; note: string }]
  review: [mode: 'approve' | 'reject']
  uploaded: [payload: { taskId: number }]
  preview: [attachment: ImageAttachment]
}>()

const open = defineModel<boolean>('open', { default: false })

const note = ref('')
const approvalError = ref<string | null>(null)
const reviewing = ref(false)
const existing = ref<TaskAttachment[]>([])
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

// 打开、切换审批动作或切换决策时都重置，避免把上一条的理由与图片带进下一条。
watch([open, () => props.mode, () => props.decision?.taskId], async ([value, mode, taskId]) => {
  if (!value) {
    resetImages()
    return
  }
  note.value = ''
  approvalError.value = null
  reviewing.value = false
  existing.value = []
  resetImages()
  if (mode !== 'approve' || taskId === undefined) return
  try {
    const loaded = await listTaskAttachments(taskId)
    if (open.value && props.mode === 'approve' && props.decision?.taskId === taskId) {
      existing.value = loaded
    }
  } catch {
    // 已有附件不影响审批本身；新图上传失败会单独报错，不把入口整个堵住
  }
})

const canApprove = computed(() => !reviewing.value)
const isPending = computed(() => props.decision?.status === 'PENDING')

const title = computed(() => {
  if (props.mode === 'approve') return '批准并继续？'
  if (props.mode === 'reject') return '驳回并终止这条任务'
  return props.decision ? `决策 #${props.decision.id}` : '决策详情'
})

const description = computed(() => {
  if (props.mode === 'approve') {
    return '批准后任务会继续处理；如果已有执行会话，执行器会带着当前上下文从断点续跑。'
  }
  if (props.mode === 'reject') return '任务会终止；可以补充理由，填写后会写入决策留痕。'
  if (!isPending.value) return '这项决策已经处理，下面是当时的判断依据与处理结果。'
  return '先核对完整问题和影响范围，再决定是否放行。'
})

const resolvedByText = (by: string | null) => (by === 'AI' ? 'AI' : '人工')

const outcomeText = (status: DecisionSummary['status']) => {
  if (status === 'APPROVED') return '已批准'
  if (status === 'REJECTED') return '已驳回'
  if (status === 'AUTO_RESOLVED') return 'AI 自主决断'
  return '待批准'
}

const confirm = async () => {
  const decision = props.decision
  const mode = props.mode
  if (!decision || mode === 'view') return
  if (mode === 'approve' && !canApprove.value) return

  approvalError.value = null
  if (mode === 'approve') {
    reviewing.value = true
    const uploaded = await uploadPending((file, onProgress) =>
      uploadTaskAttachment(decision.taskId, file, { onProgress }),
    )
    if (!open.value || props.decision?.id !== decision.id || props.mode !== mode) {
      reviewing.value = false
      return
    }
    if (uploaded > 0) emit('uploaded', { taskId: decision.taskId })
    if (failed.value.length > 0) {
      approvalError.value = `${failed.value.length} 张图片没传上去，请重试后再批准`
      reviewing.value = false
      return
    }
  }

  // 图片先落库再批准：批准后调度器可能立刻续跑，晚到的附件赶不上这一轮。
  open.value = false
  emit('done', { mode, note: note.value.trim() })
}
</script>

<template>
  <Dialog
    v-model:open="open"
    layout="log"
    :icon="mode === 'approve' ? Check : AlertTriangle"
    :icon-tone="mode === 'reject' ? 'danger' : 'default'"
    :title="title"
    :description="description"
  >
    <template v-if="decision">
      <div class="flex flex-wrap items-center gap-2">
        <span class="tnum text-caption text-label-tertiary">决策 #{{ decision.id }}</span>
        <RouterLink
          :to="{ name: 'tasks', query: { taskId: String(decision.taskId) } }"
          class="tnum rounded-sm text-accent transition-colors duration-100 ease-apple hover:underline hover:underline-offset-2"
          :aria-label="`在任务页查看任务 #${decision.taskId}`"
        >
          #{{ decision.taskId }}
        </RouterLink>
        <DecisionLevelBadge :level="decision.effectiveLevel" />
        <span v-if="decision.levelEscalated" class="text-caption text-label-tertiary">
          AI 自评 {{ decision.aiLevel }}
        </span>
        <span class="text-caption text-label-tertiary">{{ decision.projectName }}</span>
        <span class="tnum ml-auto text-caption text-label-tertiary">
          {{ formatRelative(decision.raisedAt) }}提出
        </span>
      </div>

      <div class="mt-3 rounded-panel border border-separator-soft bg-content p-3">
        <p class="text-caption text-label-tertiary">任务</p>
        <p class="mt-1 text-body font-emphasis text-label">{{ decision.taskTitle }}</p>

        <div class="mt-4 border-t border-separator-soft pt-3">
          <p class="text-caption font-emphasis text-label-secondary">待决问题</p>
          <p class="mt-1 whitespace-pre-wrap break-words text-body text-label">
            {{ decision.question }}
          </p>
        </div>

        <div class="mt-4 border-t border-separator-soft pt-3">
          <p class="text-caption font-emphasis text-label-secondary">候选方案</p>
          <ol v-if="decision.options.length > 0" class="mt-1.5 space-y-1.5">
            <li
              v-for="(option, index) in decision.options"
              :key="`${index}-${option}`"
              class="flex gap-2 text-caption text-label"
            >
              <span class="tnum shrink-0 text-label-tertiary">{{ index + 1 }}.</span>
              <span class="break-words">{{ option }}</span>
            </li>
          </ol>
          <p v-else class="mt-1 text-caption text-label-tertiary">执行器未提供候选方案。</p>
        </div>

        <div class="mt-4 border-t border-separator-soft pt-3">
          <p class="text-caption font-emphasis text-label-secondary">AI 建议</p>
          <p class="mt-1 whitespace-pre-wrap break-words text-caption text-label">
            {{ decision.recommendation ?? '执行器未提供建议。' }}
          </p>
        </div>

        <div class="mt-4 border-t border-separator-soft pt-3">
          <p class="text-caption font-emphasis text-label-secondary">影响判断</p>
          <p class="mt-1 text-caption text-label">
            {{ decision.reversible ? '可逆改动' : '不可逆改动' }}
            <span v-if="decision.blastRadius"> · {{ decision.blastRadius }}</span>
          </p>
        </div>

        <div class="mt-4 border-t border-separator-soft pt-3">
          <p class="text-caption font-emphasis text-label-secondary">分级依据</p>
          <p class="mt-1 text-caption text-label">
            命中规则「{{ decision.matchedRuleName ?? '未命中规则' }}」
            <template v-if="decision.levelEscalated">
              · AI 自评 {{ decision.aiLevel }}，最终按 {{ decision.effectiveLevel }} 处理
            </template>
          </p>
        </div>

        <div
          v-if="decision.status !== 'PENDING'"
          class="mt-4 border-t border-separator-soft pt-3"
        >
          <p class="text-caption font-emphasis text-label-secondary">处理结果</p>
          <p class="mt-1 text-caption text-label">
            {{ outcomeText(decision.status) }} · {{ resolvedByText(decision.resolvedBy) }}
            <template v-if="decision.resolvedAt">
              · {{ formatRelative(decision.resolvedAt) }}
            </template>
          </p>
          <p v-if="decision.resolvedChoice" class="mt-1 text-caption text-label-secondary">
            选择：{{ decision.resolvedChoice }}
          </p>
          <p class="mt-1 whitespace-pre-wrap break-words text-caption text-label-secondary">
            {{ decision.resolutionNote ?? '没有留下说明。' }}
          </p>
        </div>
      </div>

      <div
        v-if="mode === 'approve' || mode === 'reject'"
        class="mt-3"
        @paste="mode === 'approve' ? onPaste : undefined"
      >
        <p class="text-caption font-emphasis text-label-secondary">
          {{ mode === 'approve' ? '补充建议（可选）' : '驳回理由（可选）' }}
        </p>
        <Textarea
          v-model="note"
          class="mt-1"
          :rows="3"
          :placeholder="
            mode === 'approve'
              ? '例如：按第一个方案执行，但保留旧索引'
              : '例如：这个改动会影响线上数据，必须人工确认后再执行'
          "
        />
      </div>

      <div
        v-if="mode === 'approve'"
        class="mt-3 rounded-panel border border-separator-soft p-3"
        :class="dragging ? 'ring-2 ring-accent/50' : ''"
        @paste="onPaste"
        @dragover.prevent="dragging = true"
        @dragleave="onDragLeave"
        @drop.prevent="onDrop"
      >
        <div class="flex items-center gap-2">
          <p class="text-caption font-emphasis text-label-secondary">补充图片</p>
          <span v-if="existing.length + pending.length > 0" class="tnum text-caption text-label-tertiary">
            {{ existing.length + pending.length }} 张
          </span>
          <span v-if="pendingBytes > 0" class="tnum ml-auto text-caption text-label-tertiary">
            {{ formatBytes(pendingBytes) }}
          </span>
        </div>

        <ImageAttachmentGrid
          v-if="existing.length > 0"
          :attachments="existing"
          size="sm"
          class="mt-2"
          @select="emit('preview', $event)"
        />

        <div class="mt-2 flex flex-wrap items-start gap-2">
          <div v-for="item in pending" :key="item.localId" class="group relative size-16">
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
            title="添加图片"
            class="flex size-16 items-center justify-center rounded-panel border border-dashed border-separator text-label-secondary transition-colors duration-100 ease-apple hover:border-accent/60 hover:text-accent focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-accent/35"
            :disabled="reviewing"
            @click="fileInput?.click()"
          >
            <Icon :as="ImagePlus" size="large" />
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
      </div>

      <p v-if="approvalError" class="mt-1.5 text-caption text-danger">{{ approvalError }}</p>
    </template>
    <div v-else class="py-8 text-center">
      <p class="text-body text-label">决策详情尚未加载</p>
      <p class="mt-1 text-caption text-label-secondary">关闭弹框后重新打开这条决策。</p>
    </div>

    <template #footer>
      <Button :disabled="reviewing" @click="open = false">
        {{ mode === 'view' ? '关闭' : '取消' }}
      </Button>
      <template v-if="decision && mode === 'view' && isPending">
        <Button @click="emit('review', 'reject')">驳回</Button>
        <Button variant="primary" @click="emit('review', 'approve')">批准并继续</Button>
      </template>
      <Button
        v-if="decision && mode === 'approve'"
        variant="primary"
        :disabled="!canApprove"
        @click="confirm"
      >
        {{ reviewing ? '上传中…' : '批准并继续' }}
      </Button>
      <Button
        v-else-if="decision && mode === 'reject'"
        variant="danger"
        @click="confirm"
      >
        确认驳回
      </Button>
    </template>
  </Dialog>
</template>
