<script setup lang="ts">
import {
  BookOpenText,
  Check,
  ChevronDown,
  ChevronUp,
  CircleAlert,
  CircleCheck,
  Copy,
  FileCode2,
  FolderOpen,
  Plus,
  RefreshCw,
  ServerCog,
  Sparkles,
  Square,
  Upload,
  X,
} from '@lucide/vue'
import { computed, onUnmounted, ref, watch } from 'vue'

import { ApiError } from '@/api/http'
import {
  createDeployment,
  cancelDeploymentScriptGeneration,
  generateDeploymentScript,
  getDeployment,
  updateDeployment,
  validateDeploymentScript,
  type SaveDeploymentInput,
} from '@/api/deployments'
import { listDeploymentServers } from '@/api/deploymentServers'
import Button from '@/components/ui/button/Button.vue'
import DeploymentServerDialog from '@/components/business/DeploymentServerDialog.vue'
import DirectoryPickerDialog from '@/components/business/DirectoryPickerDialog.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import DialogField from '@/components/ui/dialog/DialogField.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import Input from '@/components/ui/input/Input.vue'
import Select from '@/components/ui/select/Select.vue'
import {
  DEPLOYMENT_SCRIPT_RULES,
  DEPLOYMENT_SCRIPT_TEMPLATE,
  DEPLOYMENT_SCRIPT_VARIABLES,
  PROJECT_DEPLOYMENT_SCRIPT_RULES,
  PROJECT_DEPLOYMENT_SCRIPT_VARIABLES,
} from '@/content/deploymentScript'
import { useToast } from '@/composables/useToast'
import type {
  DeploymentDetail,
  DeploymentExecutionMode,
  DeploymentScriptValidation,
  DeploymentServer,
  DeploymentSummary,
  Project,
} from '@/types/api'
import { inferDeploymentDirectory } from '@/utils/deploymentDirectory'
import { resolveDeploymentServerSelection } from '@/utils/deploymentServerSelection'
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
const serverId = ref('')
const allServers = ref<DeploymentServer[]>([])
const name = ref('')
const remoteDirectory = ref('')
const remoteDirectoryAutoFilled = ref(false)
let lastAutoFilledDirectory: string | null = null
const workspacePath = ref('')
const executionMode = ref<DeploymentExecutionMode>('REMOTE_SCRIPT')
const projectScriptPath = ref('')
const scriptFilename = ref<string | null>(null)
const scriptContent = ref<string | null>(null)
const scriptValidation = ref<DeploymentScriptValidation | null>(null)
const checkingScript = ref(false)
const generatingScript = ref(false)
const stoppingScript = ref(false)
const cancelledGeneration = ref(false)
let generationRequestId: string | null = null
const loading = ref(false)
const loadingServers = ref(false)
const saving = ref(false)
const error = ref<string | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const specOpen = ref(true)
const copiedTemplate = ref(false)
const serverDialogOpen = ref(false)
const directoryPickerOpen = ref(false)
const editingServer = ref<DeploymentServer | null>(null)
let copiedTemplateTimer: number | undefined
let initializing = false

const projectItems = computed(() =>
  props.projects.map((project) => ({
    value: String(project.id),
    label: project.enabled ? project.name : `${project.name}（已暂停）`,
  })),
)
const servers = computed(() => {
  const selectedProjectId = Number(projectId.value)
  if (!Number.isSafeInteger(selectedProjectId) || selectedProjectId <= 0) return []
  return allServers.value.filter((server) => server.projectId === selectedProjectId)
})
const selectedProject = computed(
  () => props.projects.find((project) => String(project.id) === projectId.value) ?? null,
)
const serverItems = computed(() =>
  servers.value.map((server) => ({
    value: String(server.id),
    label: `${server.name} · ${server.username}@${server.host}:${server.port}`,
  })),
)
const executionModeItems = [
  { value: 'REMOTE_SCRIPT', label: '远端脚本（SSH stdin）' },
  { value: 'PROJECT_SCRIPT', label: '项目发布脚本（本机构建、上传、重启）' },
] as const
const deploymentVariables = computed(() =>
  executionMode.value === 'PROJECT_SCRIPT'
    ? PROJECT_DEPLOYMENT_SCRIPT_VARIABLES
    : DEPLOYMENT_SCRIPT_VARIABLES,
)
const deploymentRules = computed(() =>
  executionMode.value === 'PROJECT_SCRIPT'
    ? PROJECT_DEPLOYMENT_SCRIPT_RULES
    : DEPLOYMENT_SCRIPT_RULES,
)
const selectedServer = computed(
  () => servers.value.find((server) => String(server.id) === serverId.value) ?? null,
)

