<script setup lang="ts">
import { CalendarClock, ListChecks } from '@lucide/vue'
import { computed, ref, watch } from 'vue'

import { ApiError } from '@/api/http'
import { createPlan } from '@/api/plans'
import { listTasks } from '@/api/tasks'
import TaskStatusBadge from '@/components/business/TaskStatusBadge.vue'
import Button from '@/components/ui/button/Button.vue'
import Checkbox from '@/components/ui/checkbox/Checkbox.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import DialogField from '@/components/ui/dialog/DialogField.vue'
import EmptyState from '@/components/ui/empty-state/EmptyState.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import Input from '@/components/ui/input/Input.vue'
import { useToast } from '@/composables/useToast'
import type { PlanSummary, TaskSummary } from '@/types/api'

const emit = defineEmits<{ created: [plan: PlanSummary] }>()

const open = defineModel<boolean>('open', { default: false })
const toast = useToast()

const name = ref('')
const scheduledAt = ref('')
const tasks = ref<TaskSummary[]>([])
const selectedIds = ref<Set<number>>(new Set())
const loadingTasks = ref(false)
const saving = ref(false)
const error = ref<string | null>(null)

const selectableTasks = computed(() =>
  tasks.value.filter((task) => task.status === 'PENDING' || task.status === 'QUEUED'),
)

const scheduledTime = computed(() => {
  if (!scheduledAt.value) return null
  const value = new Date(scheduledAt.value)
  return Number.isNaN(value.getTime()) ? null : value
})

const scheduledValid = computed(
  () => scheduledTime.value !== null && scheduledTime.value.getTime() > Date.now(),
)

const canSubmit = computed(
  () =>
    name.value.trim().length > 0 &&
    selectedIds.value.size > 0 &&
    scheduledValid.value &&
    !saving.value,
)

function localDateTimeValue(date: Date): string {
  const offset = date.getTimezoneOffset() * 60_000
  return new Date(date.getTime() - offset).toISOString().slice(0, 16)
}

function defaultSchedule(): string {
  const next = new Date(Date.now() + 5 * 60_000)
  next.setSeconds(0, 0)
  return localDateTimeValue(next)
}

function messageOf(value: unknown): string {
  if (value instanceof ApiError) return value.message
  return '无法连接后端，请确认服务已启动（默认 127.0.0.1:8787）'
}

async function loadTasks() {
  loadingTasks.value = true
  try {
    const page = await listTasks({ size: 200 })
    tasks.value = page.items
  } catch (value) {
    error.value = messageOf(value)
    tasks.value = []
  } finally {
    loadingTasks.value = false
  }
}

function toggleTask(taskId: number, checked: boolean | 'indeterminate') {
  const next = new Set(selectedIds.value)
  if (checked === true) next.add(taskId)
  else next.delete(taskId)
  selectedIds.value = next
}

watch(open, (value) => {
  if (!value) return
  name.value = ''
  scheduledAt.value = defaultSchedule()
  selectedIds.value = new Set()
  error.value = null
  saving.value = false
  void loadTasks()
})

async function save() {
  if (!canSubmit.value || scheduledTime.value === null) return
  saving.value = true
  error.value = null
  try {
    const created = await createPlan({
      name: name.value.trim(),
      taskIds: [...selectedIds.value],
      scheduledAt: scheduledTime.value.toISOString(),
    })
    open.value = false
    emit('created', created)
    toast.success(
      `计划「${created.name}」已创建`,
      `已纳管 ${created.taskCount} 个任务，到点后自动执行`,
    )
  } catch (value) {
    error.value = messageOf(value)
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
    title="新建计划"
    description="把待执行或排队中的任务组成一次定时批次，执行结束后生成总结。"
    :icon="CalendarClock"
  >
    <div class="flex flex-col gap-3">
      <DialogField label="名称" hint="用于执行历史与总结标题">
        <Input v-model="name" placeholder="例如：周五发布检查" />
      </DialogField>

      <DialogField label="执行时间" hint="到点后由平台自动逐项派发">
        <Input
          v-model="scheduledAt"
          type="datetime-local"
          :invalid="scheduledAt.length > 0 && !scheduledValid"
        />
        <p v-if="scheduledAt.length > 0 && !scheduledValid" class="mt-1 text-caption text-danger">
          执行时间必须晚于当前时间
        </p>
      </DialogField>

      <DialogField label="任务" hint="只显示当前可派发的待执行与排队任务">
        <div class="overflow-hidden rounded-panel border border-separator-soft">
          <div class="flex items-center gap-2 border-b border-separator-soft bg-window px-3 py-2">
            <Icon :as="ListChecks" size="inline" class="text-label-secondary" />
            <span class="text-caption text-label-secondary">
              已选 {{ selectedIds.size }} / 可选 {{ selectableTasks.length }}
            </span>
          </div>
          <div class="max-h-64 overflow-y-auto">
            <EmptyState
              v-if="loadingTasks"
              title="正在读取任务…"
              description="从后端加载当前可纳入计划的任务"
            />
            <EmptyState
              v-else-if="selectableTasks.length === 0"
              title="没有可纳管的任务"
              description="先创建任务，或在任务页重试失败任务后再回来。"
            />
            <template v-else>
              <div
                v-for="task in selectableTasks"
                :key="task.id"
                class="flex cursor-default items-center gap-2 border-b border-separator-soft px-3 py-2 last:border-b-0 hover:bg-hover"
              >
                <Checkbox
                  :model-value="selectedIds.has(task.id)"
                  :aria-label="`选择任务 ${task.id}`"
                  @update:model-value="toggleTask(task.id, $event)"
                />
                <span class="tnum w-10 shrink-0 text-caption text-label-tertiary">#{{ task.id }}</span>
                <span class="min-w-0 flex-1 truncate text-caption text-label">{{ task.title }}</span>
                <span class="hidden max-w-28 truncate text-caption text-label-tertiary sm:block">
                  {{ task.projectName }}
                </span>
                <TaskStatusBadge :status="task.status" />
              </div>
            </template>
          </div>
        </div>
      </DialogField>

      <p v-if="error" class="text-caption text-danger">{{ error }}</p>
    </div>

    <template #footer>
      <Button @click="open = false">取消</Button>
      <Button variant="primary" :disabled="!canSubmit" @click="save">
        {{ saving ? '创建中…' : '创建计划' }}
      </Button>
    </template>
  </Dialog>
</template>
