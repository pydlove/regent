<script setup lang="ts">
import {
  ChevronLeft,
  Download,
  Pencil,
  Plus,
  RefreshCw,
  RotateCcw,
  Search,
  Sparkles,
  Trash2,
  Upload,
  X,
} from '@lucide/vue'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { listInitiatives } from '@/api/delivery'
import { ApiError } from '@/api/http'
import { listProjectMembers } from '@/api/projectMembers'
import { listProductVersions } from '@/api/productVersions'
import {
  assignRequirementVersion,
  createRequirement,
  deleteRequirement,
  deleteRequirements,
  listRequirements,
  listRequirementTrash,
  purgeRequirements,
  restoreRequirement,
  type RequirementTrashItem,
} from '@/api/requirements'
import RequirementPoolEditorDialog from '@/components/business/RequirementPoolEditorDialog.vue'
import Badge from '@/components/ui/badge/Badge.vue'
import Button from '@/components/ui/button/Button.vue'
import Checkbox from '@/components/ui/checkbox/Checkbox.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import DialogField from '@/components/ui/dialog/DialogField.vue'
import EmptyState from '@/components/ui/empty-state/EmptyState.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import IconButton from '@/components/ui/icon-button/IconButton.vue'
import Input from '@/components/ui/input/Input.vue'
import ListToolbar from '@/components/ui/list-toolbar/ListToolbar.vue'
import Select from '@/components/ui/select/Select.vue'
import SegmentedControl from '@/components/ui/segmented-control/SegmentedControl.vue'
import Table from '@/components/ui/table/Table.vue'
import type { TableColumn } from '@/components/ui/table/table.types'
import Tooltip from '@/components/ui/tooltip/Tooltip.vue'
import { useToast } from '@/composables/useToast'
import { useAuthStore } from '@/stores/useAuthStore'
import { useTaskStore } from '@/stores/useTaskStore'
import type { Requirement } from '@/types/api'
import type { Initiative, ProductVersion } from '@/types/delivery'
import type { ProjectMember } from '@/types/projectMember'
import { canActWithRoles } from '@/utils/deliveryWorkflow'
import { formatRelative } from '@/utils/format'
import { findDuplicateImportRows } from '@/utils/requirementImport'
import {
  formatRequirementNo,
  requirementDisplayStatus,
  type RequirementDisplayStatus,
} from '@/utils/requirementPool'

type ImportRow = {
  title: string
  rawText: string
  acceptanceText: string
  clarificationText: string
  error: string | null
}

const store = useTaskStore()
const auth = useAuthStore()
const toast = useToast()
const route = useRoute()

