<script setup lang="ts">
import { Copy, Eye, EyeOff, KeyRound, Pencil, Plus, RefreshCw, Trash2 } from '@lucide/vue'
import { computed, onMounted, ref } from 'vue'

import { deleteEnvironment, listEnvironments } from '@/api/environments'
import { ApiError } from '@/api/http'
import EnvironmentDetailDialog from '@/components/business/EnvironmentDetailDialog.vue'
import EnvironmentDialog from '@/components/business/EnvironmentDialog.vue'
import Badge from '@/components/ui/badge/Badge.vue'
import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import EmptyState from '@/components/ui/empty-state/EmptyState.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import IconButton from '@/components/ui/icon-button/IconButton.vue'
import Table from '@/components/ui/table/Table.vue'
import type { TableColumn } from '@/components/ui/table/table.types'
import Tooltip from '@/components/ui/tooltip/Tooltip.vue'
import { useToast } from '@/composables/useToast'
import { useTaskStore } from '@/stores/useTaskStore'
import type { EnvironmentEntry } from '@/types/api'
import { nextEnvironmentCopyKey } from '@/utils/environmentCopy'

const store = useTaskStore()
const toast = useToast()

const columns: TableColumn[] = [
  { key: 'scope', label: '归属', width: '150px' },
  { key: 'key', label: '环境键', width: '220px' },
  { key: 'value', label: '环境信息', width: '260px' },
  { key: 'tags', label: '标签', width: '180px' },
  { key: 'description', label: '描述' },
  { key: 'actions', label: '', width: '132px' },
]