/**
 * 标准脚本只读取 REGENT_DEPLOY_DIRECTORY，不可能从脚本里反推路径。
 * 服务器上保存的默认目录才是这个参数的来源；新建部署选中服务器后自动带出。
 */
const applyServerDefaultDirectory = () => {
  const fallback = selectedServer.value?.defaultRemoteDirectory?.trim() ?? ''
  if (remoteDirectoryAutoFilled.value) {
    if (!fallback) {
      remoteDirectory.value = ''
      remoteDirectoryAutoFilled.value = false
      lastAutoFilledDirectory = null
      return
    }
    remoteDirectory.value = fallback
    lastAutoFilledDirectory = fallback
    return
  }
  if (!remoteDirectory.value.trim() && fallback) {
    remoteDirectory.value = fallback
    lastAutoFilledDirectory = fallback
    remoteDirectoryAutoFilled.value = true
  }
}

watch(remoteDirectory, (value) => {
  if (remoteDirectoryAutoFilled.value && value !== lastAutoFilledDirectory) {
    remoteDirectoryAutoFilled.value = false
    lastAutoFilledDirectory = null
  }
})
const scriptSize = computed(() => new Blob([scriptContent.value ?? '']).size)
const canGenerateScript = computed(
  () =>
    projectId.value !== '' &&
    serverId.value !== '' &&
    workspacePath.value.trim().length > 0 &&
    remoteDirectory.value.trim().length > 0 &&
    !loading.value &&
    !loadingServers.value &&
    !checkingScript.value &&
    !generatingScript.value &&
    !saving.value,
)
const canSave = computed(
  () =>
    projectId.value !== '' &&
    serverId.value !== '' &&
    name.value.trim().length > 0 &&
    remoteDirectory.value.trim().length > 0 &&
    (executionMode.value === 'REMOTE_SCRIPT' || projectScriptPath.value.trim().length > 0) &&
    scriptFilename.value !== null &&
    scriptContent.value !== null &&
    scriptValidation.value?.valid === true &&
    !checkingScript.value &&
    !generatingScript.value &&
    !saving.value &&
    !loading.value,
)

const remoteDirectoryNotice = computed(() => {
  if (remoteDirectory.value.trim() || !scriptContent.value?.trim()) return null
  if (scriptContent.value.includes('REGENT_DEPLOY_DIRECTORY')) {
    return '该脚本通过 REGENT_DEPLOY_DIRECTORY 读取远程目录，没有写死路径；请在服务器设置里保存默认远程目录，或在这里手动填写项目根目录（package.json 所在目录）。'
  }
  return '未从脚本中识别到远程目录，请手动填写服务器上的部署工作目录。'
})

const generateHint = computed(() => {
  if (projectId.value === '') return '先选择所属项目'
  if (workspacePath.value.trim() === '') return '先选择项目文件夹'
  if (serverId.value === '') return '先选择部署服务器'
  if (remoteDirectory.value.trim() === '') return '先填写远程目录'
  return '让 Codex 分析项目并生成部署脚本'
})

function messageOf(e: unknown): string {
  if (e instanceof ApiError) return e.message
  return '无法连接后端，请确认服务已启动（默认 127.0.0.1:8787）'
}

