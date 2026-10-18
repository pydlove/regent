<script setup lang="ts">
import { FlaskConical, RefreshCw, Search, X } from '@lucide/vue'
import { computed, ref, watch } from 'vue'

import { ApiError } from '@/api/http'
import {
  bindTaskTestCases,
  listTaskTestCases,
  listTestCases,
} from '@/api/testCases'
import Badge from '@/components/ui/badge/Badge.vue'
import Button from '@/components/ui/button/Button.vue'
import Checkbox from '@/components/ui/checkbox/Checkbox.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import EmptyState from '@/components/ui/empty-state/EmptyState.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import Input from '@/components/ui/input/Input.vue'
import { useToast } from '@/composables/useToast'
import type { TaskTestCaseBinding, TestCaseSummary } from '@/types/api'

const MAX_TASK_TEST_CASES = 10

const props = defineProps<{
  taskId: number
  projectId: number
  editable: boolean
}>()
const emit = defineEmits<{ saved: [bindings: TaskTestCaseBinding[]] }>()
const open = defineModel<boolean>('open', { default: false })
const toast = useToast()

const testCases = ref<TestCaseSummary[]>([])
const selected = ref<number[]>([])
const disabledBindingCount = ref(0)
const loading = ref(false)
const saving = ref(false)
const error = ref<string | null>(null)
const keyword = ref('')

const available = computed(() => testCases.value.filter((testCase) => testCase.enabled))
const filtered = computed(() => {
  const search = keyword.value.trim().toLowerCase()
  if (!search) return available.value
  return available.value.filter((testCase) =>
    [testCase.name, testCase.description ?? '', testCase.scriptFilename ?? '', testCase.command ?? '']
      .some((value) => value.toLowerCase().includes(search)),
  )
})
const selectedSet = computed(() => new Set(selected.value))
const canSave = computed(
  () => props.editable && selected.value.length <= MAX_TASK_TEST_CASES && !saving.value,
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
      listTestCases(props.projectId),
      listTaskTestCases(props.taskId),
    ])
    testCases.value = catalog
    const enabledIds = new Set(catalog.filter((testCase) => testCase.enabled).map((testCase) => testCase.id))
    selected.value = bindings
      .filter((binding) => enabledIds.has(binding.testCaseId))
      .map((binding) => binding.testCaseId)
    disabledBindingCount.value = bindings.length - selected.value.length
  } catch (e) {
    error.value = messageOf(e)
  } finally {
    loading.value = false
  }
}

watch(open, (opened) => {
  if (opened) {
    keyword.value = ''
    void load()
  }
})

const toggle = (testCase: TestCaseSummary) => {
  if (!props.editable) return
  if (selectedSet.value.has(testCase.id)) {
    selected.value = selected.value.filter((id) => id !== testCase.id)
    return
  }
  if (selected.value.length >= MAX_TASK_TEST_CASES) {
    toast.warning(`最多绑定 ${MAX_TASK_TEST_CASES} 个测试用例`, '先移除一个再选择')
    return
  }
  selected.value = [...selected.value, testCase.id]
}

const save = async () => {
  if (!canSave.value) return
  saving.value = true
  error.value = null
  try {
    const bindings = await bindTaskTestCases(props.taskId, selected.value)
    open.value = false
    emit('saved', bindings)
    toast.success(
      bindings.length > 0 ? `已绑定 ${bindings.length} 个测试用例` : '已清空测试用例绑定',
      bindings.length > 0 ? '任务成功完成后会按顺序自动执行' : '任务完成时不会自动跑测试',
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
    :title="editable ? '选择任务测试用例' : '任务测试用例'"
    description="测试在任务成功完成后执行，失败不会回滚任务，但会留下独立执行记录。"
    :icon="FlaskConical"
  >
    <div class="flex min-h-40 flex-col gap-3">
      <div v-if="loading" class="text-caption text-label-tertiary">正在读取测试用例…</div>
      <p v-else-if="error" class="text-caption text-danger">{{ error }}</p>
      <EmptyState
        v-else-if="available.length === 0"
        title="这个项目还没有可用测试用例"
        description="先到「测试用例」菜单创建并启用用例，再回来绑定。"
      >
        <Icon :as="FlaskConical" size="large" />
      </EmptyState>

      <template v-else>
        <div class="flex items-center gap-2">
          <div class="relative min-w-0 flex-1">
            <Icon
              :as="Search"
              size="inline"
              class="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-label-tertiary"
            />
            <Input
              v-model="keyword"
              placeholder="搜索名称、说明或文件"
              aria-label="搜索测试用例"
              class="pl-7 pr-7"
            />
            <button
              v-if="keyword"
              type="button"
              class="absolute right-1 top-1/2 flex size-5 -translate-y-1/2 items-center justify-center rounded-control text-label-tertiary hover:bg-hover hover:text-label"
              aria-label="清空搜索"
              @click="keyword = ''"
            >
              <Icon :as="X" size="inline" />
            </button>
          </div>
          <span class="tnum shrink-0 text-caption text-label-secondary">
            {{ selected.length }} / {{ MAX_TASK_TEST_CASES }}
          </span>
        </div>

        <div class="scroll-area max-h-72 overflow-y-auto rounded-panel border border-separator bg-content shadow-panel">
          <div
            v-for="testCase in filtered"
            :key="testCase.id"
            class="flex w-full items-start gap-2.5 border-b border-separator-soft px-3 py-2 transition-colors duration-100 ease-apple last:border-b-0"
            :class="selectedSet.has(testCase.id) ? 'bg-selection' : ''"
          >
            <Checkbox
              :model-value="selectedSet.has(testCase.id)"
              :disabled="!editable"
              :aria-label="`绑定测试用例 ${testCase.name}`"
              class="mt-0.5"
              @update:model-value="toggle(testCase)"
            />
            <button
              type="button"
              class="min-w-0 flex-1 text-left disabled:cursor-default"
              :disabled="!editable"
              @click="toggle(testCase)"
            >
              <span class="flex min-w-0 items-center gap-2">
                <span class="truncate text-body font-emphasis text-label">{{ testCase.name }}</span>
                <Badge :tone="testCase.type === 'WEB' ? 'accent' : 'neutral'">
                  {{ testCase.type === 'WEB' ? 'Web' : '接口' }}
                </Badge>
              </span>
              <span class="mt-0.5 block truncate text-caption text-label-secondary">
                {{ testCase.description || testCase.scriptFilename || testCase.command }}
              </span>
            </button>
          </div>
          <p v-if="filtered.length === 0" class="px-3 py-8 text-center text-caption text-label-tertiary">
            没有匹配的测试用例
          </p>
        </div>
        <p v-if="disabledBindingCount > 0" class="text-caption text-warning">
          {{ disabledBindingCount }} 个已绑定用例已停用或删除，保存时会自动移除。
        </p>
      </template>
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
