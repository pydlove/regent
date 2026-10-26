<script setup lang="ts">
import { FileUp, Save } from '@lucide/vue'
import { computed, ref, watch } from 'vue'

import {
  createCodeStandard,
  updateCodeStandard,
  type CodeStandardInput,
} from '@/api/codeStandards'
import { ApiError } from '@/api/http'
import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import DialogField from '@/components/ui/dialog/DialogField.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import Input from '@/components/ui/input/Input.vue'
import Select from '@/components/ui/select/Select.vue'
import Switch from '@/components/ui/switch/Switch.vue'
import Textarea from '@/components/ui/input/Textarea.vue'
import { useToast } from '@/composables/useToast'
import type { CodeStandard, CodeStandardScope } from '@/types/api'
import { formatBytes } from '@/utils/format'

const MAX_STANDARD_BYTES = 512 * 1024
const ACCEPTED_EXTENSIONS = ['.md', '.txt', '.yaml', '.yml', '.json', '.xml']

const props = defineProps<{
  projectId: number | null
  standard: CodeStandard | null
}>()
const emit = defineEmits<{ saved: [standard: CodeStandard] }>()
const open = defineModel<boolean>('open', { default: false })
const toast = useToast()

const name = ref('')
const description = ref('')
const scope = ref<CodeStandardScope>('GENERAL')
const enabled = ref(true)
const defaultApply = ref(true)
const sortOrderText = ref('0')
const file = ref<File | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const saving = ref(false)
const error = ref<string | null>(null)

const scopeItems = [
  { value: 'GENERAL', label: '通用' },
  { value: 'FRONTEND', label: '前端' },
  { value: 'BACKEND', label: '后端' },
  { value: 'TESTING', label: '测试' },
  { value: 'SECURITY', label: '安全' },
  { value: 'DATABASE', label: '数据库' },
  { value: 'DEPLOYMENT', label: '部署' },
]

const canSave = computed(
  () =>
    props.projectId !== null
    && name.value.trim().length > 0
    && (props.standard !== null || file.value !== null)
    && !saving.value,
)

watch(open, (opened) => {
  if (!opened) return
  name.value = props.standard?.name ?? ''
  description.value = props.standard?.description ?? ''
  scope.value = props.standard?.scope ?? 'GENERAL'
  enabled.value = props.standard?.enabled ?? true
  defaultApply.value = props.standard?.defaultApply ?? true
  sortOrderText.value = String(props.standard?.sortOrder ?? 0)
  file.value = null
  error.value = null
  saving.value = false
})

watch(enabled, (value) => {
  if (!value) defaultApply.value = false
})

const pickFile = (event: Event) => {
  const input = event.target as HTMLInputElement
  const selected = input.files?.[0] ?? null
  input.value = ''
  if (!selected) return
  const extension = selected.name.slice(selected.name.lastIndexOf('.')).toLowerCase()
  if (!ACCEPTED_EXTENSIONS.includes(extension)) {
    toast.warning('不支持这种规范格式', `请使用 ${ACCEPTED_EXTENSIONS.join('、')}`)
    return
  }
  if (selected.size > MAX_STANDARD_BYTES) {
    toast.warning(
      '规范文件太大',
      `开发规范必须是可读文本，单份上限 ${formatBytes(MAX_STANDARD_BYTES)}`,
    )
    return
  }
  file.value = selected
  if (!name.value.trim()) name.value = selected.name.replace(/\.[^.]+$/, '')
}

const save = async () => {
  if (!canSave.value || props.projectId === null) return
  saving.value = true
  error.value = null
  const input: CodeStandardInput = {
    projectId: props.projectId,
    name: name.value.trim(),
    description: description.value.trim(),
    scope: scope.value,
    enabled: enabled.value,
    defaultApply: defaultApply.value,
    sortOrder: Number(sortOrderText.value) || 0,
  }
  try {
    const saved = props.standard
      ? await updateCodeStandard(props.standard.id, input)
      : await createCodeStandard(input, file.value as File)
    open.value = false
    emit('saved', saved)
    toast.success(
      props.standard ? `开发规范「${saved.name}」已更新` : `开发规范「${saved.name}」已创建`,
      props.standard ? '新任务会继续使用当前版本' : '默认引用的规范会自动关联到新任务',
    )
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '开发规范保存失败'
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
    :title="standard ? '编辑开发规范' : '上传开发规范'"
    description="规范归属于当前项目。新建任务时可以自动引用，并在任务中保存版本快照。"
  >
    <div class="flex flex-col gap-3">
      <DialogField label="名称" required hint="项目内不能重名">
        <Input v-model="name" placeholder="例如：Java 后端开发规范" class="w-full" />
      </DialogField>

      <DialogField label="说明" hint="说明适用范围、负责人或注意事项">
        <Textarea v-model="description" :rows="2" placeholder="所有后端任务默认遵守" />
      </DialogField>

      <DialogField label="范围">
        <Select v-model="scope" :items="scopeItems" block />
      </DialogField>

      <DialogField
        v-if="!standard"
        label="规范文件"
        required
        :hint="`支持 ${ACCEPTED_EXTENSIONS.join('、')}，单份上限 ${formatBytes(MAX_STANDARD_BYTES)}`"
      >
        <div class="flex items-center gap-2">
          <Button :disabled="saving" @click="fileInput?.click()">
            <Icon :as="FileUp" size="inline" />选择文件
          </Button>
          <span class="min-w-0 flex-1 truncate text-caption text-label-secondary">
            {{ file?.name ?? '尚未选择' }}
          </span>
          <input
            ref="fileInput"
            type="file"
            :accept="ACCEPTED_EXTENSIONS.join(',')"
            class="hidden"
            @change="pickFile"
          />
        </div>
      </DialogField>

      <DialogField label="排序" hint="数值越小越靠前">
        <Input v-model="sortOrderText" type="number" class="w-28" />
      </DialogField>

      <DialogField label="应用">
        <div class="flex flex-col gap-2">
          <Switch v-model="enabled" label="启用这条开发规范" />
          <Switch
            v-model="defaultApply"
            :disabled="!enabled"
            label="新建任务时默认引用"
          />
        </div>
      </DialogField>

      <p v-if="error" class="text-caption text-danger">{{ error }}</p>
    </div>

    <template #footer>
      <Button :disabled="saving" @click="open = false">取消</Button>
      <Button variant="primary" :disabled="!canSave" @click="save">
        <Icon :as="Save" size="inline" />
        {{ saving ? '保存中…' : '保存规范' }}
      </Button>
    </template>
  </Dialog>
</template>
