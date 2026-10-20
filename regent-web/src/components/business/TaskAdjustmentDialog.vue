<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { ApiError } from '@/api/http'
import { addTaskAdjustment, getTask, listTasks } from '@/api/tasks'
import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import DialogField from '@/components/ui/dialog/DialogField.vue'
import Input from '@/components/ui/input/Input.vue'
import SegmentedControl from '@/components/ui/segmented-control/SegmentedControl.vue'
import Select from '@/components/ui/select/Select.vue'
import Textarea from '@/components/ui/input/Textarea.vue'
import type { TaskAdjustment, TaskDetail, TaskSummary } from '@/types/api'
import { filterAdjustmentSourceTasks } from '@/utils/taskAdjustment'

const props = defineProps<{
  task: TaskDetail | null
}>()

const emit = defineEmits<{
  created: [adjustment: TaskAdjustment]
}>()

const open = defineModel<boolean>('open', { default: false })
const sourceMode = ref<'TASK' | 'TEMPORARY'>('TEMPORARY')
const sourceTaskId = ref<number | null>(null)
const sourceKeyword = ref('')
const content = ref('')
const candidates = ref<TaskSummary[]>([])
const sourceDetail = ref<TaskDetail | null>(null)
const sourceLoading = ref(false)
const sourceError = ref<string | null>(null)
const loading = ref(false)
const saving = ref(false)
const error = ref<string | null>(null)

const modeItems = [
  { value: 'TEMPORARY', label: '临时补充' },
  { value: 'TASK', label: '选择任务' },
]

const sourceTaskItems = computed(() =>
  filterAdjustmentSourceTasks(candidates.value, props.task?.id, sourceKeyword.value)
    .map((candidate) => ({
      value: String(candidate.id),
      label: `#${candidate.id} ${candidate.title}`,
    })),
)

const sourceTaskValue = computed({
  get: () => (sourceTaskId.value === null ? undefined : String(sourceTaskId.value)),
  set: (value: string | undefined) => {
    sourceTaskId.value = value === undefined ? null : Number(value)
  },
})

const selectedSource = computed(
  () => candidates.value.find((candidate) => candidate.id === sourceTaskId.value) ?? null,
)

const canSubmit = computed(
  () =>
    !saving.value &&
    !loading.value &&
    (sourceMode.value === 'TASK'
      ? sourceTaskId.value !== null
      : content.value.trim().length > 0),
)

const messageOf = (cause: unknown) =>
  cause instanceof ApiError ? cause.message : '调整方向没有提交成功，请稍后重试'

const loadCandidates = async (projectId: number) => {
  loading.value = true
  try {
    const page = await listTasks({ projectId, page: 1, size: 100 })
    candidates.value = page.items
  } catch (cause) {
    candidates.value = []
    error.value = messageOf(cause)
  } finally {
    loading.value = false
  }
}

watch(open, async (value) => {
  if (!value || !props.task) return
  sourceMode.value = 'TEMPORARY'
  sourceTaskId.value = null
  sourceKeyword.value = ''
  sourceDetail.value = null
  sourceLoading.value = false
  sourceError.value = null
  content.value = ''
  error.value = null
  saving.value = false
  await loadCandidates(props.task.projectId)
})

watch(sourceTaskId, async (taskId) => {
  sourceDetail.value = null
  sourceError.value = null
  if (taskId === null) {
    sourceLoading.value = false
    return
  }

  sourceLoading.value = true
  try {
    const detail = await getTask(taskId)
    if (sourceTaskId.value === taskId) sourceDetail.value = detail
  } catch (cause) {
    if (sourceTaskId.value === taskId) {
      sourceError.value = messageOf(cause)
    }
  } finally {
    if (sourceTaskId.value === taskId) sourceLoading.value = false
  }
})

const submit = async () => {
  const task = props.task
  if (!task || !canSubmit.value) return

  saving.value = true
  error.value = null
  try {
    const adjustment =
      sourceMode.value === 'TASK'
        ? await addTaskAdjustment(task.id, { sourceTaskId: sourceTaskId.value! })
        : await addTaskAdjustment(task.id, { content: content.value.trim() })
    emit('created', adjustment)
    open.value = false
  } catch (cause) {
    error.value = messageOf(cause)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <Dialog
    v-model:open="open"
    layout="form"
    size="large"
    title="增加调整方向"
    :description="task ? `#${task.id} · 新方向会随下一轮执行交给执行器` : ''"
  >
    <div class="flex flex-col gap-4">
      <DialogField label="来源">
        <SegmentedControl v-model="sourceMode" :items="modeItems" size="sm" />
      </DialogField>

      <DialogField
        v-if="sourceMode === 'TASK'"
        label="任务"
        hint="只列出当前项目的任务；提交的是任务指令快照，之后编辑来源任务不会改写这条调整"
      >
        <Input
          v-model="sourceKeyword"
          :disabled="loading || saving"
          placeholder="按任务号或标题搜索"
          class="w-full"
        />
        <Select
          v-model="sourceTaskValue"
          :items="sourceTaskItems"
          :disabled="loading || saving"
          :placeholder="
            loading
              ? '正在读取任务…'
              : sourceKeyword.trim() && sourceTaskItems.length === 0
                ? '没有匹配的任务'
                : '选择一条任务'
          "
          class="mt-2"
          block
        />
        <div
          v-if="selectedSource"
          class="mt-2 rounded-panel border border-separator-soft bg-content px-3 py-2"
        >
          <p class="text-caption font-emphasis text-label">{{ selectedSource.title }}</p>
          <p class="tnum mt-0.5 text-caption text-label-tertiary">
            #{{ selectedSource.id }} · {{ selectedSource.projectName }}
          </p>
          <p v-if="sourceLoading" class="mt-2 text-caption text-label-tertiary">
            正在读取来源任务指令…
          </p>
          <p v-else-if="sourceError" class="mt-2 text-caption text-danger">
            {{ sourceError }}
          </p>
          <p
            v-else-if="sourceDetail"
            class="mt-2 line-clamp-4 whitespace-pre-wrap text-caption text-label-secondary"
          >
            {{ sourceDetail.prompt }}
          </p>
        </div>
      </DialogField>

      <DialogField
        v-else
        label="补充"
        hint="写清新增方向、边界和验收标准；不需要重抄原任务"
      >
        <Textarea
          v-model="content"
          :rows="9"
          :maxlength="12000"
          :disabled="saving"
          placeholder="例如：保留现有接口，只补移动端布局和空状态；同时补对应测试"
        />
        <p class="tnum mt-1 text-right text-caption text-label-tertiary">
          {{ content.length }} / 12000
        </p>
      </DialogField>

      <p v-if="error" class="text-caption text-danger">{{ error }}</p>
    </div>

    <template #footer>
      <Button :disabled="saving" @click="open = false">取消</Button>
      <Button variant="primary" :disabled="!canSubmit" @click="submit">
        {{ saving ? '提交中…' : '加入调整' }}
      </Button>
    </template>
  </Dialog>
</template>
