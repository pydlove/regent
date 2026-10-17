<script setup lang="ts">
import { Blocks, Pencil, Plus, RefreshCw } from '@lucide/vue'
import { onMounted, ref } from 'vue'

import { ApiError } from '@/api/http'
import { listPlugins, setPluginEnabled } from '@/api/plugins'
import PluginDialog from '@/components/business/PluginDialog.vue'
import Badge from '@/components/ui/badge/Badge.vue'
import Button from '@/components/ui/button/Button.vue'
import EmptyState from '@/components/ui/empty-state/EmptyState.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import IconButton from '@/components/ui/icon-button/IconButton.vue'
import Switch from '@/components/ui/switch/Switch.vue'
import Table from '@/components/ui/table/Table.vue'
import type { TableColumn } from '@/components/ui/table/table.types'
import Tooltip from '@/components/ui/tooltip/Tooltip.vue'
import { useToast } from '@/composables/useToast'
import type { PluginSummary } from '@/types/api'
import { formatDateTime } from '@/utils/format'

const columns: TableColumn[] = [
  { key: 'name', label: '插件' },
  { key: 'revision', label: '版本', width: '72px', numeric: true },
  { key: 'filename', label: '文件', width: '190px', hideBelow: 'lg' },
  { key: 'tags', label: '标签', width: '200px', hideBelow: 'md' },
  { key: 'updatedAt', label: '更新时间', width: '150px', hideBelow: 'lg' },
  { key: 'enabled', label: '启用', width: '76px' },
  { key: 'actions', label: '', width: '48px' },
]

const toast = useToast()
const plugins = ref<PluginSummary[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const dialogOpen = ref(false)
const editing = ref<PluginSummary | null>(null)
const toggling = ref(new Set<number>())

function messageOf(e: unknown): string {
  if (e instanceof ApiError) return e.message
  return '无法连接后端，请确认服务已启动（默认 127.0.0.1:8787）'
}

const load = async () => {
  loading.value = true
  error.value = null
  try {
    plugins.value = await listPlugins()
  } catch (e) {
    error.value = messageOf(e)
  } finally {
    loading.value = false
  }
}

onMounted(load)

const openCreate = () => {
  editing.value = null
  dialogOpen.value = true
}

const openEdit = (plugin: PluginSummary) => {
  editing.value = plugin
  dialogOpen.value = true
}

const toggle = async (plugin: PluginSummary, enabled: boolean) => {
  if (toggling.value.has(plugin.id)) return
  toggling.value = new Set(toggling.value).add(plugin.id)
  try {
    const updated = await setPluginEnabled(plugin.id, enabled)
    plugins.value = plugins.value.map((item) => (item.id === updated.id ? { ...item, ...updated } : item))
    toast.success(
      `插件「${plugin.name}」已${enabled ? '启用' : '停用'}`,
      enabled ? '新任务可以绑定这个插件' : '已绑定任务在重新执行前需要换绑或移除',
    )
  } catch (e) {
    toast.failure('插件状态修改失败', messageOf(e))
  } finally {
    const next = new Set(toggling.value)
    next.delete(plugin.id)
    toggling.value = next
  }
}
</script>

<template>
  <div class="flex h-full min-h-0 flex-col">
    <div class="flex h-toolbar shrink-0 items-center gap-3 border-b border-separator px-5">
      <h1 class="text-body font-emphasis text-label">插件</h1>
      <span class="tnum text-body text-label-tertiary">{{ plugins.length }}</span>
      <div class="ml-auto flex items-center gap-2">
        <Tooltip content="刷新">
          <IconButton label="刷新插件" size="lg" :disabled="loading" @click="load">
            <Icon :as="RefreshCw" size="large" />
          </IconButton>
        </Tooltip>
        <Button variant="primary" size="lg" @click="openCreate">
          <Icon :as="Plus" size="inline" />新增插件
        </Button>
      </div>
    </div>

    <div class="scroll-area min-h-0 flex-1 overflow-auto">
      <Table :columns="columns" :rows="plugins" :show-empty="false">
        <template #cell="{ row, column }">
          <div v-if="column.key === 'name'" class="min-w-0">
            <p class="truncate font-emphasis text-label">{{ row.name }}</p>
            <p class="mt-0.5 truncate text-caption text-label-tertiary">
              {{ row.description || '没有说明' }}
            </p>
          </div>
          <span v-else-if="column.key === 'revision'" class="tnum text-label-secondary">
            v{{ row.currentRevision }}
          </span>
          <span v-else-if="column.key === 'filename'" class="truncate font-mono text-caption text-label-secondary">
            {{ row.filename }}
          </span>
          <div v-else-if="column.key === 'tags'" class="flex min-w-0 items-center gap-1">
            <Badge v-for="tag in (row.tags as string[]).slice(0, 3)" :key="tag" tone="accent">
              {{ tag }}
            </Badge>
            <span v-if="(row.tags as string[]).length === 0" class="text-label-tertiary">—</span>
          </div>
          <span v-else-if="column.key === 'updatedAt'" class="tnum text-caption text-label-secondary">
            {{ formatDateTime(row.updatedAt as string) }}
          </span>
          <Switch
            v-else-if="column.key === 'enabled'"
            :model-value="row.enabled as boolean"
            :disabled="toggling.has(row.id as number)"
            :aria-label="`切换插件 ${row.name} 状态`"
            @update:model-value="toggle(row as PluginSummary, $event === true)"
          />
          <div v-else-if="column.key === 'actions'" class="flex justify-end">
            <Tooltip content="编辑并生成新版本">
              <IconButton label="编辑插件" size="sm" @click.stop="openEdit(row as PluginSummary)">
                <Icon :as="Pencil" size="inline" />
              </IconButton>
            </Tooltip>
          </div>
        </template>
      </Table>

      <EmptyState
        v-if="loading && plugins.length === 0"
        title="正在加载…"
        description="从后端读取插件库"
      />
      <EmptyState v-else-if="error" title="加载失败" :description="error" />
      <EmptyState
        v-else-if="plugins.length === 0"
        title="插件库还是空的"
        description="把已经写好的 Kafka 接入、数据库工具或其他组件存进来，任务执行时就不用每次重新生成。"
      >
        <Button variant="primary" @click="openCreate">
          <Icon :as="Blocks" size="inline" />新增插件
        </Button>
      </EmptyState>
    </div>

    <PluginDialog v-model:open="dialogOpen" :plugin="editing" @saved="load" />
  </div>
</template>
