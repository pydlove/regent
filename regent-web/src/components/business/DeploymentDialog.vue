<script setup lang="ts">
import {
  BookOpenText,
  Check,
  ChevronDown,
  ChevronUp,
  Copy,
  FileCode2,
  ServerCog,
  Upload,
  X,
} from '@lucide/vue'
import { computed, onUnmounted, ref, watch } from 'vue'

import { ApiError } from '@/api/http'
import {
  createDeployment,
  getDeployment,
  updateDeployment,
  type SaveDeploymentInput,
} from '@/api/deployments'
import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import DialogField from '@/components/ui/dialog/DialogField.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import Input from '@/components/ui/input/Input.vue'
import SegmentedControl from '@/components/ui/segmented-control/SegmentedControl.vue'
import Select from '@/components/ui/select/Select.vue'
import {
  DEPLOYMENT_SCRIPT_RULES,
  DEPLOYMENT_SCRIPT_TEMPLATE,
  DEPLOYMENT_SCRIPT_VARIABLES,
} from '@/content/deploymentScript'
import { useToast } from '@/composables/useToast'
import type { DeploymentDetail, DeploymentSummary, Project } from '@/types/api'
import { formatBytes } from '@/utils/format'

const MAX_SCRIPT_BYTES = 512 * 1024

const props = defineProps<{
  deployment: DeploymentSummary | null
  projects: Project[]
  defaultProjectId: number | null
}>()
const emit = defineEmits<{ saved: [deployment: DeploymentDetail] }>()
const open = defineModel<boolean>('open', { default: false })
const toast = useToast()

const projectId = ref('')
const name = ref('')
const host = ref('')
const port = ref('22')
const username = ref('root')
const authType = ref('KEY')
const password = ref('')
const credentialConfigured = ref(false)
const remoteDirectory = ref('~')
const scriptFilename = ref<string | null>(null)
const scriptContent = ref<string | null>(null)
const loading = ref(false)
const saving = ref(false)
const error = ref<string | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const specOpen = ref(true)
const copiedTemplate = ref(false)
let copiedTemplateTimer: number | undefined

const authItems = [
  { value: 'KEY', label: 'SSH 密钥' },
  { value: 'PASSWORD', label: '密码' },
]
const projectItems = computed(() =>
  props.projects.map((project) => ({
    value: String(project.id),
    label: project.enabled ? project.name : `${project.name}（已暂停）`,
  })),
)
const authModel = computed({
  get: () => authType.value,
  set: (value: string) => {
    authType.value = value
  },
})
const scriptSize = computed(() => new Blob([scriptContent.value ?? '']).size)
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
    remoteDirectory.value.trim().length > 0 &&
    scriptFilename.value !== null &&
    scriptContent.value !== null &&
    !saving.value &&
    !loading.value,
)

function messageOf(e: unknown): string {
  if (e instanceof ApiError) return e.message
  return '无法连接后端，请确认服务已启动（默认 127.0.0.1:8787）'
}

watch(open, async (opened) => {
  if (!opened) return
  loading.value = props.deployment !== null
  saving.value = false
  error.value = null
  projectId.value = String(
    props.deployment?.projectId ??
      props.defaultProjectId ??
      props.projects[0]?.id ??
      '',
  )
  name.value = props.deployment?.name ?? ''
  host.value = props.deployment?.host ?? ''
  port.value = String(props.deployment?.port ?? 22)
  username.value = props.deployment?.username ?? 'root'
  authType.value = props.deployment?.authType ?? 'KEY'
  password.value = ''
  credentialConfigured.value = false
  remoteDirectory.value = props.deployment?.remoteDirectory ?? '~'
  scriptFilename.value = props.deployment?.scriptFilename ?? null
  scriptContent.value = null
  if (!props.deployment) return
  try {
    const detail = await getDeployment(props.deployment.id)
    host.value = detail.host
    port.value = String(detail.port)
    username.value = detail.username
    authType.value = detail.authType
    credentialConfigured.value = detail.credentialConfigured
    remoteDirectory.value = detail.remoteDirectory
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
      `部署脚本上限 ${formatBytes(MAX_SCRIPT_BYTES)}`,
    )
    return
  }
  try {
    scriptContent.value = await file.text()
    scriptFilename.value = file.name
    if (!name.value.trim()) name.value = file.name.replace(/\.[^.]+$/, '')
  } catch {
    toast.warning('无法读取部署脚本', '请确认文件是 UTF-8 文本脚本')
  }
}

const removeScript = () => {
  scriptFilename.value = null
  scriptContent.value = null
}