watch(open, async (opened) => {
  if (!opened) {
    if (generationRequestId) {
      void cancelDeploymentScriptGeneration(generationRequestId).catch(() => false)
    }
    return
  }
  initializing = true
  loading.value = props.deployment !== null
  saving.value = false
  error.value = null
  const requestedProjectId = props.deployment?.projectId ?? props.defaultProjectId
  projectId.value =
    requestedProjectId === null
      ? String(props.projects[0]?.id ?? '')
      : String(requestedProjectId)
  workspacePath.value = selectedProject.value?.path ?? ''
  serverId.value =
    props.deployment?.serverId === undefined ? '' : String(props.deployment.serverId)
  executionMode.value = props.deployment?.executionMode ?? 'REMOTE_SCRIPT'
  projectScriptPath.value = props.deployment?.projectScriptPath ?? ''
  name.value = props.deployment?.name ?? ''
  remoteDirectory.value = props.deployment?.remoteDirectory ?? ''
  remoteDirectoryAutoFilled.value = false
  lastAutoFilledDirectory = null
  scriptFilename.value = props.deployment?.scriptFilename ?? null
  scriptContent.value = null
  scriptValidation.value = null
  await loadServers(requestedProjectId)
  applyServerDefaultDirectory()
  initializing = false
  if (!props.deployment) return
  try {
    const detail = await getDeployment(props.deployment.id)
    serverId.value = String(detail.serverId)
    executionMode.value = detail.executionMode
    projectScriptPath.value = detail.projectScriptPath ?? ''
    remoteDirectory.value = detail.remoteDirectory
    scriptFilename.value = detail.scriptFilename
    scriptContent.value = detail.scriptContent
    const storedDirectory = detail.remoteDirectory.trim()
    if (!storedDirectory || storedDirectory === '~') {
      const inferredDirectory = inferDeploymentDirectory(detail.scriptContent)
      if (inferredDirectory) {
        remoteDirectory.value = inferredDirectory
        remoteDirectoryAutoFilled.value = false
        lastAutoFilledDirectory = null
      }
    }
    if (!remoteDirectory.value.trim()) applyServerDefaultDirectory()
    await validateScript()
  } catch (e) {
    error.value = messageOf(e)
  } finally {
    loading.value = false
  }
})

const selectAvailableServer = () => {
  if (!servers.value.some((server) => String(server.id) === serverId.value)) {
    serverId.value = servers.value[0] ? String(servers.value[0].id) : ''
  }
}

const loadServers = async (requestedProjectId: number | null) => {
  loadingServers.value = true
  try {
    allServers.value = await listDeploymentServers()
    const selection = resolveDeploymentServerSelection(
      requestedProjectId,
      props.projects,
      allServers.value,
    )
    if (selection.projectId !== null) {
      projectId.value = String(selection.projectId)
      workspacePath.value = selectedProject.value?.path ?? ''
    }
    selectAvailableServer()
  } catch (e) {
    allServers.value = []
    serverId.value = ''
    error.value = messageOf(e)
  } finally {
    loadingServers.value = false
  }
}

watch(projectId, () => {
  if (open.value && !initializing) {
    workspacePath.value = selectedProject.value?.path ?? ''
    selectAvailableServer()
    applyServerDefaultDirectory()
  }
})

watch(serverId, () => {
  if (open.value && !initializing) applyServerDefaultDirectory()
})

watch(
  () => props.projects.map((project) => project.id).join(','),
  () => {
    if (!open.value || initializing || projectId.value !== '' || !props.projects[0]) return
    projectId.value = String(props.projects[0].id)
    workspacePath.value = selectedProject.value?.path ?? ''
  },
)

const openNewServer = () => {
  editingServer.value = null
  serverDialogOpen.value = true
}

const editSelectedServer = () => {
  if (!selectedServer.value) return
  editingServer.value = selectedServer.value
  serverDialogOpen.value = true
}

const onServerSaved = async (server: DeploymentServer) => {
  if (String(server.projectId) === projectId.value) {
    allServers.value = await listDeploymentServers()
    selectAvailableServer()
    serverId.value = String(server.id)
  }
}

const onWorkspacePicked = (path: string) => {
  workspacePath.value = path
  directoryPickerOpen.value = false
}

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
    const content = await file.text()
    scriptContent.value = content
    scriptFilename.value = file.name
    if (
      executionMode.value === 'REMOTE_SCRIPT' &&
      content.includes('deploy-local.sh') &&
      content.includes('REGENT_SERVER_IP')
    ) {
      executionMode.value = 'PROJECT_SCRIPT'
      if (!projectScriptPath.value.trim()) {
        projectScriptPath.value = 'deploy/remote/deploy.sh'
      }
      toast.success('已识别为本机完整发布脚本', projectScriptPath.value)
    }
    if (!name.value.trim()) name.value = file.name.replace(/\.[^.]+$/, '')
    const inferredDirectory = inferDeploymentDirectory(content)
    if (inferredDirectory) {
      remoteDirectory.value = inferredDirectory
      remoteDirectoryAutoFilled.value = false
      lastAutoFilledDirectory = null
      toast.success('已从脚本识别远程目录', inferredDirectory)
    } else if (!remoteDirectory.value.trim()) {
      toast.warning(
        '未从脚本识别到远程目录',
        content.includes('REGENT_DEPLOY_DIRECTORY')
          ? '脚本通过 REGENT_DEPLOY_DIRECTORY 读取路径，请手动填写服务器上的项目根目录'
          : '请手动填写服务器上的部署工作目录',
      )
    }
    await validateScript()
  } catch {
    toast.warning('无法读取部署脚本', '请确认文件是 UTF-8 文本脚本')
  }
}

