<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { ApiError } from '@/api/http'
import { addTaskAdjustment, listTasks } from '@/api/tasks'
import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import DialogField from '@/components/ui/dialog/DialogField.vue'
import SegmentedControl from '@/components/ui/segmented-control/SegmentedControl.vue'
import Select from '@/components/ui/select/Select.vue'
import Textarea from '@/components/ui/input/Textarea.vue'
import type { TaskAdjustment, TaskDetail, TaskSummary } from '@/types/api'

const props = defineProps<{
  task: TaskDetail | null
}>()

const emit = defineEmits<{
  created: [adjustment: TaskAdjustment]
}>()

const open = defineModel<boolean>('open', { default: false })
const sourceMode = ref<'TASK' | 'TEMPORARY'>('TEMPORARY')
const sourceTaskId = ref<number | null>(null)
const content = ref('')
const candidates = ref<TaskSummary[]>([])
const loading = ref(false)
const saving = ref(false)
const error = ref<string | null>(null)

const modeItems = [
  { value: 'TEMPORARY', label: '临时补充' },
  { value: 'TASK', label: '选择任务' },
]

const sourceTaskItems = computed(() =>
  candidates.value
    .filter((candidate) => candidate.id !== props.task?.id)
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
  content.value = ''
  error.value = null
  saving.value = false
  await loadCandidates(props.task.projectId)
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
        <Select
          v-model="sourceTaskValue"
          :items="sourceTaskItems"
          :disabled="loading || saving"
          :placeholder="loading ? '正在读取任务…' : '选择一条任务'"
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
