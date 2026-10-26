<script setup lang="ts">
import {
  ChevronLeft,
  Download,
  Eye,
  FileText,
  GitBranch,
  Paperclip,
  Pencil,
  Play,
  Plus,
  RefreshCw,
} from '@lucide/vue'
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import {
  actRelease,
  actRequirementDelivery,
  approveInitiative,
  createInitiative,
  createRelease,
  listInitiatives,
  listReleases,
  listRequirementDeliveries,
  rejectInitiative,
  submitInitiative,
  updateInitiative,
} from '@/api/delivery'
import { ApiError } from '@/api/http'
import { listProjectMembers } from '@/api/projectMembers'
import { listProductVersions } from '@/api/productVersions'
import { listWorkflowTemplates, type WorkflowTemplate } from '@/api/workflowTemplates'
import { listRequirements } from '@/api/requirements'
import {
  decideRequirementReview,
  listRequirementReviewRecords,
  submitRequirementReview,
  type RequirementReviewRecord,
} from '@/api/requirementReviews'
import {
  listInitiativeAttachments,
  uploadInitiativeAttachment,
} from '@/api/initiativeAttachments'
import DeliveryPipeline from '@/components/business/DeliveryPipeline.vue'
import Badge from '@/components/ui/badge/Badge.vue'
import Button from '@/components/ui/button/Button.vue'
import Checkbox from '@/components/ui/checkbox/Checkbox.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import DialogField from '@/components/ui/dialog/DialogField.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import IconButton from '@/components/ui/icon-button/IconButton.vue'
import Input from '@/components/ui/input/Input.vue'
import Textarea from '@/components/ui/input/Textarea.vue'
import Select from '@/components/ui/select/Select.vue'
import { useToast } from '@/composables/useToast'
import { useAuthStore } from '@/stores/useAuthStore'
import { useTaskStore } from '@/stores/useTaskStore'
import type { Requirement } from '@/types/api'
import type {
  DeliveryStage,
  Initiative,
  InitiativeAttachment,
  InitiativeStatus,
  ProductVersion,
  Release,
  ReleaseStage,
  RequirementDelivery,
  RequirementDeliveryAction,
  ReleaseAction,
} from '@/types/delivery'
import type { ProjectMember, ProjectMemberRole } from '@/types/projectMember'
import { formatBytes, formatDateTime, formatRelative } from '@/utils/format'
import {
  canActWithRoles,
  deliveryNextStep,
  initiativeNextStep,
  releaseNextStep,
} from '@/utils/deliveryWorkflow'
import { projectMemberRoleLabels } from '@/utils/projectMemberRoles'
import {
  formatRequirementNo,
  requirementDisplayStatus,
  type RequirementDisplayStatus,
} from '@/utils/requirementPool'

type Mode = 'flows' | 'initiatives' | 'releases'

const props = defineProps<{
  versionId: number
}>()
const emit = defineEmits<{ changed: [] }>()

const store = useTaskStore()
const auth = useAuthStore()
const router = useRouter()
const toast = useToast()

