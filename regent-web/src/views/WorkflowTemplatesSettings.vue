<script setup lang="ts">
import {
  ArrowUp,
  ChevronDown,
  Copy,
  Plus,
  RefreshCw,
  Trash2,
  Workflow,
} from '@lucide/vue'
import { computed, reactive, ref } from 'vue'

import { ApiError } from '@/api/http'
import {
  archiveWorkflowTemplate,
  createWorkflowTemplate,
  createWorkflowTemplateVersion,
  listWorkflowTemplates,
  type WorkflowTemplate,
  type WorkflowTemplateSaveInput,
  type WorkflowTemplateStatus,
} from '@/api/workflowTemplates'
import Badge from '@/components/ui/badge/Badge.vue'
import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import DialogField from '@/components/ui/dialog/DialogField.vue'
import EmptyState from '@/components/ui/empty-state/EmptyState.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import IconButton from '@/components/ui/icon-button/IconButton.vue'
import Input from '@/components/ui/input/Input.vue'
import Textarea from '@/components/ui/input/Textarea.vue'
import Select from '@/components/ui/select/Select.vue'
import { useToast } from '@/composables/useToast'
import { useOrganizationStore } from '@/stores/useOrganizationStore'
import {
  projectMemberRoleLabels,
} from '@/utils/projectMemberRoles'

type StageForm = {
  key: string
  code: string
  name: string
  scope: 'VERSION' | 'REQUIREMENT'
  approverRole: string
  description: string
}

const organizationStore = useOrganizationStore()
const toast = useToast()

