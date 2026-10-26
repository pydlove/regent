<script setup lang="ts">
import { Pencil, Plus, RefreshCw, UserRoundPlus, Users } from '@lucide/vue'
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import {
  addProjectMember,
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
const formUserId = ref('')
const formRole = ref<ProjectMemberRole>('PRODUCT')
const selectedRole = ref<ProjectMemberRole | null>(null)
const roleDialogOpen = ref(false)
const roleSaving = ref(false)
const roleMember = ref<ProjectMember | null>(null)
const roleDraft = ref<ProjectMemberRole[]>([])

const roleLabels = projectMemberRoleLabels
const roleDescriptions = projectMemberRoleDescriptions
const roleOptions = projectMemberRoleOptions
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
  (Object.keys(roleLabels) as ProjectMemberRole[])
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
const candidateItems = computed(() =>
  candidates.value
    .filter((candidate) =>
      !!candidate.email
      && !members.value.some((member) => member.userId === candidate.userId),
    )
    .map((candidate) => ({
      value: String(candidate.userId),
      label: `${candidate.displayName} · ${candidate.email}`,
    })),
)
const selectedCandidate = computed(
  () => candidates.value.find((candidate) => String(candidate.userId) === formUserId.value) ?? null,
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
  formUserId.value = ''
  formRole.value = selectedRole.value ?? 'PRODUCT'
  dialogOpen.value = true
  await loadCandidates()
  formUserId.value = String(candidateItems.value[0]?.value ?? '')
}

async function saveMember() {
  if (saving.value) return
  const candidate = selectedCandidate.value
  if (!candidate?.email) return
  saving.value = true
  try {
    await addProjectMember(
      Number(selectedProjectId.value),
      candidate.email,
      formRole.value,
    )
    dialogOpen.value = false
    await load()
    toast.success('团队角色已添加', '该成员下次登录后即可按角色参与审批')
  } catch (value) {
    toast.warning('添加团队成员失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

function openRoleEditor(member: ProjectMember) {
  roleMember.value = member
  roleDraft.value = member.roles.filter((role) => role !== 'OWNER')
  roleDialogOpen.value = true
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
              v-for="role in member.roles"
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
        description="先添加项目负责人、产品负责人、技术负责人和测试负责人，流程门禁才能按角色流转。"
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
      title="添加团队成员"
      description="从项目所属组织成员中选择。团队角色属于整个项目，一个账号可以拥有多个角色。"
      :icon="UserRoundPlus"
    >
      <div class="flex flex-col gap-3">
        <DialogField label="组织成员" required>
          <Select
            v-model="formUserId"
            :items="candidateItems"
            placeholder="选择项目所属组织成员"
            class="w-full"
          />
        </DialogField>
        <p v-if="candidateLoading" class="text-caption text-label-tertiary">
          正在读取组织成员…
        </p>
        <p v-else-if="candidateError" class="text-caption text-danger">
          {{ candidateError }}
        </p>
        <p v-else-if="candidates.length === 0" class="text-caption text-warning">
          组织里还没有可加入团队的人员，请先在组织管理中邀请成员。
        </p>
        <p
          v-else-if="!candidates.some((candidate) => !!candidate.email)"
          class="text-caption text-warning"
        >
          组织成员还没有可用的登录邮箱。
        </p>
        <p v-else-if="candidateItems.length === 0" class="text-caption text-warning">
          组织成员都已加入团队，请在下方团队列表中调整角色。
        </p>
        <div v-if="selectedCandidate" class="rounded-control bg-window px-3 py-2">
          <p class="text-caption text-label">{{ selectedCandidate.displayName }}</p>
          <p class="mt-0.5 text-[10px] text-label-tertiary">
            组织角色：{{ selectedCandidate.organizationRoles.map((role) => organizationRoleLabels[role]).join('、') }}
            · 项目角色：{{ selectedCandidate.projectRoles.length
              ? selectedCandidate.projectRoles.map((role) => roleLabels[role]).join('、')
              : '尚未加入' }}
          </p>
        </div>
        <DialogField label="项目角色" required>
          <Select v-model="formRole" :items="roleOptions" class="w-full" />
        </DialogField>
        <p class="text-caption text-label-tertiary">
          {{ roleDescriptions[formRole] }}
        </p>
      </div>
      <template #footer>
        <Button :disabled="saving" @click="dialogOpen = false">取消</Button>
        <Button
          variant="primary"
          :disabled="saving || candidateLoading || !formUserId"
          @click="saveMember"
        >
          {{ saving ? '添加中…' : candidateLoading ? '加载中…' : '添加团队成员' }}
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
