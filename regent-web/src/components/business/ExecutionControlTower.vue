<script setup lang="ts">
import {
  CircleAlert,
  CircleCheck,
  Clock3,
  RefreshCw,
  RotateCcw,
  Square,
  Workflow,
} from '@lucide/vue'
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { stopDeployment } from '@/api/deployments'
import { getExecutionControlTower } from '@/api/executionControlTower'
import { cancelTask, retryTask } from '@/api/tasks'
import Button from '@/components/ui/button/Button.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import IconButton from '@/components/ui/icon-button/IconButton.vue'
import Tooltip from '@/components/ui/tooltip/Tooltip.vue'
import { useToast } from '@/composables/useToast'
import { useTaskStore } from '@/stores/useTaskStore'
import type {
  ExecutionControlTowerEntry,
  ExecutionControlTowerSnapshot,
  ExecutionControlTowerStatus,
} from '@/types/api'
import { formatRelative } from '@/utils/format'

type Filter = 'attention' | 'active' | 'all'

const store = useTaskStore()
const router = useRouter()
const toast = useToast()
const snapshot = ref<ExecutionControlTowerSnapshot | null>(null)
const loading = ref(false)
const acting = ref<Set<string>>(new Set())
const error = ref<string | null>(null)
const filter = ref<Filter>('attention')
let timer: number | undefined
let sequence = 0

const filterOptions: Array<{ value: Filter; label: string }> = [
  { value: 'attention', label: '需处理' },
  { value: 'active', label: '执行中' },
  { value: 'all', label: '全部' },
]

const items = computed(() => {
  const entries = snapshot.value?.entries ?? []
  if (filter.value === 'all') return entries
  if (filter.value === 'active') {
    return entries.filter((entry) =>
      ['RUNNING', 'QUEUED', 'PENDING', 'BLOCKED', 'DEPLOYING'].includes(entry.businessStatus),
    )
  }
  return entries.filter((entry) =>
    ['FAILED', 'SLOW', 'STALLED', 'DEVICE_LOST'].includes(entry.healthStatus)
    || entry.businessStatus === 'BLOCKED',
  )
})

const statusMeta = (status: ExecutionControlTowerStatus) => {
  if (status === 'DEVICE_LOST') {
    return { label: '设备失联', class: 'bg-danger/10 text-danger', icon: CircleAlert }
  }
  if (status === 'STALLED') {
    return { label: '疑似卡住', class: 'bg-danger/10 text-danger', icon: CircleAlert }
  }
  if (status === 'SLOW') {
    return { label: '响应缓慢', class: 'bg-warning/12 text-warning', icon: Clock3 }
  }
  if (status === 'FAILED') {
    return { label: '失败', class: 'bg-danger/10 text-danger', icon: CircleAlert }
  }
  if (status === 'UNKNOWN') {
    return { label: '状态未知', class: 'bg-neutral/12 text-neutral', icon: CircleAlert }
  }
  return { label: '正常', class: 'bg-success/10 text-success', icon: CircleCheck }
}

const keyOf = (entry: ExecutionControlTowerEntry) =>
  `${entry.executionType}-${entry.businessId}-${entry.runId ?? 0}`

const isActing = (entry: ExecutionControlTowerEntry) => acting.value.has(keyOf(entry))

const markActing = (entry: ExecutionControlTowerEntry, value: boolean) => {
  const next = new Set(acting.value)
  if (value) next.add(keyOf(entry))
  else next.delete(keyOf(entry))
  acting.value = next
}

const setFilter = (value: Filter) => {
  filter.value = value
}

const load = async (quiet = false) => {
  const current = ++sequence
  if (!quiet) loading.value = true
  if (!quiet) error.value = null
  try {
    const next = await getExecutionControlTower(store.projectId)
    if (current !== sequence) return
    snapshot.value = next
  } catch {
    if (current !== sequence) return
    if (!quiet) error.value = '暂时无法读取执行状态'
  } finally {
    if (current === sequence && !quiet) loading.value = false
  }
}

const openEntry = async (entry: ExecutionControlTowerEntry) => {
  if (entry.executionType === 'TASK') {
    await router.push({
      path: '/tasks',
      query: {
        taskId: String(entry.businessId),
        ...(store.projectId === null ? {} : { projectId: String(store.projectId) }),
      },
    })
    return
  }
  await router.push({ path: '/delivery', query: { view: 'deployments' } })
}

const retry = async (entry: ExecutionControlTowerEntry) => {
  if (entry.executionType !== 'TASK' || isActing(entry)) return
  markActing(entry, true)
  try {
    await retryTask(entry.businessId)
    toast.success(`任务 #${entry.businessId} 已重新排队`)
    await load(true)
  } catch (e) {
    toast.failure('重试失败', e instanceof Error ? e.message : '未知错误')
  } finally {
    markActing(entry, false)
  }
}

const stop = async (entry: ExecutionControlTowerEntry) => {
  if (isActing(entry)) return
  markActing(entry, true)
  try {
    if (entry.executionType === 'TASK') await cancelTask(entry.businessId)
    else await stopDeployment(entry.businessId)
    toast.success(`已提交停止：${entry.name}`)
    await load(true)
  } catch (e) {
    toast.failure('停止失败', e instanceof Error ? e.message : '未知错误')
  } finally {
    markActing(entry, false)
  }
}