const copyTemplate = async () => {
  let copied = false
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(DEPLOYMENT_SCRIPT_TEMPLATE)
      copied = true
    }
  } catch {
    // 非 HTTPS 或浏览器拒绝剪贴板权限时，退回选中临时文本框复制
  }

  if (!copied) {
    const field = document.createElement('textarea')
    try {
      field.value = DEPLOYMENT_SCRIPT_TEMPLATE
      field.setAttribute('readonly', '')
      field.className = 'fixed left-[-9999px] top-0'
      document.body.appendChild(field)
      field.select()
      copied = document.execCommand('copy')
    } catch {
      copied = false
    } finally {
      field.remove()
    }
  }

  if (!copied) {
    toast.warning('模板没有复制成功', '可以在模板区域手动选中复制')
    return
  }
  copiedTemplate.value = true
  window.clearTimeout(copiedTemplateTimer)
  copiedTemplateTimer = window.setTimeout(() => {
    copiedTemplate.value = false
  }, 1600)
  toast.success('部署脚本模板已复制')
}

onUnmounted(() => window.clearTimeout(copiedTemplateTimer))

const save = async () => {
  if (!canSave.value) return
  saving.value = true
  error.value = null
  const input: SaveDeploymentInput = {
    projectId: Number(projectId.value),
    name: name.value.trim(),
    host: host.value.trim(),
    port: Number(port.value),
    username: username.value.trim(),
    authType: authType.value,
    remoteDirectory: remoteDirectory.value.trim(),
    scriptFilename: scriptFilename.value ?? '',
    scriptContent: scriptContent.value ?? '',
    password: password.value,
  }
  try {
    const saved = props.deployment
      ? await updateDeployment(props.deployment.id, input)
      : await createDeployment(input)
    open.value = false
    emit('saved', saved)
    toast.success(
      props.deployment ? `部署「${saved.name}」已更新` : `部署「${saved.name}」已创建`,
      '服务器信息和脚本已保存，可以立即执行',
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
    :title="deployment ? '编辑部署' : '新建部署'"
    description="先选择部署脚本，再填写项目、服务器和认证信息；SSH 密码加密保存，执行时只需二次确认。"
    :icon="ServerCog"
  >
    <div class="flex flex-col gap-3">
      <p v-if="loading" class="text-caption text-label-tertiary">正在读取部署脚本…</p>

      <DialogField
        label="部署脚本"
        required
        :hint="`先上传 UTF-8 shell 脚本，最大 ${formatBytes(MAX_SCRIPT_BYTES)}；执行时通过 SSH stdin 传入`"
      >
        <div
          v-if="scriptFilename"
          class="flex items-center gap-2 rounded-panel border border-separator-soft bg-content px-2.5 py-2"
        >
          <span class="grid size-7 shrink-0 place-items-center rounded-control bg-accent/10 text-accent">
            <Icon :as="FileCode2" size="inline" />
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
          <Icon :as="Upload" size="inline" />上传部署脚本
        </Button>
        <input
          ref="fileInput"
          type="file"
          class="hidden"
          accept=".sh,.bash,.zsh,.command,.txt"
          @change="importFile"
        />
      </DialogField>

      <DialogField
        label="归属项目"
        required
        hint="部署列表按顶栏选中的项目查询；创建后不能改归属"
      >
        <Select
          v-model="projectId"
          :items="projectItems"
          :disabled="deployment !== null"
          placeholder="选择项目"
          class="w-full"
        />
      </DialogField>

      <DialogField label="名称" required hint="例如：生产环境 API、预发布回归环境">
        <Input v-model="name" class="w-full" placeholder="生产环境 API" />
      </DialogField>

      <DialogField
        label="服务器"
        required
        hint="SSH 地址与端口；脚本中通过 REGENT_DEPLOY_HOST 与 REGENT_DEPLOY_PORT 读取"
      >
        <div class="flex gap-2">
          <div class="min-w-0 flex-1">
            <span class="mb-1 block text-caption text-label-tertiary">主机地址</span>
            <Input v-model="host" class="w-full" placeholder="10.0.0.12 或 api.example.com" />
          </div>
          <div class="w-20 shrink-0">
            <span class="mb-1 block text-caption text-label-tertiary">端口</span>
            <Input v-model="port" type="number" class="w-full" placeholder="22" />
          </div>
        </div>
      </DialogField>

      <DialogField label="登录用户" required hint="脚本中通过 REGENT_DEPLOY_USER 读取">
        <Input v-model="username" class="w-full" placeholder="root" autocomplete="username" />
      </DialogField>

      <DialogField label="认证方式" required>
        <SegmentedControl v-model="authModel" :items="authItems" />
        <p v-if="authType === 'PASSWORD'" class="mt-1 text-caption text-label-tertiary">
          密码会在保存配置时加密写入本机凭据库，不写日志，也不会通过接口回传。
        </p>
      </DialogField>

      <DialogField
        v-if="authType === 'PASSWORD'"
        label="SSH 密码"
        required
        :hint="
          credentialConfigured
            ? '已保存密码；留空保持不变，输入新密码会覆盖'
            : '保存部署时需要填写，执行时只做二次确认'
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

      <DialogField
        label="远程目录"
        required
        hint="脚本会在这个目录下执行，并通过 REGENT_DEPLOY_DIRECTORY 读取；支持 ~ 或绝对路径"
      >
        <Input v-model="remoteDirectory" class="w-full font-mono" placeholder="~ 或 /opt/regent" />
      </DialogField>

      <DialogField label="脚本规范">
        <div class="overflow-hidden rounded-panel border border-separator-soft bg-content">
          <button
            type="button"
            class="flex w-full items-center gap-2 px-3 py-2.5 text-left transition-colors duration-100 ease-apple hover:bg-hover focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-inset focus-visible:ring-accent/35"
            :aria-expanded="specOpen"
            @click="specOpen = !specOpen"
          >
            <span class="grid size-7 shrink-0 place-items-center rounded-control bg-accent/10 text-accent">
              <Icon :as="BookOpenText" size="inline" />
            </span>
            <span class="min-w-0 flex-1">
              <span class="block text-caption font-emphasis text-label">固定参数契约 · 非交互 bash</span>
              <span class="mt-0.5 block text-caption text-label-tertiary">
                平台负责 SSH 连接，脚本通过环境变量读取部署上下文
              </span>
            </span>
            <Icon :as="specOpen ? ChevronUp : ChevronDown" size="inline" class="text-label-tertiary" />
          </button>

          <div v-if="specOpen" class="border-t border-separator-soft px-3 py-3">
            <div class="flex items-start gap-2 rounded-control bg-window px-2.5 py-2">
              <Icon :as="ServerCog" size="inline" class="mt-0.5 shrink-0 text-accent" />
              <p class="text-caption leading-relaxed text-label-secondary">
                IP、端口、用户名和认证方式由表单保存，执行时自动注入。脚本无需再实现
                <code class="font-mono text-label">-H</code>、
                <code class="font-mono text-label">-u</code> 这类连接参数；SSH 密码只用于连接，
                不会传给远端脚本。
              </p>
            </div>

            <div class="mt-3">
              <p class="text-caption font-emphasis text-label">可用环境变量</p>
              <dl class="mt-1.5 divide-y divide-separator-soft">
                <div
                  v-for="variable in DEPLOYMENT_SCRIPT_VARIABLES"
                  :key="variable.name"
                  class="grid grid-cols-[minmax(0,1.1fr)_minmax(0,1fr)] gap-3 py-1.5"
                >
                  <dt class="break-all font-mono text-caption text-label">{{ variable.name }}</dt>
                  <dd class="text-caption leading-relaxed text-label-tertiary">
                    {{ variable.description }}
                  </dd>
                </div>
              </dl>
            </div>

            <ul class="mt-3 space-y-1.5">
              <li
                v-for="rule in DEPLOYMENT_SCRIPT_RULES"
                :key="rule"
                class="flex gap-2 text-caption leading-relaxed text-label-secondary"
              >
                <span class="mt-[7px] size-1 shrink-0 rounded-full bg-accent" aria-hidden="true" />
                <span>{{ rule }}</span>
              </li>
            </ul>

            <div class="mt-3 overflow-hidden rounded-control bg-window">
              <div class="flex items-center gap-2 border-b border-separator-soft px-2.5 py-1.5">
                <span class="min-w-0 flex-1 text-caption font-emphasis text-label">脚本模板</span>
                <Button size="sm" variant="ghost" @click="copyTemplate">
                  <Icon :as="copiedTemplate ? Check : Copy" size="inline" />
                  {{ copiedTemplate ? '已复制' : '复制' }}
                </Button>
              </div>
              <pre
                class="scroll-area max-h-56 overflow-auto whitespace-pre px-2.5 py-2 font-mono text-caption leading-relaxed text-label"
              ><code>{{ DEPLOYMENT_SCRIPT_TEMPLATE }}</code></pre>
            </div>
          </div>
        </div>
      </DialogField>

      <p v-if="error" class="text-caption text-danger">{{ error }}</p>
    </div>

    <template #footer>
      <Button :disabled="saving" @click="open = false">取消</Button>
      <Button variant="primary" :disabled="!canSave" @click="save">
        <Icon :as="ServerCog" size="inline" />
        {{ saving ? '保存中…' : '保存部署' }}
      </Button>
    </template>
  </Dialog>
</template>