const removeScript = () => {
  scriptFilename.value = null
  scriptContent.value = null
  scriptValidation.value = null
}

const validateScript = async () => {
  if (scriptContent.value === null) {
    scriptValidation.value = null
    return
  }
  checkingScript.value = true
  try {
    scriptValidation.value = await validateDeploymentScript(scriptContent.value)
  } catch (e) {
    scriptValidation.value = {
      valid: false,
      issues: [{ line: 1, code: 'VALIDATION_FAILED', message: messageOf(e) }],
    }
  } finally {
    checkingScript.value = false
  }
}

const generateScript = async () => {
  if (!canGenerateScript.value) return
  generatingScript.value = true
  stoppingScript.value = false
  cancelledGeneration.value = false
  generationRequestId = newGenerationRequestId()
  error.value = null
  try {
    const generated = await generateDeploymentScript({
      requestId: generationRequestId,
      projectId: Number(projectId.value),
      serverId: Number(serverId.value),
      workspacePath: workspacePath.value.trim(),
      remoteDirectory: remoteDirectory.value.trim(),
    })
    if (cancelledGeneration.value) {
      error.value = null
      toast.warning('已停止 AI 生成', '当前脚本内容保持不变')
      return
    }
    scriptFilename.value = generated.scriptFilename
    scriptContent.value = generated.scriptContent
    await validateScript()
    if (scriptValidation.value?.valid) {
      toast.success('Codex 已生成部署脚本', generated.scriptFilename)
    }
  } catch (e) {
    if (cancelledGeneration.value) {
      error.value = null
      toast.warning('已停止 AI 生成', '当前脚本内容保持不变')
      return
    }
    const message = messageOf(e)
    error.value = message
    toast.failure('部署脚本生成失败', message)
  } finally {
    generatingScript.value = false
    stoppingScript.value = false
    cancelledGeneration.value = false
    generationRequestId = null
  }
}

const newGenerationRequestId = (): string =>
  globalThis.crypto?.randomUUID?.() ??
  `deploy-${Date.now()}-${Math.random().toString(36).slice(2)}`

