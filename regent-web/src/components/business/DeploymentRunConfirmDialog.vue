<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { getProjectVersion } from '@/api/projects'
import { listTasks } from '@/api/tasks'
import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import Select from '@/components/ui/select/Select.vue'
import type { DeploymentSummary, ProjectVersion, TaskSummary } from '@/types/api'

const props = withDefaults(
  defineProps<{
    deployment: DeploymentSummary | null
    starting?: boolean
    defaultTaskId?: number | null
  }>(),
  { starting: false, defaultTaskId: null },
)
const emit = defineEmits<{ confirm: [payload: { projectId: number; taskId: number | null }] }>()
const open = defineModel<boolean>('open', { default: false })
const projectId = ref('')
const taskId = ref('')
const tasks = ref<TaskSummary[]>([])
const projectVersion = ref<ProjectVersion | null>(null)
const versionLoading = ref(false)
const projectItems = computed(() =>
  (props.deployment?.projectIds ?? []).map((id, index) => ({
    value: String(id),
    label: props.deployment?.projectNames[index] ?? `项目 #${id}`,
  })),
)
const authHint = computed(() =>
  props.deployment?.authType === 'PASSWORD'
    ? '将使用配置时保存的 SSH 密码连接，不再重复输入。'
    : '将使用本机 SSH 密钥或 ssh-agent 连接。',
)
const canConfirm = computed(
  () =>
    !props.starting &&
    projectId.value !== '' &&
    (props.deployment?.projectIds.includes(Number(projectId.value)) ?? false),
)
const taskItems = computed(() => [
  { value: '', label: '不关联任务（手工部署）' },
  ...tasks.value.map((task) => ({
    value: String(task.id),
    label: `#${task.id} ${task.title}`,
  })),
])

const loadProjectVersion = async () => {
  const selectedProjectId = Number(projectId.value)
  projectVersion.value = null
  versionLoading.value = false
  if (!selectedProjectId) return
  versionLoading.value = true
  try {
    projectVersion.value = await getProjectVersion(selectedProjectId)
  } catch {
    projectVersion.value = null
  } finally {
    versionLoading.value = false
  }
}

const loadTasks = async () => {
  const selectedProjectId = Number(projectId.value)
  taskId.value = ''
  tasks.value = []
  if (!selectedProjectId) return
  try {
    const page = await listTasks({
      status: 'DONE',
      projectId: selectedProjectId,
      page: 1,
      size: 100,
    })
    tasks.value = page.items
    if (
      props.defaultTaskId !== null
      && page.items.some((task) => task.id === props.defaultTaskId)
    ) {
      taskId.value = String(props.defaultTaskId)
    }
  } catch {
    tasks.value = []
  }
}

watch(
  [open, () => props.deployment?.id],
  ([opened]) => {
    if (!opened) return
    projectId.value = props.deployment?.projectIds[0]
      ? String(props.deployment.projectIds[0])
      : ''
    void loadTasks()
  },
)

watch(projectId, () => {
  if (open.value) {
    void loadTasks()
    void loadProjectVersion()
  }
})
</script>

<template>
  <Dialog
    v-model:open="open"
    title="确认执行部署"
    :description="deployment ? `即将执行「${deployment.name}」的部署脚本` : ''"
    icon-tone="default"
  >
    <div class="space-y-3">
      <label class="block">
        <span class="mb-1.5 block text-caption font-emphasis text-label-secondary">
          本次执行项目
        </span>
        <Select
          v-model="projectId"
          :items="projectItems"
          :disabled="projectItems.length <= 1"
          class="w-full"
          placeholder="选择执行项目"
        />
      </label>
      <label class="block">
        <span class="mb-1.5 block text-caption font-emphasis text-label-secondary">
          关联已完成任务
        </span>
        <Select
          v-model="taskId"
          :items="taskItems"
          class="w-full"
          placeholder="选择任务"
        />
      </label>
      <div class="rounded-panel border border-separator-soft bg-content px-3 py-2.5">
        <div class="flex items-center gap-2">
          <span class="text-caption font-emphasis text-label-secondary">本次发布版本</span>
          <span v-if="versionLoading" class="ml-auto text-caption text-label-tertiary">解析中…</span>
          <span v-else-if="projectVersion?.available" class="tnum ml-auto font-mono text-caption font-emphasis text-label">
            {{ projectVersion.version }}
          </span>
          <span v-else class="ml-auto text-caption text-warning">未标记版本</span>
        </div>
        <p class="mt-1 text-caption text-label-tertiary">
          {{ projectVersion?.message ?? '将会在部署入队时自动记录项目版本' }}
        </p>
      </div>
      <p class="text-body text-label-secondary">{{ authHint }}</p>
    </div>
    <p class="mt-2 text-caption text-label-tertiary">
      执行会连接远程服务器并运行脚本，请确认目标和脚本均无误。
    </p>
    <template #footer>
      <Button :disabled="starting" @click="open = false">取消</Button>
      <Button
        variant="primary"
        :disabled="!canConfirm"
        @click="emit('confirm', {
          projectId: Number(projectId),
          taskId: taskId ? Number(taskId) : null,
        })"
      >
        {{ starting ? '连接中…' : '确认执行' }}
      </Button>
    </template>
  </Dialog>
</template>
