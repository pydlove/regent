<script setup lang="ts">
import { Blocks, Eye, Pencil, Plus, RefreshCw, Search, X } from '@lucide/vue'
import { computed, onMounted, ref } from 'vue'

import { ApiError } from '@/api/http'
import { listPlugins, setPluginEnabled } from '@/api/plugins'
import PluginDialog from '@/components/business/PluginDialog.vue'
import Badge from '@/components/ui/badge/Badge.vue'
import Button from '@/components/ui/button/Button.vue'
import EmptyState from '@/components/ui/empty-state/EmptyState.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import IconButton from '@/components/ui/icon-button/IconButton.vue'
import Input from '@/components/ui/input/Input.vue'
import SegmentedControl from '@/components/ui/segmented-control/SegmentedControl.vue'
import Switch from '@/components/ui/switch/Switch.vue'
import Tooltip from '@/components/ui/tooltip/Tooltip.vue'
import { useToast } from '@/composables/useToast'
import type { PluginSummary } from '@/types/api'
import { formatDateTime } from '@/utils/format'
import { filterPlugins, type PluginFilterStatus } from '@/utils/pluginCatalog'

const toast = useToast()
const plugins = ref<PluginSummary[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const dialogOpen = ref(false)
const editing = ref<PluginSummary | null>(null)
const toggling = ref(new Set<number>())
const keyword = ref('')
const statusFilter = ref<PluginFilterStatus>('all')

const enabledCount = computed(() => plugins.value.filter((plugin) => plugin.enabled).length)
const filteredPlugins = computed(() =>
  filterPlugins(plugins.value, {
    keyword: keyword.value,
    status: statusFilter.value,
  }),
)
const statusItems = computed(() => [
  { value: 'all', label: `全部 ${plugins.value.length}` },
  { value: 'enabled', label: `启用 ${enabledCount.value}` },
  { value: 'disabled', label: `停用 ${plugins.value.length - enabledCount.value}` },
])
const statusFilterModel = computed({
  get: () => statusFilter.value,
  set: (value: string) => {
    statusFilter.value = value as PluginFilterStatus
  },
})

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

const clearFilters = () => {
  keyword.value = ''
  statusFilter.value = 'all'
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
    <div
      class="flex min-h-toolbar shrink-0 flex-wrap items-center gap-2 border-b border-separator px-4 py-2 sm:px-5 lg:h-toolbar lg:flex-nowrap lg:py-0"
    >
      <div class="mr-auto flex min-w-0 items-baseline gap-2">
        <h1 class="text-body font-emphasis text-label">插件</h1>
        <span class="tnum text-caption text-label-tertiary">
          {{ enabledCount }} 启用 / {{ plugins.length }} 全部
        </span>
      </div>

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

      <div class="order-last flex w-full items-center gap-2 sm:order-none sm:ml-auto sm:w-auto">
        <div class="relative min-w-0 flex-1 sm:w-56 sm:flex-none">
          <Icon
            :as="Search"
            size="inline"
            class="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-label-tertiary"
          />
          <Input
            v-model="keyword"
            size="lg"
            placeholder="搜索名称、标签或文件"
            aria-label="搜索插件"
            class="pl-7 pr-7"
          />
          <button
            v-if="keyword"
            type="button"
            class="absolute right-1 top-1/2 flex size-5 -translate-y-1/2 items-center justify-center rounded-control text-label-tertiary hover:bg-hover hover:text-label"
            aria-label="清空插件搜索"
            @click="keyword = ''"
          >
            <Icon :as="X" size="inline" />
          </button>
        </div>
        <SegmentedControl v-model="statusFilterModel" :items="statusItems" size="lg" />
      </div>
    </div>

    <div class="scroll-area min-h-0 flex-1 overflow-auto">
      <div
        v-if="!error && filteredPlugins.length > 0"
        class="mx-4 my-4 overflow-hidden rounded-panel border border-separator-soft bg-content shadow-panel"
      >
        <div
          class="grid h-8 select-none grid-cols-[minmax(0,1fr)_90px_32px] items-center gap-3 border-b border-separator bg-sidebar px-3 text-caption font-emphasis text-label-secondary md:grid-cols-[minmax(0,1fr)_180px_90px_76px_32px] xl:grid-cols-[minmax(0,1fr)_220px_110px_150px_76px_32px]"
        >
          <span>插件</span>
          <span class="hidden md:block">标签</span>
          <span class="hidden md:block">版本</span>
          <span class="hidden xl:block">更新时间</span>
          <span>启用</span>
          <span />
        </div>

        <div
          v-for="plugin in filteredPlugins"
          :key="plugin.id"
          class="group grid min-h-16 grid-cols-[minmax(0,1fr)_90px_32px] items-center gap-3 border-b border-separator-soft px-3 py-2 transition-colors duration-100 ease-apple last:border-b-0 hover:bg-hover md:grid-cols-[minmax(0,1fr)_180px_90px_76px_32px] xl:grid-cols-[minmax(0,1fr)_220px_110px_150px_76px_32px]"
          @dblclick="openEdit(plugin)"
        >
          <div class="flex min-w-0 items-center gap-2.5">
            <span
              class="flex size-8 shrink-0 items-center justify-center rounded-panel"
              :class="plugin.enabled ? 'bg-accent/10 text-accent' : 'bg-neutral/10 text-label-tertiary'"
            >
              <Icon :as="Blocks" size="regular" />
            </span>
            <div class="min-w-0">
              <div class="flex min-w-0 items-center gap-1.5">
                <p class="truncate text-body font-emphasis text-label">{{ plugin.name }}</p>
                <Badge v-if="plugin.builtin" tone="neutral" class="shrink-0">内置</Badge>
              </div>
              <p class="mt-0.5 truncate text-caption text-label-secondary" :title="plugin.filename">
                {{ plugin.description || plugin.filename }}
              </p>
            </div>
          </div>

          <div class="hidden min-w-0 items-center gap-1 overflow-hidden md:flex">
            <Badge v-for="tag in plugin.tags.slice(0, 2)" :key="tag" tone="accent" class="shrink-0">
              {{ tag }}
            </Badge>
            <span v-if="plugin.tags.length > 2" class="text-caption text-label-tertiary">
              +{{ plugin.tags.length - 2 }}
            </span>
            <span v-if="plugin.tags.length === 0" class="text-caption text-label-tertiary">—</span>
          </div>

          <span class="tnum hidden text-caption text-label-secondary md:block">
            v{{ plugin.currentRevision }}
          </span>
          <span class="tnum hidden text-caption text-label-secondary xl:block">
            {{ formatDateTime(plugin.updatedAt) }}
          </span>
          <Switch
            :model-value="plugin.enabled"
            :disabled="toggling.has(plugin.id)"
            :aria-label="`切换插件 ${plugin.name} 状态`"
            @update:model-value="toggle(plugin, $event === true)"
          />
          <div class="flex justify-end">
            <Tooltip :content="plugin.builtin ? '查看内置插件' : '编辑并生成新版本'">
              <IconButton
                :label="plugin.builtin ? '查看内置插件' : '编辑插件'"
                size="sm"
                @click.stop="openEdit(plugin)"
              >
                <Icon :as="plugin.builtin ? Eye : Pencil" size="inline" />
              </IconButton>
            </Tooltip>
          </div>
        </div>
      </div>

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
      <EmptyState
        v-else-if="filteredPlugins.length === 0"
        title="没有匹配的插件"
        description="换个关键词，或把状态切回「全部」"
      >
        <Button @click="clearFilters">清除筛选</Button>
      </EmptyState>
    </div>

    <PluginDialog v-model:open="dialogOpen" :plugin="editing" @saved="load" />
  </div>
</template>
