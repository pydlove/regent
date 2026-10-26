<script setup lang="ts">
import { CircleAlert, FileText, Sparkles } from '@lucide/vue'
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { ApiError } from '@/api/http'
import {
  analyzeRequirement,
  confirmRequirement,
  createRequirement,
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
import { useAuthStore } from '@/stores/useAuthStore'
import type { Requirement, RequirementTaskDraft } from '@/types/api'
import RequirementTaskEditor from '@/components/business/RequirementTaskEditor.vue'

const props = defineProps<{
  requirement: Requirement | null
  defaultProjectId: number | null
}>()
const emit = defineEmits<{
  saved: [requirement: Requirement]
  changed: []
}>()
const open = defineModel<boolean>('open', { default: false })
const store = useTaskStore()
const auth = useAuthStore()
const toast = useToast()
const router = useRouter()

const current = ref<Requirement | null>(null)
const projectId = ref('')
const title = ref('')
const rawText = ref('')
const acceptanceText = ref('')
const clarificationText = ref('')
const questions = ref<string[]>([])
const assumptions = ref<string[]>([])
const drafts = ref<RequirementTaskDraft[]>([])
const loading = ref(false)
const analyzing = ref(false)
const confirming = ref(false)
const error = ref<string | null>(null)
let draftTimer: number | undefined

const localDraftKey = computed(() =>
  auth.user?.id ? `regent.requirement-draft.${auth.user.id}` : '',
)

const persistLocalDraft = () => {
  if (!open.value || current.value || !localDraftKey.value) return
  window.clearTimeout(draftTimer)
  draftTimer = window.setTimeout(() => {
    try {
      localStorage.setItem(localDraftKey.value, JSON.stringify({
        projectId: projectId.value,
        title: title.value,
        rawText: rawText.value,
        acceptanceText: acceptanceText.value,
        clarificationText: clarificationText.value,
      }))
    } catch {
      // 本地草稿只是刷新恢复的兜底，存储失败不阻断录入。
    }
  }, 400)
}

const clearLocalDraft = () => {
  window.clearTimeout(draftTimer)
  try {
    if (localDraftKey.value) localStorage.removeItem(localDraftKey.value)
  } catch {
    // 本地存储清理失败不影响后端已保存的需求草稿。
  }
}

const projectItems = computed(() => store.projects.map((project) => ({
  value: String(project.id),
  label: project.enabled ? project.name : `${project.name}（已暂停）`,
})))
const readOnly = computed(() =>
  current.value !== null
  && ['PLANNED', 'RUNNING', 'PARTIAL', 'DONE', 'CANCELLED'].includes(current.value.status),
)
const canAnalyze = computed(() =>
  !readOnly.value
  && projectId.value !== ''
  && title.value.trim().length > 0
  && rawText.value.trim().length > 0
  && !analyzing.value
  && !loading.value,
)
const canConfirm = computed(() =>
  !readOnly.value
  && drafts.value.length > 0
  && drafts.value.every((task) =>
    task.title.trim() && task.objective.trim() && task.acceptanceCriteria.length > 0,
  )
  && !confirming.value,
)

function messageOf(value: unknown): string {
  if (value instanceof ApiError) return value.message
  return value instanceof Error ? value.message : '需求分析失败'
}

const apply = (requirement: Requirement) => {
  current.value = requirement
  projectId.value = String(requirement.projectId)
  title.value = requirement.title
  rawText.value = requirement.rawText
  acceptanceText.value = requirement.acceptanceText ?? ''
  clarificationText.value = requirement.clarificationText ?? ''
  questions.value = [...requirement.clarificationQuestions]
  assumptions.value = [...requirement.assumptions]
  drafts.value = requirement.taskDrafts.map((task) => ({ ...task }))
}

watch(open, async (opened) => {
  if (!opened) return
  error.value = null
  loading.value = false
  analyzing.value = false
  confirming.value = false
  if (props.requirement) {
    apply(props.requirement)
    return
  }
  current.value = null
  projectId.value = props.defaultProjectId === null
    ? String(store.projects[0]?.id ?? '')
    : String(props.defaultProjectId)
  title.value = ''
  rawText.value = ''
  acceptanceText.value = ''
  clarificationText.value = ''
  questions.value = []
  assumptions.value = []
  drafts.value = []
  try {
    const raw = localDraftKey.value ? localStorage.getItem(localDraftKey.value) : null
    if (raw) {
      const draft = JSON.parse(raw) as Record<string, unknown>
      projectId.value = typeof draft.projectId === 'string'
        ? draft.projectId
        : projectId.value
      title.value = typeof draft.title === 'string' ? draft.title : ''
      rawText.value = typeof draft.rawText === 'string' ? draft.rawText : ''
      acceptanceText.value = typeof draft.acceptanceText === 'string' ? draft.acceptanceText : ''
      clarificationText.value = typeof draft.clarificationText === 'string'
        ? draft.clarificationText
        : ''
    }
  } catch {
    // 损坏的本地草稿直接忽略，不阻止重新录入。
  }
})

watch(
  [projectId, title, rawText, acceptanceText, clarificationText],
  persistLocalDraft,
)

const saveInput = (): SaveRequirementInput => ({
  projectId: Number(projectId.value),
  initiativeId: current.value?.initiativeId ?? null,
  source: current.value?.source ?? 'MANUAL',
  title: title.value.trim(),
  rawText: rawText.value.trim(),
  acceptanceText: acceptanceText.value.trim() || null,
  clarificationText: clarificationText.value.trim() || null,
})

const analyze = async () => {
  if (!canAnalyze.value) return
  analyzing.value = true
  error.value = null
  try {
    const saved = current.value
      ? await updateRequirement(current.value.id, saveInput())
      : await createRequirement(saveInput())
    current.value = saved
    clearLocalDraft()
    emit('changed')
    const analyzed = await analyzeRequirement(saved.id)
    apply(analyzed)
    emit('changed')
    if (analyzed.taskDrafts.length > 0) {
      toast.success('已生成任务拆解', `共 ${analyzed.taskDrafts.length} 个任务，请确认后创建`)
    } else {
      toast.success('需要补充信息', '回答关键问题后重新分析')
    }
  } catch (e) {
    error.value = messageOf(e)
  } finally {
    analyzing.value = false
  }
}

const confirm = async () => {
  if (!canConfirm.value || !current.value) return
  confirming.value = true
  error.value = null
  try {
    const saved = await confirmRequirement(current.value.id, drafts.value)
    apply(saved)
    emit('saved', saved)
    emit('changed')
    clearLocalDraft()
    open.value = false
    toast.success('需求已确认', `已创建 ${saved.tasks.length} 个关联任务`)
  } catch (e) {
    error.value = messageOf(e)
  } finally {
    confirming.value = false
  }
}
</script>

<template>
  <Dialog
    v-model:open="open"
    layout="form"
    size="large"
    :title="current ? current.title : '录入需求'"
    :description="readOnly ? '查看需求及其关联任务' : '补充目标、范围与验收标准，由 AI 检查信息是否足够并生成任务草稿。'"
    :icon="FileText"
    :close-on-outside-click="false"
  >
    <div class="flex flex-col gap-3">
      <p v-if="loading" class="text-caption text-label-tertiary">正在读取需求…</p>

      <template v-if="readOnly && current">
        <div class="rounded-panel border border-separator-soft bg-content p-3">
          <p class="whitespace-pre-wrap text-caption text-label">{{ current.rawText }}</p>
          <p v-if="current.acceptanceText" class="mt-2 border-t border-separator-soft pt-2 text-caption text-label-secondary">
            验收：{{ current.acceptanceText }}
          </p>
        </div>
        <div class="space-y-1.5">
          <p class="text-caption font-emphasis text-label-secondary">关联任务 {{ current.tasks.length }}</p>
          <div
            v-for="task in current.tasks"
            :key="task.id"
            class="flex items-center gap-2 rounded-control border border-separator-soft px-2.5 py-2"
            role="button"
            tabindex="0"
            @click="router.push({ path: '/tasks', query: { taskId: String(task.id) } })"
            @keydown.enter="router.push({ path: '/tasks', query: { taskId: String(task.id) } })"
          >
            <span class="tnum text-caption text-label-tertiary">#{{ task.id }}</span>
            <span class="min-w-0 flex-1 truncate text-caption text-label">{{ task.title }}</span>
            <span v-if="task.dependencies.length > 0" class="text-caption text-label-tertiary">
              依赖 {{ task.dependencies.map((id) => `#${id}`).join('、') }}
            </span>
            <span class="text-caption text-label-secondary">{{ task.status }}</span>
          </div>
        </div>
      </template>

      <template v-else>
        <div class="grid gap-3 md:grid-cols-2">
          <DialogField label="所属项目" required>
            <Select
              v-model="projectId"
              :items="projectItems"
              :disabled="current !== null"
              placeholder="选择项目"
              class="w-full"
            />
          </DialogField>
          <DialogField label="需求标题" required>
            <Input v-model="title" :maxlength="256" placeholder="例如：部署与环境支持多项目" />
          </DialogField>
        </div>

        <DialogField label="需求描述" required hint="写业务目标、当前问题和期望结果，不必先拆成技术任务">
          <Textarea v-model="rawText" :rows="6" placeholder="描述你想完成的事情和必要背景" />
        </DialogField>

        <DialogField label="验收标准" hint="可以每行写一条；AI 会补充到任务草稿中">
          <Textarea v-model="acceptanceText" :rows="3" placeholder="用户能看到什么、哪些路径必须通过" />
        </DialogField>

        <DialogField
          v-if="questions.length > 0"
          label="补充澄清"
          hint="逐条回答问题，或明确说明采用哪个假设"
        >
          <div class="mb-2 space-y-1.5 rounded-panel bg-warning/8 p-2.5">
            <p
              v-for="(question, index) in questions"
              :key="`${index}-${question}`"
              class="flex gap-2 text-caption text-warning"
            >
              <Icon :as="CircleAlert" size="inline" class="mt-0.5 shrink-0" />
              <span>{{ index + 1 }}. {{ question }}</span>
            </p>
          </div>
          <Textarea v-model="clarificationText" :rows="4" placeholder="1. ...&#10;2. ..." />
        </DialogField>

        <div v-if="assumptions.length > 0" class="rounded-panel bg-accent/5 p-2.5">
          <p class="text-caption font-emphasis text-label-secondary">AI 采用的假设</p>
          <ul class="mt-1 list-inside list-disc text-caption text-label-secondary">
            <li v-for="assumption in assumptions" :key="assumption">{{ assumption }}</li>
          </ul>
        </div>

        <DialogField v-if="drafts.length > 0" label="任务草稿" required>
          <RequirementTaskEditor v-model:tasks="drafts" />
        </DialogField>

        <p v-if="error" class="flex items-start gap-1.5 text-caption text-danger">
          <Icon :as="CircleAlert" size="inline" class="mt-0.5 shrink-0" />
          {{ error }}
        </p>
      </template>
    </div>

    <template #footer>
      <Button @click="open = false">{{ readOnly ? '关闭' : '稍后继续' }}</Button>
      <Button
        v-if="!readOnly"
        :disabled="!canAnalyze"
        @click="analyze"
      >
        <Icon :as="Sparkles" size="inline" />
        {{ analyzing ? 'AI 分析中…' : questions.length > 0 ? '补充并重新分析' : '检查并拆解' }}
      </Button>
      <Button
        v-if="!readOnly && drafts.length > 0"
        variant="primary"
        :disabled="!canConfirm"
        @click="confirm"
      >
        {{ confirming ? '创建中…' : `确认创建 ${drafts.length} 个任务` }}
      </Button>
    </template>
  </Dialog>
</template>
