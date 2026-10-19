<script setup lang="ts">
import { Save, Server } from '@lucide/vue'
import { computed, ref, watch } from 'vue'

import {
  createDeploymentServer,
  updateDeploymentServer,
  type SaveDeploymentServerInput,
} from '@/api/deploymentServers'
import { ApiError } from '@/api/http'
import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import DialogField from '@/components/ui/dialog/DialogField.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import Input from '@/components/ui/input/Input.vue'
import SegmentedControl from '@/components/ui/segmented-control/SegmentedControl.vue'
import Select from '@/components/ui/select/Select.vue'
import { useToast } from '@/composables/useToast'
import type { DeploymentAuthType, DeploymentServer, Project } from '@/types/api'

const props = defineProps<{
  server: DeploymentServer | null
  projects: Project[]
  defaultProjectId: number | null
}>()
const emit = defineEmits<{ saved: [server: DeploymentServer] }>()
const open = defineModel<boolean>('open', { default: false })
const toast = useToast()

const projectId = ref('')
const name = ref('')
const host = ref('')
const port = ref('22')
const username = ref('root')
const authType = ref<DeploymentAuthType>('KEY')
const password = ref('')
const credentialConfigured = ref(false)
const saving = ref(false)
const error = ref<string | null>(null)

const projectItems = computed(() =>
  props.projects.map((project) => ({
    value: String(project.id),
    label: project.enabled ? project.name : `${project.name}（已暂停）`,
  })),
)
const authItems = [
  { value: 'KEY', label: 'SSH 密钥' },
  { value: 'PASSWORD', label: '密码' },
]
const authModel = computed({
  get: () => authType.value,
  set: (value: string) => {
    authType.value = value as DeploymentAuthType
  },
})
const canSave = computed(
  () =>
    projectId.value !== '' &&
    name.value.trim().length > 0 &&
    host.value.trim().length > 0 &&
    Number(port.value) >= 1 &&
    Number(port.value) <= 65535 &&
    username.value.trim().length > 0 &&
    (authType.value !== 'PASSWORD' ||
      password.value.length > 0 ||
      credentialConfigured.value) &&
    !saving.value,
)

function messageOf(value: unknown): string {
  return value instanceof ApiError
    ? value.message
    : '无法连接后端，请确认服务已启动（默认 127.0.0.1:8787）'
}

watch(open, (value) => {
  if (!value) return
  saving.value = false
  error.value = null
  projectId.value = String(
    props.server?.projectId ??
      props.defaultProjectId ??
      props.projects[0]?.id ??
      '',
  )
  name.value = props.server?.name ?? ''
  host.value = props.server?.host ?? ''
  port.value = String(props.server?.port ?? 22)
  username.value = props.server?.username ?? 'root'
  authType.value = props.server?.authType ?? 'KEY'
  credentialConfigured.value = props.server?.credentialConfigured ?? false
  password.value = ''
})

const save = async () => {
  if (!canSave.value) return
  saving.value = true
  error.value = null
  const input: SaveDeploymentServerInput = {
    projectId: Number(projectId.value),
    name: name.value.trim(),
    host: host.value.trim(),
    port: Number(port.value),
    username: username.value.trim(),
    authType: authType.value,
    password: password.value,
  }
  try {
    const saved = props.server
      ? await updateDeploymentServer(props.server.id, input)
      : await createDeploymentServer(input)
    open.value = false
    emit('saved', saved)
    toast.success(
      props.server ? `服务器「${saved.name}」已更新` : `服务器「${saved.name}」已创建`,
      '部署配置可以直接复用这台服务器',
    )
  } catch (value) {
    error.value = messageOf(value)
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
    :title="server ? '编辑服务器' : '新建服务器'"
    description="服务器按项目复用；部署脚本只引用服务器，不再重复填写主机和认证信息。"
    :icon="Server"
  >
    <div class="flex flex-col gap-3">
      <DialogField label="归属项目" label-width="w-24" required hint="创建后不能修改服务器归属">
        <Select
          v-model="projectId"
          :items="projectItems"
          :disabled="server !== null"
          placeholder="选择项目"
          class="w-full"
        />
      </DialogField>

      <DialogField
        label="服务器名称"
        label-width="w-24"
        required
        hint="例如：生产 Web 节点、测试数据库跳板机"
      >
        <Input v-model="name" class="w-full" placeholder="生产 Web 节点" />
      </DialogField>

      <div class="flex items-center gap-2 pt-1">
        <span class="text-caption font-emphasis text-label-secondary">SSH 连接</span>
        <span class="h-px min-w-0 flex-1 bg-separator-soft" aria-hidden="true" />
      </div>

      <DialogField label="SSH 地址" label-width="w-24" required>
        <div class="flex gap-2">
          <div class="min-w-0 flex-1">
            <span class="mb-1 block text-caption text-label-tertiary">主机地址</span>
            <Input v-model="host" class="w-full" placeholder="10.0.0.12 或 api.example.com" />
          </div>
          <div class="w-24 shrink-0">
            <span class="mb-1 block text-caption text-label-tertiary">端口</span>
            <Input v-model="port" type="number" class="w-full" placeholder="22" />
          </div>
        </div>
      </DialogField>

      <DialogField label="登录用户" label-width="w-24" required>
        <Input v-model="username" class="w-full" placeholder="root" autocomplete="username" />
      </DialogField>

      <DialogField label="认证方式" label-width="w-24" required>
        <SegmentedControl v-model="authModel" :items="authItems" />
      </DialogField>

      <DialogField
        v-if="authType === 'PASSWORD'"
        label="SSH 密码"
        label-width="w-24"
        required
        :hint="
          credentialConfigured
            ? '已保存密码；留空保持不变，输入新密码会覆盖'
            : '密码会加密保存在本机，部署执行时自动读取'
        "
      >
        <Input
          v-model="password"
          type="password"
          class="w-full"
          :placeholder="credentialConfigured ? '已保存，留空保持不变' : '输入服务器登录密码'"
          autocomplete="new-password"
        />
      </DialogField>

      <p
        v-if="error"
        class="ml-[108px] rounded-panel bg-danger/8 px-3 py-2 text-caption text-danger"
      >
        {{ error }}
      </p>
    </div>

    <template #footer>
      <Button :disabled="saving" @click="open = false">取消</Button>
      <Button variant="primary" :disabled="!canSave" @click="save">
        <Icon :as="Save" size="inline" />
        {{ saving ? '保存中…' : '保存服务器' }}
      </Button>
    </template>
  </Dialog>
</template>
