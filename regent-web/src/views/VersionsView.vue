<script setup lang="ts">
import { Boxes, Pencil, Plus, RefreshCw, Search, X } from '@lucide/vue'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { listInitiatives } from '@/api/delivery'
import { ApiError } from '@/api/http'
import {
  closeProductVersion,
  confirmProductVersion,
  createProductVersion,
  listProductVersions,
  updateProductVersion,
} from '@/api/productVersions'
import { listProjectMembers } from '@/api/projectMembers'
import {
  assignRequirementVersion,
  listRequirements,
} from '@/api/requirements'
import Badge from '@/components/ui/badge/Badge.vue'
import Button from '@/components/ui/button/Button.vue'
import Checkbox from '@/components/ui/checkbox/Checkbox.vue'
import DeliveryFlowView from '@/views/DeliveryFlowView.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import DialogField from '@/components/ui/dialog/DialogField.vue'
import EmptyState from '@/components/ui/empty-state/EmptyState.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import IconButton from '@/components/ui/icon-button/IconButton.vue'
import Input from '@/components/ui/input/Input.vue'
import Textarea from '@/components/ui/input/Textarea.vue'
import { useToast } from '@/composables/useToast'
import { useAuthStore } from '@/stores/useAuthStore'
import { useTaskStore } from '@/stores/useTaskStore'
import type { Requirement } from '@/types/api'
import type { Initiative, ProductVersion, ProductVersionStatus } from '@/types/delivery'
import type { ProjectMember } from '@/types/projectMember'
import { canActWithRoles } from '@/utils/deliveryWorkflow'
import { formatRelative } from '@/utils/format'

const store = useTaskStore()
const auth = useAuthStore()
const toast = useToast()
const route = useRoute()

