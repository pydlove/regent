<script setup lang="ts">
import { Blocks, Search, X } from '@lucide/vue'
import { computed, ref } from 'vue'

import Badge from '@/components/ui/badge/Badge.vue'
import Checkbox from '@/components/ui/checkbox/Checkbox.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import Input from '@/components/ui/input/Input.vue'
import { useToast } from '@/composables/useToast'
import type { PluginSummary } from '@/types/api'
import {
  filterPlugins,
  MAX_TASK_PLUGINS,
  pluginTagOptions,
} from '@/utils/pluginCatalog'

const props = withDefaults(
  defineProps<{
    plugins: PluginSummary[]
    disabled?: boolean
    max?: number
  }>(),
  {
    disabled: false,
    max: MAX_TASK_PLUGINS,
  },
)

const selectedIds = defineModel<number[]>({ default: () => [] })
const toast = useToast()
const keyword = ref('')
const activeTag = ref<string | null>(null)

const selected = computed(() => new Set(selectedIds.value))
const tags = computed(() => pluginTagOptions(props.plugins))
const filtered = computed(() =>
  filterPlugins(props.plugins, {
    keyword: keyword.value,
    tag: activeTag.value,
  }),
)
const selectedPlugins = computed(() => {
  const byId = new Map(props.plugins.map((plugin) => [plugin.id, plugin]))
  return selectedIds.value
    .map((id) => byId.get(id))
    .filter((plugin): plugin is PluginSummary => plugin !== undefined)
})

function toggle(plugin: PluginSummary): void {
  if (props.disabled) return
  if (selected.value.has(plugin.id)) {
    selectedIds.value = selectedIds.value.filter((id) => id !== plugin.id)
    return
  }
  if (selectedIds.value.length >= props.max) {
    toast.warning(`最多绑定 ${props.max} 个插件`, '先移除一个再选择')
    return
  }
  selectedIds.value = [...selectedIds.value, plugin.id]
}

function clearSelection(): void {
  if (!props.disabled) selectedIds.value = []
}
</script>