const templates = ref<WorkflowTemplate[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const saving = ref(false)
const archiveId = ref<number | null>(null)

const editorOpen = ref(false)
const editorMode = ref<'create' | 'version'>('create')
const sourceTemplate = ref<WorkflowTemplate | null>(null)
const editorError = ref<string | null>(null)
const form = reactive({
  name: '',
  description: '',
  stages: [] as StageForm[],
})

const roleOptions = [
  { value: '__none__', label: '不限制' },
  ...(Object.keys(projectMemberRoleLabels) as Array<keyof typeof projectMemberRoleLabels>)
    .map((role) => ({ value: role, label: projectMemberRoleLabels[role] })),
]
const scopeOptions = [
  { value: 'VERSION', label: '版本级' },
  { value: 'REQUIREMENT', label: '需求级' },
]
const statusMeta: Record<
  WorkflowTemplateStatus,
  { label: string; tone: 'success' | 'neutral' | 'warning' }
> = {
  PUBLISHED: { label: '当前发布', tone: 'success' },
  SUPERSEDED: { label: '已替代', tone: 'neutral' },
  ARCHIVED: { label: '已停用', tone: 'warning' },
}
let stageKeySequence = 0

const canManage = computed(
  () => organizationStore.currentId !== null && organizationStore.canManageOrganization,
)
const publishedCount = computed(
  () => templates.value.filter((template) => template.status === 'PUBLISHED').length,
)
const editorTitle = computed(() => {
  if (editorMode.value === 'create') return '新建研发流程模板'
  return `复制「${sourceTemplate.value?.displayName ?? ''}」为新版本`
})
const canSave = computed(() =>
  canManage.value
  && form.name.trim().length > 0
  && form.stages.length > 0
  && form.stages.every((stage) => stage.name.trim().length > 0),
)

function messageOf(value: unknown, fallback: string): string {
  return value instanceof ApiError
    ? value.message
    : value instanceof Error
      ? value.message
      : fallback
}

async function load() {
  loading.value = true
  error.value = null
  try {
    templates.value = await listWorkflowTemplates()
  } catch (value) {
    error.value = messageOf(value, '无法读取研发流程模板')
  } finally {
    loading.value = false
  }
}

function blankStage(): StageForm {
  return {
    key: `stage-${++stageKeySequence}`,
    code: '',
    name: '',
    scope: 'VERSION',
    approverRole: '__none__',
    description: '',
  }
}

function openCreate() {
  editorMode.value = 'create'
  sourceTemplate.value = null
  editorError.value = null
  form.name = ''
  form.description = ''
  form.stages = [
    {
      ...blankStage(),
      name: '版本立项',
      approverRole: 'OWNER',
      description: '确认版本目标、范围和资源投入',
    },
  ]
  editorOpen.value = true
}

function openVersion(template: WorkflowTemplate) {
  editorMode.value = 'version'
  sourceTemplate.value = template
  editorError.value = null
  form.name = template.name
  form.description = template.description ?? ''
  form.stages = template.stages.map((stage) => ({
    key: `stage-${++stageKeySequence}`,
    code: stage.code,
    name: stage.name,
    scope: stage.scope,
    approverRole: stage.approverRole ?? '__none__',
    description: stage.description ?? '',
  }))
  if (form.stages.length === 0) form.stages.push(blankStage())
  editorOpen.value = true
}

function addStage() {
  form.stages.push(blankStage())
}

function removeStage(index: number) {
  if (form.stages.length <= 1) return
  form.stages.splice(index, 1)
}

function moveStage(index: number, offset: -1 | 1) {
  const target = index + offset
  if (target < 0 || target >= form.stages.length) return
  const [stage] = form.stages.splice(index, 1)
  if (stage) form.stages.splice(target, 0, stage)
}

function inputOfForm(): WorkflowTemplateSaveInput {
  return {
    name: form.name.trim(),
    description: form.description.trim() || null,
    stages: form.stages.map((stage) => ({
      code: stage.code || null,
      name: stage.name.trim(),
      scope: stage.scope,
      approverRole: stage.approverRole === '__none__' ? null : stage.approverRole,
      description: stage.description.trim() || null,
    })),
  }
}

async function save() {
  if (!canSave.value || saving.value || organizationStore.currentId === null) return
  saving.value = true
  editorError.value = null
  try {
    const saved = editorMode.value === 'create'
      ? await createWorkflowTemplate(organizationStore.currentId, inputOfForm())
      : await createWorkflowTemplateVersion(
          organizationStore.currentId,
          sourceTemplate.value!.id,
          inputOfForm(),
        )
    editorOpen.value = false
    await load()
    toast.success(
      `${saved.displayName} 已发布`,
      '新建项目现在可以选择这套研发流程',
    )
  } catch (value) {
    editorError.value = messageOf(value, '保存研发流程模板失败')
  } finally {
    saving.value = false
  }
}

async function archive(template: WorkflowTemplate) {
  if (
    archiveId.value !== null
    || organizationStore.currentId === null
    || !window.confirm(
      `停用「${template.displayName}」？历史项目和版本仍会保留该模板快照。`,
    )
  ) {
    return
  }
  archiveId.value = template.id
  try {
    await archiveWorkflowTemplate(organizationStore.currentId, template.id)
    await load()
    toast.success(`${template.displayName} 已停用`)
  } catch (value) {
    toast.failure(messageOf(value, '停用研发流程模板失败'))
  } finally {
    archiveId.value = null
  }
}

void load()
</script>

<template>
  <div class="mx-auto w-full max-w-[1080px] px-8 py-7 max-sm:px-5 max-sm:py-6">
    <header class="mb-6 flex items-start gap-4">
      <div class="min-w-0 flex-1">
        <div class="flex flex-wrap items-center gap-2">
          <h1 class="text-metric font-emphasis text-label">研发流程模板</h1>
          <Badge tone="neutral">{{ templates.length }} 个版本</Badge>
          <Badge v-if="publishedCount > 0" tone="success">{{ publishedCount }} 个可选</Badge>
        </div>
        <p class="mt-1 max-w-[720px] text-body text-label-secondary">
          模板全局共享。新建项目时绑定一个当前发布版本，产品版本创建后固定快照，不受后续模板调整影响。
        </p>
      </div>
      <div class="flex shrink-0 items-center gap-2">
        <Button size="sm" :disabled="loading" @click="load">
          <Icon :as="RefreshCw" size="inline" :class="{ 'animate-spin': loading }" />
          刷新
        </Button>
        <Button
          v-if="canManage"
          variant="primary"
          size="sm"
          @click="openCreate"
        >
          <Icon :as="Plus" size="inline" />
          新建模板
        </Button>
      </div>
    </header>

    <div
      v-if="!canManage"
      class="mb-4 rounded-panel border border-warning/30 bg-warning/8 px-4 py-3 text-caption text-label-secondary"
    >
      只有组织所有者或组织管理员可以维护全局研发流程模板。
    </div>

    <p v-if="error" class="mb-4 text-caption text-danger">{{ error }}</p>

    <div v-if="loading && templates.length === 0" class="space-y-3">
      <div
        v-for="index in 3"
        :key="index"
        class="h-40 animate-pulse rounded-panel bg-content shadow-panel"
      />
    </div>

    <EmptyState
      v-else-if="templates.length === 0"
      title="还没有研发流程模板"
      description="新建一套全局模板后，创建项目时即可绑定这套流程。"
    >
      <template v-if="canManage">
        <Button variant="primary" @click="openCreate">
          <Icon :as="Plus" size="inline" />
          新建模板
        </Button>
      </template>
    </EmptyState>

    <div v-else class="space-y-3">
      <article
        v-for="template in templates"
        :key="template.id"
        class="overflow-hidden rounded-panel bg-content shadow-panel"
      >
        <div class="flex items-start gap-3 px-4 py-4">
          <span class="grid size-10 shrink-0 place-items-center rounded-panel bg-accent/10 text-accent">
            <Icon :as="Workflow" size="regular" />
          </span>
          <div class="min-w-0 flex-1">
            <div class="flex flex-wrap items-center gap-2">
              <h2 class="text-body font-emphasis text-label">{{ template.displayName }}</h2>
              <Badge :tone="statusMeta[template.status].tone">
                {{ statusMeta[template.status].label }}
              </Badge>
              <span class="text-caption text-label-tertiary">
                {{ template.stages.length }} 个阶段
              </span>
            </div>
            <p
              v-if="template.description"
              class="mt-1 max-w-[780px] text-caption leading-5 text-label-secondary"
            >
              {{ template.description }}
            </p>
            <div class="mt-3 flex flex-wrap gap-1.5">
              <span
                v-for="stage in template.stages"
                :key="`${template.id}-${stage.code}`"
                class="inline-flex items-center gap-1 rounded-control border border-separator-soft bg-window px-2 py-1 text-[10px] text-label-secondary"
              >
                <span class="tnum text-label-tertiary">{{ stage.order }}</span>
                {{ stage.name }}
                <span v-if="stage.approverRole" class="text-label-tertiary">
                  · {{ projectMemberRoleLabels[stage.approverRole as keyof typeof projectMemberRoleLabels] ?? stage.approverRole }}
                </span>
              </span>
            </div>
          </div>
          <div v-if="canManage" class="flex shrink-0 flex-col items-end gap-2">
            <Button size="sm" @click="openVersion(template)">
              <Icon :as="Copy" size="inline" />
              复制新版本
            </Button>
            <Button
              v-if="template.status === 'PUBLISHED'"
              variant="danger"
              size="sm"
              :disabled="archiveId === template.id"
              @click="archive(template)"
            >
              {{ archiveId === template.id ? '停用中…' : '停用' }}
            </Button>
          </div>
        </div>
      </article>
    </div>

    <Dialog
      v-model:open="editorOpen"
      layout="form"
      size="wide"
      :title="editorTitle"
      description="发布后不可原地修改；需要调整时复制新版本，历史项目继续使用原快照。"
      :close-on-outside-click="false"
    >
      <div class="flex max-h-[min(68dvh,720px)] flex-col gap-4 overflow-y-auto pr-1">
        <DialogField label="模板名称" required label-width="w-24">
          <Input v-model="form.name" :maxlength="160" placeholder="例如：敏捷迭代流程" />
        </DialogField>
        <DialogField label="模板说明" label-width="w-24">
          <Textarea
            v-model="form.description"
            :rows="2"
            placeholder="说明这套流程适合什么类型的项目和发布节奏"
          />
        </DialogField>

        <div class="rounded-panel border border-separator-soft bg-window p-3">
          <div class="flex items-center gap-3">
            <div class="min-w-0 flex-1">
              <p class="text-body font-emphasis text-label">流程阶段</p>
              <p class="mt-0.5 text-caption text-label-secondary">
                按顺序执行；角色来自项目团队配置。
              </p>
            </div>
            <Button size="sm" @click="addStage">
              <Icon :as="Plus" size="inline" />
              添加阶段
            </Button>
          </div>

          <div class="mt-3 space-y-2">
            <div
              v-for="(stage, index) in form.stages"
              :key="stage.key"
              class="rounded-control border border-separator-soft bg-content p-2.5"
            >
              <div class="flex flex-wrap items-center gap-2">
                <span class="grid size-6 shrink-0 place-items-center rounded-full bg-accent/10 text-[10px] font-emphasis text-accent">
                  {{ index + 1 }}
                </span>
                <Input
                  v-model="stage.name"
                  :maxlength="80"
                  placeholder="阶段名称"
                  class="min-w-[160px] flex-1"
                />
                <Select
                  v-model="stage.scope"
                  :items="scopeOptions"
                  size="sm"
                  class="w-24"
                />
                <Select
                  v-model="stage.approverRole"
                  :items="roleOptions"
                  size="sm"
                  class="w-32"
                />
                <IconButton
                  label="上移"
                  size="sm"
                  :disabled="index === 0"
                  @click="moveStage(index, -1)"
                >
                  <Icon :as="ArrowUp" size="inline" />
                </IconButton>
                <IconButton
                  label="下移"
                  size="sm"
                  :disabled="index === form.stages.length - 1"
                  @click="moveStage(index, 1)"
                >
                  <Icon :as="ChevronDown" size="inline" />
                </IconButton>
                <IconButton
                  label="删除阶段"
                  size="sm"
                  :disabled="form.stages.length <= 1"
                  @click="removeStage(index)"
                >
                  <Icon :as="Trash2" size="inline" />
                </IconButton>
              </div>
              <Input
                v-model="stage.description"
                :maxlength="500"
                placeholder="阶段说明，例如：确认版本目标、范围和资源投入"
                class="mt-2"
              />
            </div>
          </div>
        </div>

        <p v-if="editorError" class="text-caption text-danger">{{ editorError }}</p>
      </div>

      <template #footer>
        <div class="flex items-center justify-end gap-2">
          <Button size="sm" :disabled="saving" @click="editorOpen = false">取消</Button>
          <Button
            variant="primary"
            size="sm"
            :disabled="!canSave || saving"
            @click="save"
          >
            {{ saving ? '发布中…' : editorMode === 'create' ? '发布模板' : '发布新版本' }}
          </Button>
        </div>
      </template>
    </Dialog>
  </div>
</template>
