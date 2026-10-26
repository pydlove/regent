<script setup lang="ts">
import { Pencil, Plus, RefreshCw, Search, UserRoundPlus, Users } from '@lucide/vue'
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import {
  addProjectMembers,
  listProjectMemberCandidates,
  listProjectMembers,
  updateProjectMemberRoles,
} from '@/api/projectMembers'
import { ApiError } from '@/api/http'
import Avatar from '@/components/ui/avatar/Avatar.vue'
import Button from '@/components/ui/button/Button.vue'
import Checkbox from '@/components/ui/checkbox/Checkbox.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import DialogField from '@/components/ui/dialog/DialogField.vue'
import EmptyState from '@/components/ui/empty-state/EmptyState.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import IconButton from '@/components/ui/icon-button/IconButton.vue'
import ListToolbar from '@/components/ui/list-toolbar/ListToolbar.vue'
import Select from '@/components/ui/select/Select.vue'
import { useToast } from '@/composables/useToast'
import { useProjectDialog } from '@/composables/useProjectDialog'
import { useAuthStore } from '@/stores/useAuthStore'
import { useOrganizationStore } from '@/stores/useOrganizationStore'
import { useTaskStore } from '@/stores/useTaskStore'
import type {
  ProjectMember,
  ProjectMemberCandidate,
  ProjectMemberRole,
} from '@/types/projectMember'
import type { OrganizationRole } from '@/types/organization'
import {
  projectMemberRoleDescriptions,
  projectMemberRoleLabels,
  projectMemberRoleOptions,
} from '@/utils/projectMemberRoles'

const store = useTaskStore()
const organizationStore = useOrganizationStore()
const auth = useAuthStore()
const toast = useToast()
const router = useRouter()
const { openDialog: openProjectDialog } = useProjectDialog()

const selectedProjectId = computed(() =>
  store.projectId !== null
    && store.projects.some((project) => project.id === store.projectId)
    ? String(store.projectId)
    : '',
)
const members = ref<ProjectMember[]>([])
const candidates = ref<ProjectMemberCandidate[]>([])
const loading = ref(false)
const candidateLoading = ref(false)
const candidateError = ref<string | null>(null)
const error = ref<string | null>(null)
const dialogOpen = ref(false)
const saving = ref(false)
const formUserIds = ref<number[]>([])
const quickRole = ref<ProjectMemberRole>()
const roleAssignments = ref<Record<number, ProjectMemberRole[]>>({})
const formKeyword = ref('')
const selectedRole = ref<ProjectMemberRole | null>(null)
const roleDialogOpen = ref(false)
const roleSaving = ref(false)
const roleMember = ref<ProjectMember | null>(null)
const roleDraft = ref<ProjectMemberRole[]>([])