const mode = ref<Mode>('flows')
const versions = ref<ProductVersion[]>([])
const workflowTemplates = ref<WorkflowTemplate[]>([])
const initiatives = ref<Initiative[]>([])
const deliveries = ref<RequirementDelivery[]>([])
const releases = ref<Release[]>([])
const requirements = ref<Requirement[]>([])
const reviewRecords = ref<RequirementReviewRecord[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

const selectedDeliveryId = ref<number | null>(null)
const selectedVersionId = ref<number | null>(props.versionId)
const selectedInitiativeId = ref<number | null>(null)
const selectedReleaseId = ref<number | null>(null)
const projectMembers = ref<Record<number, ProjectMember[]>>({})
const requirementsSection = ref<HTMLElement | null>(null)
const reviewRecordOpen = ref(false)
const reviewRecordSaving = ref(false)
const reviewRecordError = ref<string | null>(null)
const reviewDate = ref('')
const requirementReviewMinutes = ref('')
const reviewFiles = ref<File[]>([])
const requirementReviewFileInput = ref<HTMLInputElement | null>(null)
const reviewDecisionOpen = ref(false)
const reviewDecisionRecord = ref<RequirementReviewRecord | null>(null)
const reviewDecisionComment = ref('')
const reviewDecisionSaving = ref(false)
const reviewDecisionError = ref<string | null>(null)
const reviewDecisionQuickPhrases = [
  { text: '通过', tone: 'approve' },
  { text: '材料完整，同意通过', tone: 'approve' },
  { text: '请补充评审材料', tone: 'reject' },
  { text: '需求范围不清晰，请补充', tone: 'reject' },
  { text: '验收标准不完整，请完善', tone: 'reject' },
] as const
const canSubmitReviewRecord = computed(() =>
  Boolean(reviewDate.value)
  && (
    requirementReviewMinutes.value.trim().length > 0
    || reviewFiles.value.length > 0
  )
  && !reviewRecordSaving.value,
)

function localDateInputValue() {
  const now = new Date()
  return new Date(now.getTime() - now.getTimezoneOffset() * 60_000)
    .toISOString()
    .slice(0, 10)
}

const deliverySteps = [
  { value: 'REQUIREMENT_REVIEW', label: '需求评审' },
  { value: 'DESIGN', label: '详细设计' },
  { value: 'DESIGN_REVIEW', label: '设计评审' },
  { value: 'DEVELOPMENT', label: '研发' },
  { value: 'SMOKE', label: '冒烟测试' },
  { value: 'AUTOMATION', label: '自动化回归' },
  { value: 'READY_FOR_TRANSFER', label: '待转测' },
  { value: 'IN_RELEASE', label: '发布中' },
  { value: 'RELEASED', label: '已发布' },
  { value: 'CLOSED', label: '已关闭' },
]

const releaseSteps = [
  { value: 'TRANSFER', label: '转测' },
  { value: 'INTEGRATION', label: '集成测试' },
  { value: 'REGRESSION', label: '版本回归' },
  { value: 'RELEASE_REVIEW', label: '发版评审' },
  { value: 'PRODUCTION', label: '生产发布' },
  { value: 'OBSERVING', label: '发布观察' },
  { value: 'CLOSED', label: '已关闭' },
]

const deliveryStageLabels: Record<DeliveryStage, string> = {
  REQUIREMENT_REVIEW: '需求评审',
  DESIGN: '详细设计',
  DESIGN_REVIEW: '设计评审',
  DEVELOPMENT: '研发中',
  SMOKE: '冒烟测试',
  AUTOMATION: '自动化回归',
  READY_FOR_TRANSFER: '待转测',
  IN_RELEASE: '发布中',
  RELEASED: '已发布',
  CLOSED: '已关闭',
}

const releaseStageLabels: Record<ReleaseStage, string> = {
  TRANSFER: '待转测',
  INTEGRATION: '集成测试',
  REGRESSION: '版本回归',
  RELEASE_REVIEW: '发版评审',
  PRODUCTION: '生产发布',
  OBSERVING: '发布观察',
  CLOSED: '已关闭',
}

const initiativeStatusLabels: Record<InitiativeStatus, string> = {
  DRAFT: '草稿',
  SUBMITTED: '待审批',
  APPROVED: '已批准',
  REJECTED: '已驳回',
  CANCELLED: '已取消',
}

const requirementPoolLabels: Record<RequirementDisplayStatus, string> = {
  PENDING_VERSION: '待纳入版本',
  PENDING_REVIEW: '待需求评审',
  IN_REVIEW: '评审中',
  FORMAL: '正式需求',
  REJECTED: '已驳回',
}

const requirementPoolTone = (
  status: RequirementDisplayStatus,
): 'neutral' | 'accent' | 'success' | 'warning' | 'danger' => {
  if (status === 'FORMAL') return 'success'
  if (status === 'IN_REVIEW') return 'accent'
  if (status === 'REJECTED') return 'danger'
  if (status === 'PENDING_VERSION') return 'warning'
  return 'accent'
}

const requirementReviewStatusMeta: Record<
  RequirementReviewRecord['status'],
  { label: string; tone: 'accent' | 'success' | 'danger' }
> = {
  SUBMITTED: { label: '待审核', tone: 'accent' },
  APPROVED: { label: '已通过', tone: 'success' },
  REJECTED: { label: '已驳回', tone: 'danger' },
}

const requirementNoLabel = (id: number) => {
  const requirement = requirements.value.find((item) => item.id === id)
  return requirement ? formatRequirementNo(requirement.requirementNo) : String(id)
}

const selectedVersion = computed(
  () => versions.value.find((version) => version.id === selectedVersionId.value) ?? null,
)
const selectedWorkflowTemplate = computed(() =>
  workflowTemplates.value.find(
    (template) => template.id === selectedVersion.value?.workflowTemplateId,
  ) ?? null,
)
const hasSelectedProject = computed(() => store.projectId !== null)
const versionInitiatives = computed(() =>
  selectedVersion.value
    ? initiatives.value.filter(
        (initiative) => initiative.versionId === selectedVersion.value?.id,
      )
    : [],
)
const versionInitiative = computed(() => versionInitiatives.value[0] ?? null)
const versionRequirements = computed(() =>
  selectedVersion.value
    ? requirements.value.filter(
        (requirement) => requirement.versionId === selectedVersion.value?.id,
      )
    : [],
)
const latestReviewRecord = computed(() => reviewRecords.value[0] ?? null)
const pendingReviewRequirements = computed(() =>
  versionRequirements.value.filter((requirement) => {
    const status = requirementDisplayStatus(requirement)
    return status === 'PENDING_REVIEW'
      || status === 'REJECTED'
  }),
)
const reviewSubmissionPending = computed(() =>
  latestReviewRecord.value?.status === 'SUBMITTED',
)
const canSubmitRequirementReview = computed(() =>
  selectedVersion.value
    ? canRole(selectedVersion.value.projectId, 'PRODUCT')
    : false,
)
const canDecideRequirementReview = computed(() =>
  selectedVersion.value
    ? canRole(selectedVersion.value.projectId, 'OWNER')
    : false,
)
const versionRequirementIds = computed(
  () => new Set(versionRequirements.value.map((requirement) => requirement.id)),
)
const versionDeliveries = computed(() => {
  if (!selectedVersion.value) return []
  return deliveries.value.filter((delivery) =>
    versionRequirementIds.value.has(delivery.requirementId),
  )
})
const versionReleases = computed(() => {
  if (!selectedVersion.value) return []
  return releases.value.filter(
    (release) => release.version === selectedVersion.value?.versionNo,
  )
})

const roleLabels: Record<string, string> = projectMemberRoleLabels
const roleLabel = (role: string | null | undefined) => roleLabels[role ?? ''] ?? role ?? '未指定'

const deliveryTone = (stage: DeliveryStage) => {
  if (stage === 'READY_FOR_TRANSFER' || stage === 'IN_RELEASE') return 'accent'
  if (stage === 'RELEASED' || stage === 'CLOSED') return 'success'
  if (stage === 'DESIGN_REVIEW') return 'warning'
  return 'neutral'
}

const releaseTone = (stage: ReleaseStage) => {
  if (stage === 'RELEASE_REVIEW') return 'warning'
  if (stage === 'CLOSED') return 'success'
  return stage === 'TRANSFER' ? 'accent' : 'neutral'
}

const initiativeTone = (status: InitiativeStatus) => {
  if (status === 'APPROVED') return 'success'
  if (status === 'SUBMITTED') return 'accent'
  if (status === 'REJECTED') return 'danger'
  return 'neutral'
}


const selectedDelivery = computed(
  () => deliveries.value.find((item) => item.id === selectedDeliveryId.value) ?? null,
)
const selectedDeliveryRequirement = computed(
  () => requirements.value.find(
    (item) => item.id === selectedDelivery.value?.requirementId,
  ) ?? null,
)
const selectedInitiative = computed(
  () => initiatives.value.find((item) => item.id === selectedInitiativeId.value) ?? null,
)
const selectedInitiativeRequirements = computed(() => {
  if (!selectedInitiative.value) return []
  return requirements.value.filter(
    (item) => item.initiativeId === selectedInitiative.value?.id,
  )
})
const selectedRelease = computed(
  () => releases.value.find((item) => item.id === selectedReleaseId.value) ?? null,
)
const currentSelection = computed(() => {
  if (mode.value === 'flows') return selectedDelivery.value
  if (mode.value === 'initiatives') return selectedInitiative.value
  return selectedRelease.value
})

const selectedDeliveryNext = computed(() =>
  selectedDelivery.value
    ? deliveryNextStep(selectedDelivery.value.stage, selectedDelivery.value.status)
    : null,
)
const requirementReviewRejected = computed(() => {
  const gates = selectedDelivery.value?.gates.filter(
    (gate) => gate.type === 'REQUIREMENT_REVIEW',
  ) ?? []
  return gates.at(-1)?.decision === 'REJECTED'
})
const selectedInitiativeNext = computed(() =>
  selectedInitiative.value ? initiativeNextStep(selectedInitiative.value.status) : null,
)
const selectedReleaseNext = computed(() =>
  selectedRelease.value
    ? releaseNextStep(selectedRelease.value.stage, selectedRelease.value.status)
    : null,
)

function rolesForProject(projectId: number | null | undefined): ProjectMemberRole[] {
  if (!projectId) return []
  const members = projectMembers.value[projectId] ?? []
  return members.find((member) => member.userId === auth.user?.id)?.roles ?? []
}

function memberCountForProject(projectId: number | null | undefined): number {
  if (!projectId) return 0
  return projectMembers.value[projectId]?.length ?? 0
}

function canRole(projectId: number | null | undefined, role: ProjectMemberRole): boolean {
  return canActWithRoles(role, rolesForProject(projectId), memberCountForProject(projectId))
}

const canInitiateVersionInitiative = computed(() =>
  selectedVersion.value
    ? canRole(selectedVersion.value.projectId, 'PRODUCT')
    : false,
)
const canManageVersionInitiative = computed(() =>
  selectedInitiative.value
    ? canRole(selectedInitiative.value.projectId, 'PRODUCT')
    : false,
)

function roleSummaryForProject(projectId: number | null | undefined): string {
  const labels = rolesForProject(projectId).map(roleLabel)
  return labels.length > 0 ? labels.join('、') : '未分配项目角色'
}

function memberNameForProject(projectId: number | null | undefined, userId: number): string {
  if (!projectId) return `用户 #${userId}`
  const member = projectMembers.value[projectId]?.find((item) => item.userId === userId)
  return member?.displayName ?? `用户 #${userId}`
}

const currentProjectId = computed(
  () => store.projectId ?? currentSelection.value?.projectId ?? null,
)
const currentProjectRoles = computed(() => rolesForProject(currentProjectId.value))
const currentProjectRoleLabels = computed(() => currentProjectRoles.value.map(roleLabel))
const canCreateRelease = computed(() => {
  if (store.projectId) return canRole(store.projectId, 'RELEASE_MANAGER')
  return Object.keys(projectMembers.value).some((projectId) =>
    canRole(Number(projectId), 'RELEASE_MANAGER'),
  )
})
type PendingWorkflowItem = {
  key: string
  mode: Mode
  id: number
  title: string
  step: string
  role: ProjectMemberRole
}

const pendingWorkflowItems = computed<PendingWorkflowItem[]>(() => {
  const items: PendingWorkflowItem[] = []
  for (const delivery of versionDeliveries.value) {
    if (delivery.status !== 'ACTIVE') continue
    if (![
      'REQUIREMENT_REVIEW',
      'DESIGN',
      'DESIGN_REVIEW',
      'DEVELOPMENT',
      'SMOKE',
      'AUTOMATION',
      'READY_FOR_TRANSFER',
    ].includes(delivery.stage)) continue
    const step = deliveryNextStep(delivery.stage, delivery.status)
    if (step?.role && canRole(delivery.projectId, step.role)) {
      items.push({
        key: `delivery-${delivery.id}`,
        mode: 'flows',
        id: delivery.id,
        title: delivery.requirementTitle,
        step: step.label,
        role: step.role,
      })
    }
  }
  for (const initiative of versionInitiatives.value) {
    if (initiative.status === 'APPROVED' || initiative.status === 'CANCELLED') continue
    const step = initiativeNextStep(initiative.status)
    if (step?.role && canRole(initiative.projectId, step.role)) {
      items.push({
        key: `initiative-${initiative.id}`,
        mode: 'initiatives',
        id: initiative.id,
        title: initiative.title,
        step: step.label,
        role: step.role,
      })
    }
  }
  for (const release of versionReleases.value) {
    if (release.status !== 'ACTIVE') continue
    const step = releaseNextStep(release.stage, release.status)
    if (step?.role && canRole(release.projectId, step.role)) {
      items.push({
        key: `release-${release.id}`,
        mode: 'releases',
        id: release.id,
        title: `${release.version} · 第 ${release.attemptNo} 轮`,
        step: step.label,
        role: step.role,
      })
    }
  }
  return items
})

const versionSetup = computed(() => {
  if (!hasSelectedProject.value) {
    return {
      action: 'project',
      label: '先选择项目',
      title: '研发流程需要先确定项目',
      description: '请在页面顶部把“本组织全部项目”切换为一个具体项目，再选择产品版本。',
    } as const
  }
  if (!selectedVersion.value) {
    return {
      action: 'version',
      label: '创建产品版本',
      title: '先创建产品版本',
      description: '研发流程以版本为入口。创建版本后，再为该版本单独发起立项。',
    } as const
  }
  if (selectedVersion.value.status === 'CLOSED') {
    return {
      action: 'none',
      label: '版本已关闭',
      title: `${selectedVersion.value.versionNo} 已关闭`,
      description: '历史需求、交付和发布记录仍可查看，但不能再立项或纳入新需求。',
    } as const
  }
  if (!versionInitiative.value) {
    if (selectedVersion.value.status !== 'PLANNING') {
      return {
        action: 'none',
        label: '缺少版本立项',
        title: '版本已确认，但没有版本立项',
        description: '该版本不能再发起立项，请新建版本并按版本流程重新开始。',
      } as const
    }
    return {
      action: 'initiative',
      label: '发起版本立项',
      title: `为 ${selectedVersion.value.versionNo} 发起版本立项`,
      description: '版本立项通过后，才能向该版本纳入需求。',
    } as const
  }
  if (versionInitiative.value.status === 'DRAFT'
      || versionInitiative.value.status === 'REJECTED') {
    return {
      action: 'initiative',
      label: '完成版本立项',
      title: '版本立项还未提交',
      description: '补全目标、范围和成功指标，提交项目负责人审批。',
    } as const
  }
  if (versionInitiative.value.status === 'SUBMITTED') {
    return {
      action: 'review',
      label: canRole(versionInitiative.value.projectId, 'OWNER')
        ? '审批版本立项'
        : '等待版本立项审批',
      title: '版本立项待负责人审批',
      description: '审批通过后，需求池才能向当前版本纳入需求。',
    } as const
  }
  if (versionRequirements.value.length === 0) {
    if (selectedVersion.value.status !== 'PLANNING') {
      return {
        action: 'none',
        label: '需求范围已锁定',
        title: '当前版本没有纳入需求',
        description: '版本确认后不能继续纳入新需求，请新建后续版本。',
      } as const
    }
    return {
      action: 'pool',
      label: '录入并纳入需求',
      title: '版本已批准，还没有需求',
      description: '先在需求池录入需求，再把它纳入当前版本并组织线下需求评审。',
    } as const
  }
  if (versionDeliveries.value.length === 0) {
    if (reviewSubmissionPending.value) {
      return {
        action: 'requirements',
        label: canDecideRequirementReview.value
          ? '审核需求评审'
          : '等待需求评审审核',
        title: '需求评审材料待项目负责人审核',
        description: '产品负责人已提交线下评审材料，请项目负责人查看纪要和附件后给出审核意见。',
      } as const
    }
    if (pendingReviewRequirements.value.length > 0) {
      return {
        action: 'requirements',
        label: canSubmitRequirementReview.value
          ? '提交需求评审'
          : '等待提交需求评审',
        title: `当前版本有 ${pendingReviewRequirements.value.length} 条需求待提交评审`,
        description: '产品负责人整理线下评审纪要和附件后提交，项目负责人再审核并通过或驳回。',
      } as const
    }
    return {
      action: 'requirements',
      label: '查看需求评审',
      title: `当前版本已有 ${versionRequirements.value.length} 条需求`,
      description: '需求评审通过后，系统会批量创建详细设计交付。',
    } as const
  }
  return {
    action: 'flows',
    label: '查看研发交付',
    title: '版本需求已进入研发',
    description: '按详细设计、设计评审、研发、测试和发布依次推进。',
  } as const
})

const workflowStartLabel = computed(() => versionSetup.value.label)

const workflowGroupDefinitions = [
  { label: '版本立项', description: '目标、范围与资源' },
  { label: '需求与评审', description: '需求池、版本范围、评审' },
  { label: '设计与研发', description: '详细设计、设计评审、研发' },
  { label: '测试与发布', description: '冒烟、自动化、转测、发版' },
] as const

const workflowGroups = computed(() => {
  let current = 0
  if (versionInitiative.value?.status === 'APPROVED') current = 1
  if (versionDeliveries.value.length > 0) {
    const rank = (stage: DeliveryStage) => {
      if (stage === 'REQUIREMENT_REVIEW') return 1
      if (stage === 'DESIGN' || stage === 'DESIGN_REVIEW' || stage === 'DEVELOPMENT') return 2
      return 3
    }
    current = Math.max(...versionDeliveries.value.map((delivery) => rank(delivery.stage)))
  }
  if (
    versionDeliveries.value.length > 0
    && versionDeliveries.value.every((delivery) => delivery.stage === 'CLOSED')
  ) {
    current = workflowGroupDefinitions.length
  }
  return workflowGroupDefinitions.map((group, index) => ({
    ...group,
    state: index < current ? 'done' : index === current ? 'active' : 'pending',
  }))
})

const activeDelivery = computed(() => selectedDelivery.value)
const designSummary = ref('')
const designContent = ref('')
const actionComment = ref('')

watch(selectedDelivery, (delivery) => {
  designSummary.value = delivery?.designSummary ?? ''
  designContent.value = delivery?.designContent ?? ''
  actionComment.value = ''
})

function messageOf(value: unknown): string {
  if (value instanceof ApiError) return value.message
  return value instanceof Error ? value.message : '无法连接后端，请稍后重试'
}

async function load(quiet = false) {
  if (!quiet) {
    loading.value = true
    error.value = null
  }
  try {
    const [
      nextInitiatives,
      nextDeliveries,
      nextReleases,
      nextRequirements,
      nextVersions,
      nextWorkflowTemplates,
    ] = await Promise.all([
      listInitiatives(store.projectId),
      listRequirementDeliveries(store.projectId),
      listReleases(store.projectId),
      listRequirements(store.projectId),
      store.projectId ? listProductVersions(store.projectId) : Promise.resolve([]),
      listWorkflowTemplates(),
    ])
    initiatives.value = nextInitiatives
    deliveries.value = nextDeliveries
    releases.value = nextReleases
    requirements.value = nextRequirements
    versions.value = nextVersions
    workflowTemplates.value = nextWorkflowTemplates
    await loadProjectMembers()
    if (nextVersions.some((version) => version.id === props.versionId)) {
      selectedVersionId.value = props.versionId
    } else if (!nextVersions.some((version) => version.id === selectedVersionId.value)) {
      selectedVersionId.value = nextVersions.find(
        (version) => version.status !== 'CLOSED',
      )?.id ?? nextVersions[0]?.id ?? null
    }
    if (selectedDeliveryId.value && !nextDeliveries.some((item) => item.id === selectedDeliveryId.value)) {
      selectedDeliveryId.value = null
    }
    if (selectedInitiativeId.value && !nextInitiatives.some((item) => item.id === selectedInitiativeId.value)) {
      selectedInitiativeId.value = null
    }
    if (selectedReleaseId.value && !nextReleases.some((item) => item.id === selectedReleaseId.value)) {
      selectedReleaseId.value = null
    }
    await loadReviewRecords()
  } catch (value) {
    error.value = messageOf(value)
    if (!quiet) {
      initiatives.value = []
      deliveries.value = []
      releases.value = []
      versions.value = []
      workflowTemplates.value = []
      reviewRecords.value = []
    }
  } finally {
    if (!quiet) loading.value = false
  }
}

async function loadReviewRecords() {
  if (!selectedVersionId.value) {
    reviewRecords.value = []
    return
  }
  try {
    reviewRecords.value = await listRequirementReviewRecords(selectedVersionId.value)
  } catch {
    reviewRecords.value = []
  }
}

async function refresh() {
  await load(true)
}

async function loadProjectMembers() {
  const projectIds = new Set<number>()
  if (store.projectId) projectIds.add(store.projectId)
  for (const project of store.projects) projectIds.add(project.id)
  for (const item of [...initiatives.value, ...deliveries.value, ...releases.value]) {
    projectIds.add(item.projectId)
  }
  if (projectIds.size === 0) {
    projectMembers.value = {}
    return
  }
  const entries = await Promise.allSettled(
    [...projectIds].map(async (projectId) => {
      const members = await listProjectMembers(projectId)
      return [projectId, members] as const
    }),
  )
  const next: Record<number, ProjectMember[]> = {}
  for (const entry of entries) {
    if (entry.status === 'fulfilled') {
      next[entry.value[0]] = entry.value[1]
    }
  }
  projectMembers.value = next
}

function startWorkflow() {
  if (!hasSelectedProject.value) {
    toast.warning('请先选择具体项目', '研发流程按项目和产品版本组织，不能在全项目视图下操作')
    return
  }
  if (!selectedVersion.value) {
    openVersionPlanning()
    return
  }
  if (versionSetup.value.action === 'requirements') {
    mode.value = 'flows'
    selectedDeliveryId.value = null
    selectedInitiativeId.value = null
    selectedReleaseId.value = null
    requestAnimationFrame(() => {
      requirementsSection.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    })
    return
  }
  if (!versionInitiative.value) {
    if (!canInitiateVersionInitiative.value) {
      toast.warning(
        '需要产品负责人发起版本立项',
        '当前账号没有产品负责人角色；单人项目允许项目负责人代发起',
      )
      return
    }
    openCreateInitiative()
    return
  }
  if (versionInitiative.value.status === 'DRAFT'
      || versionInitiative.value.status === 'REJECTED') {
    mode.value = 'initiatives'
    selectedInitiativeId.value = versionInitiative.value.id
    return
  }
  if (versionInitiative.value.status === 'SUBMITTED') {
    mode.value = 'initiatives'
    selectedInitiativeId.value = versionInitiative.value.id
    if (canRole(versionInitiative.value.projectId, 'OWNER')) {
      void openInitiativeReview(versionInitiative.value, 'approve')
    }
    return
  }
  if (versionDeliveries.value.length === 0) {
    openRequirementPool()
    return
  }
  if (versionDeliveries.value.length > 0) {
    mode.value = 'flows'
    selectedInitiativeId.value = null
    selectedReleaseId.value = null
    selectedDeliveryId.value = versionDeliveries.value[0]?.id ?? null
    return
  }
  mode.value = 'initiatives'
  selectedDeliveryId.value = null
  selectedReleaseId.value = null
  selectedInitiativeId.value = versionInitiative.value.id
}

function openRequirementPool() {
  void router.push({
    path: '/projects',
    query: {
      view: 'pool',
      ...(selectedVersionId.value ? { versionId: String(selectedVersionId.value) } : {}),
    },
  })
}

function openVersionPlanning() {
  void router.push({
    path: '/projects',
    query: {
      view: 'versions',
      ...(selectedVersionId.value ? { versionId: String(selectedVersionId.value) } : {}),
    },
  })
}

function openProjectMembers() {
  void router.push({ path: '/projects', query: { view: 'members' } })
}

function selectPendingItem(item: PendingWorkflowItem) {
  mode.value = item.mode
  selectedDeliveryId.value = item.mode === 'flows' ? item.id : null
  selectedInitiativeId.value = item.mode === 'initiatives' ? item.id : null
  selectedReleaseId.value = item.mode === 'releases' ? item.id : null
}

function openCreateInitiative() {
  if (!selectedVersion.value) {
    openVersionPlanning()
    return
  }
  if (!canRole(selectedVersion.value.projectId, 'PRODUCT')) {
    toast.warning(
      '需要产品负责人发起版本立项',
      '请在项目团队中将当前账号设为产品负责人',
    )
    return
  }
  initiativeEditingId.value = null
  initiativeProjectId.value = String(store.projectId ?? store.projects[0]?.id ?? '')
  initiativeVersionId.value = String(selectedVersion.value.id)
  initiativeMinutes.value = ''
  initiativeComposerFiles.value = []
  initiativeAttachments.value = []
  initiativeError.value = null
  initiativeOpen.value = true
}

function openEditInitiative(initiative: Initiative) {
  initiativeEditingId.value = initiative.id
  initiativeProjectId.value = String(initiative.projectId)
  initiativeVersionId.value = initiative.versionId === null
    ? ''
    : String(initiative.versionId)
  initiativeMinutes.value = initiative.meetingMinutes ?? ''
  initiativeComposerFiles.value = []
  initiativeError.value = null
  initiativeOpen.value = true
  void loadInitiativeAttachmentList(initiative.id)
}

function onInitiativeFiles(event: Event) {
  const input = event.target as HTMLInputElement
  initiativeComposerFiles.value = [
    ...initiativeComposerFiles.value,
    ...Array.from(input.files ?? []),
  ]
  input.value = ''
}

function removeInitiativeFile(index: number) {
  initiativeComposerFiles.value = initiativeComposerFiles.value.filter(
    (_, current) => current !== index,
  )
}

async function saveInitiative(submitAfterSave = false) {
  if (initiativeSaving.value || initiativeUploading.value) return
  if (submitAfterSave && !canSubmitInitiativeComposer.value) {
    initiativeError.value = '请填写会议纪要或上传纪要附件后再提交审批'
    return
  }
  initiativeSaving.value = true
  initiativeError.value = null
  try {
    const wasEditing = initiativeEditingId.value !== null
    const input = {
      projectId: Number(initiativeProjectId.value),
      versionId: initiativeVersionId.value ? Number(initiativeVersionId.value) : null,
      title: '',
      businessGoal: '',
      scopeText: '',
      outOfScopeText: '',
      successMetrics: '',
      stakeholders: '',
      targetVersion: '',
      riskText: '',
      meetingMinutes: initiativeMinutes.value.trim() || null,
    }
    const saved = initiativeEditingId.value
      ? await updateInitiative(initiativeEditingId.value, input)
      : await createInitiative(input)
    initiativeEditingId.value = saved.id
    if (initiativeComposerFiles.value.length > 0) {
      initiativeUploading.value = true
      for (const file of initiativeComposerFiles.value) {
        await uploadInitiativeAttachment(saved.id, file)
      }
      initiativeComposerFiles.value = []
      initiativeUploading.value = false
    }
    if (submitAfterSave) {
      await submitInitiative(saved.id)
    }
    initiativeOpen.value = false
    await load(true)
    mode.value = 'initiatives'
    selectedInitiativeId.value = saved.id
    toast.success(
      submitAfterSave
        ? '版本立项已提交审批'
        : wasEditing
          ? '版本立项已保存'
          : '版本立项已创建',
    )
    emit('changed')
  } catch (value) {
    initiativeError.value = messageOf(value)
  } finally {
    initiativeUploading.value = false
    initiativeSaving.value = false
  }
}

const initiativeOpen = ref(false)
const initiativeSaving = ref(false)
const initiativeError = ref<string | null>(null)
const initiativeEditingId = ref<number | null>(null)
const initiativeProjectId = ref('')
const initiativeVersionId = ref('')
const initiativeMinutes = ref('')
const initiativeComposerFiles = ref<File[]>([])
const initiativeFileInput = ref<HTMLInputElement | null>(null)
const initiativeUploading = ref(false)

const canSubmitInitiativeComposer = computed(() =>
  !initiativeSaving.value
  && !initiativeUploading.value
  && (
    initiativeMinutes.value.trim().length > 0
    || initiativeComposerFiles.value.length > 0
    || initiativeAttachments.value.length > 0
  )
)

function openCreateRequirement(initiative: Initiative) {
  if (initiative.status !== 'APPROVED') {
    toast.warning('请先完成版本立项审批', '版本立项批准后才能录入需求')
    return
  }
  openRequirementPool()
}

async function submitInitiativeForApproval(initiative: Initiative) {
  try {
    await submitInitiative(initiative.id)
    await load(true)
    selectedInitiativeId.value = initiative.id
    toast.success('版本立项已提交审批')
    emit('changed')
  } catch (value) {
    toast.warning('版本立项操作失败', messageOf(value))
  }
}

const initiativeAttachments = ref<InitiativeAttachment[]>([])
const initiativeAttachmentsLoading = ref(false)
const reviewOpen = ref(false)
const reviewMode = ref<'approve' | 'reject'>('approve')
const reviewInitiative = ref<Initiative | null>(null)
const reviewComment = ref('')
const reviewSaving = ref(false)
const reviewError = ref<string | null>(null)

const canSubmitInitiativeReview = computed(() => {
  if (reviewSaving.value) return false
  return reviewMode.value === 'approve' || reviewComment.value.trim().length > 0
})

async function loadInitiativeAttachmentList(initiativeId: number | null) {
  if (initiativeId === null) {
    initiativeAttachments.value = []
    return
  }
  initiativeAttachmentsLoading.value = true
  try {
    initiativeAttachments.value = await listInitiativeAttachments(initiativeId)
  } catch {
    initiativeAttachments.value = []
  } finally {
    initiativeAttachmentsLoading.value = false
  }
}

async function openInitiativeReview(
  initiative: Initiative,
  mode: 'approve' | 'reject',
) {
  reviewInitiative.value = initiative
  reviewMode.value = mode
  reviewComment.value = ''
  reviewError.value = null
  reviewOpen.value = true
  initiativeAttachments.value = []
  await loadInitiativeAttachmentList(initiative.id)
}

async function submitInitiativeReview() {
  const initiative = reviewInitiative.value
  if (!initiative || !canSubmitInitiativeReview.value) return
  reviewSaving.value = true
  reviewError.value = null
  try {
    if (reviewMode.value === 'approve') {
      await approveInitiative(initiative.id, {
        comment: reviewComment.value.trim(),
      })
    } else {
      await rejectInitiative(initiative.id, {
        comment: reviewComment.value.trim(),
      })
    }
    reviewOpen.value = false
    await load(true)
    selectedInitiativeId.value = initiative.id
    await loadInitiativeAttachmentList(initiative.id)
    emit('changed')
    toast.success(
      reviewMode.value === 'approve' ? '版本立项已批准' : '版本立项已驳回',
      reviewMode.value === 'approve' ? '可以继续规划产品版本与需求' : '申请方可修改后重新提交',
    )
  } catch (value) {
    reviewError.value = messageOf(value)
  } finally {
    reviewSaving.value = false
  }
}

async function deliveryAction(action: RequirementDeliveryAction, extra = {}) {
  const delivery = activeDelivery.value
  if (!delivery) return
  try {
    const updated = await actRequirementDelivery(delivery.id, {
      action,
      comment: actionComment.value,
      ...extra,
    })
    await load(true)
    selectedDeliveryId.value = updated.id
    toast.success('交付阶段已更新')
    emit('changed')
  } catch (value) {
    toast.warning('阶段推进失败', messageOf(value))
  }
}

function submitDesign() {
  void deliveryAction('SUBMIT_DESIGN', {
    designSummary: designSummary.value,
    designContent: designContent.value,
  })
}

const releaseOpen = ref(false)
const releaseSaving = ref(false)
const releaseError = ref<string | null>(null)
const releaseProjectId = ref('')
const releaseVersion = ref('')
const releaseCommit = ref('')
const releaseDigest = ref('')
const releaseSize = ref('')
const releaseStorageRef = ref('')
const releaseNotes = ref('')
const releaseRollbackPlan = ref('')
const releaseDeliveryIds = ref<number[]>([])

const releaseProjectItems = computed(() =>
  store.projects
    .filter((project) => canRole(project.id, 'RELEASE_MANAGER'))
    .map((project) => ({ value: String(project.id), label: project.name })),
)
const readyDeliveries = computed(() =>
  deliveries.value.filter(
    (delivery) =>
      delivery.projectId === Number(releaseProjectId.value)
      && delivery.stage === 'READY_FOR_TRANSFER'
      && delivery.status === 'ACTIVE',
  ),
)

watch(releaseProjectId, () => {
  releaseDeliveryIds.value = releaseDeliveryIds.value.filter((id) =>
    readyDeliveries.value.some((delivery) => delivery.id === id),
  )
})

function openCreateRelease(prefillDelivery?: RequirementDelivery) {
  releaseError.value = null
  releaseProjectId.value = String(
    prefillDelivery?.projectId
      ?? (store.projectId && canRole(store.projectId, 'RELEASE_MANAGER') ? store.projectId : null)
      ?? store.projects.find((project) => canRole(project.id, 'RELEASE_MANAGER'))?.id
      ?? '',
  )
  releaseVersion.value = ''
  releaseCommit.value = ''
  releaseDigest.value = ''
  releaseSize.value = ''
  releaseStorageRef.value = ''
  releaseNotes.value = ''
  releaseRollbackPlan.value = ''
  releaseDeliveryIds.value = prefillDelivery ? [prefillDelivery.id] : readyDeliveries.value.map((item) => item.id)
  releaseOpen.value = true
}

function toggleReleaseDelivery(id: number, checked: boolean) {
  const next = new Set(releaseDeliveryIds.value)
  if (checked) next.add(id)
  else next.delete(id)
  releaseDeliveryIds.value = [...next]
}

async function saveRelease() {
  if (releaseSaving.value) return
  releaseSaving.value = true
  releaseError.value = null
  try {
    const saved = await createRelease({
      projectId: Number(releaseProjectId.value),
      version: releaseVersion.value.trim(),
      gitCommit: releaseCommit.value.trim(),
      digest: releaseDigest.value.trim(),
      sizeBytes: Number(releaseSize.value || 0),
      storageRef: releaseStorageRef.value.trim(),
      releaseNotes: releaseNotes.value.trim(),
      rollbackPlan: releaseRollbackPlan.value.trim(),
      deliveryIds: releaseDeliveryIds.value,
    })
    releaseOpen.value = false
    mode.value = 'releases'
    await load(true)
    selectedReleaseId.value = saved.id
    toast.success('发布轮次已创建', '同一制品现在可以进入转测和集成')
    emit('changed')
  } catch (value) {
    releaseError.value = messageOf(value)
  } finally {
    releaseSaving.value = false
  }
}

const releaseActionComment = ref('')

async function releaseAction(action: ReleaseAction) {
  const release = selectedRelease.value
  if (!release) return
  try {
    const updated = await actRelease(release.id, action, releaseActionComment.value)
    await load(true)
    selectedReleaseId.value = updated.id
    toast.success('发布阶段已更新')
    emit('changed')
  } catch (value) {
    toast.warning('发布推进失败', messageOf(value))
  }
}

function selectDelivery(delivery: RequirementDelivery) {
  selectedDeliveryId.value = delivery.id
  selectedInitiativeId.value = null
  selectedReleaseId.value = null
  mode.value = 'flows'
  actionComment.value = ''
}

const deliveryForRequirement = (requirementId: number) =>
  deliveries.value.find((delivery) => delivery.requirementId === requirementId) ?? null

function openDeliveryForRequirement(requirement: Requirement) {
  const delivery = deliveryForRequirement(requirement.id)
  if (delivery) selectDelivery(delivery)
}

function openReviewRecord() {
  if (!selectedVersion.value
      || pendingReviewRequirements.value.length === 0
      || reviewSubmissionPending.value
      || !canSubmitRequirementReview.value) {
    return
  }
  reviewDate.value = localDateInputValue()
  requirementReviewMinutes.value = ''
  reviewFiles.value = []
  reviewRecordError.value = null
  reviewRecordOpen.value = true
}

function onRequirementReviewFiles(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  if (files.length === 0) return
  reviewFiles.value = [...reviewFiles.value, ...files]
}

function removeRequirementReviewFile(index: number) {
  reviewFiles.value = reviewFiles.value.filter((_, current) => current !== index)
}

async function saveReviewRecord() {
  if (!selectedVersion.value || reviewRecordSaving.value) return
  if (!reviewDate.value) {
    reviewRecordError.value = '请填写评审日期'
    return
  }
  if (!requirementReviewMinutes.value.trim() && reviewFiles.value.length === 0) {
    reviewRecordError.value = '请填写会议纪要或至少上传一份纪要文件'
    return
  }
  reviewRecordSaving.value = true
  reviewRecordError.value = null
  try {
    const record = await submitRequirementReview(selectedVersion.value.id, {
      reviewDate: reviewDate.value,
      minutes: requirementReviewMinutes.value.trim(),
      files: reviewFiles.value,
    })
    await load(true)
    emit('changed')
    reviewRecordOpen.value = false
    toast.success(
      `第 ${record.reviewNo} 次需求评审已提交`,
      `共 ${record.requirements.length} 条需求，等待项目负责人审核`,
    )
  } catch (value) {
    reviewRecordError.value = messageOf(value)
  } finally {
    reviewRecordSaving.value = false
  }
}

function openReviewDecision(record: RequirementReviewRecord) {
  if (record.status !== 'SUBMITTED' || !canDecideRequirementReview.value) return
  reviewDecisionRecord.value = record
  reviewDecisionComment.value = ''
  reviewDecisionError.value = null
  reviewDecisionOpen.value = true
}

function applyReviewDecisionPhrase(text: string) {
  reviewDecisionComment.value = text
  reviewDecisionError.value = null
}

function reviewAttachmentDownloadUrl(contentUrl: string) {
  return `${contentUrl}${contentUrl.includes('?') ? '&' : '?'}download=1`
}

async function decideReview(decision: 'APPROVE' | 'REJECT') {
  const record = reviewDecisionRecord.value
  if (!record || reviewDecisionSaving.value) return
  if (decision === 'REJECT' && !reviewDecisionComment.value.trim()) {
    reviewDecisionError.value = '驳回时必须填写审核意见'
    return
  }
  reviewDecisionSaving.value = true
  reviewDecisionError.value = null
  try {
    await decideRequirementReview(
      record.id,
      decision,
      reviewDecisionComment.value.trim(),
    )
    reviewDecisionOpen.value = false
    await load(true)
    emit('changed')
    if (decision === 'APPROVE') {
      const firstRequirementId = record.requirements[0]?.requirementId
      const delivery = firstRequirementId
        ? deliveryForRequirement(firstRequirementId)
        : null
      if (delivery) selectDelivery(delivery)
    }
    toast.success(
      decision === 'APPROVE' ? '需求评审已通过' : '需求评审已驳回',
      decision === 'APPROVE'
        ? '当前版本需求已正式化并创建详细设计交付'
        : '需求已退回，产品负责人可补充材料后重新提交',
    )
  } catch (value) {
    reviewDecisionError.value = messageOf(value)
  } finally {
    reviewDecisionSaving.value = false
  }
}

function selectRelease(release: Release) {
  selectedReleaseId.value = release.id
  selectedDeliveryId.value = null
  selectedInitiativeId.value = null
  mode.value = 'releases'
  releaseActionComment.value = ''
}

function closeSelection() {
  selectedDeliveryId.value = null
  selectedInitiativeId.value = null
  selectedReleaseId.value = null
}

onMounted(() => void load())
watch(() => store.projectId, () => void load())
watch(() => props.versionId, (versionId) => {
  selectedVersionId.value = versionId
}, { immediate: true })
watch(selectedInitiativeId, (id) => void loadInitiativeAttachmentList(id))
watch(selectedVersionId, () => {
  selectedDeliveryId.value = null
  selectedInitiativeId.value = null
  selectedReleaseId.value = null
  mode.value = 'flows'
  void loadReviewRecords()
})
</script>

<template>
  <div class="flex h-full min-h-0 min-w-0 overflow-hidden">
    <div class="flex min-w-0 flex-1 flex-col overflow-hidden">
      <p v-if="error" class="border-b border-danger/20 bg-danger/5 px-4 py-2 text-caption text-danger">
        {{ error }}
      </p>

      <section class="border-b border-separator-soft bg-content px-4 py-3">
        <div class="flex flex-wrap items-center gap-2">
          <div class="min-w-0 flex-1">
            <p class="text-caption font-emphasis text-label">版本进度与操作</p>
            <p class="mt-0.5 text-caption text-label-tertiary">
              {{ versionRequirements.length }} 条需求 · {{ versionDeliveries.length }} 个研发交付
            </p>
          </div>
          <div class="flex items-center gap-2">
            <span
              v-if="selectedWorkflowTemplate"
              class="rounded-badge bg-window px-2 py-1 text-[10px] text-label-secondary"
            >
              {{ selectedWorkflowTemplate.displayName }}
            </span>
            <span class="text-caption text-label-tertiary">
              我的角色：{{ currentProjectRoleLabels.length > 0 ? currentProjectRoleLabels.join(' / ') : '未分配' }}
            </span>
            <Button variant="ghost" size="sm" @click="openProjectMembers">配置角色</Button>
            <IconButton label="刷新版本进度" size="sm" :disabled="loading" @click="refresh">
              <Icon :as="RefreshCw" size="inline" :class="loading ? 'animate-spin' : ''" />
            </IconButton>
          </div>
        </div>

        <div class="mt-3 grid gap-3 lg:grid-cols-[minmax(0,1.2fr)_minmax(300px,0.8fr)]">
          <div class="rounded-panel border border-accent/20 bg-accent/5 p-4">
            <p class="text-[10px] font-emphasis uppercase tracking-wide text-accent">下一步</p>
            <h2 class="mt-1 text-title font-emphasis text-label">{{ versionSetup.title }}</h2>
            <p class="mt-1 max-w-2xl text-caption text-label-secondary">{{ versionSetup.description }}</p>
            <div class="mt-3 flex flex-wrap items-center gap-2">
              <Button
                v-if="versionSetup.action !== 'none'
                  && versionSetup.action !== 'project'
                  && (versionSetup.action !== 'review' || canRole(selectedVersion?.projectId, 'OWNER'))"
                variant="primary"
                size="sm"
                :disabled="versionSetup.action === 'initiative' && !canInitiateVersionInitiative"
                @click="startWorkflow"
              >
                <Icon :as="Plus" size="inline" />{{ workflowStartLabel }}
              </Button>
              <span
                v-if="versionSetup.action === 'initiative' && !canInitiateVersionInitiative"
                class="text-caption text-warning"
              >
                版本立项需由产品负责人发起
              </span>
              <span
                v-else-if="versionSetup.action === 'project'"
                class="text-caption text-warning"
              >
                请在页面顶部选择具体项目
              </span>
              <span
                v-else-if="versionSetup.action === 'review'"
                class="rounded-badge bg-window px-2 py-1 text-caption text-label-secondary"
              >
                等待项目负责人审批
              </span>
            </div>
          </div>

          <div class="rounded-panel border border-separator-soft bg-window p-3">
            <p class="text-caption font-emphasis text-label-secondary">版本研发阶段</p>
            <ol class="mt-2 space-y-2">
              <li
                v-for="(group, index) in workflowGroups"
                :key="group.label"
                class="flex items-start gap-2.5"
              >
                <span
                  class="grid size-5 shrink-0 place-items-center rounded-full text-[10px] font-emphasis"
                  :class="group.state === 'done'
                    ? 'bg-success text-white'
                    : group.state === 'active'
                      ? 'bg-accent text-white'
                      : 'border border-separator-soft bg-content text-label-tertiary'"
                >
                  {{ index + 1 }}
                </span>
                <span class="min-w-0">
                  <span
                    class="block text-caption font-emphasis"
                    :class="group.state === 'pending' ? 'text-label-tertiary' : 'text-label'"
                  >
                    {{ group.label }}
                  </span>
                  <span class="mt-0.5 block text-[10px] text-label-tertiary">{{ group.description }}</span>
                </span>
              </li>
            </ol>
          </div>
        </div>

        <div
          v-if="selectedVersion && versionInitiative?.status !== 'APPROVED'"
          class="mt-3 grid gap-2 md:grid-cols-3"
        >
          <div class="rounded-panel border border-separator-soft bg-window px-3 py-2.5">
            <p class="text-caption font-emphasis text-label">1 · 业务目标</p>
            <p class="mt-0.5 text-caption text-label-tertiary">本版本为什么做，解决什么业务问题。</p>
          </div>
          <div class="rounded-panel border border-separator-soft bg-window px-3 py-2.5">
            <p class="text-caption font-emphasis text-label">2 · 范围与边界</p>
            <p class="mt-0.5 text-caption text-label-tertiary">本版本做什么、明确不做什么。</p>
          </div>
          <div class="rounded-panel border border-separator-soft bg-window px-3 py-2.5">
            <p class="text-caption font-emphasis text-label">3 · 成功指标与风险</p>
            <p class="mt-0.5 text-caption text-label-tertiary">如何判断完成，以及主要依赖和风险。</p>
          </div>
        </div>

        <div
          v-if="pendingWorkflowItems.length > 0"
          class="mt-3 rounded-panel border border-accent/20 bg-accent/5 p-2.5"
        >
          <div class="flex items-center gap-2">
            <span class="size-1.5 rounded-full bg-accent" />
            <p class="text-caption font-emphasis text-label">待你处理 {{ pendingWorkflowItems.length }} 项</p>
            <span class="text-caption text-label-tertiary">按当前项目角色匹配</span>
          </div>
          <div class="mt-2 flex gap-2 overflow-x-auto pb-0.5">
            <button
              v-for="item in pendingWorkflowItems.slice(0, 6)"
              :key="item.key"
              type="button"
              class="min-w-48 shrink-0 rounded-control border border-accent/20 bg-content px-2.5 py-2 text-left transition-colors hover:bg-hover"
              @click="selectPendingItem(item)"
            >
              <span class="flex items-center gap-1.5">
                <span class="rounded-badge bg-accent/10 px-1.5 py-px text-[10px] text-accent">
                  {{ item.step }}
                </span>
                <span class="text-[10px] text-label-tertiary">{{ roleLabel(item.role) }}</span>
              </span>
              <span class="mt-1 block truncate text-caption text-label">{{ item.title }}</span>
            </button>
          </div>
        </div>
      </section>

      <div
        v-if="versionInitiative?.status === 'APPROVED'"
        class="scroll-area min-h-0 flex-1 overflow-auto"
      >
        <div class="divide-y divide-separator-soft">
          <section ref="requirementsSection" class="px-4 py-4">
            <div class="flex items-center gap-2">
              <p class="text-caption font-emphasis text-label">需求与交付</p>
              <span class="tnum text-caption text-label-tertiary">{{ versionRequirements.length }}</span>
              <div class="ml-auto flex items-center gap-2">
                <Button
                  v-if="reviewSubmissionPending
                    && latestReviewRecord
                    && canDecideRequirementReview"
                  variant="primary"
                  size="sm"
                  @click="openReviewDecision(latestReviewRecord)"
                >
                  审核需求评审
                </Button>
                <Button
                  v-else-if="pendingReviewRequirements.length > 0
                    && !reviewSubmissionPending
                    && canSubmitRequirementReview"
                  variant="primary"
                  size="sm"
                  @click="openReviewRecord"
                >
                  提交需求评审
                </Button>
                <Button size="sm" @click="openRequirementPool">
                  去需求池
                </Button>
              </div>
            </div>
            <div
              v-if="latestReviewRecord"
              class="mt-3 rounded-control border border-separator-soft bg-window px-3 py-2.5 text-caption"
            >
              <div class="flex flex-wrap items-center gap-2">
                <span class="font-emphasis text-label">
                  第 {{ latestReviewRecord.reviewNo }} 次需求评审
                </span>
                <Badge :tone="requirementReviewStatusMeta[latestReviewRecord.status].tone">
                  {{ requirementReviewStatusMeta[latestReviewRecord.status].label }}
                </Badge>
                <span class="text-label-tertiary">{{ latestReviewRecord.reviewDate }}</span>
                <span class="text-label-tertiary">
                  提交人 {{ memberNameForProject(selectedVersion?.projectId, latestReviewRecord.submittedBy) }}
                </span>
                <span class="text-label-tertiary">
                  {{ latestReviewRecord.requirements.length }} 条需求
                </span>
              </div>
              <p class="mt-1 text-label-secondary">
                结论：{{ latestReviewRecord.conclusion }}
              </p>
              <p
                v-if="latestReviewRecord.reviewComment"
                class="mt-1 text-label-secondary"
              >
                审核意见：{{ latestReviewRecord.reviewComment }}
                <span
                  v-if="latestReviewRecord.decidedBy"
                  class="text-label-tertiary"
                >
                  · {{ memberNameForProject(selectedVersion?.projectId, latestReviewRecord.decidedBy) }}
                </span>
              </p>
              <p
                v-if="latestReviewRecord.minutes"
                class="mt-1 line-clamp-2 whitespace-pre-wrap text-label-tertiary"
              >
                {{ latestReviewRecord.minutes }}
              </p>
              <div
                v-if="latestReviewRecord.attachments.length > 0"
                class="mt-1.5 flex flex-wrap gap-2"
              >
                <a
                  v-for="attachment in latestReviewRecord.attachments"
                  :key="attachment.id"
                  :href="attachment.contentUrl"
                  target="_blank"
                  rel="noreferrer"
                  class="inline-flex items-center gap-1 text-accent hover:underline"
                >
                  <Icon :as="Paperclip" size="inline" />{{ attachment.filename }}
                </a>
              </div>
              <p
                v-if="reviewRecords.length > 1"
                class="mt-1 text-label-tertiary"
              >
                共 {{ reviewRecords.length }} 次需求评审记录
              </p>
            </div>
            <p
              v-else-if="pendingReviewRequirements.length > 0"
              class="mt-3 text-caption text-label-tertiary"
            >
              产品负责人提交线下评审纪要和附件后，由项目负责人审核通过或驳回。
            </p>
            <p v-if="versionRequirements.length === 0" class="mt-3 text-caption text-label-tertiary">
              当前版本还没有需求。进入需求池录入需求后，会自动纳入这个版本。
            </p>
            <div v-else class="mt-3 overflow-hidden rounded-panel border border-separator-soft">
              <div
                v-for="requirement in versionRequirements"
                :key="requirement.id"
                role="button"
                tabindex="0"
                class="flex w-full items-center gap-3 border-b border-separator-soft bg-content px-3 py-2.5 text-left transition-colors last:border-b-0 hover:bg-hover"
                @click="openDeliveryForRequirement(requirement)"
                @keydown.enter.prevent="openDeliveryForRequirement(requirement)"
              >
                <span class="tnum shrink-0 text-caption text-label-tertiary">
                  #{{ formatRequirementNo(requirement.requirementNo) }}
                </span>
                <span class="min-w-0 flex-1">
                  <span class="block truncate text-caption font-emphasis text-label">{{ requirement.title }}</span>
                  <span class="mt-0.5 block truncate text-[10px] text-label-tertiary">
                    {{ deliveryForRequirement(requirement.id)?.initiativeTitle || versionInitiative?.title }}
                  </span>
                </span>
                <Badge :tone="requirementPoolTone(requirementDisplayStatus(requirement))">
                  {{ requirementPoolLabels[requirementDisplayStatus(requirement)] }}
                </Badge>
                <Badge
                  v-if="deliveryForRequirement(requirement.id)"
                  :tone="deliveryTone(deliveryForRequirement(requirement.id)!.stage)"
                >
                  {{ deliveryStageLabels[deliveryForRequirement(requirement.id)!.stage] }}
                </Badge>
                <span v-else class="text-caption text-label-tertiary">待建立交付</span>
              </div>
            </div>
          </section>

          <section v-if="versionDeliveries.length > 0" class="px-4 py-4">
            <div class="flex items-center gap-2">
              <p class="text-caption font-emphasis text-label">研发交付明细</p>
              <span class="tnum text-caption text-label-tertiary">{{ versionDeliveries.length }}</span>
            </div>
            <div class="mt-3 space-y-1.5">
              <button
                v-for="delivery in versionDeliveries"
                :key="delivery.id"
                type="button"
                class="flex w-full items-center gap-3 rounded-panel border border-separator-soft bg-content px-3 py-2.5 text-left transition-colors hover:bg-hover"
                @click="selectDelivery(delivery)"
              >
                <Icon :as="GitBranch" size="inline" class="text-label-tertiary" />
                <span class="min-w-0 flex-1">
                  <span class="block truncate text-caption font-emphasis text-label">{{ delivery.requirementTitle }}</span>
                  <span class="mt-0.5 block text-[10px] text-label-tertiary">
                    交付 #{{ delivery.id }} · 更新 {{ formatRelative(delivery.updatedAt) }}
                  </span>
                </span>
                <Badge :tone="deliveryTone(delivery.stage)">{{ deliveryStageLabels[delivery.stage] }}</Badge>
              </button>
            </div>
          </section>

          <section class="px-4 py-4">
            <div class="flex items-center gap-2">
              <p class="text-caption font-emphasis text-label">版本发布</p>
              <span class="tnum text-caption text-label-tertiary">{{ versionReleases.length }}</span>
              <Button
                v-if="canCreateRelease && selectedVersion"
                class="ml-auto"
                size="sm"
                @click="openCreateRelease()"
              >
                <Icon :as="Plus" size="inline" />新建发布
              </Button>
            </div>
            <p v-if="versionReleases.length === 0" class="mt-3 text-caption text-label-tertiary">
              需求完成冒烟与自动化回归、达到待转测后，由发布负责人创建发布轮次。
            </p>
            <div v-else class="mt-3 space-y-1.5">
              <button
                v-for="release in versionReleases"
                :key="release.id"
                type="button"
                class="flex w-full items-center gap-3 rounded-panel border border-separator-soft bg-content px-3 py-2.5 text-left transition-colors hover:bg-hover"
                @click="selectRelease(release)"
              >
                <Icon :as="Play" size="inline" class="text-label-tertiary" />
                <span class="min-w-0 flex-1">
                  <span class="block truncate text-caption font-emphasis text-label">
                    第 {{ release.attemptNo }} 轮 · {{ release.deliveries.length }} 个需求
                  </span>
                  <span class="mt-0.5 block truncate font-mono text-[10px] text-label-tertiary">
                    {{ release.artifact.gitCommit.slice(0, 12) }}
                  </span>
                </span>
                <Badge :tone="releaseTone(release.stage)">{{ releaseStageLabels[release.stage] }}</Badge>
              </button>
            </div>
          </section>
        </div>
      </div>
    </div>

    <div
      v-if="currentSelection"
      class="fixed inset-0 z-30 bg-black/20 2xl:hidden"
      aria-hidden="true"
      @click="closeSelection"
    />
    <aside
      v-if="currentSelection"
      class="fixed inset-y-0 right-0 z-40 h-full min-h-0 w-[min(520px,calc(100vw-24px))] overflow-y-auto border-l border-separator bg-window shadow-panel 2xl:static 2xl:z-auto 2xl:w-[420px] 2xl:shrink-0 2xl:shadow-none"
    >
      <template v-if="mode === 'flows' && selectedDelivery">
        <div class="p-4">
          <button
            type="button"
            class="mb-3 inline-flex items-center gap-0.5 text-caption text-accent 2xl:hidden"
            @click="selectedDeliveryId = null"
          >
            <Icon :as="ChevronLeft" size="inline" />返回列表
          </button>
          <div class="flex items-start gap-2">
            <div class="min-w-0 flex-1">
              <p class="text-caption text-label-tertiary">研发交付 #{{ selectedDelivery.id }}</p>
              <h2 class="mt-1 text-title font-emphasis text-label">{{ selectedDelivery.requirementTitle }}</h2>
              <p class="mt-1 text-caption text-label-secondary">
                {{ selectedDelivery.initiativeTitle }} · {{ selectedDelivery.projectName }}
              </p>
            </div>
            <Badge :tone="deliveryTone(selectedDelivery.stage)">
              {{ deliveryStageLabels[selectedDelivery.stage] }}
            </Badge>
          </div>

          <div class="mt-4 grid grid-cols-2 gap-2">
            <div class="rounded-panel border border-separator-soft bg-content px-3 py-2">
              <p class="text-[10px] text-label-tertiary">关联需求</p>
              <p class="tnum mt-0.5 text-caption text-label">
                #{{ requirementNoLabel(selectedDelivery.requirementId) }}
              </p>
            </div>
            <div class="rounded-panel border border-separator-soft bg-content px-3 py-2">
              <p class="text-[10px] text-label-tertiary">最近更新</p>
              <p class="mt-0.5 text-caption text-label">{{ formatDateTime(selectedDelivery.updatedAt) }}</p>
            </div>
          </div>

          <div
            v-if="selectedDeliveryNext"
            class="mt-4 rounded-panel border border-accent/20 bg-accent/5 p-3"
          >
            <div class="flex flex-wrap items-center gap-2">
              <p class="text-caption font-emphasis text-label">
                下一步：{{ selectedDeliveryNext.label }}
              </p>
              <span
                v-if="selectedDeliveryNext.role
                  && canRole(selectedDelivery.projectId, selectedDeliveryNext.role)"
                class="rounded-badge bg-accent px-1.5 py-px text-[10px] text-white"
              >
                需要你处理
              </span>
              <span
                v-else-if="selectedDeliveryNext.role"
                class="rounded-badge bg-window px-1.5 py-px text-[10px] text-label-secondary"
              >
                等待{{ roleLabel(selectedDeliveryNext.role) }}
              </span>
            </div>
            <p class="mt-1 text-caption text-label-tertiary">{{ selectedDeliveryNext.hint }}</p>
          </div>

          <div class="mt-5 border-t border-separator pt-3">
            <p class="text-caption font-emphasis text-label-secondary">阶段进度</p>
            <DeliveryPipeline
              class="mt-3"
              :steps="deliverySteps"
              :current="selectedDelivery.stage"
              :cancelled="selectedDelivery.status === 'CANCELLED'"
            />
          </div>

          <div class="mt-5 rounded-panel border border-separator-soft bg-content p-3">
            <template v-if="selectedDelivery.stage === 'REQUIREMENT_REVIEW'">
              <p class="text-caption font-emphasis text-label-secondary">需求评审</p>
              <div
                v-if="selectedDeliveryRequirement"
                class="mt-2 rounded-panel border border-separator-soft bg-window p-3"
              >
                <div class="flex items-start gap-2">
                  <div class="min-w-0 flex-1">
                    <p class="text-caption font-emphasis text-label">
                      {{ selectedDeliveryRequirement.title }}
                    </p>
                    <p class="mt-0.5 text-[10px] text-label-tertiary">
                      需求 #{{ selectedDeliveryRequirement.id }}
                    </p>
                  </div>
                  <span class="rounded-badge bg-window px-1.5 py-px text-[10px] text-label-tertiary">
                    历史评审交付
                  </span>
                </div>
                <p class="mt-2 whitespace-pre-wrap text-caption text-label-secondary">
                  {{ selectedDeliveryRequirement.rawText }}
                </p>
                <div
                  v-if="selectedDeliveryRequirement.acceptanceText"
                  class="mt-2 border-t border-separator-soft pt-2"
                >
                  <p class="text-[10px] font-emphasis text-label-tertiary">验收标准</p>
                  <p class="mt-0.5 whitespace-pre-wrap text-caption text-label-secondary">
                    {{ selectedDeliveryRequirement.acceptanceText }}
                  </p>
                </div>
                <div
                  v-if="selectedDeliveryRequirement.clarificationText"
                  class="mt-2 border-t border-separator-soft pt-2"
                >
                  <p class="text-[10px] font-emphasis text-label-tertiary">补充澄清</p>
                  <p class="mt-0.5 whitespace-pre-wrap text-caption text-label-secondary">
                    {{ selectedDeliveryRequirement.clarificationText }}
                  </p>
                </div>
              </div>
              <p
                v-if="requirementReviewRejected"
                class="mt-2 rounded-control bg-danger/8 px-2.5 py-2 text-caption text-danger"
              >
                需求已驳回，请修改需求内容后重新发起评审。
              </p>
              <p class="mt-1 text-caption text-label-tertiary">审批角色：产品负责人</p>
              <template v-if="canRole(selectedDelivery.projectId, 'PRODUCT')">
                <Textarea v-model="actionComment" class="mt-2" :rows="2" placeholder="评审意见（可选）" />
                <div class="mt-2 flex flex-wrap gap-2">
                  <Button variant="primary" size="sm" @click="deliveryAction('APPROVE_REQUIREMENT_REVIEW')">
                    评审通过，进入设计
                  </Button>
                  <Button size="sm" @click="deliveryAction('REJECT_REQUIREMENT_REVIEW')">
                    记录驳回
                  </Button>
                </div>
              </template>
              <p v-else class="mt-2 text-caption text-label-tertiary">
                等待产品负责人处理。你当前的角色：{{ roleSummaryForProject(selectedDelivery.projectId) }}。
              </p>
            </template>

            <template v-else-if="selectedDelivery.stage === 'DESIGN'">
              <p class="text-caption font-emphasis text-label-secondary">详细设计</p>
              <p class="mt-1 text-caption text-label-tertiary">提交角色：技术负责人</p>
              <template v-if="canRole(selectedDelivery.projectId, 'TECH_LEAD')">
                <Input v-model="designSummary" class="mt-2" placeholder="设计摘要，例如：新增交付流程聚合与门禁" />
                <Textarea
                  v-model="designContent"
                  class="mt-2"
                  :rows="6"
                  placeholder="写清架构、模块、接口、数据迁移、测试策略和回滚方案"
                />
                <Button class="mt-2" variant="primary" size="sm" @click="submitDesign">
                  提交设计评审
                </Button>
              </template>
              <p v-else class="mt-2 text-caption text-label-tertiary">
                等待技术负责人提交设计。你当前的角色：{{ roleSummaryForProject(selectedDelivery.projectId) }}。
              </p>
            </template>

            <template v-else-if="selectedDelivery.stage === 'DESIGN_REVIEW'">
              <p class="text-caption font-emphasis text-label-secondary">设计评审</p>
              <p class="mt-1 text-caption text-label-tertiary">审批角色：技术负责人</p>
              <div v-if="selectedDelivery.designSummary" class="mt-2 rounded-control bg-window p-2">
                <p class="text-caption text-label">{{ selectedDelivery.designSummary }}</p>
                <p class="mt-1 line-clamp-4 whitespace-pre-wrap text-caption text-label-tertiary">
                  {{ selectedDelivery.designContent }}
                </p>
              </div>
              <template v-if="canRole(selectedDelivery.projectId, 'TECH_LEAD')">
                <Textarea v-model="actionComment" class="mt-2" :rows="2" placeholder="评审意见（可选）" />
                <div class="mt-2 flex flex-wrap gap-2">
                  <Button variant="primary" size="sm" @click="deliveryAction('APPROVE_DESIGN')">
                    设计通过，进入研发
                  </Button>
                  <Button size="sm" @click="deliveryAction('REJECT_DESIGN')">
                    退回设计
                  </Button>
                </div>
              </template>
              <p v-else class="mt-2 text-caption text-label-tertiary">
                等待技术负责人评审。你当前的角色：{{ roleSummaryForProject(selectedDelivery.projectId) }}。
              </p>
            </template>

            <template v-else-if="selectedDelivery.stage === 'DEVELOPMENT'">
              <p class="text-caption font-emphasis text-label-secondary">研发</p>
              <p class="mt-1 text-caption text-label-tertiary">
                实现任务完成后，由技术负责人确认进入冒烟测试。
              </p>
              <Button
                v-if="canRole(selectedDelivery.projectId, 'TECH_LEAD')"
                class="mt-2"
                variant="primary"
                size="sm"
                @click="deliveryAction('COMPLETE_DEVELOPMENT')"
              >
                研发完成
              </Button>
              <p v-else class="mt-2 text-caption text-label-tertiary">
                等待技术负责人确认研发完成。你当前的角色：{{ roleSummaryForProject(selectedDelivery.projectId) }}。
              </p>
            </template>

            <template v-else-if="selectedDelivery.stage === 'SMOKE'">
              <p class="text-caption font-emphasis text-label-secondary">冒烟测试</p>
              <p class="mt-1 text-caption text-label-tertiary">
                审批角色：测试负责人。当前版本先保留人工确认；下一步接入真实冒烟测试计划。
              </p>
              <Button
                v-if="canRole(selectedDelivery.projectId, 'QA')"
                class="mt-2"
                variant="primary"
                size="sm"
                @click="deliveryAction('PASS_SMOKE')"
              >
                冒烟通过
              </Button>
              <p v-else class="mt-2 text-caption text-label-tertiary">
                等待测试负责人确认冒烟结果。你当前的角色：{{ roleSummaryForProject(selectedDelivery.projectId) }}。
              </p>
            </template>

            <template v-else-if="selectedDelivery.stage === 'AUTOMATION'">
              <p class="text-caption font-emphasis text-label-secondary">自动化回归</p>
              <p class="mt-1 text-caption text-label-tertiary">
                审批角色：测试负责人。当前版本先保留人工确认；下一步接入自动化测试运行和报告。
              </p>
              <Button
                v-if="canRole(selectedDelivery.projectId, 'QA')"
                class="mt-2"
                variant="primary"
                size="sm"
                @click="deliveryAction('PASS_AUTOMATION')"
              >
                自动化回归通过
              </Button>
              <p v-else class="mt-2 text-caption text-label-tertiary">
                等待测试负责人确认自动化结果。你当前的角色：{{ roleSummaryForProject(selectedDelivery.projectId) }}。
              </p>
            </template>

            <template v-else-if="selectedDelivery.stage === 'READY_FOR_TRANSFER'">
              <p class="text-caption font-emphasis text-success">已具备转测条件</p>
              <p class="mt-1 text-caption text-label-tertiary">
                这个需求可以纳入版本发布，进入转测和集成测试。
              </p>
              <Button
                v-if="canRole(selectedDelivery.projectId, 'RELEASE_MANAGER')"
                class="mt-2"
                variant="primary"
                size="sm"
                @click="openCreateRelease(selectedDelivery)"
              >
                创建发布
              </Button>
              <p v-else class="mt-2 text-caption text-label-tertiary">
                等待发布负责人纳入版本发布。你当前的角色：{{ roleSummaryForProject(selectedDelivery.projectId) }}。
              </p>
            </template>

            <template v-else>
              <p class="text-caption font-emphasis text-label-secondary">
                {{ selectedDelivery.stage === 'CLOSED' ? '交付已关闭' : '已进入发布流程' }}
              </p>
              <p class="mt-1 text-caption text-label-tertiary">
                当前阶段由发布单继续推进，需求交付信息保持只读。
              </p>
            </template>
          </div>

          <div v-if="selectedDelivery.designSummary" class="mt-4">
            <p class="text-caption font-emphasis text-label-secondary">设计摘要</p>
            <p class="mt-1 text-caption text-label">{{ selectedDelivery.designSummary }}</p>
          </div>

          <div class="mt-5 border-t border-separator pt-3">
            <p class="text-caption font-emphasis text-label-secondary">门禁记录</p>
            <p v-if="selectedDelivery.gates.length === 0" class="mt-2 text-caption text-label-tertiary">
              还没有门禁记录。
            </p>
            <ul v-else class="mt-2 space-y-1.5">
              <li
                v-for="gate in selectedDelivery.gates"
                :key="gate.id"
                class="rounded-control border border-separator-soft bg-content px-2.5 py-1.5"
              >
                <div class="flex items-center gap-2">
                  <span class="text-caption text-label">{{ gate.type }}</span>
                  <span class="text-caption text-label-tertiary">
                    · {{ memberNameForProject(selectedDelivery.projectId, gate.decidedBy) }}
                    · {{ roleLabel(gate.roleCode) }}
                  </span>
                  <Badge :tone="gate.decision === 'PASSED' ? 'success' : 'danger'">
                    {{ gate.decision === 'PASSED' ? '通过' : '驳回' }}
                  </Badge>
                  <span class="ml-auto text-caption text-label-tertiary">
                    {{ formatRelative(gate.decidedAt) }}
                  </span>
                </div>
                <p v-if="gate.comment" class="mt-1 text-caption text-label-tertiary">{{ gate.comment }}</p>
              </li>
            </ul>
          </div>

          <Button
            v-if="selectedDelivery.status === 'ACTIVE'
              && !['IN_RELEASE', 'RELEASED', 'CLOSED'].includes(selectedDelivery.stage)
              && canRole(selectedDelivery.projectId, 'OWNER')"
            class="mt-4"
            size="sm"
            variant="danger"
            block
            @click="deliveryAction('CANCEL')"
          >
            取消交付
          </Button>
        </div>
      </template>

      <template v-else-if="mode === 'initiatives' && selectedInitiative">
        <div class="p-4">
          <button
            type="button"
            class="mb-3 inline-flex items-center gap-0.5 text-caption text-accent 2xl:hidden"
            @click="selectedInitiativeId = null"
          >
            <Icon :as="ChevronLeft" size="inline" />返回列表
          </button>
          <div class="flex items-start gap-2">
            <div class="min-w-0 flex-1">
              <p class="text-caption text-label-tertiary">版本立项 #{{ selectedInitiative.id }}</p>
              <h2 class="mt-1 text-title font-emphasis text-label">{{ selectedInitiative.title }}</h2>
              <p class="mt-1 text-caption text-label-secondary">{{ selectedInitiative.projectName }}</p>
              <p
                v-if="selectedInitiative.targetVersion"
                class="mt-1 text-caption text-label-tertiary"
              >
                目标版本 {{ selectedInitiative.targetVersion }}
              </p>
            </div>
            <Badge :tone="initiativeTone(selectedInitiative.status)">
              {{ initiativeStatusLabels[selectedInitiative.status] }}
            </Badge>
          </div>

          <div
            v-if="selectedInitiativeNext"
            class="mt-4 rounded-panel border border-accent/20 bg-accent/5 p-3"
          >
            <div class="flex flex-wrap items-center gap-2">
              <p class="text-caption font-emphasis text-label">
                下一步：{{ selectedInitiativeNext.label }}
              </p>
              <span
                v-if="selectedInitiativeNext.role
                  && canRole(selectedInitiative.projectId, selectedInitiativeNext.role)"
                class="rounded-badge bg-accent px-1.5 py-px text-[10px] text-white"
              >
                需要你处理
              </span>
              <span
                v-else-if="selectedInitiativeNext.role"
                class="rounded-badge bg-window px-1.5 py-px text-[10px] text-label-secondary"
              >
                等待{{ roleLabel(selectedInitiativeNext.role) }}
              </span>
            </div>
            <p class="mt-1 text-caption text-label-tertiary">{{ selectedInitiativeNext.hint }}</p>
          </div>

          <dl class="mt-4 space-y-3">
            <div>
              <dt class="text-caption text-label-tertiary">业务目标</dt>
              <dd class="mt-1 whitespace-pre-wrap text-caption text-label">{{ selectedInitiative.businessGoal }}</dd>
            </div>
            <div v-if="selectedInitiative.scopeText">
              <dt class="text-caption text-label-tertiary">范围</dt>
              <dd class="mt-1 whitespace-pre-wrap text-caption text-label-secondary">{{ selectedInitiative.scopeText }}</dd>
            </div>
            <div v-if="selectedInitiative.outOfScopeText">
              <dt class="text-caption text-label-tertiary">非目标</dt>
              <dd class="mt-1 whitespace-pre-wrap text-caption text-label-secondary">{{ selectedInitiative.outOfScopeText }}</dd>
            </div>
            <div v-if="selectedInitiative.successMetrics">
              <dt class="text-caption text-label-tertiary">成功指标</dt>
              <dd class="mt-1 whitespace-pre-wrap text-caption text-label-secondary">{{ selectedInitiative.successMetrics }}</dd>
            </div>
            <div v-if="selectedInitiative.stakeholders">
              <dt class="text-caption text-label-tertiary">关键干系人</dt>
              <dd class="mt-1 whitespace-pre-wrap text-caption text-label-secondary">{{ selectedInitiative.stakeholders }}</dd>
            </div>
            <div v-if="selectedInitiative.riskText">
              <dt class="text-caption text-label-tertiary">主要风险</dt>
              <dd class="mt-1 whitespace-pre-wrap text-caption text-label-secondary">{{ selectedInitiative.riskText }}</dd>
            </div>
          </dl>

          <div
            v-if="selectedInitiative.meetingMinutes || initiativeAttachments.length > 0"
            class="mt-5 border-t border-separator pt-3"
          >
            <p class="text-caption font-emphasis text-label-secondary">会议纪要</p>
            <p
              v-if="selectedInitiative.meetingMinutes"
              class="mt-2 whitespace-pre-wrap rounded-control bg-window p-2.5 text-caption text-label-secondary"
            >
              {{ selectedInitiative.meetingMinutes }}
            </p>
            <ul v-if="initiativeAttachments.length > 0" class="mt-2 space-y-1.5">
              <li
                v-for="attachment in initiativeAttachments"
                :key="attachment.id"
                class="flex items-center gap-2 rounded-control border border-separator-soft bg-content px-2.5 py-2"
              >
                <Icon :as="Paperclip" size="inline" class="text-label-tertiary" />
                <a
                  :href="attachment.contentUrl"
                  target="_blank"
                  rel="noreferrer"
                  class="min-w-0 flex-1 truncate text-caption text-accent hover:underline"
                >
                  {{ attachment.filename }}
                </a>
                <span class="shrink-0 text-[10px] text-label-tertiary">
                  {{ formatBytes(attachment.sizeBytes) }}
                </span>
              </li>
            </ul>
            <p
              v-else-if="initiativeAttachmentsLoading"
              class="mt-2 text-caption text-label-tertiary"
            >
              正在读取附件…
            </p>
          </div>

          <div class="mt-5 border-t border-separator pt-3">
            <div class="flex items-center gap-2">
              <p class="text-caption font-emphasis text-label-secondary">已关联需求</p>
              <span class="tnum text-caption text-label-tertiary">
                {{ selectedInitiativeRequirements.length }}
              </span>
            </div>
            <p
              v-if="selectedInitiativeRequirements.length === 0"
              class="mt-2 text-caption text-label-tertiary"
            >
              还没有需求。版本立项批准后，请到需求池录入并纳入当前版本。
            </p>
            <div v-else class="mt-2 space-y-1.5">
              <button
                v-for="requirement in selectedInitiativeRequirements"
                :key="requirement.id"
                type="button"
                class="flex w-full items-center gap-2 rounded-control border border-separator-soft bg-content px-2.5 py-2 text-left transition-colors hover:bg-hover"
                @click="openDeliveryForRequirement(requirement)"
              >
                <span class="tnum text-caption text-label-tertiary">
                  #{{ formatRequirementNo(requirement.requirementNo) }}
                </span>
                <span class="min-w-0 flex-1 truncate text-caption text-label">{{ requirement.title }}</span>
                <Badge
                  v-if="deliveryForRequirement(requirement.id)"
                  :tone="deliveryTone(deliveryForRequirement(requirement.id)!.stage)"
                >
                  {{ deliveryStageLabels[deliveryForRequirement(requirement.id)!.stage] }}
                </Badge>
                <span v-else class="text-caption text-label-tertiary">待建立交付</span>
              </button>
            </div>
          </div>

          <div class="mt-5 flex flex-wrap gap-2">
            <Button
              v-if="(selectedInitiative.status === 'DRAFT' || selectedInitiative.status === 'REJECTED')
                && canManageVersionInitiative"
              size="sm"
              @click="openEditInitiative(selectedInitiative)"
            >
              <Icon :as="Pencil" size="inline" />编辑
            </Button>
            <Button
              v-if="(selectedInitiative.status === 'DRAFT' || selectedInitiative.status === 'REJECTED')
                && canManageVersionInitiative"
              variant="primary"
              size="sm"
              :disabled="!selectedInitiative.meetingMinutes && initiativeAttachments.length === 0"
              @click="submitInitiativeForApproval(selectedInitiative)"
            >
              提交审批
            </Button>
            <Button
              v-if="selectedInitiative.status === 'SUBMITTED'
                && canRole(selectedInitiative.projectId, 'OWNER')"
              variant="primary"
              size="sm"
              @click="openInitiativeReview(selectedInitiative, 'approve')"
            >
              审批版本立项
            </Button>
            <Button
              v-if="selectedInitiative.status === 'SUBMITTED'
                && canRole(selectedInitiative.projectId, 'OWNER')"
              size="sm"
              @click="openInitiativeReview(selectedInitiative, 'reject')"
            >
              驳回申请
            </Button>
            <Button
              v-if="selectedInitiative.status === 'APPROVED'"
              variant="primary"
              size="sm"
              @click="openCreateRequirement(selectedInitiative)"
            >
              去需求池录入
            </Button>
          </div>
          <p
            v-if="selectedInitiative.status === 'SUBMITTED'
              && !canRole(selectedInitiative.projectId, 'OWNER')"
            class="mt-3 rounded-control bg-window px-2.5 py-2 text-caption text-label-tertiary"
          >
            等待项目负责人审批。你当前的角色：{{ roleSummaryForProject(selectedInitiative.projectId) }}。
          </p>
          <p class="mt-3 text-caption text-label-tertiary">
            创建于 {{ formatDateTime(selectedInitiative.createdAt) }}
            <template v-if="selectedInitiative.approvedAt">
              · 批准于 {{ formatDateTime(selectedInitiative.approvedAt) }}
            </template>
          </p>
        </div>
      </template>

      <template v-else-if="mode === 'releases' && selectedRelease">
        <div class="p-4">
          <button
            type="button"
            class="mb-3 inline-flex items-center gap-0.5 text-caption text-accent 2xl:hidden"
            @click="selectedReleaseId = null"
          >
            <Icon :as="ChevronLeft" size="inline" />返回列表
          </button>
          <div class="flex items-start gap-2">
            <div class="min-w-0 flex-1">
              <p class="text-caption text-label-tertiary">发布 #{{ selectedRelease.id }}</p>
              <h2 class="mt-1 text-title font-emphasis text-label">
                {{ selectedRelease.version }} · 第 {{ selectedRelease.attemptNo }} 轮
              </h2>
              <p class="mt-1 text-caption text-label-secondary">{{ selectedRelease.projectName }}</p>
            </div>
            <Badge :tone="releaseTone(selectedRelease.stage)">
              {{ releaseStageLabels[selectedRelease.stage] }}
            </Badge>
          </div>

          <div
            v-if="selectedReleaseNext"
            class="mt-4 rounded-panel border border-accent/20 bg-accent/5 p-3"
          >
            <div class="flex flex-wrap items-center gap-2">
              <p class="text-caption font-emphasis text-label">
                下一步：{{ selectedReleaseNext.label }}
              </p>
              <span
                v-if="selectedReleaseNext.role
                  && canRole(selectedRelease.projectId, selectedReleaseNext.role)"
                class="rounded-badge bg-accent px-1.5 py-px text-[10px] text-white"
              >
                需要你处理
              </span>
              <span
                v-else-if="selectedReleaseNext.role"
                class="rounded-badge bg-window px-1.5 py-px text-[10px] text-label-secondary"
              >
                等待{{ roleLabel(selectedReleaseNext.role) }}
              </span>
            </div>
            <p class="mt-1 text-caption text-label-tertiary">{{ selectedReleaseNext.hint }}</p>
          </div>

          <div class="mt-4 rounded-panel border border-separator-soft bg-content p-3">
            <div class="flex items-center gap-2">
              <Icon :as="GitBranch" size="inline" class="text-label-tertiary" />
              <p class="text-caption font-emphasis text-label-secondary">构建制品</p>
              <span class="ml-auto font-mono text-caption text-label-tertiary">
                {{ selectedRelease.artifact.gitCommit.slice(0, 12) }}
              </span>
            </div>
            <p class="mt-2 break-all font-mono text-caption text-label-secondary">
              SHA-256：{{ selectedRelease.artifact.digest || '未登记' }}
            </p>
            <p class="mt-1 text-caption text-label-tertiary">
              {{ selectedRelease.artifact.sizeBytes > 0
                ? `${(selectedRelease.artifact.sizeBytes / 1024 / 1024).toFixed(1)} MB`
                : '未登记大小' }}
              · 构建于 {{ formatDateTime(selectedRelease.artifact.builtAt) }}
            </p>
          </div>

          <div class="mt-5 border-t border-separator pt-3">
            <p class="text-caption font-emphasis text-label-secondary">发布阶段</p>
            <DeliveryPipeline
              class="mt-3"
              :steps="releaseSteps"
              :current="selectedRelease.stage"
              :cancelled="selectedRelease.status === 'CANCELLED'"
            />
          </div>

          <div class="mt-5 rounded-panel border border-separator-soft bg-content p-3">
            <template v-if="selectedRelease.stage === 'TRANSFER'">
              <p class="text-caption font-emphasis text-label-secondary">转测准入</p>
              <p class="mt-1 text-caption text-label-tertiary">
                审批角色：测试负责人。版本、制品和需求范围确认后，批准进入集成测试。
              </p>
              <Button
                v-if="canRole(selectedRelease.projectId, 'QA')"
                class="mt-2"
                variant="primary"
                size="sm"
                @click="releaseAction('APPROVE_TRANSFER')"
              >
                批准转测
              </Button>
              <p v-else class="mt-2 text-caption text-label-tertiary">
                等待测试负责人批准转测。你当前的角色：{{ roleSummaryForProject(selectedRelease.projectId) }}。
              </p>
            </template>
            <template v-else-if="selectedRelease.stage === 'INTEGRATION'">
              <p class="text-caption font-emphasis text-label-secondary">集成测试</p>
              <p class="mt-1 text-caption text-label-tertiary">审批角色：测试负责人</p>
              <template v-if="canRole(selectedRelease.projectId, 'QA')">
                <Textarea v-model="releaseActionComment" class="mt-2" :rows="2" placeholder="集成测试结论（可选）" />
                <Button class="mt-2" variant="primary" size="sm" @click="releaseAction('PASS_INTEGRATION')">
                  集成测试通过
                </Button>
              </template>
              <p v-else class="mt-2 text-caption text-label-tertiary">
                等待测试负责人完成集成测试。你当前的角色：{{ roleSummaryForProject(selectedRelease.projectId) }}。
              </p>
            </template>
            <template v-else-if="selectedRelease.stage === 'REGRESSION'">
              <p class="text-caption font-emphasis text-label-secondary">版本回归</p>
              <p class="mt-1 text-caption text-label-tertiary">审批角色：测试负责人</p>
              <template v-if="canRole(selectedRelease.projectId, 'QA')">
                <Textarea v-model="releaseActionComment" class="mt-2" :rows="2" placeholder="回归结论（可选）" />
                <Button class="mt-2" variant="primary" size="sm" @click="releaseAction('PASS_REGRESSION')">
                  回归通过
                </Button>
              </template>
              <p v-else class="mt-2 text-caption text-label-tertiary">
                等待测试负责人完成版本回归。你当前的角色：{{ roleSummaryForProject(selectedRelease.projectId) }}。
              </p>
            </template>
            <template v-else-if="selectedRelease.stage === 'RELEASE_REVIEW'">
              <p class="text-caption font-emphasis text-label-secondary">发版评审</p>
              <p class="mt-1 text-caption text-label-tertiary">审批角色：发布负责人</p>
              <template v-if="canRole(selectedRelease.projectId, 'RELEASE_MANAGER')">
                <Textarea v-model="releaseActionComment" class="mt-2" :rows="2" placeholder="发版评审意见（可选）" />
                <Button class="mt-2" variant="primary" size="sm" @click="releaseAction('APPROVE_RELEASE')">
                  批准发版
                </Button>
              </template>
              <p v-else class="mt-2 text-caption text-label-tertiary">
                等待发布负责人评审。你当前的角色：{{ roleSummaryForProject(selectedRelease.projectId) }}。
              </p>
            </template>
            <template v-else-if="selectedRelease.stage === 'PRODUCTION'">
              <p class="text-caption font-emphasis text-label-secondary">生产发布</p>
              <p class="mt-1 text-caption text-label-tertiary">
                审批角色：发布负责人。同一制品完成生产部署并经过发布后冒烟后，确认进入观察期。
              </p>
              <Button
                v-if="canRole(selectedRelease.projectId, 'RELEASE_MANAGER')"
                class="mt-2"
                variant="primary"
                size="sm"
                @click="releaseAction('MARK_DEPLOYED')"
              >
                确认生产部署完成
              </Button>
              <p v-else class="mt-2 text-caption text-label-tertiary">
                等待发布负责人确认生产部署。你当前的角色：{{ roleSummaryForProject(selectedRelease.projectId) }}。
              </p>
            </template>
            <template v-else-if="selectedRelease.stage === 'OBSERVING'">
              <p class="text-caption font-emphasis text-label-secondary">发布观察</p>
              <p class="mt-1 text-caption text-label-tertiary">
                审批角色：项目负责人。观察期没有异常后关闭发布，并关闭关联需求交付。
              </p>
              <Button
                v-if="canRole(selectedRelease.projectId, 'OWNER')"
                class="mt-2"
                variant="primary"
                size="sm"
                @click="releaseAction('CLOSE')"
              >
                关闭发布
              </Button>
              <p v-else class="mt-2 text-caption text-label-tertiary">
                等待项目负责人关闭发布。你当前的角色：{{ roleSummaryForProject(selectedRelease.projectId) }}。
              </p>
            </template>
            <template v-else>
              <p class="text-caption font-emphasis text-success">发布已完成</p>
            </template>
          </div>

          <div class="mt-4">
            <p class="text-caption font-emphasis text-label-secondary">包含需求</p>
            <ul class="mt-2 space-y-1">
              <li
                v-for="delivery in selectedRelease.deliveries"
                :key="delivery.id"
                class="rounded-control border border-separator-soft bg-content px-2.5 py-1.5"
              >
                <div class="flex items-center gap-2">
                  <span class="tnum text-caption text-label-tertiary">#{{ delivery.id }}</span>
                  <span class="min-w-0 flex-1 truncate text-caption text-label">{{ delivery.requirementTitle }}</span>
                  <Badge :tone="deliveryTone(delivery.stage)">
                    {{ deliveryStageLabels[delivery.stage] }}
                  </Badge>
                </div>
              </li>
            </ul>
          </div>

          <div v-if="selectedRelease.releaseNotes" class="mt-4">
            <p class="text-caption font-emphasis text-label-secondary">发布说明</p>
            <p class="mt-1 whitespace-pre-wrap text-caption text-label-secondary">{{ selectedRelease.releaseNotes }}</p>
          </div>
          <div class="mt-4">
            <p class="text-caption font-emphasis text-label-secondary">回滚方案</p>
            <p class="mt-1 whitespace-pre-wrap text-caption text-label-secondary">{{ selectedRelease.rollbackPlan }}</p>
          </div>

          <div class="mt-5 border-t border-separator pt-3">
            <p class="text-caption font-emphasis text-label-secondary">门禁记录</p>
            <ul v-if="selectedRelease.gates.length > 0" class="mt-2 space-y-1.5">
              <li
                v-for="gate in selectedRelease.gates"
                :key="gate.id"
                class="rounded-control border border-separator-soft bg-content px-2.5 py-1.5"
              >
                <div class="flex items-center gap-2">
                  <span class="text-caption text-label">{{ gate.type }}</span>
                  <span class="text-caption text-label-tertiary">
                    · {{ memberNameForProject(selectedRelease.projectId, gate.decidedBy) }}
                    · {{ roleLabel(gate.roleCode) }}
                  </span>
                  <Badge :tone="gate.decision === 'PASSED' ? 'success' : 'danger'">
                    {{ gate.decision === 'PASSED' ? '通过' : '驳回' }}
                  </Badge>
                  <span class="ml-auto text-caption text-label-tertiary">{{ formatRelative(gate.decidedAt) }}</span>
                </div>
                <p v-if="gate.comment" class="mt-1 text-caption text-label-tertiary">{{ gate.comment }}</p>
              </li>
            </ul>
            <p v-else class="mt-2 text-caption text-label-tertiary">还没有发布门禁记录。</p>
          </div>
        </div>
      </template>
    </aside>
  </div>

  <Dialog
    v-model:open="initiativeOpen"
    layout="form"
    size="large"
    :title="initiativeEditingId ? '编辑版本立项' : '发起版本立项'"
    description="只需填写或上传会议纪要，用于记录本版本的立项决策和审批依据。"
    :icon="FileText"
  >
    <div class="flex flex-col gap-3">
      <div class="rounded-panel border border-separator-soft bg-content px-3 py-2.5">
        <p class="text-[10px] text-label-tertiary">立项归属</p>
        <p class="mt-1 text-caption text-label">
          {{ store.projects.find((project) => String(project.id) === initiativeProjectId)?.name }}
          <span class="mx-1 text-label-tertiary">/</span>
          <span class="font-mono font-emphasis">{{ selectedVersion?.versionNo }}</span>
        </p>
      </div>
      <DialogField
        label="会议纪要"
        hint="没有固定模板；记录会议结论、版本范围、风险和待办即可"
      >
        <Textarea
          v-model="initiativeMinutes"
          :rows="8"
          placeholder="例如：2026-10-24 版本立项评审，参与人……；结论：同意启动本版本；范围……"
        />
      </DialogField>
      <DialogField label="纪要附件" hint="可不上传；正文和附件至少提供一项后再提交审批">
        <input
          ref="initiativeFileInput"
          type="file"
          class="hidden"
          multiple
          accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.odt,.ods,.odp,.txt,.md,.csv,.json,.yaml,.yml,.xml,.rtf,image/png,image/jpeg,image/gif,image/webp"
          @change="onInitiativeFiles"
        />
        <div class="flex flex-wrap items-center gap-2">
          <Button size="sm" @click="initiativeFileInput?.click()">
            <Icon :as="Paperclip" size="inline" />选择纪要附件
          </Button>
          <span class="text-[10px] text-label-tertiary">
            PDF、Office、文本和图片，单份不超过 25MB
          </span>
        </div>
        <ul
          v-if="initiativeAttachments.length > 0 || initiativeComposerFiles.length > 0"
          class="mt-2 space-y-1.5"
        >
          <li
            v-for="attachment in initiativeAttachments"
            :key="`saved-${attachment.id}`"
            class="flex items-center gap-2 rounded-control border border-separator-soft bg-content px-2.5 py-2"
          >
            <Icon :as="Paperclip" size="inline" class="text-label-tertiary" />
            <span class="min-w-0 flex-1 truncate text-caption text-label">{{ attachment.filename }}</span>
            <span class="text-[10px] text-label-tertiary">{{ formatBytes(attachment.sizeBytes) }}</span>
          </li>
          <li
            v-for="(file, index) in initiativeComposerFiles"
            :key="`pending-${index}-${file.name}`"
            class="flex items-center gap-2 rounded-control border border-accent/20 bg-accent/5 px-2.5 py-2"
          >
            <Icon :as="Paperclip" size="inline" class="text-accent" />
            <span class="min-w-0 flex-1 truncate text-caption text-label">{{ file.name }}</span>
            <span class="text-[10px] text-label-tertiary">{{ formatBytes(file.size) }}</span>
            <button
              type="button"
              class="text-caption text-danger"
              @click="removeInitiativeFile(index)"
            >
              移除
            </button>
          </li>
        </ul>
      </DialogField>
      <p v-if="initiativeError" class="text-caption text-danger">{{ initiativeError }}</p>
    </div>
    <template #footer>
      <Button :disabled="initiativeSaving" @click="initiativeOpen = false">取消</Button>
      <Button
        :disabled="initiativeSaving || !selectedVersion || !initiativeVersionId"
        @click="saveInitiative(false)"
      >
        {{ initiativeSaving ? '保存中…' : '保存草稿' }}
      </Button>
      <Button
        variant="primary"
        :disabled="!canSubmitInitiativeComposer || !selectedVersion || !initiativeVersionId"
        @click="saveInitiative(true)"
      >
        {{ initiativeUploading ? '上传中…' : initiativeSaving ? '提交中…' : '保存并提交审批' }}
      </Button>
    </template>
  </Dialog>

  <Dialog
    v-model:open="reviewOpen"
    layout="form"
    size="large"
    :title="reviewMode === 'approve' ? '审批版本立项' : '驳回版本立项'"
    :description="reviewMode === 'approve'
      ? '会议纪要已在发起立项时提交，本环节只记录审批意见。'
      : '会议纪要已在发起立项时提交，本环节填写驳回原因。'"
    :icon="FileText"
  >
    <div v-if="reviewInitiative" class="flex flex-col gap-3">
      <div class="rounded-panel border border-separator-soft bg-window px-3 py-2.5">
        <p class="text-caption font-emphasis text-label">{{ reviewInitiative.title }}</p>
        <p class="mt-0.5 text-[10px] text-label-tertiary">
          {{ reviewInitiative.projectName }} · 版本立项 #{{ reviewInitiative.id }}
        </p>
        <p class="mt-1 line-clamp-2 text-caption text-label-secondary">
          {{ reviewInitiative.businessGoal }}
        </p>
      </div>

      <div class="rounded-panel border border-separator-soft bg-content p-3">
        <p class="text-caption font-emphasis text-label-secondary">发起立项时提交的会议纪要</p>
        <p
          v-if="reviewInitiative.meetingMinutes"
          class="mt-2 max-h-48 overflow-y-auto whitespace-pre-wrap rounded-control bg-window p-2.5 text-caption text-label-secondary"
        >
          {{ reviewInitiative.meetingMinutes }}
        </p>
        <ul v-if="initiativeAttachments.length > 0" class="mt-2 space-y-1.5">
          <li
            v-for="attachment in initiativeAttachments"
            :key="attachment.id"
            class="flex items-center gap-2 rounded-control border border-separator-soft bg-content px-2.5 py-2"
          >
            <Icon :as="Paperclip" size="inline" class="text-label-tertiary" />
            <a
              :href="attachment.contentUrl"
              target="_blank"
              rel="noreferrer"
              class="min-w-0 flex-1 truncate text-caption text-accent hover:underline"
            >
              {{ attachment.filename }}
            </a>
            <span class="shrink-0 text-[10px] text-label-tertiary">
              {{ formatBytes(attachment.sizeBytes) }}
            </span>
          </li>
        </ul>
        <p
          v-else-if="!reviewInitiative.meetingMinutes && !initiativeAttachmentsLoading"
          class="mt-2 text-caption text-label-tertiary"
        >
          该历史立项未记录会议纪要。
        </p>
        <p
          v-else-if="initiativeAttachmentsLoading"
          class="mt-2 text-caption text-label-tertiary"
        >
          正在读取附件…
        </p>
      </div>

      <DialogField
        label="审批意见"
        :required="reviewMode === 'reject'"
        :hint="reviewMode === 'reject' ? '驳回时必须填写原因' : '可选'"
      >
        <Textarea
          v-model="reviewComment"
          :rows="3"
          :placeholder="reviewMode === 'approve' ? '补充审批意见' : '说明驳回原因和需要补充的内容'"
        />
      </DialogField>

      <p v-if="reviewError" class="text-caption text-danger">{{ reviewError }}</p>
    </div>
    <template #footer>
      <Button :disabled="reviewSaving" @click="reviewOpen = false">取消</Button>
      <Button
        :variant="reviewMode === 'approve' ? 'primary' : 'danger'"
        :disabled="!canSubmitInitiativeReview"
        @click="submitInitiativeReview"
      >
        {{ reviewSaving
          ? '提交中…'
          : reviewMode === 'approve'
            ? '同意版本立项'
            : '确认驳回' }}
      </Button>
    </template>
  </Dialog>

  <Dialog
    v-model:open="reviewRecordOpen"
    layout="form"
    size="large"
    title="提交需求评审"
    description="提交线下评审形成的会议纪要和附件；项目负责人审核通过后，需求才会正式化并进入详细设计。"
    :icon="FileText"
  >
    <div class="flex flex-col gap-3">
      <div class="rounded-panel border border-accent/20 bg-accent/5 px-3 py-2.5 text-caption text-label-secondary">
        本次将提交 {{ pendingReviewRequirements.length }} 条需求的评审材料。
      </div>
      <DialogField label="评审日期" required>
        <Input v-model="reviewDate" type="date" :max="localDateInputValue()" />
      </DialogField>
      <div class="rounded-panel border border-success/20 bg-success/5 px-3 py-2.5 text-caption text-label-secondary">
        提交后需求进入评审中，等待项目负责人查看材料并给出审核结论。
      </div>
      <DialogField label="会议纪要">
        <Textarea
          v-model="requirementReviewMinutes"
          :rows="6"
          placeholder="记录评审范围、讨论结论、待办和风险；也可只上传纪要文件"
        />
      </DialogField>
      <DialogField label="纪要附件">
        <input
          ref="requirementReviewFileInput"
          type="file"
          class="hidden"
          multiple
          accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.odt,.ods,.odp,.txt,.md,.csv,.json,.yaml,.yml,.xml,.rtf,image/png,image/jpeg,image/gif,image/webp"
          @change="onRequirementReviewFiles"
        />
        <Button size="sm" @click="requirementReviewFileInput?.click()">
          <Icon :as="Paperclip" size="inline" />上传会议纪要
        </Button>
        <ul v-if="reviewFiles.length > 0" class="mt-2 space-y-1.5">
          <li
            v-for="(file, index) in reviewFiles"
            :key="`${index}-${file.name}`"
            class="flex items-center gap-2 rounded-control border border-separator-soft bg-content px-2.5 py-2"
          >
            <Icon :as="Paperclip" size="inline" class="text-label-tertiary" />
            <span class="min-w-0 flex-1 truncate text-caption text-label">{{ file.name }}</span>
            <span class="text-[10px] text-label-tertiary">{{ formatBytes(file.size) }}</span>
            <button
              type="button"
              class="text-caption text-danger"
              @click="removeRequirementReviewFile(index)"
            >
              移除
            </button>
          </li>
        </ul>
      </DialogField>
      <p class="text-caption text-label-tertiary">
        会议纪要正文和附件至少填写或上传一项。
      </p>
      <p v-if="reviewRecordError" class="text-caption text-danger">
        {{ reviewRecordError }}
      </p>
    </div>
    <template #footer>
      <Button :disabled="reviewRecordSaving" @click="reviewRecordOpen = false">
        取消
      </Button>
      <Button
        variant="primary"
        :disabled="!canSubmitReviewRecord"
        @click="saveReviewRecord"
      >
        {{ reviewRecordSaving ? '提交中…' : '提交评审' }}
      </Button>
    </template>
  </Dialog>

  <Dialog
    v-model:open="reviewDecisionOpen"
    layout="form"
    size="large"
    title="审核需求评审"
    description="查看产品负责人提交的评审材料和需求范围，填写审核意见后通过或驳回。"
    :icon="FileText"
  >
    <div v-if="reviewDecisionRecord" class="flex flex-col gap-3">
      <div class="rounded-panel border border-separator-soft bg-window px-3 py-2.5">
        <div class="flex flex-wrap items-center gap-2">
          <p class="text-caption font-emphasis text-label">
            第 {{ reviewDecisionRecord.reviewNo }} 次需求评审
          </p>
          <Badge tone="accent">待审核</Badge>
          <span class="text-caption text-label-tertiary">{{ reviewDecisionRecord.reviewDate }}</span>
          <span class="text-caption text-label-tertiary">
            提交人 {{ memberNameForProject(
              selectedVersion?.projectId,
              reviewDecisionRecord.submittedBy,
            ) }}
          </span>
        </div>
        <div class="mt-2 space-y-1">
          <div
            v-for="requirement in reviewDecisionRecord.requirements"
            :key="requirement.requirementId"
            class="flex items-center gap-2 text-caption"
          >
            <span class="tnum shrink-0 font-mono text-label-tertiary">
              #{{ formatRequirementNo(requirement.requirementNo) }}
            </span>
            <span class="min-w-0 flex-1 truncate text-label">{{ requirement.title }}</span>
          </div>
        </div>
      </div>

      <div class="rounded-panel border border-separator-soft bg-content p-3">
        <p class="text-caption font-emphasis text-label-secondary">评审材料</p>
        <p
          v-if="reviewDecisionRecord.minutes"
          class="mt-2 max-h-48 overflow-y-auto whitespace-pre-wrap rounded-control bg-window p-2.5 text-caption text-label-secondary"
        >
          {{ reviewDecisionRecord.minutes }}
        </p>
        <div v-if="reviewDecisionRecord.attachments.length > 0" class="mt-2 space-y-2">
          <div
            v-for="attachment in reviewDecisionRecord.attachments"
            :key="attachment.id"
            class="flex min-w-0 flex-wrap items-center gap-2 rounded-control border border-separator-soft bg-window px-2.5 py-2"
          >
            <Icon :as="Paperclip" size="inline" class="shrink-0 text-label-tertiary" />
            <div class="min-w-0 flex-1">
              <p class="truncate text-caption font-emphasis text-label">
                {{ attachment.filename }}
              </p>
              <p class="mt-0.5 text-[10px] text-label-tertiary">
                {{ formatBytes(attachment.sizeBytes) }}
              </p>
            </div>
            <a
              :href="attachment.contentUrl"
              target="_blank"
              rel="noreferrer"
              class="inline-flex items-center gap-1 rounded-control px-2 py-1 text-caption text-accent hover:bg-accent/5"
            >
              <Icon :as="Eye" size="inline" />查看
            </a>
            <a
              :href="reviewAttachmentDownloadUrl(attachment.contentUrl)"
              :download="attachment.filename"
              class="inline-flex items-center gap-1 rounded-control px-2 py-1 text-caption text-accent hover:bg-accent/5"
            >
              <Icon :as="Download" size="inline" />下载
            </a>
          </div>
        </div>
        <p
          v-if="!reviewDecisionRecord.minutes && reviewDecisionRecord.attachments.length === 0"
          class="mt-2 text-caption text-warning"
        >
          本次评审没有提交会议纪要或附件。
        </p>
        <p
          v-else-if="reviewDecisionRecord.attachments.length === 0"
          class="mt-2 text-caption text-label-tertiary"
        >
          本次未提交附件，仅包含会议纪要正文。
        </p>
      </div>

      <DialogField
        label="审核意见"
        :required="false"
        hint="驳回时必须填写；通过时可选填"
        stacked
      >
        <Textarea
          v-model="reviewDecisionComment"
          :rows="4"
          placeholder="填写对评审材料、需求范围或验收标准的审核意见"
        />
        <div class="mt-2 flex flex-wrap items-center gap-1.5">
          <span class="mr-0.5 text-caption text-label-tertiary">快捷意见</span>
          <button
            v-for="phrase in reviewDecisionQuickPhrases"
            :key="phrase.text"
            type="button"
            class="rounded-control border px-2 py-1 text-caption transition-colors"
            :class="
              phrase.tone === 'approve'
                ? 'border-success/25 bg-success/5 text-success hover:bg-success/10'
                : 'border-danger/20 bg-danger/5 text-danger hover:bg-danger/10'
            "
            @click="applyReviewDecisionPhrase(phrase.text)"
          >
            {{ phrase.text }}
          </button>
        </div>
      </DialogField>

      <p v-if="reviewDecisionError" class="text-caption text-danger">
        {{ reviewDecisionError }}
      </p>
    </div>
    <template #footer>
      <Button :disabled="reviewDecisionSaving" @click="reviewDecisionOpen = false">
        取消
      </Button>
      <Button
        variant="danger"
        :disabled="reviewDecisionSaving || !reviewDecisionComment.trim()"
        @click="decideReview('REJECT')"
      >
        {{ reviewDecisionSaving ? '提交中…' : '驳回' }}
      </Button>
      <Button
        variant="primary"
        :disabled="reviewDecisionSaving"
        @click="decideReview('APPROVE')"
      >
        {{ reviewDecisionSaving ? '提交中…' : '审核通过' }}
      </Button>
    </template>
  </Dialog>

  <Dialog
    v-model:open="releaseOpen"
    layout="form"
    size="large"
    title="新建发布"
    description="发布绑定不可变制品；转测、集成和生产必须使用同一份制品。"
    :icon="Play"
  >
    <div class="flex flex-col gap-3">
      <DialogField label="所属项目" required>
        <Select v-model="releaseProjectId" :items="releaseProjectItems" class="w-full" />
      </DialogField>
      <div class="grid gap-3 sm:grid-cols-2">
        <DialogField label="版本" required>
          <Input v-model="releaseVersion" placeholder="v1.7.0" />
        </DialogField>
        <DialogField label="Git 提交" required>
          <Input v-model="releaseCommit" placeholder="a1b2c3d" />
        </DialogField>
      </div>
      <DialogField label="制品摘要" hint="SHA-256；用于确认所有环境部署的是同一份制品">
        <Input v-model="releaseDigest" placeholder="sha256:..." />
      </DialogField>
      <div class="grid gap-3 sm:grid-cols-2">
        <DialogField label="制品大小">
          <Input v-model="releaseSize" type="number" placeholder="字节数，可留空" />
        </DialogField>
        <DialogField label="存储引用">
          <Input v-model="releaseStorageRef" placeholder="构建产物路径或地址" />
        </DialogField>
      </div>
      <DialogField label="纳入需求" required hint="只展示已经达到待转测阶段的需求">
        <div v-if="readyDeliveries.length > 0" class="max-h-48 space-y-1 overflow-y-auto">
          <label
            v-for="delivery in readyDeliveries"
            :key="delivery.id"
            class="flex items-center gap-2 rounded-control border border-separator-soft px-2.5 py-2"
          >
            <Checkbox
              :model-value="releaseDeliveryIds.includes(delivery.id)"
              @update:model-value="toggleReleaseDelivery(delivery.id, $event === true)"
            />
            <span class="tnum text-caption text-label-tertiary">#{{ delivery.id }}</span>
            <span class="min-w-0 flex-1 truncate text-caption text-label">{{ delivery.requirementTitle }}</span>
          </label>
        </div>
        <p v-else class="text-caption text-warning">当前项目没有待转测的需求交付。</p>
      </DialogField>
      <DialogField label="发布说明">
        <Textarea v-model="releaseNotes" :rows="2" placeholder="本次发布包含什么" />
      </DialogField>
      <DialogField label="回滚方案" required>
        <Textarea v-model="releaseRollbackPlan" :rows="2" placeholder="失败时如何回滚到上一份制品" />
      </DialogField>
      <p v-if="releaseError" class="text-caption text-danger">{{ releaseError }}</p>
    </div>
    <template #footer>
      <Button :disabled="releaseSaving" @click="releaseOpen = false">取消</Button>
      <Button
        variant="primary"
        :disabled="releaseSaving || releaseDeliveryIds.length === 0"
        @click="saveRelease"
      >
        {{ releaseSaving ? '创建中…' : '创建发布' }}
      </Button>
    </template>
  </Dialog>
</template>
