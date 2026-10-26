<script setup lang="ts">
import {
  Download,
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
  listRequirements,
  listRequirementTrash,
  reviewRequirement,
  restoreRequirement,
  type RequirementTrashItem,
} from '@/api/requirements'
import RequirementPoolEditorDialog from '@/components/business/RequirementPoolEditorDialog.vue'
import Badge from '@/components/ui/badge/Badge.vue'
import Button from '@/components/ui/button/Button.vue'
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
import Textarea from '@/components/ui/input/Textarea.vue'
import Tooltip from '@/components/ui/tooltip/Tooltip.vue'
import { useToast } from '@/composables/useToast'
import { useAuthStore } from '@/stores/useAuthStore'
import { useTaskStore } from '@/stores/useTaskStore'
import type { Requirement, RequirementPoolStatus } from '@/types/api'
import type { Initiative, ProductVersion } from '@/types/delivery'
import type { ProjectMember } from '@/types/projectMember'
import { canActWithRoles } from '@/utils/deliveryWorkflow'
import { formatRelative } from '@/utils/format'

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
const statusFilter = ref<'ALL' | RequirementPoolStatus>('ALL')
const viewMode = ref('active')

const editorOpen = ref(false)
const editorMode = ref<'manual' | 'ai'>('manual')
const editingRequirement = ref<Requirement | null>(null)

const assignOpen = ref(false)
const assignSaving = ref(false)
const assignTarget = ref<Requirement | null>(null)
const assignVersionId = ref('')
const assignError = ref<string | null>(null)

const reviewOpen = ref(false)
const reviewSaving = ref(false)
const reviewTarget = ref<Requirement | null>(null)
const reviewComment = ref('')
const reviewError = ref<string | null>(null)

const importOpen = ref(false)
const importSaving = ref(false)
const importProjectId = ref('')
const importInitiativeId = ref('')
const importInitiatives = ref<Initiative[]>([])
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
  { key: 'title', label: '需求' },
  { key: 'version', label: '产品版本', width: '130px', hideBelow: 'md' },
  { key: 'poolStatus', label: '需求状态', width: '102px' },
  { key: 'source', label: '来源', width: '86px', hideBelow: 'lg' },
  { key: 'updated', label: '更新', width: '92px', hideBelow: 'xl' },
  { key: 'actions', label: '', width: '210px', align: 'right' },
]
const trashColumns: TableColumn[] = [
  { key: 'title', label: '需求' },
  { key: 'version', label: '产品版本', width: '130px', hideBelow: 'md' },
  { key: 'source', label: '来源', width: '86px', hideBelow: 'lg' },
  { key: 'deletedAt', label: '删除时间', width: '112px' },
  { key: 'actions', label: '', width: '110px', align: 'right' },
]
const isTrashView = computed(() => viewMode.value === 'trash')
const columns = computed(() => isTrashView.value ? trashColumns : activeColumns)
const viewItems = [
  { value: 'active', label: '需求池' },
  { value: 'trash', label: '回收站' },
]

const statusMeta: Record<
  RequirementPoolStatus,
  { label: string; tone: 'neutral' | 'accent' | 'success' | 'danger' }
> = {
  POOL: { label: '需求池', tone: 'neutral' },
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
      && requirement.poolStatus !== statusFilter.value
    ) return false
    if (!query) return true
    const reviewComment = 'reviewComment' in requirement ? requirement.reviewComment ?? '' : ''
    return [requirement.title, requirement.rawText, reviewComment]
      .some((value) => value.toLocaleLowerCase('zh-CN').includes(query))
  })
})
const importValidCount = computed(() =>
  importRows.value.filter((row) => row.error === null).length,
)
const canReview = computed(() => {
  const roles = members.value.find((member) => member.userId === auth.user?.id)?.roles ?? []
  return roles.includes('OWNER')
    || canActWithRoles('PRODUCT', roles, members.value.length)
})
const productWorkPending = computed(() =>
  requirements.value.some((requirement) =>
    requirement.poolStatus === 'POOL'
    || requirement.poolStatus === 'IN_REVIEW'
    || requirement.poolStatus === 'REJECTED',
  ),
)

