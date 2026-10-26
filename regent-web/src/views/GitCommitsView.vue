<script setup lang="ts">
import { ChevronLeft, ChevronRight, RefreshCw, Search, X } from '@lucide/vue'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getGitCommits } from '@/api/gitCommits'
import { ApiError } from '@/api/http'
import GitCommitDetailDialog from '@/components/business/GitCommitDetailDialog.vue'
import GitCommitStatusBadge from '@/components/business/GitCommitStatusBadge.vue'
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
import type { GitCommitStatus, GitCommitSummary } from '@/types/api'
import { formatDateTime } from '@/utils/format'

const store = useTaskStore()
const route = useRoute()
const router = useRouter()
const items = ref<GitCommitSummary[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref<string | null>(null)
const keyword = ref('')
const status = ref<GitCommitStatus | 'ALL'>('ALL')
const page = ref(1)
const size = 20
const detailOpen = ref(false)
const detailId = ref<number | null>(null)

const routeTaskId = computed(() => {
  const value = Number(route.query.taskId)
  return Number.isInteger(value) && value > 0 ? value : null
})
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))

const columns: TableColumn[] = [
  { key: 'status', label: '状态', width: '84px' },
  { key: 'task', label: '任务' },
  { key: 'ticket', label: 'Bug', width: '84px', hideBelow: 'xl' },
  { key: 'message', label: '提交说明' },
  { key: 'branch', label: '分支', width: '116px', hideBelow: '2xl' },
  { key: 'commit', label: 'Commit', width: '104px', hideBelow: 'lg' },
  { key: 'operator', label: '操作人', width: '92px', hideBelow: 'xl' },
  { key: 'time', label: '时间', width: '132px', hideBelow: 'lg' },
]

function messageOf(cause: unknown): string {
  return cause instanceof ApiError ? cause.message : '无法读取 Git 提交记录'
}

async function load() {
  loading.value = true
  error.value = null
  try {
    const result = await getGitCommits({
      projectId: store.projectId ?? undefined,
      taskId: routeTaskId.value ?? undefined,
      status: status.value === 'ALL' ? undefined : status.value,
      keyword: keyword.value,
      page: page.value,
      size,
    })
    items.value = result.items
    total.value = result.total
    if (result.page > totalPages.value) page.value = totalPages.value
  } catch (cause) {
    error.value = messageOf(cause)
    items.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function openDetail(item: GitCommitSummary) {
  detailId.value = item.id
  detailOpen.value = true
}

function clearTaskFilter() {
  void router.replace({ path: '/delivery', query: { view: 'git-commits' } })
}

watch(
  () => [store.projectId, routeTaskId.value] as const,
  () => {
    page.value = 1
    void load()
  },
)

watch(status, () => {
  page.value = 1
  void load()
})

onMounted(async () => {
  await store.loadProjects()
  await load()
})
</script>

<template>
  <div class="flex h-full min-h-0 flex-col">
    <ListToolbar>
      <template #title>
        <h1 class="text-body font-emphasis text-label">Git 提交</h1>
        <span class="tnum text-body text-label-tertiary">{{ total }}</span>
      </template>
      <template #filters>
        <div class="relative min-w-0 flex-1 sm:w-64 sm:flex-none">
          <Icon :as="Search" size="inline" class="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-label-tertiary" />
          <Input
            v-model="keyword"
            size="lg"
            placeholder="搜索任务、Bug 编号、提交说明或 SHA"
            class="pl-7 pr-7"
            @keyup.enter="page = 1; load()"
          />
          <button
            v-if="keyword"
            type="button"
            class="absolute right-1 top-1/2 flex size-5 -translate-y-1/2 items-center justify-center rounded-control text-label-tertiary hover:bg-hover hover:text-label"
            aria-label="清空搜索"
            @click="keyword = ''; page = 1; load()"
          >
            <Icon :as="X" size="inline" />
          </button>
        </div>
        <select
          v-model="status"
          class="h-control rounded-control border border-separator bg-content px-2 text-caption text-label outline-none focus:border-accent"
          aria-label="筛选提交状态"
        >
          <option value="ALL">全部状态</option>
          <option value="SUCCEEDED">已提交</option>
          <option value="FAILED">提交失败</option>
          <option value="RUNNING">提交中</option>
        </select>
        <button
          v-if="routeTaskId"
          type="button"
          class="inline-flex h-control items-center gap-1 rounded-control bg-accent/10 px-2 text-caption text-accent"
          @click="clearTaskFilter"
        >
          任务 #{{ routeTaskId }}<Icon :as="X" size="inline" />
        </button>
      </template>
      <template #actions>
        <Tooltip content="刷新">
          <IconButton label="刷新 Git 提交记录" size="lg" :disabled="loading" @click="load">
            <Icon :as="RefreshCw" size="large" :class="{ 'animate-spin': loading }" />
          </IconButton>
        </Tooltip>
      </template>
    </ListToolbar>

    <div class="scroll-area min-h-0 flex-1 overflow-auto">
      <div v-if="error" class="p-6 text-caption text-danger">{{ error }}</div>
      <Table
        v-else
        :columns="columns"
        :rows="items"
        :show-empty="false"
        @select="openDetail($event as GitCommitSummary)"
      >
        <template #cell="{ row, column }">
          <GitCommitStatusBadge
            v-if="column.key === 'status'"
            :status="(row as GitCommitSummary).status"
          />
          <div v-else-if="column.key === 'task'" class="min-w-0">
            <p class="truncate text-caption font-emphasis text-label">
              #{{ row.taskId }} {{ row.taskTitle }}
            </p>
            <p class="mt-0.5 truncate text-caption text-label-tertiary">{{ row.projectName }}</p>
          </div>
          <span v-else-if="column.key === 'ticket'" class="font-mono text-caption text-label-secondary">
            {{ row.ticket ?? '—' }}
          </span>
          <span v-else-if="column.key === 'message'" class="block truncate text-caption text-label" :title="row.message">
            {{ row.message }}
          </span>
          <span v-else-if="column.key === 'branch'" class="block truncate font-mono text-caption text-label-secondary">
            {{ row.branchName }}
          </span>
          <span v-else-if="column.key === 'commit'" class="font-mono text-caption text-label-secondary">
            {{ row.commitSha?.slice(0, 10) ?? '—' }}
          </span>
          <span v-else-if="column.key === 'operator'" class="truncate text-caption text-label-secondary">
            {{ row.operatorName }}
          </span>
          <span v-else-if="column.key === 'time'" class="tnum text-caption text-label-tertiary">
            {{ formatDateTime(row.finishedAt ?? row.startedAt) }}
          </span>
        </template>
      </Table>
      <EmptyState
        v-if="!loading && !error && items.length === 0"
        title="暂无 Git 提交记录"
        description="审阅通过并在平台提交代码后，记录会出现在这里。"
      />
    </div>

    <div class="flex shrink-0 items-center border-t border-separator px-4 py-2 text-caption text-label-tertiary">
      <span>共 {{ total }} 条</span>
      <span class="ml-auto">{{ page }} / {{ totalPages }}</span>
      <Button class="ml-2" size="sm" :disabled="page <= 1 || loading" @click="page--; load()">
        <Icon :as="ChevronLeft" size="inline" />上一页
      </Button>
      <Button class="ml-1" size="sm" :disabled="page >= totalPages || loading" @click="page++; load()">
        下一页<Icon :as="ChevronRight" size="inline" />
      </Button>
    </div>

    <GitCommitDetailDialog v-model:open="detailOpen" :commit-id="detailId" />
  </div>
</template>
