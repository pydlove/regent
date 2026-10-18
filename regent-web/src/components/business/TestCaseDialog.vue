<script setup lang="ts">
import { FileCheck2, FlaskConical, Upload, X } from '@lucide/vue'
import { computed, ref, watch } from 'vue'

import { ApiError } from '@/api/http'
import {
  createTestCase,
  getTestCase,
  updateTestCase,
  type SaveTestCaseInput,
} from '@/api/testCases'
import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import DialogField from '@/components/ui/dialog/DialogField.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import Input from '@/components/ui/input/Input.vue'
import Textarea from '@/components/ui/input/Textarea.vue'
import SegmentedControl from '@/components/ui/segmented-control/SegmentedControl.vue'
import Select from '@/components/ui/select/Select.vue'
import Switch from '@/components/ui/switch/Switch.vue'
import { useToast } from '@/composables/useToast'
import type { Project, TestCaseDetail, TestCaseSummary } from '@/types/api'
import { formatBytes } from '@/utils/format'

const MAX_SCRIPT_BYTES = 512 * 1024

const props = defineProps<{
  testCase: TestCaseSummary | null
  projects: Project[]
  defaultProjectId: number | null
}>()
const emit = defineEmits<{ saved: [testCase: TestCaseDetail] }>()
const open = defineModel<boolean>('open', { default: false })
const toast = useToast()

const projectId = ref('')
const name = ref('')
const type = ref('WEB')
const description = ref('')
const command = ref('')
const scriptFilename = ref<string | null>(null)
const scriptContent = ref<string | null>(null)
const enabled = ref(true)
const loading = ref(false)
const saving = ref(false)
const error = ref<string | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)

const typeItems = [
  { value: 'WEB', label: 'Web 测试' },
  { value: 'API', label: '接口测试' },
]
const typeModel = computed({
  get: () => type.value,
  set: (value: string) => {
    type.value = value
  },
})
const projectItems = computed(() =>
  props.projects.map((project) => ({
    value: String(project.id),
    label: project.enabled ? project.name : `${project.name}（已暂停）`,
  })),
)
const scriptSize = computed(() => new Blob([scriptContent.value ?? '']).size)
const canSave = computed(
  () =>
    projectId.value !== '' &&
    name.value.trim().length > 0 &&
    (command.value.trim().length > 0 || scriptFilename.value !== null) &&
    !saving.value,
)

function messageOf(e: unknown): string {
  if (e instanceof ApiError) return e.message
  return '无法连接后端，请确认服务已启动（默认 127.0.0.1:8787）'
}

function defaultCommandFor(filename: string): string {
  const extension = filename.toLowerCase().split('.').pop() ?? ''
  if (extension === 'sh') return 'sh "$REGENT_TEST_FILE"'
  if (extension === 'js' || extension === 'mjs' || extension === 'cjs') {
    return 'node "$REGENT_TEST_FILE"'
  }
  if (extension === 'py') return 'python3 "$REGENT_TEST_FILE"'
  return ''
}

watch(open, async (opened) => {
  if (!opened) return
  loading.value = props.testCase !== null
  saving.value = false
  error.value = null
  projectId.value = String(props.testCase?.projectId ?? props.defaultProjectId ?? props.projects[0]?.id ?? '')
  name.value = props.testCase?.name ?? ''
  type.value = props.testCase?.type ?? 'WEB'
  description.value = props.testCase?.description ?? ''
  command.value = props.testCase?.command ?? ''
  scriptFilename.value = props.testCase?.scriptFilename ?? null
  scriptContent.value = null
  enabled.value = props.testCase?.enabled ?? true
  if (!props.testCase) return
  try {
    const detail = await getTestCase(props.testCase.id)
    command.value = detail.command ?? ''
    scriptFilename.value = detail.scriptFilename
    scriptContent.value = detail.scriptContent
  } catch (e) {
    error.value = messageOf(e)
  } finally {
    loading.value = false
  }
})

const importFile = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (file.size > MAX_SCRIPT_BYTES) {
    toast.warning(
      `「${file.name}」太大（${formatBytes(file.size)}）`,
      `测试脚本上限 ${formatBytes(MAX_SCRIPT_BYTES)}`,
    )
    return
  }
  try {
    scriptContent.value = await file.text()
    scriptFilename.value = file.name
    if (!name.value.trim()) name.value = file.name.replace(/\.[^.]+$/, '')
    if (!command.value.trim()) command.value = defaultCommandFor(file.name)
  } catch {
    toast.warning('无法读取测试文件', '请确认文件是 UTF-8 文本或代码文件')
  }
}