const requirements = ref<Requirement[]>([])
const trash = ref<RequirementTrashItem[]>([])
const versions = ref<ProductVersion[]>([])
const allInitiatives = ref<Initiative[]>([])
const members = ref<ProjectMember[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const keyword = ref('')
const statusFilter = ref<'ALL' | RequirementDisplayStatus>('ALL')
const viewMode = ref('active')
const selectedRequirementId = ref<number | null>(null)

const editorOpen = ref(false)
const editorMode = ref<'manual' | 'ai'>('manual')
const editingRequirement = ref<Requirement | null>(null)

const assignOpen = ref(false)
const assignSaving = ref(false)
const assignTarget = ref<Requirement | null>(null)
const assignVersionId = ref('')
const assignError = ref<string | null>(null)

const selectedRequirementIds = ref<number[]>([])
const batchAssignOpen = ref(false)
const batchAssignSaving = ref(false)
const batchAssignVersionId = ref('')
const batchAssignError = ref<string | null>(null)
const batchDeleteOpen = ref(false)
const batchDeleteSaving = ref(false)
const batchDeleteError = ref<string | null>(null)
const purgeOpen = ref(false)
const purgeSaving = ref(false)
const purgeError = ref<string | null>(null)
const purgeTargets = ref<RequirementTrashItem[]>([])

const importOpen = ref(false)
const importSaving = ref(false)
const importProjectId = ref('')
const importVersionId = ref('NONE')
const importFileName = ref('')
const importRows = ref<ImportRow[]>([])
const importError = ref<string | null>(null)
const importFileInput = ref<HTMLInputElement | null>(null)
const deleteOpen = ref(false)
const deleting = ref(false)
const deleteTarget = ref<Requirement | null>(null)
const deleteError = ref<string | null>(null)
const activeProjectId = computed(() =>
  store.projectId !== null
    && store.projects.some((project) => project.id === store.projectId)
    ? store.projectId
    : null,
)
const activeColumns: TableColumn[] = [
  { key: 'selection', label: '', width: '32px' },
  { key: 'requirementNo', label: '编号', width: '88px' },
  { key: 'title', label: '需求' },
  { key: 'version', label: '产品版本', width: '130px', hideBelow: 'md' },
  { key: 'poolStatus', label: '需求状态', width: '102px' },
  { key: 'source', label: '来源', width: '86px', hideBelow: 'lg' },
  { key: 'createdBy', label: '创建人', width: '96px', hideBelow: 'xl' },
  { key: 'updated', label: '更新', width: '92px', hideBelow: 'xl' },
  { key: 'actions', label: '', width: '210px', align: 'right' },
]
const trashColumns: TableColumn[] = [
  { key: 'selection', label: '', width: '32px' },
  { key: 'requirementNo', label: '编号', width: '88px' },
  { key: 'title', label: '需求' },
  { key: 'version', label: '产品版本', width: '130px', hideBelow: 'md' },
  { key: 'source', label: '来源', width: '86px', hideBelow: 'lg' },
  { key: 'createdBy', label: '创建人', width: '96px', hideBelow: 'xl' },
  { key: 'deletedAt', label: '删除时间', width: '112px' },
  { key: 'actions', label: '', width: '210px', align: 'right' },
]
const isTrashView = computed(() => viewMode.value === 'trash')
const selectedRequirement = computed(() =>
  isTrashView.value
    ? null
    : requirements.value.find((requirement) =>
        requirement.id === selectedRequirementId.value) ?? null,
)
const columns = computed(() => isTrashView.value ? trashColumns : activeColumns)
const viewItems = [
  { value: 'active', label: '需求池' },
  { value: 'trash', label: '回收站' },
]

const statusMeta: Record<
  RequirementDisplayStatus,
  { label: string; tone: 'neutral' | 'accent' | 'success' | 'warning' | 'danger' }
> = {
  PENDING_VERSION: { label: '待纳入版本', tone: 'warning' },
  PENDING_REVIEW: { label: '待需求评审', tone: 'accent' },
  IN_REVIEW: { label: '评审中', tone: 'accent' },
  FORMAL: { label: '正式需求', tone: 'success' },
  REJECTED: { label: '已驳回', tone: 'danger' },
}

const statusFilterItems = [
  { value: 'ALL', label: '全部状态' },
  ...Object.entries(statusMeta).map(([value, item]) => ({ value, label: item.label })),
]
const versionMap = computed(() =>
  new Map(versions.value.map((version) => [version.id, version])),
)
const planningVersions = computed(() =>
  versions.value.filter((version) => version.status === 'PLANNING'),
)
const assignableVersions = computed(() =>
  planningVersions.value.filter((version) =>
    allInitiatives.value.some((initiative) =>
      initiative.versionId === version.id && initiative.status === 'APPROVED'),
  ),
)
const targetVersionId = computed(() => {
  const value = Number(route.query.versionId)
  return Number.isSafeInteger(value) && value > 0 ? value : null
})
const targetVersion = computed(() =>
  targetVersionId.value === null
    ? null
    : versions.value.find((version) => version.id === targetVersionId.value) ?? null,
)
const preferredInitiativeId = computed(() =>
  allInitiatives.value.find((initiative) =>
    initiative.versionId === targetVersionId.value
    && initiative.status === 'APPROVED')?.id ?? null,
)
const filtered = computed<Array<Requirement | RequirementTrashItem>>(() => {
  const query = keyword.value.trim().toLocaleLowerCase('zh-CN')
  const rows: Array<Requirement | RequirementTrashItem> = isTrashView.value
    ? trash.value
    : requirements.value
  return rows.filter((requirement) => {
    if (
      !isTrashView.value
      && 'poolStatus' in requirement
      && statusFilter.value !== 'ALL'
      && requirementDisplayStatus(requirement) !== statusFilter.value
    ) return false
    if (!query) return true
    const reviewComment = 'reviewComment' in requirement ? requirement.reviewComment ?? '' : ''
    return [
      requirement.title,
      requirement.rawText,
      reviewComment,
      formatRequirementNo(requirement.requirementNo),
      String(requirement.requirementNo),
      sourceLabel(requirement),
      memberName(requirement.createdBy),
    ]
      .some((value) => value.toLocaleLowerCase('zh-CN').includes(query))
  })
})
const canReview = computed(() => {
  const roles = members.value.find((member) => member.userId === auth.user?.id)?.roles ?? []
  return roles.includes('OWNER')
    || canActWithRoles('PRODUCT', roles, members.value.length)
})
const canDeleteAnyRequirement = computed(() =>
  members.value
    .find((member) => member.userId === auth.user?.id)
    ?.roles.includes('OWNER') ?? false,
)
const canPurgeRequirements = canDeleteAnyRequirement
const importVersionOptions = computed(() =>
  planningVersions.value.flatMap((version) => {
    const initiative = allInitiatives.value.find((item) =>
      item.versionId === version.id && item.status === 'APPROVED')
    return initiative ? [{ version, initiative }] : []
  }),
)
const importVersionItems = computed(() => [
  { value: 'NONE', label: '不关联版本（仅进入需求池）' },
  ...(canReview.value
    ? importVersionOptions.value.map(({ version, initiative }) => ({
        value: String(version.id),
        label: `${version.versionNo} · ${initiative.title}`,
      }))
    : []),
])
const selectedImportVersion = computed(() =>
  importVersionOptions.value.find(({ version }) => String(version.id) === importVersionId.value),
)
const evaluatedImportRows = computed(() => {
  const duplicateErrors = findDuplicateImportRows(
    importRows.value,
    requirements.value.map((requirement) => ({
      title: requirement.title,
    })),
  )
  return importRows.value.map((row, index) => ({
    ...row,
    error: row.error ?? duplicateErrors[index],
  }))
})
const importValidCount = computed(() =>
  evaluatedImportRows.value.filter((row) => row.error === null).length,
)
const importDuplicateCount = computed(() =>
  evaluatedImportRows.value.filter((row) =>
    row.error?.includes('重复') || row.error?.includes('同名需求')).length,
)
const importOtherInvalidCount = computed(() =>
  evaluatedImportRows.value.length - importValidCount.value - importDuplicateCount.value,
)
function isRequirementAssignable(requirement: Requirement) {
  return requirement.poolStatus === 'POOL' || requirement.poolStatus === 'REJECTED'
}
function isRequirementSelectable(requirement: Requirement) {
  return canDeleteAnyRequirement.value || isRequirementAssignable(requirement)
}
const selectableRequirements = computed<Array<Requirement | RequirementTrashItem>>(() =>
  (filtered.value as Array<Requirement | RequirementTrashItem>).filter((item) => {
    if (isTrashView.value) return canPurgeRequirements.value
    return canReview.value && isRequirementSelectable(item as Requirement)
  }),
)
const selectedRequirementSet = computed(() => new Set(selectedRequirementIds.value))
const selectedRequirements = computed(() =>
  requirements.value.filter((requirement) =>
    selectedRequirementSet.value.has(requirement.id)),
)
const selectedAssignableRequirements = computed(() =>
  selectedRequirements.value.filter(isRequirementAssignable),
)
const selectedTrashItems = computed(() =>
  trash.value.filter((item) => selectedRequirementSet.value.has(item.id)),
)
const selectionState = computed<boolean | 'indeterminate'>(() => {
  const selectableIds = new Set(selectableRequirements.value.map((requirement) => requirement.id))
  const selectedCount = selectedRequirementIds.value
    .filter((id) => selectableIds.has(id)).length
  if (selectedCount === 0) return false
  return selectedCount === selectableRequirements.value.length ? true : 'indeterminate'
})
const productWorkPending = computed(() =>
  requirements.value.some((requirement) =>
    requirement.poolStatus === 'POOL' || requirement.poolStatus === 'REJECTED',
  ),
)

function messageOf(value: unknown): string {
  if (value instanceof ApiError) return value.message
  return value instanceof Error ? value.message : '操作失败'
}

function clearRequirementSelection() {
  selectedRequirementIds.value = []
}

function toggleRequirementSelection(id: number, checked: boolean) {
  const next = new Set(selectedRequirementIds.value)
  if (checked) next.add(id)
  else next.delete(id)
  selectedRequirementIds.value = [...next]
}

function toggleAllRequirements() {
  const selectableIds = selectableRequirements.value.map((requirement) => requirement.id)
  if (selectionState.value === true) {
    const removing = new Set(selectableIds)
    selectedRequirementIds.value = selectedRequirementIds.value.filter(
      (id) => !removing.has(id),
    )
    return
  }
  selectedRequirementIds.value = [
    ...new Set([...selectedRequirementIds.value, ...selectableIds]),
  ]
}

function openBatchAssign() {
  if (
    selectedAssignableRequirements.value.length === 0
    || assignableVersions.value.length === 0
  ) return
  batchAssignVersionId.value = String(
    assignableVersions.value.find((version) => version.id === targetVersionId.value)?.id
      ?? assignableVersions.value[0]?.id
      ?? '',
  )
  batchAssignError.value = null
  batchAssignOpen.value = true
}

async function assignSelectedRequirements() {
  if (
    batchAssignSaving.value
    || selectedAssignableRequirements.value.length === 0
    || !batchAssignVersionId.value
  ) return
  const requirementsToAssign = [...selectedAssignableRequirements.value]
  const versionId = Number(batchAssignVersionId.value)
  const version = versions.value.find((item) => item.id === versionId)
  batchAssignSaving.value = true
  batchAssignError.value = null
  try {
    const results = await Promise.allSettled(
      requirementsToAssign.map((requirement) =>
        assignRequirementVersion(requirement.id, versionId)),
    )
    const failures = results.flatMap((result, index) =>
      result.status === 'rejected'
        ? [{
            requirement: requirementsToAssign[index]!,
            message: messageOf(result.reason),
          }]
        : [])
    await load()
    if (failures.length === 0) {
      clearRequirementSelection()
      batchAssignOpen.value = false
      toast.success(
        '需求已加入版本',
        `共 ${requirementsToAssign.length} 条，已纳入 ${version?.versionNo ?? '目标版本'}`,
      )
      return
    }
    selectedRequirementIds.value = failures.map((failure) => failure.requirement.id)
    batchAssignError.value = [
      `成功 ${requirementsToAssign.length - failures.length} 条`,
      `失败 ${failures.length} 条`,
      ...failures.slice(0, 3).map((failure) =>
        `${failure.requirement.title}：${failure.message}`),
    ].join('；')
    toast.warning('部分需求未能加入版本', '失败项已保留选中，可重试')
  } catch (value) {
    batchAssignError.value = messageOf(value)
  } finally {
    batchAssignSaving.value = false
  }
}

function openBatchDelete() {
  if (!canDeleteAnyRequirement.value || selectedRequirements.value.length === 0) return
  batchDeleteError.value = null
  batchDeleteOpen.value = true
}

async function confirmBatchDelete() {
  if (
    batchDeleteSaving.value
    || !canDeleteAnyRequirement.value
    || selectedRequirements.value.length === 0
  ) return
  const targets = [...selectedRequirements.value]
  batchDeleteSaving.value = true
  batchDeleteError.value = null
  try {
    const result = await deleteRequirements(targets.map((requirement) => requirement.id))
    batchDeleteOpen.value = false
    clearRequirementSelection()
    selectedRequirementId.value = null
    await load()
    toast.success(
      '需求已移入回收站',
      `共 ${result.deletedCount} 条，可在回收站中恢复`,
    )
  } catch (value) {
    batchDeleteError.value = messageOf(value)
  } finally {
    batchDeleteSaving.value = false
  }
}

function versionLabel(requirement: Requirement | RequirementTrashItem) {
  if (requirement.versionId === null) return '未纳入'
  return versionMap.value.get(requirement.versionId)?.versionNo ?? `#${requirement.versionId}`
}

function sourceLabel(requirement: Requirement | RequirementTrashItem) {
  return requirement.source === 'AI' ? 'AI' : requirement.source === 'IMPORT' ? '导入' : '手工'
}

function memberName(userId: number | null | undefined) {
  if (!userId) return '未知'
  const member = members.value.find((item) => item.userId === userId)
  return member?.displayName ?? `用户 #${userId}`
}

function rowUpdatedAt(row: Requirement | RequirementTrashItem) {
  return 'updatedAt' in row ? row.updatedAt : row.deletedAt
}

async function load() {
  loading.value = true
  error.value = null
  try {
    if (!activeProjectId.value) {
      requirements.value = []
      trash.value = []
      versions.value = []
      members.value = []
      allInitiatives.value = []
      clearRequirementSelection()
      selectedRequirementId.value = null
      return
    }
    const [nextRequirements, nextTrash, nextVersions, nextMembers, nextInitiatives] = await Promise.all([
      listRequirements(activeProjectId.value),
      listRequirementTrash(activeProjectId.value),
      listProductVersions(activeProjectId.value),
      listProjectMembers(activeProjectId.value),
      listInitiatives(activeProjectId.value),
    ])
    requirements.value = nextRequirements
    trash.value = nextTrash
    versions.value = nextVersions
    members.value = nextMembers
    allInitiatives.value = nextInitiatives
    const existingRequirementIds = new Set(
      (isTrashView.value ? nextTrash : nextRequirements)
        .map((requirement) => requirement.id),
    )
    selectedRequirementIds.value = selectedRequirementIds.value.filter(
      (id) => existingRequirementIds.has(id),
    )
    if (!nextRequirements.some((requirement) => requirement.id === selectedRequirementId.value)) {
      selectedRequirementId.value = null
    }
  } catch (value) {
    error.value = messageOf(value)
    requirements.value = []
    trash.value = []
    versions.value = []
    allInitiatives.value = []
    members.value = []
    clearRequirementSelection()
    selectedRequirementId.value = null
  } finally {
    loading.value = false
  }
}

function openEditor(mode: 'manual' | 'ai', requirement: Requirement | null = null) {
  editorMode.value = mode
  editingRequirement.value = requirement
  editorOpen.value = true
}

function selectRow(row: Requirement | RequirementTrashItem) {
  if (isTrashView.value) {
    selectedRequirementId.value = null
    return
  }
  selectedRequirementId.value = row.id
}

function editRequirementFromRow(row: Requirement | RequirementTrashItem) {
  if (!('poolStatus' in row)) return
  selectedRequirementId.value = row.id
  if (isRequirementAssignable(row)) {
    openEditor('manual', row)
  }
}

async function saved(requirement: Requirement) {
  await load()
  if (
    targetVersionId.value !== null
    && requirement.versionId === null
    && targetVersion.value?.status === 'PLANNING'
  ) {
    try {
      await assignRequirementVersion(requirement.id, targetVersionId.value)
      await load()
      toast.success('需求已纳入当前版本', targetVersion.value.versionNo)
    } catch (value) {
      toast.warning('需求已保存，但纳入版本失败', messageOf(value))
    }
  }
}

async function generated(items: Requirement[]) {
  await load()
  toast.success(
    `AI 已生成 ${items.length} 条需求`,
    '已自动纳入需求池，可逐条补充产品版本和验收标准',
  )
}

function openDelete(requirement: Requirement) {
  deleteTarget.value = requirement
  deleteError.value = null
  deleteOpen.value = true
}

async function confirmDelete() {
  if (!deleteTarget.value || deleting.value) return
  deleting.value = true
  deleteError.value = null
  try {
    await deleteRequirement(deleteTarget.value.id)
    deleteOpen.value = false
    await load()
    toast.success('需求已移入回收站', '可在回收站中恢复')
  } catch (value) {
    deleteError.value = messageOf(value)
  } finally {
    deleting.value = false
  }
}

function openPurge(items: RequirementTrashItem[]) {
  if (!canPurgeRequirements.value || items.length === 0) return
  purgeTargets.value = [...items]
  purgeError.value = null
  purgeOpen.value = true
}

async function confirmPurge() {
  if (
    purgeSaving.value
    || !canPurgeRequirements.value
    || purgeTargets.value.length === 0
  ) return
  const targets = [...purgeTargets.value]
  purgeSaving.value = true
  purgeError.value = null
  try {
    const result = await purgeRequirements(targets.map((item) => item.id))
    purgeOpen.value = false
    purgeTargets.value = []
    clearRequirementSelection()
    await load()
    toast.success(
      '需求已彻底删除',
      `共 ${result.deletedCount} 条，无法恢复`,
    )
  } catch (value) {
    purgeError.value = messageOf(value)
  } finally {
    purgeSaving.value = false
  }
}

async function restoreFromTrash(item: RequirementTrashItem) {
  try {
    await restoreRequirement(item.id)
    viewMode.value = 'active'
    await load()
    toast.success('需求已重新进入需求池', item.title)
  } catch (value) {
    toast.warning('移回需求池失败', messageOf(value))
  }
}

function openAssign(requirement: Requirement) {
  assignTarget.value = requirement
  assignVersionId.value = String(
    requirement.versionId
    ?? targetVersionId.value
    ?? assignableVersions.value[0]?.id
    ?? '',
  )
  assignError.value = null
  assignOpen.value = true
}

async function saveAssignment() {
  if (!assignTarget.value || !assignVersionId.value || assignSaving.value) return
  assignSaving.value = true
  assignError.value = null
  try {
    await assignRequirementVersion(assignTarget.value.id, Number(assignVersionId.value))
    assignOpen.value = false
    await load()
    toast.success('需求已纳入产品版本', '现在可以组织线下需求评审')
  } catch (value) {
    assignError.value = messageOf(value)
  } finally {
    assignSaving.value = false
  }
}

function downloadTemplate() {
  const rows = [
    ['需求标题', '需求描述', '验收标准', '补充澄清'],
    ['示例需求', '描述业务目标、当前问题和期望结果', '验收标准1；验收标准2', '已确认的假设、依赖和边界'],
  ]
  const content = '\uFEFF' + rows.map((row) =>
    row.map((cell) => `"${cell.replaceAll('"', '""')}"`).join(','),
  ).join('\n')
  const blob = new Blob([content], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = 'Regent-需求池导入模板.csv'
  anchor.click()
  URL.revokeObjectURL(url)
}

function openImport() {
  importProjectId.value = String(activeProjectId.value ?? '')
  importVersionId.value = 'NONE'
  importFileName.value = ''
  importRows.value = []
  importError.value = null
  importOpen.value = true
}

function parseCsv(text: string): string[][] {
  const rows: string[][] = []
  let row: string[] = []
  let field = ''
  let quoted = false
  for (let index = 0; index < text.length; index++) {
    const character = text[index]
    if (quoted) {
      if (character === '"' && text[index + 1] === '"') {
        field += '"'
        index++
      } else if (character === '"') {
        quoted = false
      } else {
        field += character
      }
    } else if (character === '"') {
      quoted = true
    } else if (character === ',') {
      row.push(field)
      field = ''
    } else if (character === '\n') {
      row.push(field.replace(/\r$/, ''))
      rows.push(row)
      row = []
      field = ''
    } else {
      field += character
    }
  }
  if (field.length > 0 || row.length > 0) {
    row.push(field.replace(/\r$/, ''))
    rows.push(row)
  }
  return rows.filter((items) => items.some((item) => item.trim() !== ''))
}

async function onImportFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  importRows.value = []
  importError.value = null
  if (!file) return
  importFileName.value = file.name
  try {
    const rows = parseCsv(await file.text())
    if (rows.length < 2) {
      throw new Error('文件没有可导入的数据行')
    }
    const headers = rows[0].map((item) => item.trim())
    const indexOf = (...names: string[]) =>
      headers.findIndex((header) => names.includes(header))
    const titleIndex = indexOf('需求标题', '标题', 'title')
    const rawIndex = indexOf('需求描述', '描述', 'rawText')
    const acceptanceIndex = indexOf('验收标准', 'acceptanceText')
    const clarificationIndex = indexOf('补充澄清', 'clarificationText')
    if (titleIndex < 0 || rawIndex < 0) {
      throw new Error('模板缺少“需求标题”或“需求描述”列')
    }
    importRows.value = rows.slice(1).map((cells) => {
      const title = (cells[titleIndex] ?? '').trim()
      const rawText = (cells[rawIndex] ?? '').trim()
      const error = !title ? '缺少需求标题' : !rawText ? '缺少需求描述' : null
      return {
        title,
        rawText,
        acceptanceText: acceptanceIndex >= 0 ? (cells[acceptanceIndex] ?? '').trim() : '',
        clarificationText: clarificationIndex >= 0
          ? (cells[clarificationIndex] ?? '').trim()
          : '',
        error,
      }
    })
  } catch (value) {
    importError.value = messageOf(value)
  }
}

async function runImport() {
  if (
    importSaving.value
    || !importProjectId.value
    || importValidCount.value === 0
  ) return
  importSaving.value = true
  importError.value = null
  const failures: string[] = []
  let succeeded = 0
  const selectedVersion = canReview.value ? selectedImportVersion.value : undefined
  for (const row of evaluatedImportRows.value) {
    if (row.error) continue
    try {
      await createRequirement({
        projectId: Number(importProjectId.value),
        initiativeId: selectedVersion?.initiative.id ?? null,
        versionId: selectedVersion?.version.id ?? null,
        source: 'IMPORT',
        title: row.title,
        rawText: row.rawText,
        acceptanceText: row.acceptanceText || null,
        clarificationText: row.clarificationText || null,
      })
      succeeded++
    } catch (value) {
      failures.push(`${row.title}：${messageOf(value)}`)
    }
  }
  importSaving.value = false
  await load()
  if (failures.length > 0) {
    importError.value = `成功 ${succeeded} 条，失败 ${failures.length} 条。${failures.slice(0, 3).join('；')}`
    toast.warning('部分需求导入失败', '请根据错误信息修正后重新导入')
  } else {
    importOpen.value = false
    toast.success(
      '需求导入完成',
      selectedVersion
        ? `已导入 ${succeeded} 条并纳入 ${selectedVersion.version.versionNo}`
        : `已导入 ${succeeded} 条到需求池`,
    )
  }
}

watch(
  () => activeProjectId.value,
  () => {
    clearRequirementSelection()
    selectedRequirementId.value = null
    void load()
  },
)
watch(viewMode, () => {
  clearRequirementSelection()
  selectedRequirementId.value = null
  keyword.value = ''
  statusFilter.value = 'ALL'
})
watch([keyword, statusFilter], () => {
  clearRequirementSelection()
  selectedRequirementId.value = null
})
onMounted(() => void load())
</script>

<template>
  <div class="flex h-full min-h-0 flex-col">
    <ListToolbar>
      <template #title>
        <h1 class="shrink-0 text-body font-emphasis text-label">需求池</h1>
        <SegmentedControl
          v-model="viewMode"
          :items="viewItems"
          size="lg"
          class="shrink-0"
        />
        <span class="tnum text-caption text-label-tertiary">{{ filtered.length }}</span>
      </template>
      <template #filters>
        <Select
          v-if="!isTrashView"
          v-model="statusFilter"
          :items="statusFilterItems"
          size="lg"
          class="w-36"
        />
        <div class="relative min-w-0 flex-1 sm:w-64 sm:flex-none">
          <Icon
            :as="Search"
            size="inline"
            class="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-label-tertiary"
          />
          <Input
            v-model="keyword"
            size="lg"
            :placeholder="isTrashView ? '搜索已删除需求' : '搜索标题、编号或创建人'"
            class="pl-7 pr-7"
          />
          <button
            v-if="keyword"
            type="button"
            class="absolute right-1 top-1/2 flex size-5 -translate-y-1/2 items-center justify-center rounded-control text-label-tertiary hover:bg-hover"
            aria-label="清空需求搜索"
            @click="keyword = ''"
          >
            <Icon :as="X" size="inline" />
          </button>
        </div>
      </template>
      <template #actions>
        <Tooltip content="刷新">
          <IconButton
            :label="isTrashView ? '刷新回收站' : '刷新需求池'"
            size="lg"
            :disabled="loading"
            @click="load"
          >
            <Icon :as="RefreshCw" size="large" :class="loading ? 'animate-spin' : ''" />
          </IconButton>
        </Tooltip>
        <template v-if="!isTrashView">
          <Button size="lg" @click="openImport">
            <Icon :as="Upload" size="inline" />导入
          </Button>
          <Button size="lg" @click="openEditor('ai')">
            <Icon :as="Sparkles" size="inline" />AI 生成
          </Button>
          <Button variant="primary" size="lg" @click="openEditor('manual')">
            <Icon :as="Plus" size="inline" />录入需求
          </Button>
        </template>
      </template>
    </ListToolbar>

    <p v-if="error" class="border-b border-danger/20 bg-danger/5 px-4 py-2 text-caption text-danger">
      {{ error }}
    </p>
    <div
      v-else-if="targetVersion && !isTrashView"
      class="flex flex-wrap items-center gap-2 border-b border-accent/20 bg-accent/5 px-4 py-2 text-caption text-label-secondary"
    >
      <span>当前录入目标版本</span>
      <span class="font-mono font-emphasis text-label">{{ targetVersion.versionNo }}</span>
      <span class="text-label-tertiary">新需求保存后会自动纳入该版本</span>
    </div>
    <p
      v-if="productWorkPending && !canReview && !isTrashView"
      class="border-b border-warning/20 bg-warning/5 px-4 py-2 text-caption text-warning"
    >
      产品版本规划与需求纳入需要“产品负责人”或“项目负责人”；当前账号可以录入和查看需求，但不能纳入版本。需求评审请到“版本”页登记线下评审记录。
    </p>
    <div
      v-if="!isTrashView && selectedRequirements.length > 0"
      class="flex flex-wrap items-center gap-2 border-b border-accent/20 bg-accent/5 px-4 py-2 text-caption text-label-secondary"
    >
      <span class="font-emphasis text-label">已选 {{ selectedRequirements.length }} 条需求</span>
      <span class="text-label-tertiary">
        {{ selectedAssignableRequirements.length > 0
          ? `${selectedAssignableRequirements.length} 条可加入规划版本`
          : '当前选择不支持加入版本' }}
      </span>
      <Button
        class="ml-auto"
        size="sm"
        variant="primary"
        :disabled="!canReview
          || assignableVersions.length === 0
          || selectedAssignableRequirements.length === 0"
        @click="openBatchAssign"
      >
        加入版本 {{ selectedAssignableRequirements.length }}
      </Button>
      <Button
        v-if="canDeleteAnyRequirement"
        variant="danger"
        size="sm"
        @click="openBatchDelete"
      >
        批量移入回收站
      </Button>
      <Button size="sm" @click="clearRequirementSelection">取消选择</Button>
    </div>
    <div
      v-if="isTrashView && selectedTrashItems.length > 0"
      class="flex flex-wrap items-center gap-2 border-b border-danger/20 bg-danger/5 px-4 py-2 text-caption text-label-secondary"
    >
      <span class="font-emphasis text-label">已选 {{ selectedTrashItems.length }} 条需求</span>
      <span class="text-label-tertiary">彻底删除后无法恢复</span>
      <Button
        v-if="canPurgeRequirements"
        class="ml-auto"
        variant="danger"
        size="sm"
        @click="openPurge(selectedTrashItems)"
      >
        彻底删除 {{ selectedTrashItems.length }} 条
      </Button>
      <Button size="sm" @click="clearRequirementSelection">取消选择</Button>
    </div>

    <div class="flex min-h-0 flex-1">
      <div class="flex min-w-0 flex-1 flex-col">
        <div class="scroll-area min-h-0 flex-1 overflow-auto">
          <Table
            :columns="columns"
            :rows="filtered"
            :selected-id="selectedRequirementId"
            :checked-ids="selectedRequirementIds"
            :show-empty="false"
            @select="selectRow($event as Requirement | RequirementTrashItem)"
            @row-dblclick="editRequirementFromRow($event as Requirement | RequirementTrashItem)"
          >
        <template #header="{ column }">
          <Checkbox
            v-if="column.key === 'selection'"
            :model-value="selectionState"
            :disabled="selectableRequirements.length === 0"
            aria-label="选择当前列表全部可操作需求"
            @update:model-value="toggleAllRequirements"
          />
          <span v-else>{{ column.label }}</span>
        </template>
        <template #cell="{ row, column }">
          <div v-if="column.key === 'selection'" class="flex items-center" @click.stop>
            <Checkbox
              :model-value="selectedRequirementSet.has(row.id)"
              :disabled="isTrashView
                ? !canPurgeRequirements
                : !canReview
                  || !('poolStatus' in row)
                  || !isRequirementSelectable(row as Requirement)"
              :aria-label="`选择需求 ${row.title}`"
              @update:model-value="toggleRequirementSelection(row.id, $event === true)"
            />
          </div>
          <span
            v-else-if="column.key === 'requirementNo'"
            class="tnum font-mono text-caption text-label-secondary"
          >
            #{{ formatRequirementNo(row.requirementNo) }}
          </span>
          <span v-else-if="column.key === 'title'" class="min-w-0">
            <span class="block truncate text-caption font-emphasis text-label">{{ row.title }}</span>
            <span
              v-if="'reviewComment' in row && row.reviewComment"
              class="mt-0.5 block truncate text-[10px] text-label-tertiary"
            >
              {{ row.reviewComment }}
            </span>
          </span>
          <span v-else-if="column.key === 'version'" class="text-caption text-label-secondary">
            {{ versionLabel(row as Requirement | RequirementTrashItem) }}
          </span>
          <template v-else-if="column.key === 'poolStatus'">
            <Badge
              v-if="isTrashView"
              tone="danger"
            >
              已删除
            </Badge>
            <Badge
              v-else
              :tone="statusMeta[requirementDisplayStatus(row as Requirement)].tone"
            >
              {{ statusMeta[requirementDisplayStatus(row as Requirement)].label }}
            </Badge>
          </template>
          <span v-else-if="column.key === 'source'" class="text-caption text-label-tertiary">
            {{ sourceLabel(row as Requirement | RequirementTrashItem) }}
          </span>
          <span v-else-if="column.key === 'createdBy'" class="truncate text-caption text-label-secondary">
            {{ memberName(row.createdBy) }}
          </span>
          <span v-else-if="column.key === 'updated'" class="text-caption text-label-tertiary">
            {{ formatRelative(String(rowUpdatedAt(row as Requirement | RequirementTrashItem))) }}
          </span>
          <span v-else-if="column.key === 'deletedAt'" class="text-caption text-label-tertiary">
            {{ formatRelative(String((row as RequirementTrashItem).deletedAt)) }}
          </span>
          <div v-else-if="column.key === 'actions'" class="flex items-center justify-end gap-1">
            <template v-if="isTrashView">
              <Button
                variant="ghost"
                size="sm"
                @click.stop="restoreFromTrash(row as RequirementTrashItem)"
              >
                <Icon :as="RotateCcw" size="inline" />移回需求池
              </Button>
              <Button
                v-if="canPurgeRequirements"
                variant="danger"
                size="sm"
                @click.stop="openPurge([row as RequirementTrashItem])"
              >
                <Icon :as="Trash2" size="inline" />彻底删除
              </Button>
            </template>
            <template v-else-if="isRequirementAssignable(row as Requirement)">
              <Button
                variant="ghost"
                size="sm"
                :disabled="!canReview"
                @click.stop="openAssign(row as Requirement)"
              >
                {{ row.versionId ? '更换版本' : '加入版本' }}
              </Button>
              <Tooltip content="编辑需求">
                <IconButton
                  label="编辑需求"
                  size="sm"
                  @click.stop="openEditor('manual', row as Requirement)"
                >
                  <Icon :as="Pencil" size="inline" />
                </IconButton>
              </Tooltip>
            </template>
            <span
              v-else-if="(row as Requirement).poolStatus === 'IN_REVIEW'"
              class="text-caption text-accent"
            >
              请到版本页评审
            </span>
            <Badge v-else-if="(row as Requirement).poolStatus === 'FORMAL'" tone="success">
              已进入研发
            </Badge>
            <IconButton
              v-if="!isTrashView
                && (canDeleteAnyRequirement
                  || ('poolStatus' in row && isRequirementAssignable(row as Requirement)))"
              label="移入回收站"
              size="sm"
              @click.stop="openDelete(row as Requirement)"
            >
              <Icon :as="Trash2" size="inline" />
            </IconButton>
          </div>
        </template>
      </Table>

      <EmptyState
        v-if="loading && filtered.length === 0"
        :title="isTrashView ? '正在加载回收站…' : '正在加载需求池…'"
        description="读取需求、版本和评审状态"
      />
      <EmptyState
        v-else-if="!loading && isTrashView && trash.length === 0"
        title="回收站是空的"
        description="删除的需求会暂时保留在这里，随时可以恢复。"
      />
      <EmptyState
        v-else-if="!loading && !isTrashView && requirements.length === 0"
        title="需求池还是空的"
        description="可以手工录入、从模板导入，或者把一句产品想法交给 AI 拆成多条需求。"
      >
        <Button variant="primary" size="sm" @click="openEditor('manual')">录入第一条需求</Button>
      </EmptyState>
      <EmptyState
        v-else-if="!loading && filtered.length === 0"
        :title="isTrashView ? '回收站没有匹配的需求' : '没有匹配的需求'"
        description="换个关键词再试。"
      />
        </div>
      </div>

      <aside
        v-if="!isTrashView"
        :class="[
          'overflow-y-auto border-separator bg-window',
          selectedRequirement
            ? 'fixed inset-0 z-40 w-full p-4 lg:static lg:inset-auto lg:z-auto lg:w-[320px] lg:shrink-0 lg:border-l'
            : 'hidden lg:block lg:w-[320px] lg:shrink-0 lg:border-l lg:p-4',
        ]"
      >
        <template v-if="selectedRequirement">
          <button
            type="button"
            class="mb-3 inline-flex items-center gap-0.5 text-caption text-accent lg:hidden"
            @click="selectedRequirementId = null"
          >
            <Icon :as="ChevronLeft" size="inline" />返回列表
          </button>
          <div class="flex items-center gap-2">
            <span class="tnum font-mono text-caption text-label-tertiary">
              #{{ formatRequirementNo(selectedRequirement.requirementNo) }}
            </span>
            <Badge :tone="statusMeta[requirementDisplayStatus(selectedRequirement)].tone">
              {{ statusMeta[requirementDisplayStatus(selectedRequirement)].label }}
            </Badge>
            <IconButton
              class="ml-auto"
              label="关闭需求详情"
              size="sm"
              @click="selectedRequirementId = null"
            >
              <Icon :as="X" size="inline" />
            </IconButton>
          </div>
          <h2 class="mt-2 text-body font-emphasis text-label">
            {{ selectedRequirement.title }}
          </h2>
          <p class="mt-1 text-caption text-label-tertiary">
            {{ versionLabel(selectedRequirement) }} ·
            来源：{{ sourceLabel(selectedRequirement) }} ·
            创建人：{{ memberName(selectedRequirement.createdBy) }} ·
            {{ formatRelative(selectedRequirement.updatedAt) }}
          </p>

          <div class="mt-4 rounded-panel border border-separator-soft bg-content p-3">
            <p class="text-caption font-emphasis text-label-secondary">需求描述</p>
            <p class="mt-1 whitespace-pre-wrap text-caption text-label-secondary">
              {{ selectedRequirement.rawText }}
            </p>
          </div>
          <div
            v-if="selectedRequirement.acceptanceText"
            class="mt-3 rounded-panel border border-separator-soft bg-content p-3"
          >
            <p class="text-caption font-emphasis text-label-secondary">验收标准</p>
            <p class="mt-1 whitespace-pre-wrap text-caption text-label-secondary">
              {{ selectedRequirement.acceptanceText }}
            </p>
          </div>
          <div
            v-if="selectedRequirement.clarificationText"
            class="mt-3 rounded-panel border border-separator-soft bg-content p-3"
          >
            <p class="text-caption font-emphasis text-label-secondary">补充澄清</p>
            <p class="mt-1 whitespace-pre-wrap text-caption text-label-secondary">
              {{ selectedRequirement.clarificationText }}
            </p>
          </div>
          <div
            v-if="selectedRequirement.reviewComment"
            class="mt-3 rounded-panel border border-separator-soft bg-content p-3"
          >
            <p class="text-caption font-emphasis text-label-secondary">评审意见</p>
            <p class="mt-1 whitespace-pre-wrap text-caption text-label-secondary">
              {{ selectedRequirement.reviewComment }}
            </p>
          </div>

          <p
            v-if="selectedRequirement.poolStatus === 'IN_REVIEW'"
            class="mt-4 rounded-panel border border-accent/20 bg-accent/5 p-3 text-caption text-label-secondary"
          >
            需求评审请在“版本”页登记线下评审结论和会议纪要。
          </p>
          <p
            v-else-if="selectedRequirement.poolStatus === 'FORMAL'"
            class="mt-4 rounded-panel border border-success/20 bg-success/5 p-3 text-caption text-label-secondary"
          >
            正式需求已进入详细设计，请到“版本”页继续推进研发流程。
          </p>
          <div class="mt-4 flex flex-wrap items-center gap-2 border-t border-separator pt-3">
            <template v-if="isRequirementAssignable(selectedRequirement)">
              <Button size="sm" @click="openEditor('manual', selectedRequirement)">
                <Icon :as="Pencil" size="inline" />编辑
              </Button>
              <Button
                variant="primary"
                size="sm"
                :disabled="!canReview"
                @click="openAssign(selectedRequirement)"
              >
                {{ selectedRequirement.versionId ? '更换版本' : '加入版本' }}
              </Button>
            </template>
            <IconButton
              v-if="canDeleteAnyRequirement || isRequirementAssignable(selectedRequirement)"
              label="移入回收站"
              size="sm"
              @click="openDelete(selectedRequirement)"
            >
              <Icon :as="Trash2" size="inline" />
            </IconButton>
          </div>
        </template>
        <div v-else class="flex h-full items-center justify-center px-4 text-center">
          <p class="text-caption text-label-tertiary">选中一条需求查看完整内容</p>
        </div>
      </aside>
    </div>

    <RequirementPoolEditorDialog
      v-model:open="editorOpen"
      :requirement="editingRequirement"
      :default-project-id="activeProjectId"
      :preferred-initiative-id="preferredInitiativeId"
      :initial-mode="editorMode"
      @saved="saved"
      @generated="generated"
    />

    <Dialog
      v-model:open="deleteOpen"
      layout="form"
      title="移入需求回收站"
      description="删除后需求会进入回收站，可随时移回需求池。"
      :icon="Trash2"
    >
      <div class="flex flex-col gap-3">
        <div class="rounded-panel border border-warning/25 bg-warning/5 p-3">
          <p class="text-caption font-emphasis text-label">{{ deleteTarget?.title }}</p>
          <p class="mt-1 text-caption text-label-secondary">
            #{{ formatRequirementNo(deleteTarget?.requirementNo) }} · 删除后可在回收站移回需求池
          </p>
        </div>
        <p v-if="deleteError" class="text-caption text-danger">{{ deleteError }}</p>
      </div>
      <template #footer>
        <Button :disabled="deleting" @click="deleteOpen = false">取消</Button>
        <Button variant="danger" :disabled="deleting" @click="confirmDelete">
          {{ deleting ? '删除中…' : '移入回收站' }}
        </Button>
      </template>
    </Dialog>

    <Dialog
      v-model:open="batchDeleteOpen"
      layout="form"
      size="large"
      title="批量移入需求回收站"
      description="项目负责人可以删除任意状态的需求；研发记录会保留，需求之后仍可从回收站恢复。"
      :icon="Trash2"
    >
      <div class="flex flex-col gap-3">
        <div class="max-h-56 space-y-1 overflow-y-auto rounded-panel border border-separator-soft p-2">
          <div
            v-for="requirement in selectedRequirements"
            :key="requirement.id"
            class="flex items-center gap-2 rounded-control px-1.5 py-1 text-caption"
          >
            <span class="tnum shrink-0 font-mono text-label-tertiary">
              #{{ formatRequirementNo(requirement.requirementNo) }}
            </span>
            <span class="min-w-0 flex-1 truncate text-label">{{ requirement.title }}</span>
            <Badge :tone="statusMeta[requirementDisplayStatus(requirement)].tone">
              {{ statusMeta[requirementDisplayStatus(requirement)].label }}
            </Badge>
          </div>
        </div>
        <p class="text-caption text-label-secondary">
          共 {{ selectedRequirements.length }} 条需求，删除后可在回收站恢复。
        </p>
        <p v-if="batchDeleteError" class="text-caption text-danger">{{ batchDeleteError }}</p>
      </div>
      <template #footer>
        <Button :disabled="batchDeleteSaving" @click="batchDeleteOpen = false">取消</Button>
        <Button variant="danger" :disabled="batchDeleteSaving" @click="confirmBatchDelete">
          {{ batchDeleteSaving
            ? '删除中…'
            : `确认删除 ${selectedRequirements.length} 条` }}
        </Button>
      </template>
    </Dialog>

    <Dialog
      v-model:open="purgeOpen"
      layout="form"
      size="large"
      title="彻底删除需求"
      description="此操作不可恢复。关联任务与发布历史会保留，但会解除与这些需求的关联。"
      :icon="Trash2"
    >
      <div class="flex flex-col gap-3">
        <div class="max-h-56 space-y-1 overflow-y-auto rounded-panel border border-danger/20 p-2">
          <div
            v-for="item in purgeTargets"
            :key="item.id"
            class="flex items-center gap-2 rounded-control px-1.5 py-1 text-caption"
          >
            <span class="tnum shrink-0 font-mono text-label-tertiary">
              #{{ formatRequirementNo(item.requirementNo) }}
            </span>
            <span class="min-w-0 flex-1 truncate text-label">{{ item.title }}</span>
          </div>
        </div>
        <p class="text-caption text-danger">
          共 {{ purgeTargets.length }} 条需求将被永久删除，无法恢复。
        </p>
        <p v-if="purgeError" class="text-caption text-danger">{{ purgeError }}</p>
      </div>
      <template #footer>
        <Button :disabled="purgeSaving" @click="purgeOpen = false">取消</Button>
        <Button variant="danger" :disabled="purgeSaving" @click="confirmPurge">
          {{ purgeSaving ? '删除中…' : `确认彻底删除 ${purgeTargets.length} 条` }}
        </Button>
      </template>
    </Dialog>

    <Dialog
      v-model:open="assignOpen"
      layout="form"
      title="纳入产品版本"
      description="需求纳入规划中的产品版本后，才能登记线下需求评审记录。"
    >
      <div class="flex flex-col gap-3">
        <div class="rounded-control bg-window px-3 py-2 text-caption text-label">
          {{ assignTarget?.title }}
        </div>
        <DialogField label="产品版本" required>
          <Select
            v-model="assignVersionId"
            :items="assignableVersions.map((version) => ({
              value: String(version.id),
              label: version.versionNo,
            }))"
            placeholder="先创建一个完成立项审批的规划版本"
            class="w-full"
          />
        </DialogField>
        <p v-if="assignableVersions.length === 0" class="text-caption text-warning">
          当前项目没有可纳入的规划版本，请先创建版本并完成版本立项审批。
        </p>
        <p v-if="assignError" class="text-caption text-danger">{{ assignError }}</p>
      </div>
      <template #footer>
        <Button :disabled="assignSaving" @click="assignOpen = false">取消</Button>
        <Button
          variant="primary"
          :disabled="assignSaving || !assignVersionId"
          @click="saveAssignment"
        >
          {{ assignSaving ? '纳入中…' : '纳入版本' }}
        </Button>
      </template>
    </Dialog>

    <Dialog
      v-model:open="batchAssignOpen"
      layout="form"
      size="large"
      title="批量加入产品版本"
      description="已选中的需求会统一纳入所选版本；原本关联其他版本的需求会被调整到目标版本。"
    >
      <div class="flex flex-col gap-3">
        <div class="max-h-48 space-y-1 overflow-y-auto rounded-panel border border-separator-soft p-2">
          <div
            v-for="requirement in selectedAssignableRequirements"
            :key="requirement.id"
            class="flex items-center gap-2 rounded-control px-1.5 py-1 text-caption"
          >
            <span class="tnum shrink-0 font-mono text-label-tertiary">
              #{{ formatRequirementNo(requirement.requirementNo) }}
            </span>
            <span class="min-w-0 flex-1 truncate text-label">{{ requirement.title }}</span>
            <span v-if="requirement.versionId" class="shrink-0 text-label-tertiary">
              {{ versionLabel(requirement) }}
            </span>
          </div>
        </div>
        <DialogField label="目标产品版本" required>
          <Select
            v-model="batchAssignVersionId"
            :items="assignableVersions.map((version) => ({
              value: String(version.id),
              label: version.versionNo,
            }))"
            placeholder="选择规划中的产品版本"
            class="w-full"
          />
        </DialogField>
        <p v-if="assignableVersions.length === 0" class="text-caption text-warning">
          当前项目没有可纳入的规划版本，请先创建版本并完成版本立项审批。
        </p>
        <p v-if="batchAssignError" class="text-caption text-danger">{{ batchAssignError }}</p>
      </div>
      <template #footer>
        <Button :disabled="batchAssignSaving" @click="batchAssignOpen = false">取消</Button>
        <Button
          variant="primary"
          :disabled="batchAssignSaving
            || !batchAssignVersionId
            || selectedAssignableRequirements.length === 0"
          @click="assignSelectedRequirements"
        >
          {{ batchAssignSaving
            ? '纳入中…'
            : `纳入 ${selectedAssignableRequirements.length} 条需求` }}
        </Button>
      </template>
    </Dialog>

    <Dialog
      v-model:open="importOpen"
      layout="form"
      size="large"
      title="导入需求池"
      description="按模板整理后导入 CSV；可选择直接关联一个规划中的产品版本。"
      :icon="Upload"
    >
      <div class="flex flex-col gap-3">
        <div class="flex flex-wrap items-center gap-2">
          <Button size="sm" @click="downloadTemplate">
            <Icon :as="Download" size="inline" />下载模板
          </Button>
          <input
            ref="importFileInput"
            type="file"
            accept=".csv,text/csv"
            class="hidden"
            @change="onImportFile"
          />
          <Button size="sm" @click="importFileInput?.click()">
            <Icon :as="Upload" size="inline" />选择 CSV
          </Button>
          <span v-if="importFileName" class="text-caption text-label-tertiary">
            {{ importFileName }} · {{ importRows.length }} 行
          </span>
        </div>
        <DialogField label="关联产品版本（可选）">
          <Select
            v-model="importVersionId"
            :items="importVersionItems"
            class="w-full"
          />
        </DialogField>
        <p class="text-caption text-label-tertiary">
          选择“不关联版本”时只进入需求池，稍后仍可加入版本；关联版本后会同时纳入该版本。
        </p>
        <p class="text-caption text-label-tertiary">
          重复检测按当前项目内的需求名称判断，忽略大小写和多余空格；同名需求会自动跳过。
        </p>
        <p v-if="importRows.length > 0" class="text-caption text-label-secondary">
          可导入 {{ importValidCount }} 条
          <span v-if="importDuplicateCount > 0">，重复 {{ importDuplicateCount }} 条将跳过</span>
          <span v-if="importOtherInvalidCount > 0">，其他异常 {{ importOtherInvalidCount }} 条</span>
        </p>
        <div v-if="importRows.length > 0" class="max-h-56 overflow-y-auto rounded-panel border border-separator-soft">
          <div
            v-for="(row, index) in evaluatedImportRows.slice(0, 20)"
            :key="`${index}-${row.title}`"
            class="flex items-center gap-2 border-b border-separator-soft px-2.5 py-2 last:border-b-0"
          >
            <span class="tnum w-5 shrink-0 text-caption text-label-tertiary">{{ index + 1 }}</span>
            <span class="min-w-0 flex-1 truncate text-caption" :class="row.error ? 'text-danger' : 'text-label'">
              {{ row.title || '未填写标题' }}
            </span>
            <span v-if="row.error" class="text-[10px] text-danger">{{ row.error }}</span>
          </div>
        </div>
        <p v-if="importError" class="text-caption text-danger">{{ importError }}</p>
        <p v-if="!canReview" class="text-caption text-warning">
          当前账号没有版本规划权限，只能导入到需求池，不能直接关联产品版本。
        </p>
        <p
          v-else-if="importVersionOptions.length === 0 && importProjectId"
          class="text-caption text-label-tertiary"
        >
          当前项目没有可关联的规划中版本；也可以先只导入需求池。
        </p>
      </div>
      <template #footer>
        <Button :disabled="importSaving" @click="importOpen = false">取消</Button>
        <Button
          variant="primary"
          :disabled="importSaving || importValidCount === 0"
          @click="runImport"
        >
          {{ importSaving ? '导入中…' : `导入 ${importValidCount} 条` }}
        </Button>
      </template>
    </Dialog>
  </div>
</template>
