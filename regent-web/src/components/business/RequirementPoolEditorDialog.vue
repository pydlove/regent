<script setup lang="ts">
import { FileText, Lightbulb, Sparkles } from '@lucide/vue'
import { computed, ref, watch } from 'vue'

import { listInitiatives } from '@/api/delivery'
import { ApiError } from '@/api/http'
import {
  createRequirement,
  generateRequirementsFromIdea,
  updateRequirement,
  type SaveRequirementInput,
} from '@/api/requirements'
import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import DialogField from '@/components/ui/dialog/DialogField.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import Input from '@/components/ui/input/Input.vue'
import Select from '@/components/ui/select/Select.vue'
import Textarea from '@/components/ui/input/Textarea.vue'
import { useToast } from '@/composables/useToast'
import { useTaskStore } from '@/stores/useTaskStore'
import type { Requirement } from '@/types/api'
import type { Initiative } from '@/types/delivery'

const props = defineProps<{
  requirement: Requirement | null
  defaultProjectId: number | null
  preferredInitiativeId?: number | null
  initialMode?: 'manual' | 'ai'
}>()
const emit = defineEmits<{
  saved: [requirement: Requirement]
  generated: [requirements: Requirement[]]
}>()
const open = defineModel<boolean>('open', { default: false })

const store = useTaskStore()
const toast = useToast()

const projectId = ref('')
const initiativeId = ref('')
const initiatives = ref<Initiative[]>([])
const title = ref('')
const rawText = ref('')
const acceptanceText = ref('')
const clarificationText = ref('')
const idea = ref('')
const source = ref<'MANUAL' | 'IMPORT' | 'AI'>('MANUAL')
const loadingInitiatives = ref(false)
const generating = ref(false)
const saving = ref(false)
const error = ref<string | null>(null)

const editing = computed(() => props.requirement !== null)
const isAiMode = computed(() => !editing.value && props.initialMode === 'ai')
const initiativeItems = computed(() =>
  initiatives.value
    .filter((initiative) => initiative.status === 'APPROVED' && initiative.versionId !== null)
    .map((initiative) => ({
      value: String(initiative.id),
      label: `${initiative.title} · ${initiative.targetVersion || '未定版本'}`,
    })),
)
const canSave = computed(() =>
  !saving.value
  && !generating.value
  && title.value.trim().length > 0
  && rawText.value.trim().length > 0
  && initiativeId.value !== '',
)
const canGenerate = computed(() =>
  !generating.value
  && projectId.value !== ''
  && initiativeId.value !== ''
  && idea.value.trim().length > 0,
)

function messageOf(value: unknown): string {
  if (value instanceof ApiError) return value.message
  return value instanceof Error ? value.message : '需求保存失败'
}

async function loadInitiatives() {
  const id = Number(projectId.value)
  initiatives.value = []
  initiativeId.value = ''
  if (!id) return
  loadingInitiatives.value = true
  try {
    initiatives.value = await listInitiatives(id)
    const eligible = initiatives.value.filter((initiative) =>
      initiative.status === 'APPROVED' && initiative.versionId !== null)
    const preferred = eligible.find(
      (initiative) => initiative.id === props.preferredInitiativeId,
    )
    initiativeId.value = String(preferred?.id ?? eligible[0]?.id ?? '')
  } catch (value) {
    error.value = messageOf(value)
  } finally {
    loadingInitiatives.value = false
  }
}

watch(open, async (opened) => {
  if (!opened) return
  error.value = null
  generating.value = false
  saving.value = false
  if (props.requirement) {
    const current = props.requirement
    projectId.value = String(current.projectId)
    title.value = current.title
    rawText.value = current.rawText
    acceptanceText.value = current.acceptanceText ?? ''
    clarificationText.value = current.clarificationText ?? ''
    source.value = current.source
    await loadInitiatives()
    initiativeId.value = String(current.initiativeId ?? initiativeId.value)
    return
  }
  projectId.value = String(props.defaultProjectId ?? store.projectId ?? store.projects[0]?.id ?? '')
  title.value = ''
  rawText.value = ''
  acceptanceText.value = ''
  clarificationText.value = ''
  idea.value = ''
  source.value = props.initialMode === 'ai' ? 'AI' : 'MANUAL'
  await loadInitiatives()
})

watch(projectId, () => {
  if (!open.value || editing.value) return
  void loadInitiatives()
})