const entries = ref<EnvironmentEntry[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const revealed = ref(new Set<number>())

const dialogOpen = ref(false)
const editing = ref<EnvironmentEntry | null>(null)
const copying = ref<EnvironmentEntry | null>(null)
const copyKey = computed(() =>
  copying.value ? nextEnvironmentCopyKey(copying.value, entries.value) : '',
)

const detailOpen = ref(false)
const detailEntry = ref<EnvironmentEntry | null>(null)

const deleteOpen = ref(false)
const deleteTarget = ref<EnvironmentEntry | null>(null)
const deleting = ref(false)

const projectNames = computed(() =>
  Object.fromEntries(store.projects.map((project) => [project.id, project.name])),
)

function messageOf(e: unknown): string {
  if (e instanceof ApiError) return e.message
  return '无法连接后端，请确认服务已启动（默认 127.0.0.1:8787）'
}

const load = async () => {
  loading.value = true
  error.value = null
  try {
    const [items] = await Promise.all([listEnvironments(), store.loadProjects()])
    entries.value = items
    revealed.value = new Set([...revealed.value].filter((id) => items.some((item) => item.id === id)))
  } catch (e) {
    error.value = messageOf(e)
  } finally {
    loading.value = false
  }
}

onMounted(load)

const openCreate = () => {
  editing.value = null
  copying.value = null
  dialogOpen.value = true
}

const openEdit = (entry: EnvironmentEntry) => {
  editing.value = entry
  copying.value = null
  dialogOpen.value = true
}

const openCopy = (entry: EnvironmentEntry) => {
  editing.value = null
  copying.value = entry
  dialogOpen.value = true
}

const openDetail = (entry: EnvironmentEntry) => {
  detailEntry.value = entry
  detailOpen.value = true
}

const editFromDetail = (entry: EnvironmentEntry) => {
  detailOpen.value = false
  openEdit(entry)
}

const copyFromDetail = (entry: EnvironmentEntry) => {
  detailOpen.value = false
  openCopy(entry)
}

const saved = async () => {
  await load()
}

const askDelete = (entry: EnvironmentEntry) => {
  deleteTarget.value = entry
  deleteOpen.value = true
}

const confirmDelete = async () => {
  if (!deleteTarget.value || deleting.value) return
  deleting.value = true
  try {
    await deleteEnvironment(deleteTarget.value.id)
    toast.success(`环境键「${deleteTarget.value.key}」已删除`, '后续任务不会再生效')
    deleteOpen.value = false
    deleteTarget.value = null
    await load()
  } catch (e) {
    toast.failure('删除失败', messageOf(e))
  } finally {
    deleting.value = false
  }
}

const toggleValue = (id: number) => {
  const next = new Set(revealed.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  revealed.value = next
}

const scopeLabel = (entry: EnvironmentEntry) =>
  entry.scope === 'GLOBAL' ? '全局' : projectNames.value[entry.projectId ?? -1] ?? `项目 #${entry.projectId}`
</script>

<template>
  <div class="flex h-full min-h-0 flex-col">
    <div class="flex h-toolbar shrink-0 items-center gap-3 border-b border-separator px-5">
      <h1 class="text-body font-emphasis text-label">环境</h1>
      <span class="tnum text-body text-label-tertiary">{{ entries.length }}</span>
      <div class="ml-auto flex items-center gap-2">
        <Tooltip content="刷新">
          <IconButton label="刷新环境信息" size="lg" :disabled="loading" @click="load">
            <Icon :as="RefreshCw" size="large" />
          </IconButton>
        </Tooltip>
        <Button variant="primary" size="lg" @click="openCreate">
          <Icon :as="Plus" size="inline" />新增环境信息
        </Button>
      </div>
    </div>

    <div class="scroll-area min-h-0 flex-1 overflow-auto">
      <Table
        :columns="columns"
        :rows="entries"
        :show-empty="false"
        @select="openDetail($event as EnvironmentEntry)"
      >
        <template #cell="{ row, column }">
          <Badge
            v-if="column.key === 'scope'"
            :tone="row.scope === 'GLOBAL' ? 'accent' : 'neutral'"
          >
            {{ scopeLabel(row as EnvironmentEntry) }}
          </Badge>

          <span
            v-else-if="column.key === 'key'"
            class="block truncate font-mono text-caption text-label"
          >
            {{ row.key }}
          </span>

          <div v-else-if="column.key === 'value'" class="flex min-w-0 items-center gap-1">
            <span
              class="min-w-0 flex-1 truncate font-mono text-caption text-label-secondary"
              :title="revealed.has(row.id as number) ? String(row.value) : undefined"
            >
              {{ row.value === '' ? '空值' : revealed.has(row.id as number) ? row.value : '••••••••' }}
            </span>
            <IconButton
              :label="revealed.has(row.id as number) ? '隐藏环境信息' : '显示环境信息'"
              size="sm"
              @click.stop="toggleValue(row.id as number)"
            >
              <Icon :as="revealed.has(row.id as number) ? EyeOff : Eye" size="inline" />
            </IconButton>
          </div>

          <div v-else-if="column.key === 'tags'" class="flex min-w-0 items-center gap-1">
            <Badge v-for="tag in (row.tags as string[]).slice(0, 2)" :key="tag" tone="success">
              {{ tag }}
            </Badge>
            <span v-if="(row.tags as string[]).length > 2" class="text-caption text-label-tertiary">
              +{{ (row.tags as string[]).length - 2 }}
            </span>
            <span v-if="(row.tags as string[]).length === 0" class="text-label-tertiary">—</span>
          </div>

          <span
            v-else-if="column.key === 'description'"
            class="block truncate text-caption text-label-secondary"
          >
            {{ row.description || '—' }}
          </span>

          <div v-else-if="column.key === 'actions'" class="flex justify-end gap-1">
            <Tooltip content="拷贝">
              <IconButton label="拷贝环境信息" size="sm" @click.stop="openCopy(row as EnvironmentEntry)">
                <Icon :as="Copy" size="inline" />
              </IconButton>
            </Tooltip>
            <Tooltip content="编辑">
              <IconButton label="编辑环境信息" size="sm" @click.stop="openEdit(row as EnvironmentEntry)">
                <Icon :as="Pencil" size="inline" />
              </IconButton>
            </Tooltip>
            <Tooltip content="删除">
              <IconButton label="删除环境信息" size="sm" @click.stop="askDelete(row as EnvironmentEntry)">
                <Icon :as="Trash2" size="inline" />
              </IconButton>
            </Tooltip>
          </div>
        </template>
      </Table>

      <EmptyState
        v-if="loading && entries.length === 0"
        title="正在加载…"
        description="从后端读取环境信息"
      />
      <EmptyState v-else-if="error" title="加载失败" :description="error" />
      <EmptyState
        v-else-if="entries.length === 0"
        title="还没有环境信息"
        description="新增数据库地址、账号、密钥或其他任务执行时需要的信息。"
      >
        <Button variant="primary" @click="openCreate">
          <Icon :as="KeyRound" size="inline" />新增环境信息
        </Button>
      </EmptyState>
    </div>

    <EnvironmentDetailDialog
      v-model:open="detailOpen"
      :entry="detailEntry"
      :project-name="
        detailEntry?.scope === 'PROJECT'
          ? projectNames[detailEntry.projectId ?? -1] ?? `项目 #${detailEntry.projectId}`
          : ''
      "
      @edit="editFromDetail"
      @copy="copyFromDetail"
    />

    <EnvironmentDialog
      v-model:open="dialogOpen"
      :entry="editing"
      :copy-source="copying"
      :copy-key="copyKey"
      @saved="saved"
    />

    <Dialog
      v-model:open="deleteOpen"
      title="删除环境信息？"
      :description="deleteTarget ? `环境键「${deleteTarget.key}」后续任务将不再读取。` : ''"
    >
      <p class="text-body text-label-secondary">
        删除只影响以后开始的执行，不会改动已经完成的任务。
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
