<script setup lang="ts">
import { Bell, CircleAlert, CircleCheck, Workflow } from '@lucide/vue'
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { listTasks } from '@/api/tasks'
import { listDecisions } from '@/api/decisions'
import Button from '@/components/ui/button/Button.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import IconButton from '@/components/ui/icon-button/IconButton.vue'
import Popover from '@/components/ui/popover/Popover.vue'
import { useTaskStore } from '@/stores/useTaskStore'
import type { DecisionSummary, TaskSummary } from '@/types/api'
import { formatRelative } from '@/utils/format'

const store = useTaskStore()
const router = useRouter()

const open = ref(false)
const failedTasks = ref<TaskSummary[]>([])
const pendingDecisions = ref<DecisionSummary[]>([])
const total = ref(0)
const decisionTotal = ref(0)
const loaded = ref(false)
const loading = ref(false)
const error = ref<string | null>(null)
let loadSequence = 0

const badgeCount = computed(() =>
  loaded.value ? total.value + decisionTotal.value : store.counts.FAILED + store.counts.BLOCKED,
)
const scopeLabel = computed(() => {
  if (store.projectId === null) return '全部项目'
  return store.projects.find((project) => project.id === store.projectId)?.name ?? '当前项目'
})
const buttonLabel = computed(() => {
  if (!loaded.value || badgeCount.value === 0) return '待办'
  return `待办，共 ${badgeCount.value} 项需要处理`
})

const load = async () => {
  const sequence = ++loadSequence
  loading.value = true
  error.value = null
  try {
    const [result, decisions] = await Promise.all([
      listTasks({
        status: 'FAILED',
        projectId: store.projectId,
        page: 1,
        size: 5,
      }),
      listDecisions({
        scope: 'PENDING',
        projectId: store.projectId,
        page: 1,
        size: 5,
      }),
    ])
    if (sequence !== loadSequence) return
    failedTasks.value = result.items
    total.value = result.total
    pendingDecisions.value = decisions.items
    decisionTotal.value = decisions.total
    loaded.value = true
  } catch {
    if (sequence !== loadSequence) return
    error.value = '暂时无法读取失败任务'
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

watch(open, (value) => {
  if (value) void load()
})

onMounted(() => {
  void load()
})

watch(() => store.projectId, () => void load())

const showFailedTasks = async () => {
  store.statusFilter = 'FAILED'
  store.levelFilter = 'ALL'
  store.keyword = ''
  store.page = 1
  store.clearSelection()
  open.value = false
  await router.push('/tasks')
}

const openTask = async (task: TaskSummary) => {
  store.statusFilter = 'FAILED'
  store.levelFilter = 'ALL'
  store.keyword = ''
  store.page = 1
  store.clearSelection()
  open.value = false
  await store.select(task)
  await router.push('/tasks')
}

const openDecisions = async () => {
  open.value = false
  await router.push('/decisions')
}
</script>

<template>
  <Popover v-model:open="open" align="end">
    <template #trigger>
      <IconButton :label="buttonLabel" size="lg" class="relative">
        <Icon :as="Bell" size="large" />
        <span
          v-if="badgeCount > 0"
          class="absolute -right-0.5 -top-0.5 flex h-3.5 min-w-3.5 items-center justify-center rounded-full bg-danger px-1 text-caption text-white"
          aria-hidden="true"
        >
          {{ badgeCount > 99 ? '99+' : badgeCount }}
        </span>
      </IconButton>
    </template>

    <section class="w-80" aria-label="待办">
      <div class="flex items-start justify-between gap-3">
        <div>
          <h2 class="text-body font-emphasis text-label">待办</h2>
          <p class="mt-0.5 text-caption text-label-secondary">
            {{ scopeLabel }} · {{ badgeCount > 0 ? `${badgeCount} 项需要处理` : '当前没有待办' }}
          </p>
        </div>
        <span
          v-if="badgeCount > 0"
          class="rounded-badge bg-danger/10 px-1.5 py-0.5 text-caption text-danger"
        >
          {{ badgeCount }}
        </span>
      </div>

      <p v-if="loading" class="py-6 text-center text-caption text-label-secondary">正在读取…</p>
      <p v-else-if="error" class="py-6 text-center text-caption text-danger">{{ error }}</p>
      <div
        v-else-if="failedTasks.length === 0 && pendingDecisions.length === 0"
        class="flex flex-col items-center gap-2 py-6 text-label-secondary"
      >
        <Icon :as="CircleCheck" size="xlarge" class="text-success" />
        <span class="text-caption">暂时没有失败任务</span>
      </div>
      <div v-else class="mt-2 max-h-80 overflow-y-auto">
        <button
          v-for="decision in pendingDecisions"
          :key="`decision-${decision.id}`"
          type="button"
          class="flex w-full items-start gap-2 rounded-control px-2 py-2 text-left hover:bg-hover focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-accent/35"
          @click="openDecisions"
        >
          <Icon :as="Workflow" size="regular" class="mt-0.5 text-warning" />
          <span class="min-w-0 flex-1">
            <span class="flex min-w-0 items-center gap-1.5">
              <span class="truncate text-body text-label">{{ decision.question }}</span>
              <span class="tnum shrink-0 text-caption text-label-tertiary">#{{ decision.id }}</span>
            </span>
            <span class="mt-0.5 block truncate text-caption text-label-secondary">
              {{ decision.projectName }} · {{ decision.effectiveLevel }} 待批准
            </span>
          </span>
        </button>
        <button
          v-for="task in failedTasks"
          :key="task.id"
          type="button"
          class="flex w-full items-start gap-2 rounded-control px-2 py-2 text-left hover:bg-hover focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-accent/35"
          @click="openTask(task)"
        >
          <Icon :as="CircleAlert" size="regular" class="mt-0.5 text-danger" />
          <span class="min-w-0 flex-1">
            <span class="flex min-w-0 items-center gap-1.5">
              <span class="truncate text-body text-label">{{ task.title }}</span>
              <span class="tnum shrink-0 text-caption text-label-tertiary">#{{ task.id }}</span>
            </span>
            <span class="mt-0.5 block truncate text-caption text-label-secondary">
              {{ task.projectName }} · {{ formatRelative(task.updatedAt) }}
            </span>
          </span>
        </button>
      </div>

      <div v-if="badgeCount > 0" class="mt-2 flex gap-2">
        <Button class="min-w-0 flex-1" @click="openDecisions">
          处理决策
        </Button>
        <Button v-if="total > 0" class="min-w-0 flex-1" @click="showFailedTasks">
          失败任务
        </Button>
      </div>
    </section>
  </Popover>
</template>
