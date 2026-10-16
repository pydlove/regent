<script setup lang="ts">
import { ListChecks, Plus, RefreshCw } from '@lucide/vue'
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { ApiError } from '@/api/http'
import { listTasks } from '@/api/tasks'
import DecisionLevelBadge from '@/components/business/DecisionLevelBadge.vue'
import TaskStatusBadge from '@/components/business/TaskStatusBadge.vue'
import Button from '@/components/ui/button/Button.vue'
import EmptyState from '@/components/ui/empty-state/EmptyState.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import IconButton from '@/components/ui/icon-button/IconButton.vue'
import StatusDot from '@/components/ui/status-dot/StatusDot.vue'
import Table from '@/components/ui/table/Table.vue'
import type { TableColumn } from '@/components/ui/table/table.types'
import Tooltip from '@/components/ui/tooltip/Tooltip.vue'
import { useProjectDialog } from '@/composables/useProjectDialog'
import { useTaskStore } from '@/stores/useTaskStore'
import type { TaskSummary } from '@/types/api'

/*
 * 项目管理。
 *
 * 这一页现在只做「看得清 + 建得出来」：项目画像（路径、阈值、是否暂停）、每个项目下有多少任务、
 * 最近几条是什么。改名 / 暂停 / 删除都还没有接口（ARCHITECTURE.md 待办 19），
 * 所以这里**不摆假开关** —— 一个点了没反应的暂停按钮，比没有这个按钮更让人怀疑平台。
 *
 * 列表不分页：项目是平台里数量最少的东西（个位数），分页是照猫画虎。
 */
const store = useTaskStore()
const router = useRouter()
const { openDialog: openProjectDialog, lastRegistered } = useProjectDialog()

const columns: TableColumn[] = [
  { key: 'name', label: '项目' },
  { key: 'code', label: '标识', width: '152px' },
  { key: 'path', label: '工作区路径', width: '220px' },
  { key: 'level', label: '自动决策', width: '136px' },
  { key: 'status', label: '状态', width: '88px' },
  { key: 'tasks', label: '任务', align: 'right', width: '64px' },
]

const loading = ref(false)
const error = ref<string | null>(null)

const selectedId = ref<number | null>(null)
const selected = computed(
  () => store.projects.find((project) => project.id === selectedId.value) ?? null,
)

/**
 * 每个项目下的任务总数。
 *
 * 项目是个位数，所以这里用 N 次 `size=1` 的查询换「这个项目有没有在跑东西」——
 * 后端还没有按项目聚合的统计接口，而为一个列表页加一个 SQL 聚合 + 领域投影，代价比它现在值；
 * 项目数量涨到几十个时再回头加接口，那时它才划算。
 */
const taskCounts = ref<Record<number, number>>({})

/** 选中项目的最近任务。只给选中的那一个拉列表，不给每一行拉 */
const projectTasks = ref<TaskSummary[]>([])

const loadProjectTasks = async () => {
  const id = selectedId.value
  if (id === null) {
    projectTasks.value = []
    return
  }
  try {
    projectTasks.value = (await listTasks({ projectId: id, size: 8 })).items
  } catch {
    // 这是详情里的补充信息，取不到就空着，不拿红字顶掉整个面板
    projectTasks.value = []
  }
}