const review = async () => {
  await router.push('/decisions')
}

onMounted(() => {
  void load()
  timer = window.setInterval(() => {
    if (document.visibilityState !== 'hidden') void load(true)
  }, 5_000)
})
watch(() => store.projectId, () => void load())
onUnmounted(() => window.clearInterval(timer))
</script>

<template>
  <section class="mt-4 overflow-hidden rounded-panel border border-separator-soft bg-content shadow-panel">
    <header class="flex flex-wrap items-center gap-x-4 gap-y-2 border-b border-separator-soft px-4 py-3">
      <div class="flex min-w-0 items-center gap-2">
        <span class="grid size-7 place-items-center rounded-control bg-accent/10 text-accent">
          <Icon :as="Workflow" size="inline" />
        </span>
        <div class="min-w-0">
          <h2 class="text-body font-emphasis text-label">执行控制塔</h2>
          <p class="text-caption text-label-tertiary">
            运行 {{ snapshot?.activeCount ?? 0 }} · 排队 {{ snapshot?.queuedCount ?? 0 }} ·
            待决策 {{ snapshot?.blockedCount ?? 0 }} · 异常 {{ snapshot?.abnormalCount ?? 0 }}
          </p>
        </div>
      </div>
      <div class="flex items-center gap-1 rounded-control bg-neutral/8 p-0.5">
        <button
          v-for="option in filterOptions"
          :key="option.value"
          type="button"
          class="h-control-sm rounded-[5px] px-2 text-caption"
          :class="filter === option.value ? 'bg-content font-emphasis text-label shadow-control' : 'text-label-secondary'"
          @click="setFilter(option.value)"
        >
          {{ option.label }}
        </button>
      </div>
      <Tooltip content="刷新执行状态">
        <IconButton label="刷新执行状态" size="sm" :disabled="loading" @click="load()">
          <Icon :as="RefreshCw" size="inline" :class="loading ? 'animate-spin' : ''" />
        </IconButton>
      </Tooltip>
    </header>

    <p v-if="error" class="border-b border-separator-soft px-4 py-2 text-caption text-danger">
      {{ error }}
    </p>

    <div v-if="items.length === 0" class="flex items-center gap-2 px-4 py-5 text-caption text-label-tertiary">
      <Icon :as="CircleCheck" size="inline" class="text-success" />
      {{ filter === 'attention' ? '当前没有需要处理的执行异常' : '当前没有执行记录' }}
    </div>

    <div v-else class="divide-y divide-separator-soft">
      <button
        v-for="entry in items"
        :key="keyOf(entry)"
        type="button"
        class="grid w-full grid-cols-[minmax(0,1.5fr)_minmax(0,1fr)_minmax(0,1fr)_auto] items-center gap-3 px-4 py-2.5 text-left transition-colors hover:bg-hover focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-inset focus-visible:ring-accent/35 max-lg:grid-cols-[minmax(0,1fr)_auto]"
        @click="openEntry(entry)"
      >
        <span class="flex min-w-0 items-start gap-2">
          <span
            class="mt-0.5 grid size-6 shrink-0 place-items-center rounded-control"
            :class="statusMeta(entry.healthStatus).class"
          >
            <Icon :as="statusMeta(entry.healthStatus).icon" size="inline" />
          </span>
          <span class="min-w-0">
            <span class="block truncate text-caption font-emphasis text-label">{{ entry.name }}</span>
            <span class="mt-0.5 block truncate text-caption text-label-tertiary">
              {{ entry.projectName }} · {{ entry.executionType === 'TASK' ? '任务' : '部署' }}
              <template v-if="entry.runId"> · Run {{ entry.runId }}</template>
            </span>
          </span>
        </span>

        <span class="min-w-0 max-lg:hidden">
          <span class="block text-caption text-label">{{ entry.stage }}</span>
          <span class="mt-0.5 block truncate text-caption text-label-tertiary" :title="entry.statusDetail">
            {{ entry.failureCategory || entry.statusDetail }}
          </span>
        </span>

        <span class="min-w-0 max-lg:hidden">
          <span class="block truncate text-caption text-label-secondary">{{ entry.deviceName }}</span>
          <span class="mt-0.5 block text-caption text-label-tertiary">
            {{ entry.lastActivityAt ? formatRelative(entry.lastActivityAt) : '暂无活动时间' }}
          </span>
        </span>

        <span class="flex items-center justify-end gap-1" @click.stop>
          <Button v-if="entry.reviewable" size="sm" variant="primary" @click="review">
            处理决策
          </Button>
          <Tooltip v-if="entry.retryable" content="重新执行">
            <IconButton
              label="重新执行"
              size="sm"
              :disabled="isActing(entry)"
              @click="retry(entry)"
            >
              <Icon :as="RotateCcw" size="inline" />
            </IconButton>
          </Tooltip>
          <Tooltip v-if="entry.stoppable" content="停止执行">
            <IconButton
              label="停止执行"
              size="sm"
              :disabled="isActing(entry)"
              @click="stop(entry)"
            >
              <Icon :as="Square" size="inline" />
            </IconButton>
          </Tooltip>
        </span>
      </button>
    </div>
  </section>
</template>