const removeScript = () => {
  scriptFilename.value = null
  scriptContent.value = null
}

const save = async () => {
  if (!canSave.value) return
  saving.value = true
  error.value = null
  const input: SaveTestCaseInput = {
    projectId: Number(projectId.value),
    name: name.value.trim(),
    type: type.value,
    description: description.value.trim(),
    command: command.value.trim(),
    scriptFilename: scriptFilename.value,
    scriptContent: scriptContent.value,
    enabled: enabled.value,
  }
  try {
    const saved = props.testCase
      ? await updateTestCase(props.testCase.id, input)
      : await createTestCase(input)
    open.value = false
    emit('saved', saved)
    toast.success(
      props.testCase ? `测试用例「${saved.name}」已更新` : `测试用例「${saved.name}」已创建`,
      '绑定到任务后，会在任务成功完成时自动执行',
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
    :title="testCase ? '编辑测试用例' : '新建测试用例'"
    description="测试命令在项目目录执行，并自动继承项目环境信息。"
    :icon="FlaskConical"
  >
    <div class="flex flex-col gap-3">
      <p v-if="loading" class="text-caption text-label-tertiary">正在读取测试脚本…</p>

      <DialogField label="项目" required>
        <Select
          v-model="projectId"
          :items="projectItems"
          :disabled="testCase !== null"
          placeholder="选择项目"
          class="w-full"
        />
      </DialogField>

      <DialogField label="类型" required>
        <SegmentedControl v-model="typeModel" :items="typeItems" />
      </DialogField>

      <DialogField label="名称" required hint="例如：登录页回归、订单接口冒烟">
        <Input v-model="name" placeholder="登录页回归" class="w-full" />
      </DialogField>

      <DialogField label="说明" hint="写清覆盖范围、依赖环境和已知限制">
        <Textarea v-model="description" :rows="2" placeholder="验证登录成功后进入工作台，并检查错误提示" />
      </DialogField>

      <DialogField
        label="命令"
        required
        hint='在项目目录执行；上传文件可通过 $REGENT_TEST_FILE 取得临时路径。也可以只上传 .sh / .js / .py，由平台推断命令。'
      >
        <Input
          v-model="command"
          class="w-full font-mono"
          placeholder='npx playwright test "$REGENT_TEST_FILE" --reporter=line'
        />
      </DialogField>

      <DialogField label="测试文件" :hint="`UTF-8 文本或代码，最大 ${formatBytes(MAX_SCRIPT_BYTES)}`">
        <div v-if="scriptFilename" class="flex items-center gap-2 rounded-panel border border-separator-soft bg-content px-2.5 py-2">
          <span class="grid size-7 shrink-0 place-items-center rounded-control bg-accent/10 text-accent">
            <Icon :as="FileCheck2" size="inline" />
          </span>
          <div class="min-w-0 flex-1">
            <p class="truncate text-caption font-emphasis text-label" :title="scriptFilename">
              {{ scriptFilename }}
            </p>
            <p class="tnum mt-0.5 text-caption text-label-tertiary">
              {{ scriptContent ? formatBytes(scriptSize) : '已上传' }}
            </p>
          </div>
          <Button size="sm" @click="fileInput?.click()">替换</Button>
          <Button size="sm" variant="ghost" @click="removeScript">
            <Icon :as="X" size="inline" />移除
          </Button>
        </div>
        <Button v-else :disabled="loading || saving" @click="fileInput?.click()">
          <Icon :as="Upload" size="inline" />上传测试文件
        </Button>
        <input
          ref="fileInput"
          type="file"
          class="hidden"
          accept=".sh,.js,.mjs,.cjs,.ts,.py,.json,.yaml,.yml,.feature,.http,.txt"
          @change="importFile"
        />
      </DialogField>

      <DialogField label="状态">
        <Switch v-model="enabled" label="启用，可绑定到任务" />
      </DialogField>

      <p v-if="error" class="text-caption text-danger">{{ error }}</p>
    </div>

    <template #footer>
      <Button :disabled="saving" @click="open = false">取消</Button>
      <Button variant="primary" :disabled="!canSave" @click="save">
        <Icon :as="FlaskConical" size="inline" />
        {{ saving ? '保存中…' : '保存测试用例' }}
      </Button>
    </template>
  </Dialog>
</template>
