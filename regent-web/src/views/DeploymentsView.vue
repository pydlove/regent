<script setup lang="ts">
import {
  AlertTriangle,
  Pencil,
  Play,
  Plus,
  RefreshCw,
  ScrollText,
  Search,
  ServerCog,
  Square,
  Trash2,
  X,
} from '@lucide/vue'
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { ApiError } from '@/api/http'
import {
  deleteDeployment,
  listDeployments,
  runDeployment,
  stopDeployment,
} from '@/api/deployments'
import DeploymentDialog from '@/components/business/DeploymentDialog.vue'
import DeploymentRunConfirmDialog from '@/components/business/DeploymentRunConfirmDialog.vue'
import DeploymentRunsDialog from '@/components/business/DeploymentRunsDialog.vue'
import DeploymentRunStatusBadge from '@/components/business/DeploymentRunStatusBadge.vue'
import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import EmptyState from '@/components/ui/empty-state/EmptyState.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import IconButton from '@/components/ui/icon-button/IconButton.vue'
import Input from '@/components/ui/input/Input.vue'
import ListToolbar from '@/components/ui/list-toolbar/ListToolbar.vue'
import Table from '@/components/ui/table/Table.vue'
import type { TableColumn } from '@/components/ui/table/table.types'
import Tooltip from '@/components/ui/tooltip/Tooltip.vue'
import { useToast } from '@/composables/useToast'
import { useTaskStore } from '@/stores/useTaskStore'
import type { DeploymentDetail, DeploymentSummary } from '@/types/api'
import { formatDateTime, formatRelative } from '@/utils/format'