const roleLabels = projectMemberRoleLabels
const roleDescriptions = projectMemberRoleDescriptions
const roleOptions = projectMemberRoleOptions
const roleCardRoles: ProjectMemberRole[] = [
  'OWNER',
  ...roleOptions.map((role) => role.value),
]
const MAX_BATCH_MEMBERS = 100
const organizationRoleLabels: Record<OrganizationRole, string> = {
  ORG_OWNER: '组织所有者',
  ORG_ADMIN: '组织管理员',
  PROJECT_MANAGER: '项目经理',
  ORG_MEMBER: '普通成员',
}
const currentProject = computed(() =>
  store.projects.find((project) => String(project.id) === selectedProjectId.value) ?? null,
)
const currentUserRoles = computed(
  () => members.value.find((member) => member.userId === auth.user?.id)?.roles ?? [],
)
const canManage = computed(() => currentUserRoles.value.includes('OWNER'))
const canManageFromOrganization = computed(
  () => !!currentProject.value?.organizationId
    && currentProject.value.organizationId === organizationStore.currentId
    && organizationStore.canManageProjects,
)
const canManageMembers = computed(() => canManage.value || canManageFromOrganization.value)
const activeMemberCount = computed(() => members.value.length)
const hasProductRole = computed(() =>
  members.value.some((member) => member.roles.includes('PRODUCT')),
)
const soloOwnerMode = computed(() =>
  members.value.length === 1 && members.value[0]?.roles.includes('OWNER'),
)
const hasVersionInitiator = computed(() => hasProductRole.value || soloOwnerMode.value)
const roleCards = computed(() =>
  roleCardRoles
    .map((role) => ({
      role,
      count: members.value.filter((member) => member.roles.includes(role)).length,
      description: roleDescriptions[role],
    })),
)
const filteredMembers = computed(() =>
  selectedRole.value
    ? members.value.filter((member) => member.roles.includes(selectedRole.value!))
    : members.value,
)
const selectedRoleLabel = computed(() =>
  selectedRole.value ? roleLabels[selectedRole.value] : null,
)
const roleMemberOwnsProject = computed(
  () => roleMember.value?.roles.includes('OWNER') ?? false,
)
const canSaveRoles = computed(
  () => !roleSaving.value && (roleMemberOwnsProject.value || roleDraft.value.length > 0),
)
const availableCandidates = computed(() =>
  candidates.value
    .filter((candidate) =>
      !!candidate.email
      && !members.value.some((member) => member.userId === candidate.userId),
    )
)
const filteredCandidates = computed(() => {
  const keyword = formKeyword.value.trim().toLocaleLowerCase('zh-CN')
  if (!keyword) return availableCandidates.value
  return availableCandidates.value.filter((candidate) =>
    [candidate.displayName, candidate.email ?? '', candidate.username]
      .some((value) => value.toLocaleLowerCase('zh-CN').includes(keyword)),
  )
})
const selectedCandidates = computed(() =>
  availableCandidates.value.filter((candidate) =>
    formUserIds.value.includes(candidate.userId)),
)
const allFilteredSelected = computed(() =>
  filteredCandidates.value.length > 0
  && filteredCandidates.value.every((candidate) =>
    formUserIds.value.includes(candidate.userId)),
)
const selectedNamesSummary = computed(() => {
  const names = selectedCandidates.value.slice(0, 6)
    .map((candidate) => candidate.displayName)
  return selectedCandidates.value.length > names.length
    ? `${names.join('、')} 等 ${selectedCandidates.value.length} 人`
    : names.join('、')
})
const selectedAssignmentsValid = computed(() =>
  selectedCandidates.value.length > 0
  && selectedCandidates.value.every((candidate) =>
    (roleAssignments.value[candidate.userId] ?? []).length > 0),
)

function messageOf(value: unknown): string {
  if (value instanceof ApiError) return value.message
  return value instanceof Error ? value.message : '团队信息读取失败'
}

function goToOrganization() {
  void router.push('/organizations')
}

async function load() {
  if (!selectedProjectId.value) {
    members.value = []
    candidates.value = []
    return
  }
  loading.value = true
  error.value = null
  try {
    members.value = await listProjectMembers(Number(selectedProjectId.value))
    if (canManageMembers.value) {
      await loadCandidates()
    } else {
      candidates.value = []
      candidateError.value = null
    }
  } catch (value) {
    error.value = messageOf(value)
    members.value = []
    candidates.value = []
    candidateError.value = null
  } finally {
    loading.value = false
  }
}

async function retryOrganizationLoad() {
  if (organizationStore.loading) return
  try {
    await organizationStore.load()
  } catch {
    // 错误已经落在 organizationStore.error，空状态会展示具体原因和重试入口。
  }
}

async function loadCandidates() {
  if (!selectedProjectId.value) {
    candidates.value = []
    candidateError.value = null
    return
  }
  candidateLoading.value = true
  candidateError.value = null
  try {
    candidates.value = await listProjectMemberCandidates(Number(selectedProjectId.value))
  } catch (value) {
    candidates.value = []
    candidateError.value = messageOf(value)
  } finally {
    candidateLoading.value = false
  }
}

async function openAdd() {
  formUserIds.value = []
  formKeyword.value = ''
  quickRole.value = undefined
  roleAssignments.value = {}
  dialogOpen.value = true
  await loadCandidates()
}

function toggleCandidate(userId: number, checked: boolean) {
  const next = new Set(formUserIds.value)
  if (checked) {
    if (!next.has(userId) && next.size >= MAX_BATCH_MEMBERS) {
      toast.warning('一次最多添加 100 名成员')
      return
    }
    next.add(userId)
    if (!roleAssignments.value[userId]) {
      roleAssignments.value = {
        ...roleAssignments.value,
        [userId]: [],
      }
    }
  }
  else {
    next.delete(userId)
    const assignments = { ...roleAssignments.value }
    delete assignments[userId]
    roleAssignments.value = assignments
  }
  formUserIds.value = [...next]
}

