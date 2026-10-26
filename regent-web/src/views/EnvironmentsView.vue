<script setup lang="ts">
import { Copy, Eye, EyeOff, Pencil, Plus, RefreshCw, Search, Trash2, X } from '@lucide/vue'
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
import Input from '@/components/ui/input/Input.vue'
import ListToolbar from '@/components/ui/list-toolbar/ListToolbar.vue'
import Table from '@/components/ui/table/Table.vue'
import type { TableColumn } from '@/components/ui/table/table.types'
import Tooltip from '@/components/ui/tooltip/Tooltip.vue'
import { useToast } from '@/composables/useToast'
import { useTaskStore } from '@/stores/useTaskStore'
import type { EnvironmentEntry } from '@/types/api'
import {
  hasInlineUrlCredentials,
  isHttpUrl,
  isMaskedEnvironmentValue,
  MASKED_ENVIRONMENT_VALUE,
} from '@/utils/environmentValue'

const store = useTaskStore()
const toast = useToast()

const columns: TableColumn[] = [
  // 环境信息是主列，占剩余宽度；名称和其他辅助列使用固定宽度。
  { key: 'scope', label: '归属项目', width: '128px', hideBelow: 'xl' },
  { key: 'description', label: '名称', width: '160px' },
  { key: 'value', label: '环境' },
  { key: 'tags', label: '标签', width: '120px', hideBelow: 'xl' },
  { key: 'key', label: '环境键', width: '96px', hideBelow: '2xl' },
  { key: 'actions', label: '', width: '96px' },
]

const entries = ref<EnvironmentEntry[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const keyword = ref('')
const revealed = ref(new Set<number>())

const dialogOpen = ref(false)
const editing = ref<EnvironmentEntry | null>(null)
const copying = ref<EnvironmentEntry | null>(null)

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
    toast.success(
      `环境信息「${deleteTarget.value.description || deleteTarget.value.key}」已删除`,
      '后续任务不会再生效',
    )
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

const projectNamesOf = (entry: EnvironmentEntry) =>
  entry.projectIds.map(
    (projectId) => projectNames.value[projectId] ?? `项目 #${projectId}`,
  )

const scopeLabel = (entry: EnvironmentEntry) =>
  entry.scope === 'GLOBAL' ? '全局' : projectNamesOf(entry).join('、')

const filteredEntries = computed(() => {
  const query = keyword.value.trim().toLocaleLowerCase('zh-CN')
  if (!query) return entries.value
  return entries.value.filter((entry) =>
    [
      entry.key,
      scopeLabel(entry),
      entry.description ?? '',
      isMaskedEnvironmentValue(entry) ? '' : entry.value,
      entry.tags.join(' '),
    ].some((value) => value.toLocaleLowerCase('zh-CN').includes(query)),
  )
})
const entryCountLabel = computed(() =>
  keyword.value.trim()
    ? `${filteredEntries.value.length} / ${entries.value.length}`
    : String(entries.value.length),
)
</script>

<template>
  <div class="flex h-full min-h-0 flex-col">
    <ListToolbar>
      <template #title>
        <h1 class="text-body font-emphasis text-label">环境</h1>
        <span class="tnum text-body text-label-tertiary">{{ entryCountLabel }}</span>
      </template>
      <template #filters>
        <div class="relative min-w-0 flex-1 sm:w-52 sm:flex-none">
          <Icon
            :as="Search"
            size="inline"
            class="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-label-tertiary"
          />
          <Input
            v-model="keyword"
            size="lg"
            placeholder="搜索名称、环境、归属项目或标签"
            aria-label="搜索环境信息"
            class="pl-7 pr-7"
          />
          <button
            v-if="keyword"
            type="button"
            class="absolute right-1 top-1/2 flex size-5 -translate-y-1/2 items-center justify-center rounded-control text-label-tertiary hover:bg-hover hover:text-label"
            aria-label="清空环境搜索"
            @click="keyword = ''"
          >
            <Icon :as="X" size="inline" />
          </button>
        </div>
      </template>
      <template #actions>
        <Tooltip content="刷新">
          <IconButton label="刷新环境信息" size="lg" :disabled="loading" @click="load">
            <Icon :as="RefreshCw" size="large" />
          </IconButton>
        </Tooltip>
        <Button variant="primary" size="lg" @click="openCreate">
          <Icon :as="Plus" size="inline" />新增环境信息
        </Button>
      </template>
    </ListToolbar>

    <div class="scroll-area min-h-0 flex-1 overflow-auto">
      <Table
        :columns="columns"
        :rows="filteredEntries"
        :show-empty="false"
        @select="openDetail($event as EnvironmentEntry)"
      >
        <template #cell="{ row, column }">
          <Badge
            v-if="column.key === 'scope'"
            :tone="row.scope === 'GLOBAL' ? 'accent' : 'neutral'"
            class="max-w-full truncate"
            :title="scopeLabel(row as EnvironmentEntry)"
          >
            {{ scopeLabel(row as EnvironmentEntry) }}
          </Badge>

          <span
            v-else-if="column.key === 'description'"
            class="block min-w-0 truncate text-caption text-label"
            :title="row.description || undefined"
          >
            {{ row.description || '未填写名称' }}
          </span>

          <span
            v-else-if="column.key === 'key'"
            class="block truncate font-mono text-caption text-label"
          >
            {{ row.key }}
          </span>

          <div v-else-if="column.key === 'value'" class="flex min-w-0 items-center gap-1">
            <span
              v-if="
                isMaskedEnvironmentValue(row as EnvironmentEntry) &&
                !revealed.has(row.id as number)
              "
              class="min-w-0 flex-1 truncate font-mono text-caption text-label-tertiary"
              title="密码值已脱敏"
            >
              {{ MASKED_ENVIRONMENT_VALUE }}
            </span>
            <a
              v-else-if="
                isHttpUrl(String(row.value)) &&
                !hasInlineUrlCredentials(String(row.value))
              "
              :href="String(row.value).trim()"
              target="_blank"
              rel="noopener noreferrer"
              class="min-w-0 flex-1 truncate font-mono text-caption text-accent hover:underline"
              :title="String(row.value)"
              @click.stop
            >
              {{ row.value }}
            </a>
            <span
              v-else
              class="min-w-0 flex-1 truncate font-mono text-caption text-label-secondary"
              :title="String(row.value)"
            >
              {{ row.value === '' ? '空值' : row.value }}
            </span>
            <IconButton
              v-if="
                isMaskedEnvironmentValue(row as EnvironmentEntry) &&
                String(row.value) !== ''
              "
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
      />
      <EmptyState
        v-else-if="filteredEntries.length === 0"
        title="没有匹配的环境信息"
        description="换个名称、环境内容、归属项目或标签再试。"
      >
        <Button @click="keyword = ''">清除搜索</Button>
      </EmptyState>
    </div>

    <EnvironmentDetailDialog
      v-model:open="detailOpen"
      :entry="detailEntry"
      :project-names="
        detailEntry?.scope === 'PROJECT'
          ? projectNamesOf(detailEntry)
          : []
      "
      @edit="editFromDetail"
      @copy="copyFromDetail"
    />

    <EnvironmentDialog
      v-model:open="dialogOpen"
      :entry="editing"
      :copy-source="copying"
      @saved="saved"
    />

    <Dialog
      v-model:open="deleteOpen"
      title="删除环境信息？"
      :description="
        deleteTarget
          ? `「${deleteTarget.description || deleteTarget.key}」后续任务将不再读取。`
          : ''
      "
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