const stopScriptGeneration = async () => {
  const requestId = generationRequestId
  if (!requestId || stoppingScript.value) return
  stoppingScript.value = true
  cancelledGeneration.value = true
  try {
    const cancelled = await cancelDeploymentScriptGeneration(requestId)
    if (!cancelled) {
      cancelledGeneration.value = false
      toast.warning('AI 生成已经结束', '无需再停止')
    }
  } catch (e) {
    cancelledGeneration.value = false
    stoppingScript.value = false
    toast.failure('停止 AI 生成失败', messageOf(e))
  }
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

onUnmounted(() => {
  window.clearTimeout(copiedTemplateTimer)
  if (generationRequestId) {
    void cancelDeploymentScriptGeneration(generationRequestId).catch(() => false)
  }
})

const save = async () => {
  if (!canSave.value) return
  saving.value = true
  error.value = null
  const input: SaveDeploymentInput = {
    projectId: Number(projectId.value),
    serverId: Number(serverId.value),
    name: name.value.trim(),
    remoteDirectory: remoteDirectory.value.trim(),
    scriptFilename: scriptFilename.value ?? '',
    scriptContent: scriptContent.value ?? '',
    executionMode: executionMode.value,
    projectScriptPath:
      executionMode.value === 'PROJECT_SCRIPT' ? projectScriptPath.value.trim() : null,
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
    description="先选择部署脚本和已录入的服务器；主机与密码只在服务器资源里维护一次。"
    :icon="ServerCog"
  >
    <div class="flex flex-col gap-3">
      <p v-if="loading" class="text-caption text-label-tertiary">正在读取部署脚本…</p>

      <DialogField
        label="部署脚本"
        required
        :hint="`上传 UTF-8 shell 脚本，或选择项目文件夹和远程目录后由 Codex 生成；保存前自动检测规范`"
      >
        <div
          v-if="scriptFilename"
          class="flex flex-wrap items-center gap-2 rounded-panel border border-separator-soft bg-content px-2.5 py-2"
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
          <Button
            size="sm"
            :disabled="!canGenerateScript"
            :title="generateHint"
            @click="generateScript"
          >
            <Icon :as="generatingScript ? RefreshCw : Sparkles" size="inline" />
            {{ generatingScript ? '生成中…' : 'AI 生成' }}
          </Button>
          <Button size="sm" variant="ghost" @click="removeScript">
            <Icon :as="X" size="inline" />移除
          </Button>
        </div>
        <div v-else class="flex flex-wrap gap-2">
          <Button :disabled="loading || saving" @click="fileInput?.click()">
            <Icon :as="Upload" size="inline" />上传部署脚本
          </Button>
          <Button
            :disabled="!canGenerateScript"
            :title="generateHint"
            @click="generateScript"
          >
            <Icon :as="generatingScript ? RefreshCw : Sparkles" size="inline" />
            {{ generatingScript ? 'Codex 生成中…' : 'AI 生成脚本' }}
          </Button>
        </div>
        <input
          ref="fileInput"
          type="file"
          class="hidden"
          accept=".sh,.bash,.zsh,.command,.txt"
          @change="importFile"
        />
        <div class="mt-2">
          <div
            v-if="generatingScript"
            class="flex items-center justify-between gap-3"
          >
            <p class="flex min-w-0 items-center gap-1.5 text-caption text-label-tertiary">
              <Icon :as="RefreshCw" size="inline" class="animate-spin" />
              <span class="truncate">Codex 正在分析项目并生成脚本…</span>
            </p>
            <Button
              size="sm"
              variant="ghost"
              :disabled="stoppingScript"
              @click="stopScriptGeneration"
            >
              <Icon :as="Square" size="inline" />
              {{ stoppingScript ? '停止中…' : '停止生成' }}
            </Button>
          </div>
          <p v-else-if="checkingScript" class="flex items-center gap-1.5 text-caption text-label-tertiary">
            <Icon :as="RefreshCw" size="inline" class="animate-spin" />正在检测脚本…
          </p>
          <div
            v-else-if="scriptValidation?.valid"
            class="flex items-center gap-1.5 text-caption text-success"
          >
            <Icon :as="CircleCheck" size="inline" />脚本检测通过
          </div>
          <div v-else-if="scriptValidation && !scriptValidation.valid">
            <div class="flex items-center gap-1.5 text-caption font-emphasis text-danger">
              <Icon :as="CircleAlert" size="inline" />脚本不符合规范，修复后才能保存和执行
            </div>
            <ul class="mt-1 space-y-1">
              <li
                v-for="issue in scriptValidation.issues"
                :key="`${issue.line}-${issue.code}`"
                class="text-caption leading-relaxed text-danger"
              >
                第 {{ issue.line }} 行：{{ issue.message }}
              </li>
            </ul>
            <Button
              size="sm"
              class="mt-1.5"
              :disabled="checkingScript || scriptContent === null"
              @click="validateScript"
            >
              <Icon :as="RefreshCw" size="inline" />重新检测
            </Button>
          </div>
        </div>
      </DialogField>

      <DialogField
        label="执行方式"
        required
        hint="完整发布选择项目发布脚本；由脚本在本机构建并上传，平台不再把脚本文本送到远端"
      >
        <Select
          v-model="executionMode"
          :items="[...executionModeItems]"
          class="w-full"
        />
      </DialogField>

      <DialogField
        v-if="executionMode === 'PROJECT_SCRIPT'"
        label="项目脚本路径"
        required
        hint="相对归属项目根目录，例如 deploy/remote/deploy.sh；上传内容必须与文件一致"
      >
        <Input
          v-model="projectScriptPath"
          class="w-full font-mono"
          placeholder="deploy/remote/deploy.sh"
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

      <DialogField
        label="项目文件夹"
        hint="AI 生成时读取这个目录，优先参考其中的 deploy / script 脚本；必须位于所选项目工作区内"
      >
        <div class="flex gap-2">
          <Input
            v-model="workspacePath"
            class="min-w-0 flex-1 font-mono"
            placeholder="选择项目工作区或子目录"
          />
          <Button
            :disabled="projectId === ''"
            @click="directoryPickerOpen = true"
          >
            <Icon :as="FolderOpen" size="inline" />选择目录
          </Button>
        </div>
      </DialogField>

      <DialogField
        label="部署名称"
        required
        hint="用于部署列表识别；与上方脚本文件名相互独立，例如：生产环境 API、预发布回归环境"
      >
        <Input v-model="name" class="w-full" placeholder="生产环境 API" />
      </DialogField>

      <DialogField
        label="服务器"
        required
        hint="服务器连接信息由项目级服务器资源统一维护，可被多个部署脚本复用"
      >
        <div class="flex gap-2">
          <div class="min-w-0 flex-1">
            <Select
              v-model="serverId"
              :items="serverItems"
              :disabled="loadingServers"
              placeholder="选择服务器"
              class="w-full"
            />
          </div>
          <Button :disabled="loadingServers" @click="openNewServer">
            <Icon :as="Plus" size="inline" />新建服务器
          </Button>
        </div>
        <p v-if="loadingServers" class="mt-1 text-caption text-label-tertiary">
          正在读取服务器…
        </p>
        <p
          v-else-if="selectedServer"
          class="mt-1 truncate font-mono text-caption text-label-tertiary"
        >
          {{ selectedServer.username }}@{{ selectedServer.host }}:{{ selectedServer.port }}
        </p>
        <p v-else class="mt-1 text-caption text-warning">
          当前项目还没有服务器，请切换上方项目或先新建一台。
        </p>
      </DialogField>

      <DialogField
        label="远程目录"
        required
        hint="部署脚本在服务器上的工作目录；新建部署会优先带出服务器默认目录，也可以在这里覆盖"
      >
        <Input
          v-model="remoteDirectory"
          class="w-full font-mono"
          :invalid="Boolean(remoteDirectoryNotice)"
          placeholder="~ 或 /opt/regent"
        />
        <div
          v-if="remoteDirectoryNotice"
          class="mt-1 flex flex-wrap items-start gap-x-2 gap-y-1"
        >
          <p class="min-w-0 flex-1 text-caption leading-relaxed text-warning">
            {{ remoteDirectoryNotice }}
          </p>
          <button
            v-if="selectedServer && !selectedServer.defaultRemoteDirectory"
            type="button"
            class="shrink-0 text-caption font-emphasis text-accent hover:underline hover:underline-offset-2"
            @click="editSelectedServer"
          >
            设置服务器默认目录
          </button>
        </div>
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
                远端、本机两种执行方式都使用同一套服务器配置
              </span>
            </span>
            <Icon :as="specOpen ? ChevronUp : ChevronDown" size="inline" class="text-label-tertiary" />
          </button>

          <div v-if="specOpen" class="border-t border-separator-soft px-3 py-3">
            <div class="flex items-start gap-2 rounded-control bg-window px-2.5 py-2">
              <Icon :as="ServerCog" size="inline" class="mt-0.5 shrink-0 text-accent" />
              <p class="text-caption leading-relaxed text-label-secondary">
                <template v-if="executionMode === 'PROJECT_SCRIPT'">
                  平台在项目根目录执行指定脚本，并传入
                  <code class="font-mono text-label">-H</code>
                  <code class="font-mono text-label">-P</code>
                  <code class="font-mono text-label">-u</code>
                  <code class="font-mono text-label">-r</code>；
                  密码只通过
                  <code class="font-mono text-label">REGENT_SERVER_PASSWORD</code>
                  环境变量传递，不出现在命令行和日志中。
                </template>
                <template v-else>
                  IP、端口、用户名和认证方式由表单保存，执行时自动注入。脚本无需再实现
                  <code class="font-mono text-label">-H</code>、
                  <code class="font-mono text-label">-u</code> 这类连接参数；SSH 密码只用于连接，
                  不会传给远端脚本。
                </template>
              </p>
            </div>

            <div class="mt-3">
              <p class="text-caption font-emphasis text-label">可用环境变量</p>
              <dl class="mt-1.5 divide-y divide-separator-soft">
                <div
                  v-for="variable in deploymentVariables"
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
                v-for="rule in deploymentRules"
                :key="rule"
                class="flex gap-2 text-caption leading-relaxed text-label-secondary"
              >
                <span class="mt-[7px] size-1 shrink-0 rounded-full bg-accent" aria-hidden="true" />
                <span>{{ rule }}</span>
              </li>
            </ul>

            <div
              v-if="executionMode === 'REMOTE_SCRIPT'"
              class="mt-3 overflow-hidden rounded-control bg-window"
            >
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

  <DeploymentServerDialog
    v-model:open="serverDialogOpen"
    :server="editingServer"
    :projects="projects"
    :default-project-id="projectId === '' ? null : Number(projectId)"
    @saved="onServerSaved"
  />

  <DirectoryPickerDialog
    v-model:open="directoryPickerOpen"
    :initial="workspacePath || selectedProject?.path"
    @picked="onWorkspacePicked"
  />
</template>