function toggleAllFilteredCandidates() {
  const next = new Set(formUserIds.value)
  if (allFilteredSelected.value) {
    const assignments = { ...roleAssignments.value }
    for (const candidate of filteredCandidates.value) {
      next.delete(candidate.userId)
      delete assignments[candidate.userId]
    }
    roleAssignments.value = assignments
  } else {
    const remaining = MAX_BATCH_MEMBERS - next.size
    const unselected = filteredCandidates.value
      .filter((candidate) => !next.has(candidate.userId))
    const selectable = unselected.slice(0, Math.max(0, remaining))
    const assignments = { ...roleAssignments.value }
    for (const candidate of selectable) {
      next.add(candidate.userId)
      if (!assignments[candidate.userId]) {
        assignments[candidate.userId] = []
      }
    }
    roleAssignments.value = assignments
    if (selectable.length < unselected.length) {
      toast.warning('一次最多添加 100 名成员', '请分批选择其余成员')
    }
  }
  formUserIds.value = [...next]
}

function memberRoles(userId: number) {
  return roleAssignments.value[userId] ?? []
}

function toggleMemberRole(userId: number, role: ProjectMemberRole) {
  const current = new Set(memberRoles(userId))
  if (current.has(role)) current.delete(role)
  else current.add(role)
  roleAssignments.value = {
    ...roleAssignments.value,
    [userId]: [...current],
  }
}

function applyQuickRoleToSelected() {
  const role = quickRole.value
  if (!role) return
  const assignments = { ...roleAssignments.value }
  for (const candidate of selectedCandidates.value) {
    assignments[candidate.userId] = [
      ...new Set([...(assignments[candidate.userId] ?? []), role]),
    ]
  }
  roleAssignments.value = assignments
}

function organizationRolesLabel(candidate: ProjectMemberCandidate) {
  return candidate.organizationRoles
    .map((role) => organizationRoleLabels[role])
    .join('、')
}

