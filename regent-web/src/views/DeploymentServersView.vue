<script setup lang="ts">
import {
  AlertTriangle,
  KeyRound,
  Pencil,
  Plus,
  RefreshCw,
  Search,
  Server,
  Trash2,
  X,
} from '@lucide/vue'
import { computed, ref, watch } from 'vue'

import {
  deleteDeploymentServer,
  listDeploymentServers,
} from '@/api/deploymentServers'
import { ApiError } from '@/api/http'
import DeploymentServerDialog from '@/components/business/DeploymentServerDialog.vue'
import Badge from '@/components/ui/badge/Badge.vue'
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
import type { DeploymentServer } from '@/types/api'
import { formatDateTime, formatRelative } from '@/utils/format'

const store = useTaskStore()
const toast = useToast()
const servers = ref<DeploymentServer[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const keyword = ref('')
const dialogOpen = ref(false)
const editing = ref<DeploymentServer | null>(null)
const deleteOpen = ref(false)
const deleteTarget = ref<DeploymentServer | null>(null)
const deleting = ref(false)

const columns: TableColumn[] = [
  { key: 'name', label: '服务器名称' },
  { key: 'project', label: '项目', width: '120px', hideBelow: 'lg' },
  { key: 'connection', label: 'SSH', width: '220px' },
  { key: 'auth', label: '认证', width: '108px', hideBelow: 'md' },
  { key: 'usage', label: '关联部署', width: '88px', hideBelow: 'xl' },
  { key: 'updated', label: '更新时间', width: '88px', hideBelow: 'xl' },
  { key: 'actions', label: '', align: 'right', width: '88px' },
]

const filtered = computed(() => {
  const search = keyword.value.trim().toLowerCase()
  if (!search) return servers.value
  return servers.value.filter((server) =>
    [server.name, server.host, server.username].some((value) =>
      value.toLowerCase().includes(search),
    ),
  )
})

const deleteDescription = computed(() => {
  const target = deleteTarget.value
  if (!target) return ''
  return target.deploymentCount > 0
    ? `将删除「${target.name}」及 ${target.deploymentCount} 个关联部署配置，历史执行记录会保留。`
    : `「${target.name}」将从服务器列表中移除。`
})

function messageOf(value: unknown): string {
  return value instanceof ApiError
    ? value.message
    : '无法连接后端，请确认服务已启动（默认 127.0.0.1:8787）'
}

const load = async (quiet = false) => {
  if (!quiet) {
    loading.value = true
    error.value = null
  }
  try {
    servers.value = await listDeploymentServers(store.projectId)
  } catch (value) {
    if (!quiet) {
      error.value = messageOf(value)
      servers.value = []
    }
  } finally {
    if (!quiet) loading.value = false
  }
}

watch(
  () => store.projectId,
  () => void load(),
  { immediate: true },
)

const openCreate = () => {
  editing.value = null
  dialogOpen.value = true
}

const openEdit = (server: DeploymentServer) => {
  editing.value = server
  dialogOpen.value = true
}

const onSaved = (server: DeploymentServer) => {
  servers.value = servers.value.some((item) => item.id === server.id)
    ? servers.value.map((item) => (item.id === server.id ? server : item))
    : [server, ...servers.value]
  void load(true)
}

const requestDelete = (server: DeploymentServer) => {
  deleteTarget.value = server
  deleteOpen.value = true
}

const confirmDelete = async () => {
  const target = deleteTarget.value
  if (!target || deleting.value) return
  deleting.value = true
  try {
    await deleteDeploymentServer(target.id)
    servers.value = servers.value.filter((server) => server.id !== target.id)
    deleteOpen.value = false
    deleteTarget.value = null
    toast.success(
      `服务器「${target.name}」已删除`,
      target.deploymentCount > 0 ? `同时移除了 ${target.deploymentCount} 个关联部署配置` : undefined,
    )
  } catch (value) {
    toast.failure(`服务器「${target.name}」删除失败`, messageOf(value))
  } finally {
    deleting.value = false
  }
}
</script>

<template>
  <div class="flex h-full min-h-0 flex-col">
    <ListToolbar>
      <template #title>
        <h1 class="text-body font-emphasis text-label">服务器</h1>
        <span class="tnum text-caption text-label-tertiary">{{ servers.length }} 台</span>
      </template>
      <template #actions>
        <Tooltip content="刷新服务器列表">
          <IconButton label="刷新服务器列表" size="lg" :disabled="loading" @click="load()">
            <Icon :as="RefreshCw" size="large" :class="loading ? 'animate-spin' : ''" />
          </IconButton>
        </Tooltip>
        <Button variant="primary" size="lg" @click="openCreate">
          <Icon :as="Plus" size="inline" />新建服务器
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
            placeholder="搜索名称、主机或用户"
            aria-label="搜索服务器"
            class="pl-7 pr-7"
          />
          <button
            v-if="keyword"
            type="button"
            class="absolute right-1 top-1/2 flex size-5 -translate-y-1/2 items-center justify-center rounded-control text-label-tertiary hover:bg-hover hover:text-label"
            aria-label="清空服务器搜索"
            @click="keyword = ''"
          >
            <Icon :as="X" size="inline" />
          </button>
        </div>
      </template>
    </ListToolbar>

    <div class="scroll-area min-h-0 flex-1 overflow-auto">
      <Table :columns="columns" :rows="filtered" :show-empty="false" touch-rows>
        <template #cell="{ row, column }">
          <span v-if="column.key === 'name'" class="flex min-w-0 items-center gap-2">
            <span class="grid size-6 shrink-0 place-items-center rounded-control bg-accent/10 text-accent">
              <Icon :as="Server" size="inline" />
            </span>
            <span class="truncate font-emphasis text-label">{{ row.name }}</span>
          </span>
          <span
            v-else-if="column.key === 'project'"
            class="truncate text-caption text-label-secondary"
          >
            {{ store.projects.find((project) => project.id === row.projectId)?.name ?? `项目 #${row.projectId}` }}
          </span>
          <span
            v-else-if="column.key === 'connection'"
            class="truncate font-mono text-caption text-label-secondary"
            :title="`${row.username}@${row.host}:${row.port}`"
          >
            {{ row.username }}@{{ row.host }}:{{ row.port }}
          </span>
          <span v-else-if="column.key === 'auth'" class="flex items-center gap-1.5">
            <Badge :tone="row.authType === 'PASSWORD' ? 'warning' : 'neutral'">
              {{ row.authType === 'PASSWORD' ? '密码' : '密钥' }}
            </Badge>
            <Tooltip v-if="row.authType === 'PASSWORD' && row.credentialConfigured" content="密码密文已保存">
              <Icon :as="KeyRound" size="inline" class="text-success" />
            </Tooltip>
          </span>
          <span v-else-if="column.key === 'usage'" class="tnum text-caption text-label-secondary">
            {{ row.deploymentCount }} 个
          </span>
          <span
            v-else-if="column.key === 'updated'"
            class="tnum text-caption text-label-tertiary"
            :title="formatDateTime(row.updatedAt)"
          >
            {{ formatRelative(row.updatedAt) }}
          </span>
          <div v-else-if="column.key === 'actions'" class="flex justify-end gap-0.5">
            <Tooltip content="编辑服务器">
              <IconButton label="编辑服务器" size="sm" @click.stop="openEdit(row)">
                <Icon :as="Pencil" size="inline" />
              </IconButton>
            </Tooltip>
            <Tooltip content="删除服务器">
              <IconButton
                label="删除服务器"
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
        v-if="loading && servers.length === 0"
        title="正在加载…"
        description="从后端读取可复用服务器"
      />
      <EmptyState v-else-if="error" title="加载失败" :description="error">
        <Button size="sm" @click="load()">重新加载</Button>
      </EmptyState>
      <EmptyState
        v-else-if="servers.length === 0"
        title="还没有服务器"
        description="先录入 SSH 连接信息，多个部署脚本就可以直接复用。"
      >
        <Button variant="primary" size="sm" @click="openCreate">
          <Icon :as="Plus" size="inline" />新建服务器
        </Button>
      </EmptyState>
      <EmptyState
        v-else-if="filtered.length === 0"
        title="没有匹配的服务器"
        description="换个名称、主机地址或登录用户再试。"
      >
        <Button size="sm" @click="keyword = ''">清除搜索</Button>
      </EmptyState>
    </div>

    <DeploymentServerDialog
      v-model:open="dialogOpen"
      :server="editing"
      :projects="store.projects"
      :default-project-id="store.projectId"
      @saved="onSaved"
    />

    <Dialog
      v-model:open="deleteOpen"
      title="删除服务器？"
      :description="deleteDescription"
      :icon="AlertTriangle"
      icon-tone="danger"
    >
      <p class="text-caption text-label-secondary">
        关联部署正在执行时不能删除服务器；请等执行结束后再试。
      </p>
      <template #footer>
        <Button :disabled="deleting" @click="deleteOpen = false">取消</Button>
        <Button variant="danger" :disabled="deleting" @click="confirmDelete">
          {{ deleting ? '删除中…' : '删除' }}
        </Button>
      </template>
    </Dialog>
  </div>
</template>