const store = useTaskStore()
const route = useRoute()
const toast = useToast()
const deployments = ref<DeploymentSummary[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const keyword = ref('')
const runningIds = ref<Set<number>>(new Set())

const dialogOpen = ref(false)
const editing = ref<DeploymentSummary | null>(null)
const confirmOpen = ref(false)
const confirmTarget = ref<DeploymentSummary | null>(null)
const startingConfirm = ref(false)
const runsOpen = ref(false)
const runsTarget = ref<DeploymentSummary | null>(null)
const runsInitialId = ref<number | null>(null)
const deleteOpen = ref(false)
const deleteTarget = ref<DeploymentSummary | null>(null)
const deleting = ref(false)
const stopOpen = ref(false)
const stopTarget = ref<DeploymentSummary | null>(null)
const stopping = ref(false)
const routeTaskId = computed(() => {
  const value = Number(route.query.taskId)
  return Number.isInteger(value) && value > 0 ? value : null
})

const columns: TableColumn[] = [
  { key: 'name', label: '部署名称' },
  { key: 'project', label: '项目', width: '120px', hideBelow: 'lg' },
  { key: 'server', label: '服务器', width: '176px', hideBelow: 'md' },
  { key: 'remoteDirectory', label: '远程目录', width: '144px', hideBelow: 'xl' },
  { key: 'status', label: '最近部署', width: '92px' },
  { key: 'updated', label: '更新时间', width: '88px', hideBelow: 'xl' },
  { key: 'actions', label: '', align: 'right', width: '132px' },
]

const projectNamesOf = (deployment: DeploymentSummary) =>
  deployment.projectNames.length > 0
    ? deployment.projectNames.join('、')
    : deployment.projectName

const filtered = computed(() => {
  const search = keyword.value.trim().toLowerCase()
  if (!search) return deployments.value
  return deployments.value.filter((deployment) =>
    [
      deployment.name,
      deployment.serverName,
      projectNamesOf(deployment),
      deployment.host,
      deployment.username,
      deployment.remoteDirectory,
      deployment.scriptFilename,
    ].some((value) => value.toLowerCase().includes(search)),
  )
})

const hasActiveRuns = computed(() =>
  deployments.value.some(
    (deployment) =>
      deployment.latestRunStatus === 'QUEUED' ||
      deployment.latestRunStatus === 'RUNNING',
  ),
)

function messageOf(e: unknown): string {
  if (e instanceof ApiError) return e.message
  return '无法连接后端，请确认服务已启动（默认 127.0.0.1:8787）'
}

const load = async (quiet = false) => {
  if (!quiet) {
    loading.value = true
    error.value = null
  }
  try {
    deployments.value = await listDeployments(store.projectId)
  } catch (e) {
    if (!quiet) {
      error.value = messageOf(e)
      deployments.value = []
    }
  } finally {
    if (!quiet) loading.value = false
  }
}

watch(
  () => store.projectId,
  () => {
    void load()
  },
  { immediate: true },
)

watch(
  () => route.query.projectId,
  (value) => {
    const projectId = Number(value)
    if (Number.isInteger(projectId) && projectId > 0) store.projectId = projectId
  },
  { immediate: true },
)

let pollTimer: number | undefined
watch(
  hasActiveRuns,
  (active) => {
    window.clearInterval(pollTimer)
    pollTimer = undefined
    if (active) pollTimer = window.setInterval(() => void load(true), 2_000)
  },
  { immediate: true },
)

onUnmounted(() => window.clearInterval(pollTimer))

const openCreate = () => {
  editing.value = null
  dialogOpen.value = true
}

const openEdit = (deployment: DeploymentSummary) => {
  editing.value = deployment
  dialogOpen.value = true
}

const onSaved = (deployment: DeploymentDetail) => {
  deployments.value = deployments.value.some((item) => item.id === deployment.id)
    ? deployments.value.map((item) => (item.id === deployment.id ? deployment : item))
    : [deployment, ...deployments.value]
  void load(true)
}

const setRunning = (id: number, running: boolean) => {
  const next = new Set(runningIds.value)
  if (running) next.add(id)
  else next.delete(id)
  runningIds.value = next
}

const isActive = (deployment: DeploymentSummary) =>
  deployment.latestRunStatus === 'QUEUED' || deployment.latestRunStatus === 'RUNNING'

const openRuns = (deployment: DeploymentSummary, initialRunId: number | null = null) => {
  runsTarget.value = deployment
  runsInitialId.value = initialRunId
  runsOpen.value = true
}

const startRun = async (
  deployment: DeploymentSummary,
  projectId: number,
  taskId: number | null = null,
): Promise<boolean> => {
  if (runningIds.value.has(deployment.id) || isActive(deployment)) return false
  setRunning(deployment.id, true)
  try {
    const run = await runDeployment(deployment.id, projectId, taskId)
    deployments.value = deployments.value.map((item) =>
      item.id === deployment.id
        ? {
            ...item,
            latestRunStatus: run.status,
            latestRunAt: run.queuedAt,
          }
        : item,
    )
    openRuns(deployment, run.id)
    toast.success(`部署「${deployment.name}」已开始`, '日志会在执行记录中持续刷新')
    return true
  } catch (e) {
    toast.failure(`部署「${deployment.name}」未能开始`, messageOf(e))
    return false
  } finally {
    setRunning(deployment.id, false)
  }
}

const requestRun = (deployment: DeploymentSummary) => {
  confirmTarget.value = deployment
  confirmOpen.value = true
}

const confirmRun = async (payload: { projectId: number; taskId: number | null }) => {
  const deployment = confirmTarget.value
  if (!deployment || startingConfirm.value) return
  startingConfirm.value = true
  const started = await startRun(deployment, payload.projectId, payload.taskId)
  startingConfirm.value = false
  if (started) {
    confirmOpen.value = false
    confirmTarget.value = null
  }
}

const requestStop = (deployment: DeploymentSummary) => {
  stopTarget.value = deployment
  stopOpen.value = true
}

const confirmStop = async () => {
  const deployment = stopTarget.value
  if (!deployment || stopping.value) return
  stopping.value = true
  try {
    const run = await stopDeployment(deployment.id)
    deployments.value = deployments.value.map((item) =>
      item.id === deployment.id
        ? {
            ...item,
            latestRunStatus: run.status,
            latestRunAt: run.finishedAt ?? run.queuedAt,
          }
        : item,
    )
    stopOpen.value = false
    stopTarget.value = null
    toast.success(`正在停止部署「${deployment.name}」`, run.summary || '停止请求已提交')
    void load(true)
  } catch (e) {
    toast.failure(`部署「${deployment.name}」未能停止`, messageOf(e))
  } finally {
    stopping.value = false
  }
}

const requestDelete = (deployment: DeploymentSummary) => {
  deleteTarget.value = deployment
  deleteOpen.value = true
}

const confirmDelete = async () => {
  const deployment = deleteTarget.value
  if (!deployment || deleting.value) return
  deleting.value = true
  try {
    await deleteDeployment(deployment.id)
    deleteOpen.value = false
    deployments.value = deployments.value.filter((item) => item.id !== deployment.id)
    toast.success(`部署「${deployment.name}」已删除`, '历史执行记录仍会保留')
  } catch (e) {
    toast.failure(`部署「${deployment.name}」删除失败`, messageOf(e))
  } finally {
    deleting.value = false
    deleteTarget.value = null
  }
}
</script>

<template>
  <div class="flex h-full min-h-0 flex-col">
    <ListToolbar>
      <template #title>
        <h1 class="text-body font-emphasis text-label">部署</h1>
        <span class="tnum text-caption text-label-tertiary">{{ deployments.length }} 个配置</span>
      </template>
      <template #actions>
        <Tooltip content="刷新部署列表">
          <IconButton label="刷新部署列表" size="lg" :disabled="loading" @click="load()">
            <Icon :as="RefreshCw" size="large" :class="loading ? 'animate-spin' : ''" />
          </IconButton>
        </Tooltip>
        <Button variant="primary" size="lg" @click="openCreate">
          <Icon :as="Plus" size="inline" />新建部署
        </Button>
      </template>
      <template #filters>
        <div class="relative min-w-0 flex-1 sm:w-64 sm:flex-none">
          <Icon
            :as="Search"
            size="inline"
            class="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-label-tertiary"
          />
          <Input
            v-model="keyword"
            size="lg"
            placeholder="搜索名称、服务器或脚本"
            aria-label="搜索部署"
            class="pl-7 pr-7"
          />
          <button
            v-if="keyword"
            type="button"
            class="absolute right-1 top-1/2 flex size-5 -translate-y-1/2 items-center justify-center rounded-control text-label-tertiary hover:bg-hover hover:text-label"
            aria-label="清空部署搜索"
            @click="keyword = ''"
          >
            <Icon :as="X" size="inline" />
          </button>
        </div>
      </template>
    </ListToolbar>

    <div class="scroll-area min-h-0 flex-1 overflow-auto">
      <Table
        :columns="columns"
        :rows="filtered"
        :show-empty="false"
        touch-rows
        @select="openRuns"
      >
        <template #cell="{ row, column }">
          <span v-if="column.key === 'name'" class="flex min-w-0 items-center gap-2">
            <span class="grid size-6 shrink-0 place-items-center rounded-control bg-accent/10 text-accent">
              <Icon :as="ServerCog" size="inline" />
            </span>
            <span
              class="min-w-0 truncate font-emphasis text-label"
              :title="`${row.name} · ${row.scriptFilename}`"
            >
              {{ row.name }}
            </span>
            <span
              class="hidden min-w-0 truncate text-caption text-label-tertiary xl:inline"
              :title="row.scriptFilename"
            >
              {{ row.scriptFilename }}
            </span>
          </span>
          <span
            v-else-if="column.key === 'project'"
            class="truncate text-caption text-label-secondary"
            :title="projectNamesOf(row as DeploymentSummary)"
          >
            {{ projectNamesOf(row as DeploymentSummary) }}
          </span>
          <span
            v-else-if="column.key === 'server'"
            class="truncate text-caption text-label-secondary"
            :title="`${row.serverName} · ${row.username}@${row.host}:${row.port}`"
          >
            {{ row.serverName }}
          </span>
          <span
            v-else-if="column.key === 'remoteDirectory'"
            class="truncate font-mono text-caption text-label-tertiary"
            :title="row.remoteDirectory"
          >
            {{ row.remoteDirectory }}
          </span>
          <DeploymentRunStatusBadge v-else-if="column.key === 'status'" :status="row.latestRunStatus" />
          <span
            v-else-if="column.key === 'updated'"
            class="tnum text-caption text-label-tertiary"
            :title="formatDateTime(row.updatedAt)"
          >
            {{ formatRelative(row.updatedAt) }}
          </span>
          <div v-else-if="column.key === 'actions'" class="flex justify-end gap-0.5" @click.stop>
            <Tooltip :content="isActive(row) ? '停止部署' : '立即部署'">
              <span class="inline-flex">
                <IconButton
                  :label="isActive(row) ? '停止部署' : '立即部署'"
                  size="sm"
                  :disabled="runningIds.has(row.id) || (stopping && stopTarget?.id === row.id)"
                  @click.stop="isActive(row) ? requestStop(row) : requestRun(row)"
                >
                  <Icon :as="isActive(row) ? Square : Play" size="inline" />
                </IconButton>
              </span>
            </Tooltip>
            <Tooltip content="查看执行日志">
              <IconButton label="查看部署日志" size="sm" @click.stop="openRuns(row)">
                <Icon :as="ScrollText" size="inline" />
              </IconButton>
            </Tooltip>
            <Tooltip content="编辑">
              <IconButton label="编辑部署" size="sm" @click.stop="openEdit(row)">
                <Icon :as="Pencil" size="inline" />
              </IconButton>
            </Tooltip>
            <Tooltip content="删除">
              <IconButton
                label="删除部署"
                size="sm"
                variant="ghost"
                @click.stop="requestDelete(row)"
              >
                <Icon :as="Trash2" size="inline" />
              </IconButton>
            </Tooltip>
          </div>
        </template>
      </Table>

      <EmptyState
        v-if="loading && deployments.length === 0"
        title="正在加载…"
        description="从后端读取部署配置"
      />
      <EmptyState v-else-if="error" title="加载失败" :description="error">
        <Button size="sm" @click="load()">重新加载</Button>
      </EmptyState>
      <EmptyState
        v-else-if="deployments.length === 0"
        title="还没有部署配置"
        :description="
          store.projectId === null
            ? '录入服务器信息并上传部署脚本，之后可以在这里一键执行并查看日志。'
            : '当前项目还没有部署配置，可以录入服务器信息并上传部署脚本。'
        "
      >
        <Button variant="primary" size="sm" @click="openCreate">
          <Icon :as="Plus" size="inline" />新建部署
        </Button>
      </EmptyState>
      <EmptyState
        v-else-if="filtered.length === 0"
        title="没有匹配的部署"
        description="换个名称、服务器地址或脚本文案再试。"
      >
        <Button size="sm" @click="keyword = ''">清除搜索</Button>
      </EmptyState>
    </div>

    <DeploymentDialog
      v-model:open="dialogOpen"
      :deployment="editing"
      :projects="store.projects"
      :default-project-id="store.projectId"
      @saved="onSaved"
    />
    <DeploymentRunConfirmDialog
      v-model:open="confirmOpen"
      :deployment="confirmTarget"
      :default-task-id="routeTaskId"
      :starting="startingConfirm"
      @confirm="confirmRun"
    />
    <DeploymentRunsDialog
      v-model:open="runsOpen"
      :deployment="runsTarget"
      :initial-run-id="runsInitialId"
    />

    <Dialog
      v-model:open="deleteOpen"
      title="删除部署配置？"
      :description="deleteTarget ? `「${deleteTarget.name}」将从部署列表中移除。` : ''"
      :icon="AlertTriangle"
      icon-tone="danger"
    >
      <p class="text-caption text-label-secondary">
        历史执行记录会保留；已经执行的服务器变更不会回滚。
      </p>
      <template #footer>
        <Button :disabled="deleting" @click="deleteOpen = false">取消</Button>
        <Button variant="danger" :disabled="deleting" @click="confirmDelete">
          {{ deleting ? '删除中…' : '删除' }}
        </Button>
      </template>
    </Dialog>

    <Dialog
      v-model:open="stopOpen"
      title="停止当前部署？"
      :description="stopTarget ? `将终止「${stopTarget.name}」正在执行的 SSH 或本机发布进程。` : ''"
      :icon="AlertTriangle"
      icon-tone="danger"
    >
      <p class="text-caption text-label-secondary">
        已经完成的服务器变更不会自动回滚；停止后请查看执行日志确认停在哪一步。
      </p>
      <template #footer>
        <Button :disabled="stopping" @click="stopOpen = false">继续执行</Button>
        <Button variant="danger" :disabled="stopping" @click="confirmStop">
          {{ stopping ? '停止中…' : '停止部署' }}
        </Button>
      </template>
    </Dialog>
  </div>
</template>
