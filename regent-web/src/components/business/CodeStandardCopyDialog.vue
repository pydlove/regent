<script setup lang="ts">
import { Copy, RefreshCw } from '@lucide/vue'
import { computed, ref, watch } from 'vue'

import { copyCodeStandards, listCodeStandards } from '@/api/codeStandards'
import { ApiError } from '@/api/http'
import Badge from '@/components/ui/badge/Badge.vue'
import Button from '@/components/ui/button/Button.vue'
import Checkbox from '@/components/ui/checkbox/Checkbox.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import DialogField from '@/components/ui/dialog/DialogField.vue'
import EmptyState from '@/components/ui/empty-state/EmptyState.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import Select from '@/components/ui/select/Select.vue'
import { useToast } from '@/composables/useToast'
import { useTaskStore } from '@/stores/useTaskStore'
import type { CodeStandard } from '@/types/api'

const props = defineProps<{ targetProjectId: number | null }>()
const emit = defineEmits<{ copied: [] }>()
const open = defineModel<boolean>('open', { default: false })

const store = useTaskStore()
const toast = useToast()
const sourceProjectId = ref<number | null>(null)
const standards = ref<CodeStandard[]>([])
const selectedIds = ref<number[]>([])
const strategy = ref<'SKIP' | 'RENAME'>('SKIP')
const loading = ref(false)
const copying = ref(false)
const error = ref<string | null>(null)

const sourceProjects = computed(() =>
  store.projects.filter((project) => project.id !== props.targetProjectId),
)
const projectItems = computed(() =>
  sourceProjects.value.map((project) => ({
    value: String(project.id),
    label: `${project.name}（${project.code}）`,
  })),
)
const sourceProjectValue = computed({
  get: () => (sourceProjectId.value === null ? undefined : String(sourceProjectId.value)),
  set: (value: string | undefined) => {
    sourceProjectId.value = value === undefined ? null : Number(value)
  },
})
const strategyItems = [
  { value: 'SKIP', label: '跳过同名规范' },
  { value: 'RENAME', label: '复制并自动重命名' },
]
const allSelected = computed(
  () => standards.value.length > 0 && standards.value.every((item) => selectedIds.value.includes(item.id)),
)
const canCopy = computed(
  () => props.targetProjectId !== null
    && sourceProjectId.value !== null
    && selectedIds.value.length > 0
    && !copying.value,
)

watch(open, async (opened) => {
  if (!opened) return
  error.value = null
  copying.value = false
  standards.value = []
  selectedIds.value = []
  await store.loadProjects()
  sourceProjectId.value = sourceProjects.value[0]?.id ?? null
  await load()
})

watch(sourceProjectId, () => void load())

const load = async () => {
  if (!open.value || sourceProjectId.value === null) return
  loading.value = true
  error.value = null
  try {
    standards.value = await listCodeStandards(sourceProjectId.value)
    selectedIds.value = standards.value.filter((item) => item.enabled).map((item) => item.id)
  } catch (e) {
    standards.value = []
    selectedIds.value = []
    error.value = e instanceof ApiError ? e.message : '来源项目规范读取失败'
  } finally {
    loading.value = false
  }
}

const toggleAll = (value: boolean | 'indeterminate') => {
  selectedIds.value = value === true ? standards.value.map((item) => item.id) : []
}

const toggle = (id: number, value: boolean | 'indeterminate') => {
  selectedIds.value = value === true
    ? [...new Set([...selectedIds.value, id])]
    : selectedIds.value.filter((item) => item !== id)
}

const copy = async () => {
  if (!canCopy.value || props.targetProjectId === null || sourceProjectId.value === null) return
  copying.value = true
  error.value = null
  try {
    const result = await copyCodeStandards(
      props.targetProjectId,
      sourceProjectId.value,
      selectedIds.value,
      strategy.value,
    )
    emit('copied')
    open.value = false
    const skipped = result.skipped.length
    toast.success(
      `已复制 ${result.copied.length} 条开发规范`,
      skipped > 0 ? `${skipped} 条因同名或内容相同被跳过` : '目标项目已得到独立副本',
    )
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '复制失败'
  } finally {
    copying.value = false
  }
}
</script>

<template>
  <Dialog
    v-model:open="open"
    layout="form"
    size="large"
    title="从其他项目复制开发规范"
    description="只复制当前版本。复制后目标项目拥有独立副本，源项目后续修改不会同步。"
  >
    <div class="flex flex-col gap-3">
      <DialogField label="来源项目" required>
        <div class="flex items-center gap-2">
          <Select
            v-model="sourceProjectValue"
            :items="projectItems"
            placeholder="选择包含规范的项目"
            class="min-w-0 flex-1"
            block
          />
          <Button size="sm" :disabled="loading" @click="load">
            <Icon :as="RefreshCw" size="inline" :class="{ 'animate-spin': loading }" />
            刷新
          </Button>
        </div>
      </DialogField>

      <DialogField label="冲突处理">
        <Select v-model="strategy" :items="strategyItems" block />
      </DialogField>

      <div class="rounded-panel border border-separator-soft bg-content p-2">
        <div class="flex items-center gap-2 border-b border-separator-soft px-1 pb-2">
          <Checkbox
            :model-value="allSelected"
            :disabled="standards.length === 0"
            aria-label="选择全部开发规范"
            @update:model-value="toggleAll"
          />
          <span class="text-caption font-emphasis text-label-secondary">来源规范</span>
          <Badge class="ml-auto">{{ selectedIds.length }} / {{ standards.length }}</Badge>
        </div>
        <p v-if="loading" class="px-2 py-4 text-caption text-label-tertiary">正在读取…</p>
        <p v-else-if="error" class="px-2 py-4 text-caption text-danger">{{ error }}</p>
        <EmptyState
          v-else-if="standards.length === 0"
          title="来源项目还没有开发规范"
          description="选择其他项目，或先到来源项目上传规范。"
        />
        <div v-else class="scroll-area mt-2 max-h-64 space-y-1 overflow-y-auto">
          <div
            v-for="standard in standards"
            :key="standard.id"
            class="flex min-h-12 items-start gap-2 rounded-control px-2 py-2 hover:bg-hover"
          >
            <Checkbox
              :model-value="selectedIds.includes(standard.id)"
              :aria-label="`选择 ${standard.name}`"
              @update:model-value="toggle(standard.id, $event)"
            />
            <span class="min-w-0 flex-1">
              <span class="flex items-center gap-2">
                <span class="truncate text-caption font-emphasis text-label">{{ standard.name }}</span>
                <Badge v-if="!standard.enabled" tone="warning">已停用</Badge>
                <span class="tnum ml-auto shrink-0 text-caption text-label-tertiary">
                  v{{ standard.currentVersion }}
                </span>
              </span>
              <span class="mt-0.5 block truncate text-caption text-label-tertiary">
                {{ standard.description || standard.filename || '无说明' }}
              </span>
            </span>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <Button :disabled="copying" @click="open = false">取消</Button>
      <Button variant="primary" :disabled="!canCopy" @click="copy">
        <Icon :as="Copy" size="inline" />
        {{ copying ? '复制中…' : `复制 ${selectedIds.length} 条` }}
      </Button>
    </template>
  </Dialog>
</template>
