<script setup lang="ts">
import { Bell, CircleAlert, CircleCheck } from '@lucide/vue'
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { listTasks } from '@/api/tasks'
import Button from '@/components/ui/button/Button.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import IconButton from '@/components/ui/icon-button/IconButton.vue'
import Popover from '@/components/ui/popover/Popover.vue'
import { useTaskStore } from '@/stores/useTaskStore'
import type { TaskSummary } from '@/types/api'
import { formatRelative } from '@/utils/format'

const store = useTaskStore()
const router = useRouter()

const open = ref(false)
const failedTasks = ref<TaskSummary[]>([])
const total = ref(0)
const loaded = ref(false)
const loading = ref(false)
const error = ref<string | null>(null)

const badgeCount = computed(() => (loaded.value ? total.value : store.counts.FAILED))
const buttonLabel = computed(() => {
  if (!loaded.value || badgeCount.value === 0) return '失败任务通知'
  return `失败任务通知，共 ${badgeCount.value} 个失败任务`
})

const load = async () => {
  if (loading.value) return
  loading.value = true
  error.value = null
  try {
    const result = await listTasks({ status: 'FAILED', page: 1, size: 5 })
    failedTasks.value = result.items
    total.value = result.total
    loaded.value = true
  } catch {
    error.value = '暂时无法读取失败任务'
  } finally {
    loading.value = false
  }
}

watch(open, (value) => {
  if (value) void load()
})

onMounted(() => {
  void load()
})

const showFailedTasks = async () => {
  store.statusFilter = 'FAILED'
  store.levelFilter = 'ALL'
  store.projectId = null
  store.keyword = ''
  store.page = 1
  store.clearSelection()
  open.value = false
  await router.push('/tasks')
}

const openTask = async (task: TaskSummary) => {
  store.statusFilter = 'FAILED'
  store.levelFilter = 'ALL'
  store.projectId = task.projectId
  store.keyword = ''
  store.page = 1
  store.clearSelection()
  open.value = false
  await store.select(task)
  await router.push('/tasks')
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

    <section class="w-80" aria-label="失败任务通知">
      <div class="flex items-start justify-between gap-3">
        <div>
          <h2 class="text-body font-emphasis text-label">失败任务</h2>
          <p class="mt-0.5 text-caption text-label-secondary">
            {{ total > 0 ? `${total} 个任务需要处理` : '暂时没有失败任务' }}
          </p>
        </div>
        <span
          v-if="total > 0"
          class="rounded-badge bg-danger/10 px-1.5 py-0.5 text-caption text-danger"
        >
          {{ total }}
        </span>
      </div>

      <p v-if="loading" class="py-6 text-center text-caption text-label-secondary">正在读取…</p>
      <p v-else-if="error" class="py-6 text-center text-caption text-danger">{{ error }}</p>
      <div
        v-else-if="failedTasks.length === 0"
        class="flex flex-col items-center gap-2 py-6 text-label-secondary"
      >
        <Icon :as="CircleCheck" size="xlarge" class="text-success" />
        <span class="text-caption">暂时没有失败任务</span>
      </div>
      <div v-else class="mt-2 max-h-64 overflow-y-auto">
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

      <Button v-if="total > 0" variant="ghost" block class="mt-2" @click="showFailedTasks">
        查看全部失败任务
      </Button>
    </section>
  </Popover>
</template>