function messageOf(value: unknown): string {
  if (value instanceof ApiError) return value.message
  return value instanceof Error ? value.message : '操作失败'
}

function versionLabel(requirement: Requirement | RequirementTrashItem) {
  if (requirement.versionId === null) return '未纳入'
  return versionMap.value.get(requirement.versionId)?.versionNo ?? `#${requirement.versionId}`
}

function sourceLabel(requirement: Requirement | RequirementTrashItem) {
  return requirement.source === 'AI' ? 'AI' : requirement.source === 'IMPORT' ? '导入' : '手工'
}

function requirementHint(row: Requirement | RequirementTrashItem) {
  return 'reviewComment' in row
    ? row.reviewComment || '等待纳入版本与评审'
    : '已移入回收站'
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
  } catch (value) {
    error.value = messageOf(value)
    requirements.value = []
    trash.value = []
    versions.value = []
    allInitiatives.value = []
    members.value = []
  } finally {
    loading.value = false
  }
}

function openEditor(mode: 'manual' | 'ai', requirement: Requirement | null = null) {
  editorMode.value = mode
  editingRequirement.value = requirement
  editorOpen.value = true
}

function openRequirementRow(requirement: Requirement) {
  if (requirement.poolStatus === 'POOL' || requirement.poolStatus === 'REJECTED') {
    openEditor('manual', requirement)
    return
  }
  toast.show(
    requirement.poolStatus === 'FORMAL'
      ? '正式需求已进入详细设计'
      : '需求正在评审',
    {
      description: requirement.poolStatus === 'FORMAL'
        ? '请到“版本”页继续推进详细设计与研发。'
        : '评审结束后才能继续编辑需求内容。',
    },
  )
}

function selectRow(row: Requirement | RequirementTrashItem) {
  if (isTrashView.value) return
  openRequirementRow(row as Requirement)
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
    ?? planningVersions.value[0]?.id
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
    toast.success('需求已纳入产品版本', '现在可以提交需求评审')
  } catch (value) {
    assignError.value = messageOf(value)
  } finally {
    assignSaving.value = false
  }
}

async function submitReview(requirement: Requirement) {
  try {
    await reviewRequirement(requirement.id, 'SUBMIT')
    await load()
    toast.success('需求已提交评审')
  } catch (value) {
    toast.warning('提交需求评审失败', messageOf(value))
  }
}

function openReview(requirement: Requirement) {
  reviewTarget.value = requirement
  reviewComment.value = ''
  reviewError.value = null
  reviewOpen.value = true
}