const versions = ref<ProductVersion[]>([])
const initiatives = ref<Initiative[]>([])
const requirements = ref<Requirement[]>([])
const members = ref<ProjectMember[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const keyword = ref('')
const selectedProjectId = computed(() =>
  store.projectId !== null
    && store.projects.some((project) => project.id === store.projectId)
    ? String(store.projectId)
    : '',
)
const selectedVersionId = ref<number | null>(null)

const createOpen = ref(false)
const saving = ref(false)
const createError = ref<string | null>(null)
const editingVersion = ref<ProductVersion | null>(null)
const versionNo = ref('')
const versionDescription = ref('')

const closeOpen = ref(false)
const closing = ref(false)
const closeError = ref<string | null>(null)

const addOpen = ref(false)
const addSaving = ref(false)
const addError = ref<string | null>(null)
const addIds = ref<number[]>([])

const statusMeta: Record<
  ProductVersionStatus,
  { label: string; tone: 'neutral' | 'accent' | 'success' }
> = {
  PLANNING: { label: '规划中', tone: 'accent' },
  CONFIRMED: { label: '已确认', tone: 'success' },
  CLOSED: { label: '已关闭', tone: 'neutral' },
}

const currentProject = computed(
  () => store.projects.find((project) => String(project.id) === selectedProjectId.value) ?? null,
)
const selectedVersion = computed(
  () => versions.value.find((version) => version.id === selectedVersionId.value) ?? null,
)
const filteredVersions = computed(() => {
  const query = keyword.value.trim().toLocaleLowerCase('zh-CN')
  if (!query) return versions.value
  return versions.value.filter((version) =>
    [version.versionNo, version.description ?? '', statusMeta[version.status].label]
      .some((value) => value.toLocaleLowerCase('zh-CN').includes(query)),
  )
})
const canManageVersions = computed(() => {
  const roles = members.value.find((member) => member.userId === auth.user?.id)?.roles ?? []
  return roles.includes('OWNER') || canActWithRoles('PRODUCT', roles, members.value.length)
})
const requirementsInVersion = computed(() =>
  selectedVersion.value
    ? requirements.value.filter((item) => item.versionId === selectedVersion.value?.id)
    : [],
)
const selectedVersionInitiative = computed(() =>
  selectedVersion.value
    ? initiatives.value.find(
        (initiative) => initiative.versionId === selectedVersion.value?.id,
      ) ?? null
    : null,
)
const selectedVersionReady = computed(
  () => selectedVersionInitiative.value?.status === 'APPROVED',
)
const initiativeForVersion = (version: ProductVersion) =>
  initiatives.value.find((initiative) => initiative.versionId === version.id) ?? null
const initiativeStatusLabel = (version: ProductVersion) => {
  const initiative = initiativeForVersion(version)
  if (!initiative) return '未立项'
  if (initiative.status === 'APPROVED') return '已批准'
  if (initiative.status === 'SUBMITTED') return '待审批'
  if (initiative.status === 'REJECTED') return '已驳回'
  if (initiative.status === 'CANCELLED') return '已取消'
  return '草稿'
}
const availablePoolRequirements = computed(() =>
  requirements.value.filter((item) =>
    item.poolStatus !== 'FORMAL'
    && item.poolStatus !== 'IN_REVIEW'
    && (item.versionId === null || item.versionId === selectedVersion.value?.id),
  ),
)
const selectedRequirementNames = computed(() =>
  availablePoolRequirements.value
    .filter((item) => addIds.value.includes(item.id))
    .map((item) => item.title),
)

function messageOf(value: unknown): string {
  if (value instanceof ApiError) return value.message
  return value instanceof Error ? value.message : '操作失败'
}

async function load() {
  if (!selectedProjectId.value) {
    versions.value = []
    requirements.value = []
    members.value = []
    initiatives.value = []
    return
  }
  loading.value = true
  error.value = null
  try {
    const projectId = Number(selectedProjectId.value)
    const [nextVersions, nextRequirements, nextMembers, nextInitiatives] = await Promise.all([
      listProductVersions(projectId),
      listRequirements(projectId),
      listProjectMembers(projectId),
      listInitiatives(projectId),
    ])
    versions.value = nextVersions
    requirements.value = nextRequirements
    members.value = nextMembers
    initiatives.value = nextInitiatives
    const requestedVersionId = Number(route.query.versionId)
    if (
      Number.isSafeInteger(requestedVersionId)
      && requestedVersionId > 0
      && nextVersions.some((version) => version.id === requestedVersionId)
    ) {
      selectedVersionId.value = requestedVersionId
      return
    }
    if (
      selectedVersionId.value
      && !nextVersions.some((version) => version.id === selectedVersionId.value)
    ) {
      selectedVersionId.value = null
    }
    selectedVersionId.value ??= nextVersions[0]?.id ?? null
  } catch (value) {
    error.value = messageOf(value)
    versions.value = []
    requirements.value = []
    members.value = []
    initiatives.value = []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingVersion.value = null
  versionNo.value = ''
  versionDescription.value = ''
  createError.value = null
  createOpen.value = true
}

function openEdit(version: ProductVersion) {
  editingVersion.value = version
  versionNo.value = version.versionNo
  versionDescription.value = version.description ?? ''
  createError.value = null
  createOpen.value = true
}

async function saveVersion() {
  if (saving.value || !selectedProjectId.value) return
  saving.value = true
  createError.value = null
  try {
    const saved = editingVersion.value
      ? await updateProductVersion(editingVersion.value.id, {
          description: versionDescription.value.trim(),
        })
      : await createProductVersion({
          projectId: Number(selectedProjectId.value),
          versionNo: versionNo.value.trim(),
          description: versionDescription.value.trim(),
        })
    createOpen.value = false
    await load()
    selectedVersionId.value = saved.id
    toast.success(
      editingVersion.value ? '产品版本已更新' : '产品版本已创建',
      saved.versionNo,
    )
    editingVersion.value = null
  } catch (value) {
    createError.value = messageOf(value)
  } finally {
    saving.value = false
  }
}

function openAddRequirements() {
  addIds.value = []
  addError.value = null
  addOpen.value = true
}

function toggleRequirement(id: number, checked: boolean) {
  const next = new Set(addIds.value)
  if (checked) next.add(id)
  else next.delete(id)
  addIds.value = [...next]
}

async function addRequirementsToVersion() {
  if (!selectedVersion.value || addIds.value.length === 0 || addSaving.value) return
  addSaving.value = true
  addError.value = null
  try {
    await Promise.all(
      addIds.value.map((id) => assignRequirementVersion(id, selectedVersion.value!.id)),
    )
    addOpen.value = false
    await load()
    toast.success('需求已纳入版本', `共 ${addIds.value.length} 条，等待需求评审`)
  } catch (value) {
    addError.value = messageOf(value)
  } finally {
    addSaving.value = false
  }
}

async function confirmVersion() {
  if (!selectedVersion.value) return
  try {
    await confirmProductVersion(selectedVersion.value.id)
    await load()
    toast.success('产品版本已确认', '新增需求范围已锁定，已有需求可继续评审')
  } catch (value) {
    toast.warning('确认版本失败', messageOf(value))
  }
}

function openCloseVersion() {
  if (!selectedVersion.value) return
  closeError.value = null
  closeOpen.value = true
}

async function closeVersion() {
  if (!selectedVersion.value || closing.value) return
  closing.value = true
  closeError.value = null
  try {
    await closeProductVersion(selectedVersion.value.id)
    closeOpen.value = false
    await load()
    toast.success('产品版本已关闭', '版本范围和历史需求记录会继续保留')
  } catch (value) {
    closeError.value = messageOf(value)
  } finally {
    closing.value = false
  }
}

watch(
  () => store.projectId,
  () => {
    selectedVersionId.value = null
    void load()
  },
  { immediate: true },
)
onMounted(() => void load())
</script>

<template>
  <div class="flex h-full min-h-0 min-w-0 max-lg:flex-col overflow-hidden">
    <aside class="flex min-h-0 min-w-0 w-full shrink-0 flex-col border-b border-separator bg-window lg:w-[320px] lg:border-b-0 lg:border-r">
      <div class="flex items-center gap-2 border-b border-separator px-3 py-2.5">
        <h1 class="text-body font-emphasis text-label">版本</h1>
        <span class="tnum text-caption text-label-tertiary">{{ versions.length }}</span>
        <div class="ml-auto flex items-center gap-1">
          <IconButton label="刷新版本" size="sm" :disabled="loading" @click="load">
            <Icon :as="RefreshCw" size="regular" :class="loading ? 'animate-spin' : ''" />
          </IconButton>
          <Button
            variant="primary"
            size="sm"
            :disabled="!selectedProjectId || !canManageVersions"
            @click="openCreate"
          >
            <Icon :as="Plus" size="inline" />新建
          </Button>
        </div>
      </div>
      <div class="border-b border-separator px-3 py-2">
        <div class="relative">
          <Icon
            :as="Search"
            size="inline"
            class="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-label-tertiary"
          />
          <Input v-model="keyword" size="sm" placeholder="搜索版本号或说明" class="pl-7 pr-7" />
          <button
            v-if="keyword"
            type="button"
            class="absolute right-1 top-1/2 flex size-5 -translate-y-1/2 items-center justify-center rounded-control text-label-tertiary hover:bg-hover"
            aria-label="清空版本搜索"
            @click="keyword = ''"
          >
            <Icon :as="X" size="inline" />
          </button>
        </div>
      </div>

      <p v-if="error" class="border-b border-danger/20 bg-danger/5 px-4 py-2 text-caption text-danger">
        {{ error }}
      </p>
      <p
        v-else-if="selectedProjectId && !canManageVersions"
        class="border-b border-warning/20 bg-warning/5 px-4 py-2 text-caption text-warning"
      >
        产品版本需要“产品负责人”或“项目负责人”维护；当前账号只能查看版本与需求范围。
      </p>

      <div class="scroll-area min-h-0 flex-1 overflow-auto max-lg:max-h-52">
        <div v-if="filteredVersions.length > 0" class="space-y-1 p-2">
          <button
            v-for="version in filteredVersions"
            :key="version.id"
            type="button"
            class="w-full rounded-panel border px-3 py-2.5 text-left transition-colors"
            :class="selectedVersionId === version.id
              ? 'border-accent/30 bg-accent/10'
              : 'border-transparent hover:border-separator-soft hover:bg-hover'"
            @click="selectedVersionId = version.id"
          >
            <div class="flex items-center gap-2">
              <span class="min-w-0 flex-1 truncate font-mono text-caption font-emphasis text-label">
                {{ version.versionNo }}
              </span>
              <Badge :tone="statusMeta[version.status].tone">
                {{ statusMeta[version.status].label }}
              </Badge>
            </div>
            <p class="mt-1 truncate text-caption text-label-secondary">
              {{ version.description || '暂无版本说明' }}
            </p>
            <div class="mt-1.5 flex flex-wrap items-center gap-1.5 text-[10px] text-label-tertiary">
              <span>{{ initiativeStatusLabel(version) }}</span>
              <span>·</span>
              <span>{{ requirements.filter((item) => item.versionId === version.id).length }} 条需求</span>
              <span>·</span>
              <span>{{ formatRelative(String(version.updatedAt)) }}</span>
            </div>
          </button>
        </div>
        <p v-else-if="!loading" class="p-4 text-caption text-label-tertiary">
          {{ !selectedProjectId ? '请先在页面顶部选择项目。' : keyword ? '没有匹配的版本。' : '还没有产品版本。' }}
        </p>
        <div v-if="!loading && selectedProjectId && versions.length === 0" class="px-3 pb-3">
          <Button
            variant="primary"
            size="sm"
            block
            :disabled="!canManageVersions"
            @click="openCreate"
          >
            创建第一个版本
          </Button>
        </div>
      </div>
    </aside>

    <main class="flex min-w-0 flex-1 flex-col overflow-hidden bg-window">
      <template v-if="selectedVersion">
        <div class="shrink-0 border-b border-separator p-4">
        <div class="flex items-start gap-2">
          <span class="grid size-9 shrink-0 place-items-center rounded-panel bg-accent/10 text-accent">
            <Icon :as="Boxes" size="regular" />
          </span>
          <div class="min-w-0 flex-1">
            <h2 class="font-mono text-title font-emphasis text-label">{{ selectedVersion.versionNo }}</h2>
            <p class="mt-1 text-caption text-label-tertiary">{{ currentProject?.name }}</p>
          </div>
          <Badge :tone="statusMeta[selectedVersion.status].tone">
            {{ statusMeta[selectedVersion.status].label }}
          </Badge>
        </div>

        <div class="mt-3 flex flex-wrap gap-2">
          <Badge
            :tone="selectedVersionInitiative?.status === 'APPROVED'
              ? 'success'
              : selectedVersionInitiative?.status === 'SUBMITTED'
                ? 'accent'
                : selectedVersionInitiative?.status === 'REJECTED'
                  ? 'danger'
                  : 'neutral'"
          >
            {{ selectedVersionInitiative
              ? `版本立项 · ${selectedVersion ? initiativeStatusLabel(selectedVersion) : ''}`
              : '尚未版本立项' }}
          </Badge>
          <Badge tone="neutral">{{ requirementsInVersion.length }} 条需求</Badge>
        </div>

        <p v-if="selectedVersion.description" class="mt-4 whitespace-pre-wrap rounded-panel border border-separator-soft bg-content p-3 text-caption text-label-secondary">
          {{ selectedVersion.description }}
        </p>

        <div class="mt-4 flex flex-wrap gap-2">
          <Button
            v-if="selectedVersion.status === 'PLANNING' && canManageVersions"
            size="sm"
            @click="openEdit(selectedVersion)"
          >
            <Icon :as="Pencil" size="inline" />编辑
          </Button>
          <Button
            v-if="selectedVersion.status === 'PLANNING'"
            variant="primary"
            size="sm"
            :disabled="!canManageVersions || !selectedVersionReady"
            @click="openAddRequirements"
          >
            <Icon :as="Plus" size="inline" />纳入已有需求
          </Button>
          <Button
            v-if="selectedVersion.status === 'PLANNING'"
            size="sm"
            :disabled="requirementsInVersion.length === 0 || !canManageVersions || !selectedVersionReady"
            @click="confirmVersion"
          >
            确认版本
          </Button>
          <Button
            v-if="selectedVersion.status !== 'CLOSED' && canManageVersions"
            variant="danger"
            size="sm"
            @click="openCloseVersion"
          >
            关闭版本
          </Button>
        </div>
        <p
          v-if="selectedVersion.status === 'PLANNING' && !selectedVersionReady"
          class="mt-2 text-caption text-warning"
        >
          完成当前版本的立项审批后，才能向版本纳入需求并确认版本。
        </p>
        </div>

        <DeliveryFlowView
          :key="selectedVersion.id"
          :version-id="selectedVersion.id"
          class="min-h-0 flex-1"
          @changed="load"
        />
      </template>
      <EmptyState
        v-else
        title="还没有可查看的版本"
        description="从左侧选择一个版本查看研发进度。"
      />
    </main>

    <Dialog
      v-model:open="createOpen"
      layout="form"
      size="large"
      :title="editingVersion ? '编辑产品版本' : '新建产品版本'"
      :description="editingVersion
        ? '规划中的版本可以调整说明；版本号创建后不可修改。'
        : '版本号用于确定本次产品发布范围；创建后可以把需求池中的需求纳入版本。'"
      :icon="Boxes"
    >
      <div class="flex flex-col gap-3">
        <DialogField label="产品版本号" required class="max-w-[340px]">
          <Input v-model="versionNo" placeholder="例如：v1.7.0" :disabled="editingVersion !== null" />
        </DialogField>
        <DialogField label="版本说明">
          <Textarea v-model="versionDescription" :rows="4" placeholder="本版本的目标、范围和发布主题" />
        </DialogField>
        <p v-if="createError" class="text-caption text-danger">{{ createError }}</p>
      </div>
      <template #footer>
        <Button :disabled="saving" @click="createOpen = false">取消</Button>
        <Button
          variant="primary"
          :disabled="saving || !versionNo.trim() || !canManageVersions"
          @click="saveVersion"
        >
          {{ saving ? '保存中…' : editingVersion ? '保存修改' : '创建版本' }}
        </Button>
      </template>
    </Dialog>

    <Dialog
      v-model:open="closeOpen"
      layout="form"
      title="关闭产品版本"
      description="关闭后不能再纳入新需求，也不能继续提交或评审该版本下的需求；历史记录会保留。"
      :icon="Boxes"
    >
      <div class="flex flex-col gap-3">
        <div class="rounded-control bg-window px-3 py-2 text-caption text-label">
          {{ selectedVersion?.versionNo }}
        </div>
        <p class="text-caption text-label-secondary">
          当前版本有 {{ requirementsInVersion.length }} 条需求，其中
          {{ requirementsInVersion.filter((item) => item.poolStatus === 'FORMAL').length }} 条已正式化。
        </p>
        <p v-if="closeError" class="text-caption text-danger">{{ closeError }}</p>
      </div>
      <template #footer>
        <Button :disabled="closing" @click="closeOpen = false">取消</Button>
        <Button variant="danger" :disabled="closing" @click="closeVersion">
          {{ closing ? '关闭中…' : '确认关闭' }}
        </Button>
      </template>
    </Dialog>

    <Dialog
      v-model:open="addOpen"
      layout="form"
      size="large"
      title="纳入需求池需求"
      description="选择尚未正式化的需求加入当前版本；纳入后再发起需求评审。"
      :icon="Plus"
    >
      <div class="flex flex-col gap-3">
        <div v-if="availablePoolRequirements.length > 0" class="max-h-80 space-y-1.5 overflow-y-auto">
          <label
            v-for="requirement in availablePoolRequirements"
            :key="requirement.id"
            class="flex cursor-pointer items-start gap-2 rounded-control border border-separator-soft px-2.5 py-2 hover:bg-hover"
          >
            <Checkbox
              :model-value="addIds.includes(requirement.id)"
              @update:model-value="toggleRequirement(requirement.id, $event === true)"
            />
            <span class="min-w-0">
              <span class="block truncate text-caption text-label">{{ requirement.title }}</span>
              <span class="mt-0.5 block text-[10px] text-label-tertiary">
                {{ requirement.source === 'AI' ? 'AI 生成'
                  : requirement.source === 'IMPORT' ? '批量导入' : '手工录入' }}
                · {{ requirement.poolStatus === 'REJECTED' ? '曾驳回' : '需求池' }}
              </span>
            </span>
          </label>
        </div>
        <p v-else class="text-caption text-label-tertiary">
          没有可纳入的需求。请先在需求池录入、导入或生成需求。
        </p>
        <p v-if="selectedRequirementNames.length > 0" class="text-caption text-label-tertiary">
          将纳入：{{ selectedRequirementNames.join('、') }}
        </p>
        <p v-if="addError" class="text-caption text-danger">{{ addError }}</p>
      </div>
      <template #footer>
        <Button :disabled="addSaving" @click="addOpen = false">取消</Button>
        <Button
          variant="primary"
          :disabled="addSaving || addIds.length === 0"
          @click="addRequirementsToVersion"
        >
          {{ addSaving ? '纳入中…' : `纳入 ${addIds.length} 条需求` }}
        </Button>
      </template>
    </Dialog>
  </div>
</template>
