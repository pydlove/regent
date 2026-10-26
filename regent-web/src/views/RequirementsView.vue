<script setup lang="ts">
import { FileText, Plus, RefreshCw, Search, X } from '@lucide/vue'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getRequirement, listRequirements } from '@/api/requirements'
import RequirementDialog from '@/components/business/RequirementDialog.vue'
import Badge from '@/components/ui/badge/Badge.vue'
import Button from '@/components/ui/button/Button.vue'
import EmptyState from '@/components/ui/empty-state/EmptyState.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import IconButton from '@/components/ui/icon-button/IconButton.vue'
import Input from '@/components/ui/input/Input.vue'
import ListToolbar from '@/components/ui/list-toolbar/ListToolbar.vue'
import Table from '@/components/ui/table/Table.vue'
import type { TableColumn } from '@/components/ui/table/table.types'
import Tooltip from '@/components/ui/tooltip/Tooltip.vue'
import { useTaskStore } from '@/stores/useTaskStore'
import type { Requirement, RequirementStatus } from '@/types/api'
import { formatRelative } from '@/utils/format'

const store = useTaskStore()
const route = useRoute()
const router = useRouter()
const requirements = ref<Requirement[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const keyword = ref('')
const dialogOpen = ref(false)
const editing = ref<Requirement | null>(null)
let sequence = 0

const columns: TableColumn[] = [
  { key: 'title', label: '需求' },
  { key: 'status', label: '状态', width: '96px' },
  { key: 'progress', label: '任务进度', width: '120px', hideBelow: 'md' },
  { key: 'updated', label: '更新时间', width: '96px', hideBelow: 'xl' },
]

const statusMeta: Record<
  RequirementStatus,
  { label: string; tone: 'accent' | 'neutral' | 'success' | 'warning' | 'danger' }
> = {
  DRAFT: { label: '草稿', tone: 'neutral' },
  CLARIFYING: { label: '待澄清', tone: 'warning' },
  READY: { label: '待确认', tone: 'accent' },
  PLANNED: { label: '已计划', tone: 'neutral' },
  RUNNING: { label: '执行中', tone: 'accent' },
  PARTIAL: { label: '部分完成', tone: 'warning' },
  DONE: { label: '已完成', tone: 'success' },
  CANCELLED: { label: '已取消', tone: 'neutral' },
}

const filtered = computed(() => {
  const query = keyword.value.trim().toLocaleLowerCase('zh-CN')
  if (!query) return requirements.value
  return requirements.value.filter((requirement) =>
    [requirement.title, requirement.rawText, requirement.status]
      .some((value) => value.toLocaleLowerCase('zh-CN').includes(query)),
  )
})

const progressOf = (requirement: Requirement) => {
  if (requirement.tasks.length === 0) {
    return requirement.taskDrafts.length > 0
      ? `${requirement.taskDrafts.length} 个草稿`
      : '尚未拆解'
  }
  const done = requirement.tasks.filter((task) => task.status === 'DONE').length
  return `${done} / ${requirement.tasks.length}`
}

const load = async () => {
  const current = ++sequence
  loading.value = true
  error.value = null
  try {
    const items = await listRequirements(store.projectId)
    if (current !== sequence) return
    requirements.value = items
  } catch (e) {
    if (current !== sequence) return
    error.value = e instanceof Error ? e.message : '需求列表加载失败'
    requirements.value = []
  } finally {
    if (current === sequence) loading.value = false
  }
}

const openCreate = () => {
  editing.value = null
  dialogOpen.value = true
}

const openRequirement = (requirement: Requirement) => {
  editing.value = requirement
  dialogOpen.value = true
}

const openRouteRequirement = async () => {
  const raw = route.query.requirementId
  const id = typeof raw === 'string' ? Number(raw) : NaN
  if (!Number.isSafeInteger(id) || id <= 0) return
  try {
    openRequirement(await getRequirement(id))
  } catch {
    await router.replace({ path: '/tasks', query: { view: 'requirements' } })
  }
}

const saved = async () => {
  await load()
}

onMounted(async () => {
  await load()
  await openRouteRequirement()
})
watch(() => store.projectId, () => void load())
watch(() => route.query.requirementId, () => void openRouteRequirement())
</script>

<template>
  <div class="flex h-full min-h-0 flex-col">
    <ListToolbar>
      <template #title>
        <h1 class="text-body font-emphasis text-label">需求</h1>
        <span class="tnum text-caption text-label-tertiary">{{ filtered.length }}</span>
      </template>
      <template #filters>
        <div class="relative min-w-0 flex-1 sm:w-60 sm:flex-none">
          <Icon
            :as="Search"
            size="inline"
            class="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-label-tertiary"
          />
          <Input
            v-model="keyword"
            size="lg"
            placeholder="搜索需求标题或内容"
            aria-label="搜索需求"
            class="pl-7 pr-7"
          />
          <button
            v-if="keyword"
            type="button"
            class="absolute right-1 top-1/2 flex size-5 -translate-y-1/2 items-center justify-center rounded-control text-label-tertiary hover:bg-hover hover:text-label"
            aria-label="清空需求搜索"
            @click="keyword = ''"
          >
            <Icon :as="X" size="inline" />
          </button>
        </div>
      </template>
      <template #actions>
        <Tooltip content="刷新需求">
          <IconButton label="刷新需求" size="lg" :disabled="loading" @click="load">
            <Icon :as="RefreshCw" size="large" :class="loading ? 'animate-spin' : ''" />
          </IconButton>
        </Tooltip>
        <Button variant="primary" size="lg" @click="openCreate">
          <Icon :as="Plus" size="inline" />录入需求
        </Button>
      </template>
    </ListToolbar>

    <div class="scroll-area min-h-0 flex-1 overflow-auto">
      <Table
        :columns="columns"
        :rows="filtered"
        :show-empty="false"
        touch-rows
        @select="openRequirement($event as Requirement)"
      >
        <template #cell="{ row, column }">
          <span v-if="column.key === 'title'" class="flex min-w-0 items-center gap-2">
            <span class="grid size-6 shrink-0 place-items-center rounded-control bg-accent/10 text-accent">
              <Icon :as="FileText" size="inline" />
            </span>
            <span class="min-w-0">
              <span class="block truncate text-caption font-emphasis text-label">{{ row.title }}</span>
              <span class="mt-0.5 block truncate text-caption text-label-tertiary">
                {{ row.tasks.length > 0 ? `${row.tasks.length} 个任务` : '尚未创建任务' }}
              </span>
            </span>
          </span>
          <Badge
            v-else-if="column.key === 'status'"
            :tone="statusMeta[(row as Requirement).status].tone"
          >
            {{ statusMeta[(row as Requirement).status].label }}
          </Badge>
          <span v-else-if="column.key === 'progress'" class="text-caption text-label-secondary">
            {{ progressOf(row as Requirement) }}
          </span>
          <span v-else-if="column.key === 'updated'" class="text-caption text-label-tertiary">
            {{ formatRelative(String(row.updatedAt)) }}
          </span>
        </template>
      </Table>

      <EmptyState
        v-if="loading && requirements.length === 0"
        title="正在加载需求…"
        description="读取需求草稿和拆解结果"
      />
      <EmptyState v-else-if="error" title="需求加载失败" :description="error">
        <Button size="sm" @click="load">重新加载</Button>
      </EmptyState>
      <EmptyState
        v-else-if="requirements.length === 0"
        title="从业务需求开始"
        description="描述目标、范围和验收标准，由 AI 检查信息并生成可编辑任务草稿。"
      >
        <Button variant="primary" size="sm" @click="openCreate">
          <Icon :as="Plus" size="inline" />录入需求
        </Button>
      </EmptyState>
      <EmptyState
        v-else-if="filtered.length === 0"
        title="没有匹配的需求"
        description="换个标题、内容或状态再试。"
      >
        <Button size="sm" @click="keyword = ''">清除搜索</Button>
      </EmptyState>
    </div>

    <RequirementDialog
      v-model:open="dialogOpen"
      :requirement="editing"
      :default-project-id="store.projectId"
      @saved="saved"
      @changed="load"
    />
  </div>
</template>
