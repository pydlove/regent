<script setup lang="ts">
import { Blocks, RefreshCw } from '@lucide/vue'
import { computed, ref, watch } from 'vue'

import { ApiError } from '@/api/http'
import {
  bindTaskPlugins,
  listPlugins,
  listTaskPlugins,
} from '@/api/plugins'
import PluginPicker from '@/components/business/PluginPicker.vue'
import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import EmptyState from '@/components/ui/empty-state/EmptyState.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import { useToast } from '@/composables/useToast'
import type { PluginSummary, TaskPluginBinding } from '@/types/api'
import { MAX_TASK_PLUGINS } from '@/utils/pluginCatalog'

const props = defineProps<{ taskId: number; editable: boolean }>()
const emit = defineEmits<{ saved: [bindings: TaskPluginBinding[]] }>()
const open = defineModel<boolean>('open', { default: false })
const toast = useToast()

const plugins = ref<PluginSummary[]>([])
const selected = ref<number[]>([])
const disabledBindings = ref<TaskPluginBinding[]>([])
const loading = ref(false)
const saving = ref(false)
const error = ref<string | null>(null)

const available = computed(() => plugins.value.filter((plugin) => plugin.enabled))
const canSave = computed(
  () => props.editable && selected.value.length <= MAX_TASK_PLUGINS && !saving.value,
)

function messageOf(e: unknown): string {
  if (e instanceof ApiError) return e.message
  return '无法连接后端，请确认服务已启动（默认 127.0.0.1:8787）'
}

const load = async () => {
  loading.value = true
  error.value = null
  try {
    const [catalog, bindings] = await Promise.all([
      listPlugins(),
      listTaskPlugins(props.taskId),
    ])
    plugins.value = catalog
    const enabledIds = new Set(catalog.filter((plugin) => plugin.enabled).map((plugin) => plugin.id))
    selected.value = bindings
      .filter((binding) => enabledIds.has(binding.pluginId))
      .map((binding) => binding.pluginId)
    disabledBindings.value = bindings.filter((binding) => !enabledIds.has(binding.pluginId))
  } catch (e) {
    error.value = messageOf(e)
  } finally {
    loading.value = false
  }
}

watch(open, (opened) => {
  if (opened) void load()
})

const save = async () => {
  if (!canSave.value) return
  saving.value = true
  error.value = null
  try {
    const bindings = await bindTaskPlugins(props.taskId, selected.value)
    open.value = false
    emit('saved', bindings)
    toast.success(
      `已绑定 ${bindings.length} 个插件`,
      bindings.length > 0 ? '下一次执行会读取这些固定版本的内容' : '后续执行不再注入插件',
    )
  } catch (e) {
    error.value = messageOf(e)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <Dialog
    v-model:open="open"
    layout="form"
    size="large"
    :title="editable ? '选择任务插件' : '任务插件'"
    description="插件版本在绑定时固定。插件后续升级不会改变这个任务，除非重新选择。"
  >
    <div class="flex min-h-40 flex-col gap-3">
      <div v-if="loading" class="text-caption text-label-tertiary">正在读取插件库…</div>
      <p v-else-if="error" class="text-caption text-danger">{{ error }}</p>
      <EmptyState
        v-else-if="available.length === 0"
        title="没有可用插件"
        description="先在插件页新增并启用能力包。"
      >
        <Icon :as="Blocks" size="large" />
      </EmptyState>
      <PluginPicker
        v-else
        v-model="selected"
        :plugins="available"
        :disabled="!editable || saving"
        :max="MAX_TASK_PLUGINS"
      />

      <p v-if="disabledBindings.length > 0" class="text-caption text-warning">
        {{ disabledBindings.length }} 个已绑定插件已停用，保存时会自动移除。
      </p>
    </div>

    <template #footer>
      <Button v-if="editable" :disabled="loading || saving" @click="load">
        <Icon :as="RefreshCw" size="inline" />刷新
      </Button>
      <Button :disabled="saving" @click="open = false">{{ editable ? '取消' : '关闭' }}</Button>
      <Button v-if="editable" variant="primary" :disabled="!canSave" @click="save">
        {{ saving ? '保存中…' : '保存绑定' }}
      </Button>
    </template>
  </Dialog>
</template>
