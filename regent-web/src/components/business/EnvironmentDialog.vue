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
import Switch from '@/components/ui/switch/Switch.vue'
import { useToast } from '@/composables/useToast'
import { useTaskStore } from '@/stores/useTaskStore'
import type { EnvironmentEntry, EnvironmentScope } from '@/types/api'
import { ENVIRONMENT_NAME_MAX_LENGTH } from '@/utils/environmentValue'

const props = defineProps<{
  entry: EnvironmentEntry | null
  copySource: EnvironmentEntry | null
}>()

const emit = defineEmits<{ saved: [entry: EnvironmentEntry] }>()
const open = defineModel<boolean>('open', { default: false })

const store = useTaskStore()
const toast = useToast()

const scope = ref<EnvironmentScope>('GLOBAL')
const projectId = ref<string>()
const value = ref('')
const tagsText = ref('')
const name = ref('')
const secret = ref(false)
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
const followsSelectedProject = computed(
  () =>
    props.entry === null &&
    props.copySource === null &&
    store.projectId !== null,
)
const selectedProjectName = computed(
  () =>
    store.projects.find((project) => project.id === store.projectId)?.name ??
    `项目 #${store.projectId}`,
)

const nameValid = computed(() => {
  const length = name.value.trim().length
  return length > 0 && length <= ENVIRONMENT_NAME_MAX_LENGTH
})
const canSave = computed(
  () =>
    nameValid.value &&
    (scope.value === 'GLOBAL' || projectId.value !== undefined) &&
    !saving.value,
)

watch(open, async (opened) => {
  if (!opened) return
  error.value = null
  saving.value = false
  const source = props.entry ?? props.copySource
  if (followsSelectedProject.value) {
    scope.value = 'PROJECT'
    projectId.value = String(store.projectId)
  } else {
    scope.value = source?.scope ?? 'GLOBAL'
    projectId.value = source?.projectId === null || source?.projectId === undefined
      ? undefined
      : String(source.projectId)
  }
  value.value = source?.value ?? ''
  tagsText.value = source?.tags.join(', ') ?? ''
  name.value = source?.description ?? ''
  secret.value = source?.secret ?? false
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
    value: value.value,
    tags: parsedTags(),
    description: name.value.trim(),
    secret: secret.value,
  }
  try {
    const saved = props.entry
      ? await updateEnvironment(props.entry.id, input)
      : await createEnvironment(input)
    open.value = false
    emit('saved', saved)
    toast.success(
      props.entry
        ? `环境信息「${saved.description || saved.key}」已更新`
        : `环境信息「${saved.description || saved.key}」已创建`,
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
    :title="props.entry ? '编辑环境信息' : props.copySource ? '拷贝环境信息' : '新增环境信息'"
    description="执行任务时可用。项目配置与全局配置同名时，项目值优先。"
  >
    <div class="flex flex-col gap-3">
      <div
        v-if="followsSelectedProject"
        class="flex items-center gap-2 rounded-panel bg-accent/5 px-2.5 py-2 text-caption"
      >
        <span class="text-label-secondary">归属项目</span>
        <span class="min-w-0 flex-1 truncate font-emphasis text-label">
          {{ selectedProjectName }}
        </span>
        <span class="text-label-tertiary">跟随顶栏</span>
      </div>

      <template v-else>
        <DialogField label="归属项目" required>
          <Select v-model="scope" :items="scopeItems" block />
        </DialogField>

        <DialogField v-if="scope === 'PROJECT'" label="项目" required>
          <Select v-model="projectId" :items="projectItems" placeholder="选择项目" block />
        </DialogField>
      </template>

      <p class="rounded-panel bg-accent/5 px-2.5 py-1.5 text-caption text-label-secondary">
        环境键由平台自动生成。名称用于识别这条环境信息，最多 {{ ENVIRONMENT_NAME_MAX_LENGTH }} 个字符。
      </p>

      <DialogField
        label="名称"
        required
        :hint="`例如：生产 MySQL 只读连接；最多 ${ENVIRONMENT_NAME_MAX_LENGTH} 个字符`"
      >
        <Input
          v-model="name"
          :maxlength="ENVIRONMENT_NAME_MAX_LENGTH"
          placeholder="本地数据库连接信息"
          class="w-full"
        />
      </DialogField>

      <DialogField label="环境信息" hint="可以是地址、账号、密码、证书或任意文本；HTTP / HTTPS 地址会显示为链接">
        <Textarea v-model="value" :rows="5" placeholder="jdbc:mysql://127.0.0.1:3306/app" />
      </DialogField>

      <DialogField label="密码值" hint="开启后列表和详情默认脱敏，需要手动点击眼睛才能查看">
        <Switch v-model="secret" label="按密码脱敏" />
      </DialogField>

      <DialogField label="标签" hint="用逗号分隔的信息分类，如 数据库, 测试环境">
        <Input v-model="tagsText" placeholder="数据库, 测试环境" class="w-full" />
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