async function decideReview(action: 'APPROVE' | 'REJECT') {
  if (!reviewTarget.value || reviewSaving.value) return
  reviewSaving.value = true
  reviewError.value = null
  try {
    await reviewRequirement(reviewTarget.value.id, action, reviewComment.value.trim())
    reviewOpen.value = false
    await load()
    toast.success(action === 'APPROVE' ? '需求评审通过' : '需求已驳回')
  } catch (value) {
    reviewError.value = messageOf(value)
  } finally {
    reviewSaving.value = false
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
  importInitiativeId.value = ''
  importInitiatives.value = []
  importFileName.value = ''
  importRows.value = []
  importError.value = null
  importOpen.value = true
  void loadImportInitiatives()
}

async function loadImportInitiatives() {
  const projectId = Number(importProjectId.value)
  importInitiatives.value = []
  importInitiativeId.value = ''
  if (!projectId) return
  try {
    const items = await listInitiatives(projectId)
    importInitiatives.value = items.filter(
      (initiative) => initiative.status === 'APPROVED' && initiative.versionId !== null,
    )
    importInitiativeId.value = String(importInitiatives.value[0]?.id ?? '')
  } catch (value) {
    importError.value = messageOf(value)
  }
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
    || !importInitiativeId.value
    || importValidCount.value === 0
  ) return
  importSaving.value = true
  importError.value = null
  const failures: string[] = []
  let succeeded = 0
  for (const row of importRows.value) {
    if (row.error) continue
    try {
      await createRequirement({
        projectId: Number(importProjectId.value),
        initiativeId: Number(importInitiativeId.value),
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
    toast.success('需求导入完成', `已导入 ${succeeded} 条到需求池`)
  }
}

watch(
  () => activeProjectId.value,
  () => void load(),
)
watch(viewMode, () => {
  keyword.value = ''
  statusFilter.value = 'ALL'
})
watch(importProjectId, () => void loadImportInitiatives())
onMounted(() => void load())
</script>

<template>
  <div class="flex h-full min-h-0 flex-col">
    <ListToolbar>
      <template #title>
        <h1 class="text-body font-emphasis text-label">
          {{ isTrashView ? '需求回收站' : '需求池' }}
        </h1>
        <span class="tnum text-caption text-label-tertiary">{{ filtered.length }}</span>
      </template>
      <template #filters>
        <SegmentedControl v-model="viewMode" :items="viewItems" size="lg" />
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
            :placeholder="isTrashView ? '搜索已删除需求' : '搜索需求'"
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
      产品版本规划与需求评审需要“产品负责人”或“项目负责人”；当前账号可以录入和查看需求，但不能纳入版本或评审。
    </p>

    <div class="scroll-area min-h-0 flex-1 overflow-auto">
      <Table
        :columns="columns"
        :rows="filtered"
        :show-empty="false"
        @select="selectRow($event as Requirement | RequirementTrashItem)"
      >
        <template #cell="{ row, column }">
          <span v-if="column.key === 'title'" class="min-w-0">
            <span class="block truncate text-caption font-emphasis text-label">{{ row.title }}</span>
            <span class="mt-0.5 block truncate text-[10px] text-label-tertiary">
              #{{ row.id }} · {{ requirementHint(row as Requirement | RequirementTrashItem) }}
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
              :tone="statusMeta[(row as Requirement).poolStatus].tone"
            >
              {{ statusMeta[(row as Requirement).poolStatus].label }}
            </Badge>
          </template>
          <span v-else-if="column.key === 'source'" class="text-caption text-label-tertiary">
            {{ sourceLabel(row as Requirement | RequirementTrashItem) }}
          </span>
          <span v-else-if="column.key === 'updated'" class="text-caption text-label-tertiary">
            {{ formatRelative(String(rowUpdatedAt(row as Requirement | RequirementTrashItem))) }}
          </span>
          <span v-else-if="column.key === 'deletedAt'" class="text-caption text-label-tertiary">
            {{ formatRelative(String((row as RequirementTrashItem).deletedAt)) }}
          </span>
          <div v-else-if="column.key === 'actions'" class="flex justify-end gap-1">
            <template v-if="isTrashView">
              <Button size="sm" @click.stop="restoreFromTrash(row as RequirementTrashItem)">
                <Icon :as="RotateCcw" size="inline" />移回需求池
              </Button>
            </template>
            <template v-else-if="(row as Requirement).poolStatus === 'POOL' || (row as Requirement).poolStatus === 'REJECTED'">
              <Button size="sm" :disabled="!canReview" @click.stop="openAssign(row as Requirement)">
                {{ row.versionId ? '更换版本' : '加入版本' }}
              </Button>
              <Button
                v-if="row.versionId"
                size="sm"
                variant="primary"
                :disabled="!canReview"
                @click.stop="submitReview(row as Requirement)"
              >
                提交评审
              </Button>
              <IconButton
                label="移入回收站"
                size="sm"
                @click.stop="openDelete(row as Requirement)"
              >
                <Icon :as="Trash2" size="inline" />
              </IconButton>
            </template>
            <Button
              v-else-if="(row as Requirement).poolStatus === 'IN_REVIEW'"
              size="sm"
              variant="primary"
              :disabled="!canReview"
              @click.stop="openReview(row as Requirement)"
            >
              评审
            </Button>
            <Badge v-else tone="success">已进入研发</Badge>
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
            #{{ deleteTarget?.id }} · 删除后可在回收站移回需求池
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
      v-model:open="assignOpen"
      layout="form"
      title="纳入产品版本"
      description="需求纳入规划中的产品版本后，才能提交需求评审。"
    >
      <div class="flex flex-col gap-3">
        <div class="rounded-control bg-window px-3 py-2 text-caption text-label">
          {{ assignTarget?.title }}
        </div>
        <DialogField label="产品版本" required>
          <Select
            v-model="assignVersionId"
            :items="planningVersions.map((version) => ({
              value: String(version.id),
              label: version.versionNo,
            }))"
            placeholder="先创建一个规划中的版本"
            class="w-full"
          />
        </DialogField>
        <p v-if="planningVersions.length === 0" class="text-caption text-warning">
          当前项目没有规划中的产品版本，请先到“版本”页创建。
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
      v-model:open="reviewOpen"
      layout="form"
      title="需求评审"
      description="产品负责人确认需求价值、范围和验收标准。通过后需求成为正式需求并进入详细设计。"
    >
      <div class="flex flex-col gap-3">
        <div class="rounded-control bg-window px-3 py-2 text-caption text-label">
          {{ reviewTarget?.title }}
        </div>
        <p class="whitespace-pre-wrap text-caption text-label-secondary">
          {{ reviewTarget?.rawText }}
        </p>
        <p v-if="reviewTarget?.acceptanceText" class="rounded-control bg-content px-3 py-2 text-caption text-label-secondary">
          验收标准：{{ reviewTarget.acceptanceText }}
        </p>
        <DialogField label="评审意见">
          <Textarea v-model="reviewComment" :rows="3" placeholder="通过可选填；驳回必须填写原因" />
        </DialogField>
        <p v-if="reviewError" class="text-caption text-danger">{{ reviewError }}</p>
      </div>
      <template #footer>
        <Button :disabled="reviewSaving" @click="reviewOpen = false">取消</Button>
        <Button
          variant="danger"
          :disabled="reviewSaving || !reviewComment.trim()"
          @click="decideReview('REJECT')"
        >
          驳回
        </Button>
        <Button
          variant="primary"
          :disabled="reviewSaving"
          @click="decideReview('APPROVE')"
        >
          {{ reviewSaving ? '提交中…' : '评审通过' }}
        </Button>
      </template>
    </Dialog>

    <Dialog
      v-model:open="importOpen"
      layout="form"
      size="large"
      title="导入需求池"
      description="按模板整理后导入 CSV；每行会创建一条待版本规划的需求。"
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
        <DialogField label="所属版本立项" required>
          <Select
            v-model="importInitiativeId"
            :items="importInitiatives.map((initiative) => ({
              value: String(initiative.id),
              label: initiative.title,
            }))"
            placeholder="选择已批准的版本立项"
            class="w-full"
          />
        </DialogField>
        <p v-if="importRows.length > 0" class="text-caption text-label-secondary">
          可导入 {{ importValidCount }} 条，异常 {{ importRows.length - importValidCount }} 条
        </p>
        <div v-if="importRows.length > 0" class="max-h-56 overflow-y-auto rounded-panel border border-separator-soft">
          <div
            v-for="(row, index) in importRows.slice(0, 20)"
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
        <p v-if="importInitiatives.length === 0 && importProjectId" class="text-caption text-warning">
          当前项目没有已批准的版本立项，请先到“版本”页完成版本立项。
        </p>
      </div>
      <template #footer>
        <Button :disabled="importSaving" @click="importOpen = false">取消</Button>
        <Button
          variant="primary"
          :disabled="importSaving || importValidCount === 0 || !importInitiativeId"
          @click="runImport"
        >
          {{ importSaving ? '导入中…' : `导入 ${importValidCount} 条` }}
        </Button>
      </template>
    </Dialog>
  </div>
</template>