async function generateRequirements() {
  if (!canGenerate.value) return
  generating.value = true
  error.value = null
  try {
    const generated = await generateRequirementsFromIdea(
      Number(projectId.value),
      Number(initiativeId.value),
      idea.value.trim(),
    )
    emit('generated', generated)
    open.value = false
    toast.success(
      `已生成 ${generated.length} 条需求`,
      '已自动纳入需求池，可继续补充版本和验收标准',
    )
  } catch (value) {
    error.value = messageOf(value)
  } finally {
    generating.value = false
  }
}

async function save() {
  if (!canSave.value) return
  saving.value = true
  error.value = null
  try {
    const input: SaveRequirementInput = {
      projectId: Number(projectId.value),
      initiativeId: Number(initiativeId.value),
      source: source.value,
      title: title.value.trim(),
      rawText: rawText.value.trim(),
      acceptanceText: acceptanceText.value.trim() || null,
      clarificationText: clarificationText.value.trim() || null,
    }
    const saved = props.requirement
      ? await updateRequirement(props.requirement.id, input)
      : await createRequirement(input)
    emit('saved', saved)
    open.value = false
    toast.success(editing.value ? '需求已更新' : '需求已进入需求池')
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
    :title="editing ? '编辑需求池需求' : initialMode === 'ai' ? 'AI 生成需求' : '录入需求池'"
    :description="isAiMode
      ? 'AI 会把一个想法拆成多条可独立评审的需求，并自动加入需求池。'
      : editing
      ? '修改后仍需纳入产品版本并提交需求评审。'
      : '先进入需求池，纳入产品版本后走需求评审；通过后成为正式需求。'"
    :icon="FileText"
  >
    <div class="flex flex-col gap-3">
      <DialogField label="所属版本立项" required :hint="loadingInitiatives ? '正在读取版本立项…' : '只展示已批准的版本立项'">
        <Select
          v-model="initiativeId"
          :items="initiativeItems"
          placeholder="选择已批准的版本立项"
          class="w-full"
          :disabled="loadingInitiatives"
        />
      </DialogField>

      <div
        v-if="isAiMode"
        class="rounded-panel border border-accent/20 bg-accent/5 p-3"
      >
        <div class="flex items-center gap-2">
          <Icon :as="Lightbulb" size="inline" class="text-accent" />
          <p class="text-caption font-emphasis text-label">只有一句想法？交给 AI 整理</p>
        </div>
        <Textarea
          v-model="idea"
          class="mt-2"
          :rows="6"
          placeholder="例如：希望不同组织的项目完全隔离，管理员可以给成员分配角色"
        />
        <p class="mt-2 text-caption text-label-tertiary">
          AI 会优先拆成 2-8 个可独立交付、独立验收的需求；生成后直接进入需求池。
        </p>
      </div>

      <template v-if="!isAiMode">
      <div class="grid gap-3 sm:grid-cols-2">
        <DialogField label="需求标题" required>
          <Input v-model="title" :maxlength="256" placeholder="例如：组织数据隔离" />
        </DialogField>
        <DialogField label="来源">
          <Input :model-value="source === 'AI' ? 'AI 生成' : source === 'IMPORT' ? '批量导入' : '手工录入'" disabled />
        </DialogField>
      </div>

      <DialogField label="需求描述" required hint="写业务目标、当前问题、期望结果和边界">
        <Textarea v-model="rawText" :rows="6" placeholder="描述需求背景和期望结果" />
      </DialogField>
      <DialogField label="验收标准" hint="建议每行写一条可验证结果">
        <Textarea v-model="acceptanceText" :rows="3" placeholder="哪些路径必须通过" />
      </DialogField>
      <DialogField label="补充澄清" hint="记录已确认的假设、依赖和边界">
        <Textarea v-model="clarificationText" :rows="3" placeholder="例如：本期不包含历史数据迁移" />
      </DialogField>
      </template>

      <p v-if="error" class="text-caption text-danger">{{ error }}</p>
    </div>
    <template #footer>
      <Button :disabled="saving || generating" @click="open = false">取消</Button>
      <Button
        v-if="isAiMode"
        variant="primary"
        :disabled="!canGenerate"
        @click="generateRequirements"
      >
        <Icon :as="Sparkles" size="inline" />
        {{ generating ? 'AI 拆分生成中…' : '生成并纳入需求池' }}
      </Button>
      <Button v-else variant="primary" :disabled="!canSave" @click="save">
        {{ saving ? '保存中…' : editing ? '保存需求' : '保存到需求池' }}
      </Button>
    </template>
  </Dialog>
</template>