<template>
  <div class="overflow-hidden rounded-panel border border-separator bg-content shadow-panel">
    <div class="border-b border-separator-soft bg-sidebar/70 p-2.5">
      <div class="flex items-center gap-2">
        <div class="relative min-w-0 flex-1">
          <Icon
            :as="Search"
            size="inline"
            class="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-label-tertiary"
          />
          <Input
            v-model="keyword"
            placeholder="搜索插件、标签或文件"
            :disabled="disabled"
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
        <span class="tnum shrink-0 text-caption text-label-secondary">
          {{ selected.size }} / {{ max }}
        </span>
      </div>

      <div v-if="tags.length > 0" class="scroll-area mt-2 flex gap-1 overflow-x-auto pb-0.5">
        <button
          type="button"
          class="shrink-0 rounded-badge border px-1.5 py-0.5 text-caption transition-colors duration-100 ease-apple"
          :class="
            activeTag === null
              ? 'border-accent/30 bg-accent/10 text-accent'
              : 'border-separator-soft text-label-secondary hover:bg-hover'
          "
          @click="activeTag = null"
        >
          全部
        </button>
        <button
          v-for="tag in tags"
          :key="tag.value"
          type="button"
          class="shrink-0 rounded-badge border px-1.5 py-0.5 text-caption transition-colors duration-100 ease-apple"
          :class="
            activeTag === tag.value
              ? 'border-accent/30 bg-accent/10 text-accent'
              : 'border-separator-soft text-label-secondary hover:bg-hover'
          "
          @click="activeTag = activeTag === tag.value ? null : tag.value"
        >
          {{ tag.value }} <span class="tnum opacity-60">{{ tag.count }}</span>
        </button>
      </div>
    </div>

    <div v-if="selectedPlugins.length > 0" class="border-b border-separator-soft bg-accent/5 px-2.5 py-2">
      <div class="flex items-center gap-2">
        <span class="shrink-0 text-caption font-emphasis text-label-secondary">已选</span>
        <div class="flex min-w-0 flex-1 flex-wrap gap-1">
          <span
            v-for="plugin in selectedPlugins"
            :key="plugin.id"
            class="inline-flex max-w-full items-center gap-1 rounded-badge bg-content px-1.5 py-0.5 text-caption text-label shadow-control"
          >
            <span class="truncate">{{ plugin.name }}</span>
            <button
              v-if="!disabled"
              type="button"
              class="shrink-0 text-label-tertiary hover:text-danger"
              :aria-label="`移除插件 ${plugin.name}`"
              @click="toggle(plugin)"
            >
              <Icon :as="X" size="inline" />
            </button>
          </span>
        </div>
        <button
          v-if="!disabled"
          type="button"
          class="shrink-0 text-caption text-label-tertiary hover:text-label"
          @click="clearSelection"
        >
          清空
        </button>
      </div>
    </div>

    <div class="scroll-area max-h-64 overflow-y-auto p-1.5">
      <div
        v-for="plugin in filtered"
        :key="plugin.id"
        class="flex w-full items-start gap-2 rounded-control px-2 py-1.5 transition-colors duration-100 ease-apple"
        :class="
          selected.has(plugin.id)
            ? 'bg-selection hover:bg-accent/15'
            : 'hover:bg-hover'
        "
      >
        <Checkbox
          :model-value="selected.has(plugin.id)"
          :disabled="disabled"
          :aria-label="`绑定插件 ${plugin.name}`"
          class="mt-0.5"
          @update:model-value="toggle(plugin)"
          @click.stop
        />
        <button
          type="button"
          class="flex min-w-0 flex-1 items-start gap-2 text-left disabled:cursor-default"
          :disabled="disabled"
          :aria-pressed="selected.has(plugin.id)"
          @click="toggle(plugin)"
        >
          <span class="flex size-7 shrink-0 items-center justify-center rounded-control bg-accent/10 text-accent">
            <Icon :as="Blocks" size="regular" />
          </span>
          <span class="min-w-0 flex-1">
            <span class="flex min-w-0 items-center gap-1.5">
              <span class="truncate text-body font-emphasis text-label">{{ plugin.name }}</span>
              <Badge v-if="plugin.builtin" tone="neutral" class="shrink-0">内置</Badge>
              <span class="tnum shrink-0 text-caption text-label-tertiary">
                v{{ plugin.currentRevision }}
              </span>
            </span>
            <span class="mt-0.5 block truncate text-caption text-label-secondary">
              {{ plugin.description || plugin.filename }}
            </span>
            <span v-if="plugin.tags.length > 0" class="mt-1 flex min-w-0 gap-1 overflow-hidden">
              <Badge
                v-for="tag in plugin.tags.slice(0, 3)"
                :key="tag"
                tone="accent"
                class="shrink-0"
              >
                {{ tag }}
              </Badge>
              <span v-if="plugin.tags.length > 3" class="text-caption text-label-tertiary">
                +{{ plugin.tags.length - 3 }}
              </span>
            </span>
          </span>
        </button>
      </div>

      <div v-if="filtered.length === 0" class="px-3 py-7 text-center">
        <p class="text-body text-label-secondary">没有匹配的插件</p>
        <button
          v-if="keyword || activeTag"
          type="button"
          class="mt-1 text-caption text-accent hover:underline"
          @click="
            keyword = '';
            activeTag = null
          "
        >
          清除筛选
        </button>
      </div>
    </div>

    <div class="flex items-center justify-between border-t border-separator-soft bg-sidebar/60 px-2.5 py-1.5">
      <span class="text-caption text-label-tertiary">
        显示 {{ filtered.length }} / {{ plugins.length }} 个可用插件
      </span>
      <span v-if="selected.size >= max" class="text-caption text-warning">
        已达绑定上限
      </span>
    </div>
  </div>
</template>