const load = async () => {
  loading.value = true
  error.value = null
  try {
    await store.loadProjects()
    taskCounts.value = Object.fromEntries(
      await Promise.all(
        store.projects.map(async (project) => {
          const page = await listTasks({ projectId: project.id, size: 1 })
          return [project.id, page.total] as const
        }),
      ),
    )
    // 选中项目丢了（换库、或者列表刷新后不在了）就退回第一个，别让右侧空着
    if (!store.projects.some((project) => project.id === selectedId.value)) {
      selectedId.value = store.projects[0]?.id ?? null
    }
    await loadProjectTasks()
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '无法连接后端，请确认服务已启动（默认 127.0.0.1:8787）'
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(selectedId, () => void loadProjectTasks())

/** 注册成功后：整页刷新，并把新项目选中 —— 刚建的东西应该立刻是焦点 */
watch(lastRegistered, async (project) => {
  if (!project) return
  await load()
  selectedId.value = project.id
})

/** 跳到任务页并带上这个项目的筛选：从「这个项目的任务」到「这些任务的详情」只要一步 */
const openInTasks = () => {
  if (!selected.value) return
  store.projectId = selected.value.id
  void router.push('/tasks')
}
</script>

<template>
  <div class="flex h-full min-h-0">
    <div class="flex min-w-0 flex-1 flex-col">
      <div class="flex h-toolbar shrink-0 items-center gap-3 border-b border-separator px-5">
        <h1 class="text-body font-emphasis text-label">项目</h1>
        <span class="tnum text-body text-label-tertiary">{{ store.projects.length }}</span>
        <div class="ml-auto flex items-center gap-2">
          <Tooltip content="刷新">
            <IconButton label="刷新项目" size="lg" :disabled="loading" @click="load">
              <Icon :as="RefreshCw" size="large" />
            </IconButton>
          </Tooltip>
          <Button variant="primary" size="lg" @click="openProjectDialog()">
            <Icon :as="Plus" size="inline" />注册项目
          </Button>
        </div>
      </div>

      <div class="scroll-area min-h-0 flex-1 overflow-auto">
        <Table
          :columns="columns"
          :rows="store.projects"
          :selected-id="selectedId"
          :show-empty="false"
          @select="selectedId = $event.id"
        >
          <template #cell="{ row, column }">
            <DecisionLevelBadge v-if="column.key === 'level'" :level="row.defaultDecisionLevel" />
            <StatusDot
              v-else-if="column.key === 'status'"
              :dot="row.enabled ? 'bg-success' : 'bg-neutral'"
            >
              {{ row.enabled ? '启用' : '已暂停' }}
            </StatusDot>
            <span
              v-else-if="column.key === 'tasks'"
              class="text-label-secondary group-data-[selected=true]:text-white"
            >
              {{ taskCounts[row.id as number] ?? '—' }}
            </span>
          </template>
        </Table>

        <EmptyState
          v-if="loading && store.projects.length === 0"
          title="正在加载…"
          description="从后端读取项目列表"
        />
        <EmptyState v-else-if="error" title="加载失败" :description="error" />
        <EmptyState
          v-else-if="store.projects.length === 0"
          title="还没有注册任何项目"
          description="项目决定 codex 在哪个目录跑。注册一个，任务才有地方挂 —— 路径必须是本机已存在的目录。"
        >
          <Button variant="primary" @click="openProjectDialog()">
            <Icon :as="Plus" size="inline" />注册项目
          </Button>
        </EmptyState>
      </div>
    </div>

    <aside class="w-72 shrink-0 overflow-y-auto border-l border-separator bg-window p-4">
      <template v-if="selected">
        <div class="flex items-center gap-2">
          <span class="text-caption text-label-tertiary">{{ selected.code }}</span>
          <StatusDot
            class="ml-auto"
            :dot="selected.enabled ? 'bg-success' : 'bg-neutral'"
          >
            {{ selected.enabled ? '启用' : '已暂停' }}
          </StatusDot>
        </div>
        <h2 class="mt-2 text-body font-emphasis text-label">{{ selected.name }}</h2>

        <p class="mt-3 text-caption text-label-secondary">工作区路径</p>
        <p class="mt-1 break-all rounded-panel border border-separator-soft bg-content px-2 py-1.5 font-mono text-caption text-label">
          {{ selected.path }}
        </p>

        <dl class="mt-4 space-y-2">
          <div class="flex items-center gap-2">
            <dt class="whitespace-nowrap text-caption text-label-secondary">自动决策</dt>
            <dd class="ml-auto"><DecisionLevelBadge :level="selected.defaultDecisionLevel" /></dd>
          </div>
          <div class="flex items-center gap-2">
            <dt class="whitespace-nowrap text-caption text-label-secondary">任务总数</dt>
            <dd class="tnum ml-auto text-caption text-label">
              {{ taskCounts[selected.id] ?? '—' }}
            </dd>
          </div>
        </dl>

        <p class="mt-3 text-caption text-label-tertiary">
          生效等级高于「{{ selected.defaultDecisionLevel }}」的决策一律升级人工。暂停的项目不再派发新任务，
          但录入不受影响。
        </p>

        <div class="mt-4 border-t border-separator pt-3">
          <div class="flex items-center gap-2">
            <p class="text-caption font-emphasis text-label-secondary">该项目的任务</p>
            <span v-if="projectTasks.length > 0" class="tnum text-caption text-label-tertiary">
              {{ projectTasks.length }}
            </span>
          </div>

          <p v-if="projectTasks.length === 0" class="mt-2 text-caption text-label-tertiary">
            还没有任务。任务页右上角的「+」可以给它录一条。
          </p>
          <ul v-else class="mt-2 space-y-1">
            <li
              v-for="task in projectTasks"
              :key="task.id"
              class="rounded-panel border border-separator-soft bg-content px-2.5 py-1.5"
            >
              <span class="flex items-center gap-2">
                <span class="tnum text-caption text-label-tertiary">#{{ task.id }}</span>
                <TaskStatusBadge :status="task.status" class="ml-auto" />
              </span>
              <span class="mt-0.5 block truncate text-caption text-label-secondary">
                {{ task.title }}
              </span>
            </li>
          </ul>

          <Button class="mt-3" size="sm" block @click="openInTasks">
            <Icon :as="ListChecks" size="inline" />在任务页查看
          </Button>
        </div>
      </template>

      <p v-else class="text-caption text-label-tertiary">选中一个项目查看详情</p>
    </aside>
  </div>
</template>