async function saveMembers() {
  if (saving.value || !selectedAssignmentsValid.value) return
  const members = selectedCandidates.value.map((candidate) => ({
    email: candidate.email!,
    roles: memberRoles(candidate.userId),
  }))
  saving.value = true
  try {
    await addProjectMembers(
      Number(selectedProjectId.value),
      members,
    )
    dialogOpen.value = false
    await load()
    toast.success(
      `已添加 ${members.length} 名团队成员`,
      '所有成员的项目角色已一次性配置完成',
    )
  } catch (value) {
    toast.warning('批量添加团队成员失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

function openRoleEditor(member: ProjectMember) {
  roleMember.value = member
  const editableRoles = new Set(roleOptions.map((role) => role.value))
  roleDraft.value = member.roles.filter((role) => editableRoles.has(role))
  roleDialogOpen.value = true
}

function visibleMemberRoles(roles: ProjectMemberRole[]): ProjectMemberRole[] {
  const visibleRoles = new Set(roleCardRoles)
  return roles.filter((role) => visibleRoles.has(role))
}

function toggleRoleDraft(role: ProjectMemberRole, checked: boolean) {
  const next = new Set(roleDraft.value)
  if (checked) next.add(role)
  else next.delete(role)
  roleDraft.value = [...next]
}

async function saveRoles() {
  const member = roleMember.value
  if (!member || !canSaveRoles.value) return
  roleSaving.value = true
  try {
    await updateProjectMemberRoles(
      Number(selectedProjectId.value),
      member.userId,
      roleDraft.value,
    )
    roleDialogOpen.value = false
    await load()
    toast.success(`已更新 ${member.displayName} 的项目角色`)
  } catch (value) {
    toast.warning('角色更新失败', messageOf(value))
  } finally {
    roleSaving.value = false
  }
}

function toggleRole(role: ProjectMemberRole) {
  selectedRole.value = selectedRole.value === role ? null : role
}

watch(
  () => store.projectId,
  () => {
    selectedRole.value = null
    void load()
  },
  { immediate: true },
)
</script>

<template>
  <div class="flex h-full min-h-0 flex-col">
    <ListToolbar>
      <template #title>
        <h1 class="text-body font-emphasis text-label">团队</h1>
        <span class="tnum text-caption text-label-tertiary">{{ activeMemberCount }}</span>
      </template>
      <template #actions>
        <IconButton label="刷新团队" size="lg" :disabled="loading" @click="load">
          <Icon :as="RefreshCw" size="large" :class="loading ? 'animate-spin' : ''" />
        </IconButton>
        <Button v-if="canManageMembers" variant="primary" size="lg" @click="openAdd">
            <Icon :as="Plus" size="inline" />添加团队成员
        </Button>
      </template>
    </ListToolbar>

    <p
      v-if="organizationStore.error || error"
      class="flex flex-wrap items-center gap-2 border-b border-danger/20 bg-danger/5 px-4 py-2 text-caption text-danger"
    >
      <span class="min-w-0 flex-1">{{ organizationStore.error || error }}</span>
      <button
        type="button"
        class="font-emphasis underline underline-offset-2 disabled:opacity-50"
        :disabled="organizationStore.loading || loading"
        @click="organizationStore.error ? retryOrganizationLoad() : load()"
      >
        重试
      </button>
    </p>

    <div class="scroll-area min-h-0 flex-1 overflow-auto p-4">
      <div
        v-if="selectedProjectId"
        class="mb-4 rounded-panel border border-accent/20 bg-accent/5 px-4 py-3"
      >
        <p class="text-caption font-emphasis text-label">团队角色属于整个项目</p>
        <p class="mt-1 text-caption text-label-tertiary">
          团队只在项目/产品层面配置一次；所有产品版本和研发流程共用同一套成员角色，不需要按版本重复设置。
        </p>
        <p v-if="!loading && selectedProjectId && !hasVersionInitiator" class="mt-2 text-caption text-warning">
          当前还没有产品负责人。请至少给一名团队成员分配“产品负责人”，否则无法发起版本立项。
        </p>
      </div>
      <div v-if="currentProject" class="mb-4 rounded-panel border border-separator-soft bg-content p-4 shadow-panel">
        <div class="flex items-start gap-3">
          <span class="grid size-9 shrink-0 place-items-center rounded-panel bg-accent/10 text-accent">
            <Icon :as="Users" size="regular" />
          </span>
          <div class="min-w-0 flex-1">
            <p class="text-body font-emphasis text-label">{{ currentProject.name }}</p>
            <p class="mt-0.5 text-caption text-label-tertiary">
              角色属于项目。同一个账号可以在不同项目里承担不同角色。
            </p>
          </div>
          <span class="rounded-badge bg-accent/10 px-2 py-0.5 text-caption text-accent">
            {{ activeMemberCount }} 位团队成员
          </span>
        </div>
        <div class="mt-4 grid gap-2 sm:grid-cols-2 xl:grid-cols-4">
          <button
            v-for="item in roleCards"
            :key="item.role"
            type="button"
            class="rounded-control border px-3 py-2.5 text-left transition-colors duration-100 ease-apple"
            :class="selectedRole === item.role
              ? 'border-accent/40 bg-accent/8'
              : 'border-separator-soft bg-window hover:border-accent/20 hover:bg-hover'"
            :aria-pressed="selectedRole === item.role"
            @click="toggleRole(item.role)"
          >
            <span class="flex items-center gap-2">
              <span class="min-w-0 flex-1 text-caption font-emphasis text-label">
                {{ roleLabels[item.role] }}
              </span>
              <span
                class="tnum grid min-w-6 place-items-center rounded-badge px-1.5 py-px text-caption font-emphasis"
                :class="selectedRole === item.role
                  ? 'bg-accent text-white'
                  : item.count > 0
                    ? 'bg-accent/10 text-accent'
                    : 'bg-window text-label-tertiary'"
              >
                {{ item.count }}
              </span>
            </span>
            <span class="mt-1 block text-[10px] leading-4 text-label-tertiary">
              {{ item.description }}
            </span>
          </button>
        </div>
      </div>

      <div
        v-if="members.length > 0"
        class="overflow-hidden rounded-panel border border-separator-soft bg-content shadow-panel"
      >
        <div class="flex items-center gap-2 border-b border-separator-soft px-4 py-2.5">
          <p class="text-caption font-emphasis text-label">
            {{ selectedRoleLabel ?? '全部角色' }}
          </p>
          <span class="tnum text-caption text-label-tertiary">{{ filteredMembers.length }}</span>
          <Button
            v-if="selectedRole"
            class="ml-auto"
            size="sm"
            variant="ghost"
            @click="selectedRole = null"
          >
            查看全部
          </Button>
        </div>
        <article
          v-for="member in filteredMembers"
          :key="member.userId"
          class="flex flex-wrap items-center gap-3 border-b border-separator-soft px-4 py-3 last:border-b-0"
        >
          <Avatar
            :src="member.avatarUrl ?? ''"
            :name="member.displayName"
            size="member"
          />
          <div class="min-w-40 flex-1">
            <p class="text-caption font-emphasis text-label">{{ member.displayName }}</p>
            <p class="mt-0.5 text-[10px] text-label-tertiary">
              {{ member.email || member.username }}
            </p>
          </div>
          <div class="flex flex-wrap items-center gap-1.5">
            <span
              v-for="role in visibleMemberRoles(member.roles)"
              :key="role"
              class="inline-flex items-center rounded-badge bg-accent/10 px-1.5 py-px text-caption text-accent"
            >
              {{ roleLabels[role] }}
            </span>
          </div>
          <Button
            v-if="canManageMembers"
            size="sm"
            variant="ghost"
            @click="openRoleEditor(member)"
          >
            <Icon :as="Pencil" size="inline" />调整角色
          </Button>
        </article>
        <p
          v-if="filteredMembers.length === 0"
          class="px-4 py-8 text-center text-caption text-label-tertiary"
        >
          该角色暂无团队成员，可点击右上角“添加团队成员”分配角色。
        </p>
      </div>

      <EmptyState
        v-else-if="!loading && selectedProjectId"
        title="还没有团队成员"
        description="先添加项目负责人、产品负责人、技术负责人、测试负责人和测试人员，流程门禁才能按角色流转。"
      >
        <Button v-if="canManageMembers" @click="openAdd">添加第一位团队成员</Button>
      </EmptyState>
      <EmptyState
        v-else-if="!selectedProjectId"
        :title="organizationStore.loading
          ? '正在加载组织…'
          : organizationStore.error
            ? '组织加载失败'
            : organizationStore.organizations.length === 0
              ? '先创建组织'
              : '先创建项目'"
        :description="organizationStore.error
          ? `${organizationStore.error}。请检查网络后重试，已有组织和项目不会丢失。`
          : organizationStore.organizations.length === 0
            ? '项目必须归属组织。创建组织后，才能创建研发项目并配置团队。'
            : '创建项目后先配置团队角色，所有版本共用的研发角色都来自这里。'"
      >
        <Button
          v-if="!organizationStore.loading && organizationStore.error"
          variant="primary"
          @click="retryOrganizationLoad"
        >
          重新加载组织
        </Button>
        <Button
          v-else-if="!organizationStore.loading && organizationStore.organizations.length === 0"
          variant="primary"
          @click="goToOrganization"
        >
          去组织管理
        </Button>
        <Button
          v-else-if="!organizationStore.loading && organizationStore.canManageProjects"
          variant="primary"
          @click="openProjectDialog"
        >
          新建项目
        </Button>
        <p
          v-else-if="!organizationStore.loading"
          class="text-caption text-warning"
        >
          当前组织角色不能创建项目，请让组织管理员创建项目后把你加入项目团队。
        </p>
      </EmptyState>
    </div>

    <Dialog
      v-model:open="dialogOpen"
      layout="form"
      size="wide"
      title="批量添加团队成员"
      description="在同一页面选择成员并配置各自的项目角色，最后一次提交完成整个团队设置。"
      :icon="UserRoundPlus"
    >
      <div class="grid gap-4 lg:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)]">
        <section class="overflow-hidden rounded-panel border border-separator-soft bg-content">
          <div class="flex flex-wrap items-center gap-2 border-b border-separator-soft bg-window px-3 py-2">
            <div class="relative min-w-48 flex-1">
              <Icon
                :as="Search"
                size="inline"
                class="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-label-tertiary"
              />
              <Input
                v-model="formKeyword"
                placeholder="搜索姓名、邮箱或账号"
                class="w-full pl-8"
              />
            </div>
            <span class="tnum text-caption text-label-tertiary">
              已选 {{ selectedCandidates.length }} / {{ MAX_BATCH_MEMBERS }} 人
            </span>
            <Button
              v-if="filteredCandidates.length > 0"
              size="sm"
              variant="ghost"
              @click="toggleAllFilteredCandidates"
            >
              {{ allFilteredSelected ? '取消全选' : '全选当前结果' }}
            </Button>
          </div>

          <div v-if="candidateLoading" class="px-3 py-8 text-center text-caption text-label-tertiary">
            正在读取组织成员…
          </div>
          <div v-else-if="candidateError" class="px-3 py-8 text-center text-caption text-danger">
            {{ candidateError }}
          </div>
          <div
            v-else-if="candidates.length === 0"
            class="px-3 py-8 text-center text-caption text-warning"
          >
            组织里还没有可加入团队的人员，请先在组织管理中邀请成员。
          </div>
          <div
            v-else-if="!candidates.some((candidate) => !!candidate.email)"
            class="px-3 py-8 text-center text-caption text-warning"
          >
            组织成员还没有可用的登录邮箱。
          </div>
          <div
            v-else-if="availableCandidates.length === 0"
            class="px-3 py-8 text-center text-caption text-warning"
          >
            组织成员都已加入团队，请在下方团队列表中调整角色。
          </div>
          <div
            v-else-if="filteredCandidates.length === 0"
            class="px-3 py-8 text-center text-caption text-label-tertiary"
          >
            没有匹配的组织成员。
          </div>
          <div v-else class="max-h-72 overflow-y-auto">
            <label
              v-for="candidate in filteredCandidates"
              :key="candidate.userId"
              class="flex cursor-pointer items-start gap-2.5 border-b border-separator-soft px-3 py-2.5 last:border-b-0"
              :class="formUserIds.includes(candidate.userId)
                ? 'bg-accent/5'
                : 'hover:bg-hover'"
            >
              <Checkbox
                class="mt-1"
                :model-value="formUserIds.includes(candidate.userId)"
                @update:model-value="toggleCandidate(candidate.userId, $event === true)"
              />
              <Avatar
                :src="candidate.avatarUrl ?? ''"
                :name="candidate.displayName"
                size="lg"
              />
              <span class="min-w-0 flex-1">
                <span class="flex flex-wrap items-center gap-x-2 gap-y-0.5">
                  <span class="truncate text-caption font-emphasis text-label">
                    {{ candidate.displayName }}
                  </span>
                  <span class="truncate text-[10px] text-label-tertiary">
                    {{ candidate.email }}
                  </span>
                </span>
                <span class="mt-0.5 block text-[10px] text-label-tertiary">
                  {{ organizationRolesLabel(candidate) || '组织成员' }}
                </span>
              </span>
            </label>
          </div>
        </section>

        <section class="overflow-hidden rounded-panel border border-separator-soft bg-content">
          <div class="border-b border-separator-soft bg-window px-3 py-2">
            <div class="flex items-center gap-2">
              <p class="text-caption font-emphasis text-label">成员与项目角色</p>
              <span class="tnum text-caption text-label-tertiary">
                {{ selectedCandidates.length }}
              </span>
              <span class="ml-auto text-[10px] text-label-tertiary">角色可多选</span>
            </div>
            <div v-if="selectedCandidates.length > 0" class="mt-2 flex items-center gap-2">
              <Select
                v-model="quickRole"
                :items="roleOptions"
                placeholder="选择统一角色"
                class="min-w-0 flex-1"
              />
              <Button size="sm" :disabled="!quickRole" @click="applyQuickRoleToSelected">
                应用到已选
              </Button>
            </div>
            <p
              v-if="selectedCandidates.length > 0"
              class="mt-1 truncate text-[10px] text-label-tertiary"
            >
              已选：{{ selectedNamesSummary }}
            </p>
          </div>

          <div
            v-if="selectedCandidates.length === 0"
            class="grid min-h-72 place-items-center px-6 py-10 text-center"
          >
            <div>
              <Icon :as="Users" size="large" class="mx-auto text-label-tertiary" />
              <p class="mt-2 text-caption text-label-tertiary">
                从左侧选择成员后，在这里一次配置每个人的角色。
              </p>
            </div>
          </div>
          <div v-else class="max-h-96 overflow-y-auto">
            <div
              v-for="candidate in selectedCandidates"
              :key="candidate.userId"
              class="border-b border-separator-soft px-3 py-3 last:border-b-0"
            >
              <div class="flex min-w-0 items-center gap-2">
                <Avatar
                  :src="candidate.avatarUrl ?? ''"
                  :name="candidate.displayName"
                  size="lg"
                />
                <div class="min-w-0 flex-1">
                  <p class="truncate text-caption font-emphasis text-label">
                    {{ candidate.displayName }}
                  </p>
                  <p class="truncate text-[10px] text-label-tertiary">
                    {{ candidate.email }}
                  </p>
                </div>
                <button
                  type="button"
                  class="rounded-control px-1.5 py-1 text-[10px] text-label-tertiary hover:bg-hover hover:text-label"
                  @click="toggleCandidate(candidate.userId, false)"
                >
                  移除
                </button>
              </div>
              <div class="mt-2 flex flex-wrap gap-1">
                <button
                  v-for="role in roleOptions"
                  :key="role.value"
                  type="button"
                  class="rounded-badge border px-1.5 py-0.5 text-[10px] transition-colors"
                  :class="memberRoles(candidate.userId).includes(role.value)
                    ? 'border-accent/30 bg-accent/10 text-accent'
                    : 'border-separator-soft bg-window text-label-tertiary hover:bg-hover hover:text-label'"
                  :aria-pressed="memberRoles(candidate.userId).includes(role.value)"
                  @click="toggleMemberRole(candidate.userId, role.value)"
                >
                  {{ role.label }}
                </button>
              </div>
              <p
                v-if="memberRoles(candidate.userId).length === 0"
                class="mt-1.5 text-[10px] text-danger"
              >
                请至少选择一个项目角色。
              </p>
            </div>
          </div>
        </section>
      </div>
      <template #footer>
        <Button :disabled="saving" @click="dialogOpen = false">取消</Button>
        <Button
          variant="primary"
          :disabled="saving || candidateLoading || !selectedAssignmentsValid"
          @click="saveMembers"
        >
          {{
            saving
              ? '添加中…'
              : candidateLoading
                ? '加载中…'
                : `添加 ${selectedCandidates.length} 名成员`
          }}
        </Button>
      </template>
    </Dialog>

    <Dialog
      v-model:open="roleDialogOpen"
      layout="form"
      size="large"
      title="调整项目角色"
      description="勾选该成员在团队中的角色；提交后整体替换，不需要先删除旧角色。"
      :icon="Pencil"
    >
      <div v-if="roleMember" class="flex flex-col gap-3">
        <div class="rounded-panel border border-separator-soft bg-window px-3 py-2.5">
          <p class="text-caption font-emphasis text-label">{{ roleMember.displayName }}</p>
          <p class="mt-0.5 text-[10px] text-label-tertiary">
            {{ roleMember.email || roleMember.username }}
          </p>
          <p v-if="roleMemberOwnsProject" class="mt-1 text-[10px] text-warning">
            项目负责人角色固定保留，不随本次调整移除。
          </p>
        </div>
        <DialogField label="项目角色" required>
          <div class="grid gap-1.5 sm:grid-cols-2">
            <label
              v-for="item in roleOptions"
              :key="item.value"
              class="flex cursor-pointer items-start gap-2 rounded-control border px-2.5 py-2"
              :class="roleDraft.includes(item.value)
                ? 'border-accent/30 bg-accent/5'
                : 'border-separator-soft bg-content hover:bg-hover'"
            >
              <Checkbox
                class="mt-0.5"
                :model-value="roleDraft.includes(item.value)"
                @update:model-value="toggleRoleDraft(item.value, $event === true)"
              />
              <span class="min-w-0">
                <span class="block text-caption font-emphasis text-label">
                  {{ item.label }}
                </span>
                <span class="mt-0.5 block text-[10px] text-label-tertiary">
                  {{ roleDescriptions[item.value] }}
                </span>
              </span>
            </label>
          </div>
        </DialogField>
        <p
          v-if="!roleMemberOwnsProject && roleDraft.length === 0"
          class="text-caption text-warning"
        >
          团队成员至少需要保留一个角色。
        </p>
      </div>
      <template #footer>
        <Button :disabled="roleSaving" @click="roleDialogOpen = false">取消</Button>
        <Button
          variant="primary"
          :disabled="!canSaveRoles"
          @click="saveRoles"
        >
          {{ roleSaving ? '保存中…' : '保存角色' }}
        </Button>
      </template>
    </Dialog>
  </div>
</template>
