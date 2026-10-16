<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { createEnvironment, updateEnvironment, type SaveEnvironmentInput } from '@/api/environments'
import { ApiError } from '@/api/http'
import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import DialogField from '@/components/ui/dialog/DialogField.vue'
import Input from '@/components/ui/input/Input.vue'
import Textarea from '@/components/ui/input/Textarea.vue'
import Select from '@/components/ui/select/Select.vue'
import { useToast } from '@/composables/useToast'
import { useTaskStore } from '@/stores/useTaskStore'
import type { EnvironmentEntry, EnvironmentScope } from '@/types/api'

const props = defineProps<{
  entry: EnvironmentEntry | null
}>()

const emit = defineEmits<{ saved: [entry: EnvironmentEntry] }>()
const open = defineModel<boolean>('open', { default: false })

const store = useTaskStore()
const toast = useToast()

const scope = ref<EnvironmentScope>('GLOBAL')
const projectId = ref<string>()
const key = ref('')
const value = ref('')
const tagsText = ref('')
const description = ref('')
const error = ref<string | null>(null)
const saving = ref(false)

const scopeItems = [
  { value: 'GLOBAL', label: '全局' },
  { value: 'PROJECT', label: '指定项目' },
]

const projectItems = computed(() =>
  store.projects.map((project) => ({
    value: String(project.id),
    label: project.enabled ? `${project.name}（${project.code}）` : `${project.name}（已暂停）`,
  })),
)

const keyValid = computed(() => /^[A-Za-z_][A-Za-z0-9_]{0,127}$/.test(key.value.trim()))
const canSave = computed(
  () =>
    keyValid.value &&
    (scope.value === 'GLOBAL' || projectId.value !== undefined) &&
    !saving.value,
)

watch(open, async (opened) => {
  if (!opened) return
  error.value = null
  saving.value = false
  scope.value = props.entry?.scope ?? 'GLOBAL'
  projectId.value = props.entry?.projectId === null || props.entry?.projectId === undefined
    ? undefined
    : String(props.entry.projectId)
  key.value = props.entry?.key ?? ''
  value.value = props.entry?.value ?? ''
  tagsText.value = props.entry?.tags.join(', ') ?? ''
  description.value = props.entry?.description ?? ''
  try {
    await store.loadProjects()
    if (
      scope.value === 'PROJECT' &&
      projectId.value === undefined &&
      store.projects.length === 1
    ) {
      projectId.value = String(store.projects[0].id)
    }
  } catch (e) {
    error.value = messageOf(e)
  }
})

watch(scope, (next) => {
  if (next === 'GLOBAL') projectId.value = undefined
})

function messageOf(e: unknown): string {
  if (e instanceof ApiError) return e.message
  return '无法连接后端，请确认服务已启动（默认 127.0.0.1:8787）'
}

function parsedTags(): string[] {
  return tagsText.value
    .split(',')
    .map((tag) => tag.trim())
    .filter((tag, index, tags) => tag.length > 0 && tags.indexOf(tag) === index)
}

const save = async () => {
  if (!canSave.value) return
  saving.value = true
  error.value = null
  const input: SaveEnvironmentInput = {
    scope: scope.value,
    projectId: scope.value === 'PROJECT' ? Number(projectId.value) : null,
    key: key.value.trim(),
    value: value.value,
    tags: parsedTags(),
    description: description.value.trim(),
  }
  try {
    const saved = props.entry
      ? await updateEnvironment(props.entry.id, input)
      : await createEnvironment(input)
    open.value = false
    emit('saved', saved)
    toast.success(
      props.entry ? `环境键「${saved.key}」已更新` : `环境键「${saved.key}」已创建`,
      saved.scope === 'GLOBAL' ? '全局配置，所有项目可用' : '项目配置，同键时覆盖全局值',
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
    :title="props.entry ? '编辑环境信息' : '新增环境信息'"
    description="执行任务时可用。项目配置与全局配置同名时，项目值优先。"
  >
    <div class="flex flex-col gap-3">
      <DialogField label="归属" required>
        <Select v-model="scope" :items="scopeItems" block />
      </DialogField>

      <DialogField v-if="scope === 'PROJECT'" label="项目" required>
        <Select v-model="projectId" :items="projectItems" placeholder="选择项目" block />
      </DialogField>

      <DialogField label="环境键" required hint="用于执行进程环境变量，如 DB_HOST；只含字母、数字和下划线">
        <Input
          v-model="key"
          placeholder="DB_HOST"
          :invalid="key.length > 0 && !keyValid"
          class="w-full"
        />
      </DialogField>

      <DialogField label="环境信息" hint="可以是地址、账号、密码、证书或任意文本；允许为空和多行内容">
        <Textarea v-model="value" :rows="5" placeholder="jdbc:mysql://127.0.0.1:3306/app" />
      </DialogField>

      <DialogField label="标签" hint="用逗号分隔的信息分类，如 数据库, 测试环境">
        <Input v-model="tagsText" placeholder="数据库, 测试环境" class="w-full" />
      </DialogField>

      <DialogField label="描述">
        <Textarea v-model="description" :rows="3" placeholder="说明用途、权限范围或使用注意事项" />
      </DialogField>

      <p v-if="error" class="text-caption text-danger">{{ error }}</p>
    </div>

    <template #footer>
      <Button @click="open = false">取消</Button>
      <Button variant="primary" :disabled="!canSave" @click="save">
        {{ saving ? '保存中…' : '保存' }}
      </Button>
    </template>
  </Dialog>
</template>
