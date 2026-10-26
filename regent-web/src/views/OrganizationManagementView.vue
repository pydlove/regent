<script setup lang="ts">
import {
  Building2,
  Check,
  Copy,
  Download,
  FileSpreadsheet,
  FolderKanban,
  KeyRound,
  MailPlus,
  Plus,
  RefreshCw,
  ShieldCheck,
  Trash2,
  Upload,
  UserPlus,
  X,
} from '@lucide/vue'
import { computed, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { ApiError } from '@/api/http'
import {
  addOrganizationMemberRole,
  createOrganizationAccount,
  downloadOrganizationMemberTemplate,
  exportOrganizationMembers,
  importOrganizationMembers,
  inviteOrganizationMember,
  listOrganizationAudit,
  listOrganizationInvitations,
  listOrganizationMembers,
  listOrganizationProjects,
  removeOrganizationMember,
  removeOrganizationMemberRole,
  resetOrganizationMemberPassword,
  revokeOrganizationInvitation,
  type OrganizationMemberImportResult,
} from '@/api/organizations'
import Badge from '@/components/ui/badge/Badge.vue'
import Avatar from '@/components/ui/avatar/Avatar.vue'
import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import DialogField from '@/components/ui/dialog/DialogField.vue'
import EmptyState from '@/components/ui/empty-state/EmptyState.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import IconButton from '@/components/ui/icon-button/IconButton.vue'
import Input from '@/components/ui/input/Input.vue'
import Textarea from '@/components/ui/input/Textarea.vue'
import ListToolbar from '@/components/ui/list-toolbar/ListToolbar.vue'
import SectionTabs from '@/components/ui/section-tabs/SectionTabs.vue'
import Select from '@/components/ui/select/Select.vue'
import { useToast } from '@/composables/useToast'
import { useAuthStore } from '@/stores/useAuthStore'
import { useOrganizationStore } from '@/stores/useOrganizationStore'
import { useTaskStore } from '@/stores/useTaskStore'
import type { Project } from '@/types/api'
import type {
  CreatedOrganizationInvitation,
  CreatedOrganizationAccount,
  OrganizationAudit,
  OrganizationInvitation,
  OrganizationMember,
  OrganizationRole,
} from '@/types/organization'
import type { ProjectMemberRole } from '@/types/projectMember'
import { appServerUrl } from '@/utils/appBase'
import { copyTextToClipboard } from '@/utils/clipboard'
import { saveBlob } from '@/utils/download'
import { formatDateTime, formatRelative } from '@/utils/format'
import { projectMemberRoleLabels } from '@/utils/projectMemberRoles'

type Tab = 'overview' | 'members' | 'invitations' | 'projects' | 'audit'

const organizationStore = useOrganizationStore()
const taskStore = useTaskStore()
const auth = useAuthStore()
const router = useRouter()
const toast = useToast()

const tab = ref<Tab>('overview')
const members = ref<OrganizationMember[]>([])
const invitations = ref<OrganizationInvitation[]>([])
const projects = ref<Project[]>([])
const audit = ref<OrganizationAudit[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

const roleLabels: Record<OrganizationRole, string> = {
  ORG_OWNER: '组织所有者',
  ORG_ADMIN: '组织管理员',
  PROJECT_MANAGER: '项目经理',
  ORG_MEMBER: '普通成员',
}
const roleDescriptions: Record<OrganizationRole, string> = {
  ORG_OWNER: '组织最高负责人，可转移所有权',
  ORG_ADMIN: '管理组织、成员、邀请和项目',
  PROJECT_MANAGER: '查看组织目录并管理项目',
  ORG_MEMBER: '仅访问被加入的项目',
}
const assignableRoles: OrganizationRole[] = ['ORG_ADMIN', 'PROJECT_MANAGER', 'ORG_MEMBER']
const roleOptions = assignableRoles.map((role) => ({
  value: role,
  label: roleLabels[role],
}))
const roleDrafts = reactive<Record<number, OrganizationRole>>({})
const projectRoleLabel = (role: ProjectMemberRole) => projectMemberRoleLabels[role] ?? role

const current = computed(() => organizationStore.current)
const canManageOrganization = computed(() => organizationStore.canManageOrganization)
const canAccessManagement = computed(() => organizationStore.canAccessManagement)
const currentRoleLabels = computed(() =>
  organizationStore.currentRoles.map((role) => roleLabels[role]),
)
const tabs = computed(() => [
  { value: 'overview', label: '概览' },
  { value: 'members', label: '成员', badge: members.value.length },
  ...(canManageOrganization.value
    ? [{ value: 'invitations', label: '邀请', badge: pendingInvitationCount.value }]
    : []),
  { value: 'projects', label: '项目', badge: projects.value.length },
  ...(canManageOrganization.value ? [{ value: 'audit', label: '审计' }] : []),
])
const pendingInvitationCount = computed(
  () => invitations.value.filter((item) => item.status === 'PENDING').length,
)
const activeTab = computed({
  get: () => tab.value,
  set: (value: string) => {
    tab.value = value as Tab
  },
})

const orgDialogOpen = ref(false)
const orgDialogMode = ref<'create' | 'edit'>('create')
const orgSaving = ref(false)
const orgError = ref<string | null>(null)
const orgCode = ref('')
const orgName = ref('')
const orgDescription = ref('')

const inviteDialogOpen = ref(false)
const inviteSaving = ref(false)
const inviteError = ref<string | null>(null)
const inviteEmail = ref('')
const inviteRole = ref<OrganizationRole>('ORG_MEMBER')
const createdInvitation = ref<CreatedOrganizationInvitation | null>(null)
const inviteLink = computed(() => createdInvitation.value?.invitationUrl
  ?? (createdInvitation.value
    ? `${appServerUrl()}/invite/${createdInvitation.value.token}`
    : ''))

const accountDialogOpen = ref(false)
const accountSaving = ref(false)
const accountError = ref<string | null>(null)
const accountEmail = ref('')
const accountDisplayName = ref('')
const accountRole = ref<OrganizationRole>('ORG_MEMBER')
const createdAccount = ref<CreatedOrganizationAccount | null>(null)

const memberImportOpen = ref(false)
const memberImportFile = ref<File | null>(null)
const memberImportInput = ref<HTMLInputElement | null>(null)
const memberImporting = ref(false)
const memberImportError = ref<string | null>(null)
const memberImportResult = ref<OrganizationMemberImportResult | null>(null)
const memberExporting = ref(false)
const memberTemplateDownloading = ref(false)

const resetDialogOpen = ref(false)
const resetTarget = ref<OrganizationMember | null>(null)
const resetting = ref(false)
const resetError = ref<string | null>(null)
const resetPassword = ref<string | null>(null)

const statusLabels: Record<string, string> = {
  PENDING: '待接受',
  ACCEPTED: '已加入',
  REVOKED: '已撤销',
  EXPIRED: '已过期',
}
const auditLabels: Record<string, string> = {
  ORGANIZATION_CREATED: '创建组织',
  ORGANIZATION_UPDATED: '更新组织',
  INVITATION_CREATED: '创建邀请',
  INVITATION_REVOKED: '撤销邀请',
  INVITATION_ACCEPTED: '接受邀请',
  ACCOUNT_CREATED: '创建账号',
  MEMBER_ROLE_ADDED: '添加成员角色',
  MEMBER_ROLE_REMOVED: '移除成员角色',
  MEMBER_REMOVED: '移出成员',
  MEMBER_PASSWORD_RESET: '重置成员密码',
}

function messageOf(value: unknown): string {
  if (value instanceof ApiError) return value.message
  return value instanceof Error ? value.message : '组织操作失败'
}

async function load() {
  if (!current.value || !canAccessManagement.value) {
    members.value = []
    invitations.value = []
    projects.value = []
    audit.value = []
    return
  }
  const organizationId = current.value.id
  loading.value = true
  error.value = null
  try {
    const [nextMembers, nextProjects, nextInvitations, nextAudit] = await Promise.all([
      listOrganizationMembers(organizationId),
      listOrganizationProjects(organizationId),
      canManageOrganization.value ? listOrganizationInvitations(organizationId) : Promise.resolve([]),
      canManageOrganization.value ? listOrganizationAudit(organizationId) : Promise.resolve([]),
    ])
    members.value = nextMembers
    projects.value = nextProjects
    invitations.value = nextInvitations
    audit.value = nextAudit
    for (const member of nextMembers) {
      if (!roleDrafts[member.userId]) roleDrafts[member.userId] = 'ORG_MEMBER'
    }
  } catch (value) {
    error.value = messageOf(value)
  } finally {
    loading.value = false
  }
}

function openCreateOrganization() {
  orgDialogMode.value = 'create'
  orgCode.value = ''
  orgName.value = ''
  orgDescription.value = ''
  orgError.value = null
  orgDialogOpen.value = true
}

function openEditOrganization() {
  if (!current.value) return
  orgDialogMode.value = 'edit'
  orgCode.value = current.value.code
  orgName.value = current.value.name
  orgDescription.value = current.value.description ?? ''
  orgError.value = null
  orgDialogOpen.value = true
}

async function saveOrganization() {
  if (orgSaving.value) return
  orgSaving.value = true
  orgError.value = null
  try {
    if (orgDialogMode.value === 'create') {
      await organizationStore.create({
        code: orgCode.value.trim(),
        name: orgName.value.trim(),
        description: orgDescription.value.trim(),
      })
      toast.success('组织已创建', '你已成为组织所有者')
    } else if (current.value) {
      await organizationStore.update(current.value.id, {
        name: orgName.value.trim(),
        description: orgDescription.value.trim(),
      })
      toast.success('组织信息已更新')
    }
    orgDialogOpen.value = false
    await load()
  } catch (value) {
    orgError.value = messageOf(value)
  } finally {
    orgSaving.value = false
  }
}

function openInvite() {
  inviteEmail.value = ''
  inviteRole.value = 'ORG_MEMBER'
  inviteError.value = null
  createdInvitation.value = null
  inviteDialogOpen.value = true
}

function openCreateAccount() {
  accountEmail.value = ''
  accountDisplayName.value = ''
  accountRole.value = 'ORG_MEMBER'
  accountError.value = null
  createdAccount.value = null
  accountDialogOpen.value = true
}

async function createAccount() {
  if (!current.value || accountSaving.value) return
  accountSaving.value = true
  accountError.value = null
  try {
    createdAccount.value = await createOrganizationAccount(
      current.value.id,
      accountEmail.value.trim(),
      accountDisplayName.value.trim(),
      accountRole.value,
    )
    await load()
    toast.success('账号已创建', '初始密码为 12345678，首次登录必须修改')
  } catch (value) {
    accountError.value = messageOf(value)
  } finally {
    accountSaving.value = false
  }
}

async function copyInitialPassword() {
  if (!createdAccount.value) return
  if (await copyTextToClipboard(createdAccount.value.initialPassword)) {
    toast.success('初始密码已复制')
  } else {
    toast.warning('复制失败', '请手动选择并复制初始密码')
  }
}

async function sendInvitation() {
  if (!current.value || inviteSaving.value) return
  inviteSaving.value = true
  inviteError.value = null
  try {
    createdInvitation.value = await inviteOrganizationMember(
      current.value.id,
      inviteEmail.value.trim(),
      inviteRole.value,
    )
    await load()
    if (createdInvitation.value.emailSent) {
      toast.success(
        '邀请邮件已发送',
        `已发送至 ${createdInvitation.value.invitation.email}，也保留了可复制的邀请链接`,
      )
    } else {
      toast.warning(
        '邀请已创建，但邮件发送失败',
        '请复制下方邀请链接发送给成员',
      )
    }
  } catch (value) {
    inviteError.value = messageOf(value)
  } finally {
    inviteSaving.value = false
  }
}

async function copyInviteLink() {
  if (!inviteLink.value) return
  if (await copyTextToClipboard(inviteLink.value)) {
    toast.success('邀请链接已复制')
  } else {
    toast.warning('复制失败', '请手动选择并复制邀请链接')
  }
}

async function addRole(member: OrganizationMember) {
  if (!current.value) return
  const role = roleDrafts[member.userId]
  if (!role) return
  try {
    members.value = await addOrganizationMemberRole(current.value.id, member.userId, role)
    toast.success(`已添加${roleLabels[role]}角色`)
  } catch (value) {
    toast.warning('添加角色失败', messageOf(value))
  }
}

async function removeRole(member: OrganizationMember, role: OrganizationRole) {
  if (!current.value) return
  try {
    await removeOrganizationMemberRole(current.value.id, member.userId, role)
    await load()
    toast.success(`已移除${roleLabels[role]}角色`)
  } catch (value) {
    toast.warning('移除角色失败', messageOf(value))
  }
}

async function removeMember(member: OrganizationMember) {
  if (!current.value) return
  try {
    await removeOrganizationMember(current.value.id, member.userId)
    await load()
    toast.success(`已将 ${member.displayName} 移出组织`)
  } catch (value) {
    toast.warning('移出成员失败', messageOf(value))
  }
}

function openResetPassword(member: OrganizationMember) {
  resetTarget.value = member
  resetError.value = null
  resetPassword.value = null
  resetDialogOpen.value = true
}

async function confirmResetPassword() {
  if (!current.value || !resetTarget.value || resetting.value) return
  resetting.value = true
  resetError.value = null
  try {
    const result = await resetOrganizationMemberPassword(
      current.value.id,
      resetTarget.value.userId,
    )
    resetPassword.value = result.initialPassword
    await load()
    toast.success(
      '密码已重置',
      `已重置为 ${result.initialPassword}，成员下次操作时必须修改密码`,
    )
  } catch (value) {
    resetError.value = messageOf(value)
  } finally {
    resetting.value = false
  }
}

async function copyResetPassword() {
  if (!resetPassword.value) return
  if (await copyTextToClipboard(resetPassword.value)) {
    toast.success('初始密码已复制')
  } else {
    toast.warning('复制失败', '请手动选择并复制初始密码')
  }
}

function openMemberImport() {
  memberImportFile.value = null
  memberImportError.value = null
  memberImportResult.value = null
  memberImportOpen.value = true
}

function chooseMemberImportFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] ?? null
  memberImportResult.value = null
  memberImportError.value = null
  if (!file) {
    memberImportFile.value = null
    return
  }
  if (!/\.(xlsx|xls)$/i.test(file.name)) {
    memberImportFile.value = null
    memberImportError.value = '请选择 .xlsx 或 .xls 文件'
    input.value = ''
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    memberImportFile.value = null
    memberImportError.value = '导入文件不能超过 2MB'
    input.value = ''
    return
  }
  memberImportFile.value = file
}

async function downloadMemberTemplate() {
  if (!current.value || memberTemplateDownloading.value) return
  memberTemplateDownloading.value = true
  memberImportError.value = null
  try {
    const downloaded = await downloadOrganizationMemberTemplate(current.value.id)
    saveBlob(downloaded.blob, downloaded.filename ?? 'Regent-组织成员导入模板.xlsx')
  } catch (value) {
    memberImportError.value = messageOf(value)
  } finally {
    memberTemplateDownloading.value = false
  }
}

async function exportMembers() {
  if (!current.value || memberExporting.value) return
  memberExporting.value = true
  try {
    const downloaded = await exportOrganizationMembers(current.value.id)
    saveBlob(downloaded.blob, downloaded.filename ?? `Regent-组织成员-${current.value.id}.xlsx`)
    toast.success('成员 Excel 已导出')
  } catch (value) {
    toast.warning('导出成员失败', messageOf(value))
  } finally {
    memberExporting.value = false
  }
}

async function submitMemberImport() {
  if (!current.value || !memberImportFile.value || memberImporting.value) return
  memberImporting.value = true
  memberImportError.value = null
  memberImportResult.value = null
  try {
    const result = await importOrganizationMembers(
      current.value.id,
      memberImportFile.value,
    )
    memberImportResult.value = result
    saveBlob(result.blob, result.filename ?? 'Regent-组织成员导入结果.xlsx')
    await load()
    if (result.failed > 0) {
      toast.warning(
        '导入完成，但存在失败行',
        `成功 ${result.total - result.failed} 行，失败 ${result.failed} 行，请查看结果 Excel`,
      )
    } else {
      toast.success(
        '成员导入完成',
        `新建 ${result.created} 个账号，加入 ${result.added} 人，跳过 ${result.skipped} 人`,
      )
    }
  } catch (value) {
    memberImportError.value = messageOf(value)
  } finally {
    memberImporting.value = false
  }
}

function downloadMemberImportResult() {
  const result = memberImportResult.value
  if (!result) return
  saveBlob(result.blob, result.filename ?? 'Regent-组织成员导入结果.xlsx')
}

async function revokeInvitation(invitation: OrganizationInvitation) {
  if (!current.value) return
  try {
    await revokeOrganizationInvitation(current.value.id, invitation.id)
    await load()
    toast.success('邀请已撤销')
  } catch (value) {
    toast.warning('撤销邀请失败', messageOf(value))
  }
}

async function openProject(project: Project) {
  await organizationStore.select(current.value?.id ?? project.organizationId ?? 0)
  taskStore.projectId = project.id
  await router.push('/projects')
}

watch(
  () => current.value?.id,
  (organizationId) => {
    if (!organizationId) return
    tab.value = 'overview'
    void load()
  },
  { immediate: true },
)
</script>

<template>
  <div class="flex h-full min-h-0 flex-col">
    <ListToolbar>
      <template #title>
        <h1 class="text-body font-emphasis text-label">组织管理</h1>
        <span v-if="current" class="tnum text-caption text-label-tertiary">
          {{ members.length }}
        </span>
      </template>
      <template #filters>
        <div v-if="current" class="flex flex-wrap items-center gap-1.5">
          <Badge v-for="role in currentRoleLabels" :key="role" tone="accent">{{ role }}</Badge>
          <span class="text-caption text-label-tertiary">{{ current.code }}</span>
        </div>
      </template>
      <template #actions>
        <IconButton label="刷新组织" size="lg" :disabled="loading" @click="load">
          <Icon :as="RefreshCw" size="large" :class="loading ? 'animate-spin' : ''" />
        </IconButton>
        <Button v-if="canManageOrganization" size="lg" @click="openCreateOrganization">
          <Icon :as="Plus" size="inline" />新建组织
        </Button>
        <Button v-if="canManageOrganization && current" size="lg" @click="openEditOrganization">
          编辑组织
        </Button>
        <Button
          v-if="canManageOrganization && current"
          size="lg"
          @click="openCreateAccount"
        >
          <Icon :as="KeyRound" size="inline" />创建账号
        </Button>
        <Button
          v-if="canManageOrganization && current"
          variant="primary"
          size="lg"
          @click="openInvite"
        >
          <Icon :as="UserPlus" size="inline" />邀请成员
        </Button>
        <Button v-else-if="!current" variant="primary" size="lg" @click="openCreateOrganization">
          <Icon :as="Plus" size="inline" />创建组织
        </Button>
      </template>
    </ListToolbar>

    <SectionTabs v-if="current" v-model="activeTab" :items="tabs" />

    <p v-if="error" class="border-b border-danger/20 bg-danger/5 px-4 py-2 text-caption text-danger">
      {{ error }}
    </p>

    <div
      v-if="current && !canAccessManagement"
      class="min-h-0 flex-1 overflow-auto p-6"
    >
      <EmptyState
        title="没有组织管理权限"
        description="只有组织所有者、组织管理员或项目经理可以进入组织管理。请联系所在组织的管理员分配角色。"
      />
    </div>

    <div v-else-if="!current" class="min-h-0 flex-1 overflow-auto p-6">
      <EmptyState
        title="创建你的第一个组织"
        description="组织用来管理成员、项目和研发角色。创建后，你会成为组织所有者。"
      >
        <Button variant="primary" @click="openCreateOrganization">
          <Icon :as="Building2" size="inline" />创建组织
        </Button>
      </EmptyState>
    </div>

    <div v-else class="scroll-area min-h-0 flex-1 overflow-auto p-4">
      <template v-if="tab === 'overview'">
        <section class="rounded-panel border border-separator-soft bg-content p-5 shadow-panel">
          <div class="flex flex-wrap items-start gap-4">
            <span class="grid size-12 shrink-0 place-items-center rounded-panel bg-accent/10 text-accent">
              <Icon :as="Building2" size="large" />
            </span>
            <div class="min-w-0 flex-1">
              <div class="flex flex-wrap items-center gap-2">
                <h2 class="text-title font-emphasis text-label">{{ current.name }}</h2>
                <Badge tone="success">正常</Badge>
              </div>
              <p class="mt-1 text-caption text-label-secondary">
                {{ current.description || '还没有填写组织说明。' }}
              </p>
              <p class="mt-2 text-caption text-label-tertiary">
                组织标识 {{ current.code }} · 创建于 {{ formatDateTime(current.createdAt) }}
              </p>
            </div>
            <Button v-if="canManageOrganization" @click="openEditOrganization">编辑信息</Button>
          </div>

          <div class="mt-5 grid gap-2 sm:grid-cols-3">
            <div class="rounded-panel border border-separator-soft bg-window px-4 py-3">
              <p class="text-[10px] text-label-tertiary">成员</p>
              <p class="tnum mt-1 text-title font-emphasis text-label">{{ members.length }}</p>
            </div>
            <div class="rounded-panel border border-separator-soft bg-window px-4 py-3">
              <p class="text-[10px] text-label-tertiary">项目</p>
              <p class="tnum mt-1 text-title font-emphasis text-label">{{ projects.length }}</p>
            </div>
            <div class="rounded-panel border border-separator-soft bg-window px-4 py-3">
              <p class="text-[10px] text-label-tertiary">待接受邀请</p>
              <p class="tnum mt-1 text-title font-emphasis text-label">{{ pendingInvitationCount }}</p>
            </div>
          </div>
        </section>

        <section class="mt-4 rounded-panel border border-separator-soft bg-content p-4">
          <div class="flex items-center gap-2">
            <Icon :as="ShieldCheck" size="inline" class="text-accent" />
            <p class="text-caption font-emphasis text-label">组织角色</p>
          </div>
          <div class="mt-3 grid gap-2 sm:grid-cols-2 xl:grid-cols-4">
            <div
              v-for="(description, role) in roleDescriptions"
              :key="role"
              class="rounded-control border border-separator-soft bg-window px-3 py-2.5"
            >
              <p class="text-caption font-emphasis text-label">{{ roleLabels[role] }}</p>
              <p class="mt-1 text-[10px] leading-4 text-label-tertiary">{{ description }}</p>
            </div>
          </div>
        </section>
      </template>

      <template v-else-if="tab === 'members'">
        <div v-if="canManageOrganization" class="mb-3 flex flex-wrap items-center justify-end gap-2">
          <Button size="sm" :disabled="memberExporting" @click="exportMembers">
            <Icon :as="Download" size="inline" />
            {{ memberExporting ? '导出中…' : '导出 Excel' }}
          </Button>
          <Button variant="primary" size="sm" @click="openMemberImport">
            <Icon :as="Upload" size="inline" />批量导入
          </Button>
        </div>
        <section class="overflow-hidden rounded-panel border border-separator-soft bg-content shadow-panel">
          <article
            v-for="member in members"
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
              <div v-if="member.projects.length > 0" class="mt-1.5 flex flex-wrap items-center gap-1">
                <span class="text-[10px] text-label-tertiary">项目：</span>
                <span
                  v-for="assignment in member.projects"
                  :key="assignment.projectId"
                  class="rounded-badge bg-window px-1.5 py-px text-[10px] text-label-secondary"
                >
                  {{ assignment.projectName }}
                  <template v-if="assignment.roles.length">
                    · {{ assignment.roles.map(projectRoleLabel).join('、') }}
                  </template>
                </span>
              </div>
              <p v-else class="mt-1 text-[10px] text-label-tertiary">尚未加入项目</p>
            </div>
            <div class="flex flex-wrap items-center gap-1.5">
              <span
                v-for="role in member.roles"
                :key="role"
                class="inline-flex items-center gap-1 rounded-badge bg-accent/10 px-1.5 py-px text-caption text-accent"
              >
                {{ roleLabels[role] }}
                <button
                  v-if="canManageOrganization && role !== 'ORG_OWNER'"
                  type="button"
                  class="text-label-tertiary hover:text-danger"
                  :aria-label="`移除 ${member.displayName} 的${roleLabels[role]}角色`"
                  @click="removeRole(member, role)"
                >
                  <Icon :as="X" size="inline" />
                </button>
              </span>
            </div>
            <div v-if="canManageOrganization && !member.roles.includes('ORG_OWNER')" class="flex items-center gap-1.5">
              <Select v-model="roleDrafts[member.userId]" :items="roleOptions" size="sm" class="w-32" />
              <Button size="sm" @click="addRole(member)">添加角色</Button>
              <IconButton
                v-if="member.userId !== auth.user?.id"
                label="重置密码"
                size="sm"
                @click="openResetPassword(member)"
              >
                <Icon :as="KeyRound" size="inline" />
              </IconButton>
              <IconButton label="移出组织" size="sm" @click="removeMember(member)">
                <Icon :as="Trash2" size="inline" />
              </IconButton>
            </div>
          </article>
        </section>
        <p v-if="members.length === 0 && !loading" class="p-4 text-caption text-label-tertiary">
          还没有组织成员。
        </p>
      </template>

      <template v-else-if="tab === 'invitations'">
        <section class="overflow-hidden rounded-panel border border-separator-soft bg-content shadow-panel">
          <article
            v-for="invitation in invitations"
            :key="invitation.id"
            class="flex flex-wrap items-center gap-3 border-b border-separator-soft px-4 py-3 last:border-b-0"
          >
            <span class="grid size-9 shrink-0 place-items-center rounded-full bg-warning/10 text-warning">
              <Icon :as="MailPlus" size="inline" />
            </span>
            <div class="min-w-48 flex-1">
              <p class="text-caption font-emphasis text-label">{{ invitation.email }}</p>
              <p class="mt-0.5 text-[10px] text-label-tertiary">
                {{ roleLabels[invitation.role] }} · 创建于 {{ formatRelative(invitation.createdAt) }}
              </p>
            </div>
            <Badge :tone="invitation.status === 'PENDING' ? 'accent' : invitation.status === 'ACCEPTED' ? 'success' : 'neutral'">
              {{ statusLabels[invitation.status] ?? invitation.status }}
            </Badge>
            <Button
              v-if="invitation.status === 'PENDING' && canManageOrganization"
              size="sm"
              @click="revokeInvitation(invitation)"
            >
              撤销
            </Button>
          </article>
          <div v-if="invitations.length === 0 && !loading" class="p-8 text-center">
            <p class="text-caption text-label-secondary">还没有邀请记录</p>
            <Button v-if="canManageOrganization" class="mt-3" @click="openInvite">
              邀请第一位成员
            </Button>
          </div>
        </section>
      </template>

      <template v-else-if="tab === 'projects'">
        <section class="grid gap-2 xl:grid-cols-2">
          <article
            v-for="project in projects"
            :key="project.id"
            class="rounded-panel border border-separator-soft bg-content p-4 shadow-panel"
          >
            <div class="flex items-start gap-3">
              <span class="grid size-9 shrink-0 place-items-center rounded-panel bg-accent/10 text-accent">
                <Icon :as="FolderKanban" size="regular" />
              </span>
              <div class="min-w-0 flex-1">
                <div class="flex flex-wrap items-center gap-2">
                  <p class="text-caption font-emphasis text-label">{{ project.name }}</p>
                  <Badge :tone="project.enabled ? 'success' : 'warning'">
                    {{ project.enabled ? '运行中' : '已暂停' }}
                  </Badge>
                </div>
                <p class="mt-1 font-mono text-[10px] text-label-tertiary">{{ project.code }}</p>
                <p class="mt-2 truncate text-caption text-label-tertiary">{{ project.path }}</p>
              </div>
              <Button size="sm" @click="openProject(project)">打开项目</Button>
            </div>
          </article>
        </section>
        <EmptyState
          v-if="projects.length === 0 && !loading"
          title="还没有项目"
          description="项目经理或组织管理员可以在顶部注册第一个项目。"
        />
      </template>

      <template v-else-if="tab === 'audit'">
        <section class="overflow-hidden rounded-panel border border-separator-soft bg-content shadow-panel">
          <article
            v-for="entry in audit"
            :key="entry.id"
            class="flex flex-wrap items-center gap-3 border-b border-separator-soft px-4 py-3 last:border-b-0"
          >
            <span class="grid size-8 shrink-0 place-items-center rounded-full bg-window text-label-tertiary">
              <Icon :as="ShieldCheck" size="inline" />
            </span>
            <div class="min-w-48 flex-1">
              <p class="text-caption text-label">
                {{ auditLabels[entry.action] ?? entry.action }}
              </p>
              <p class="mt-0.5 text-[10px] text-label-tertiary">
                操作人 #{{ entry.actorId }}
                <template v-if="entry.targetId"> · 对象 #{{ entry.targetId }}</template>
              </p>
            </div>
            <span class="text-caption text-label-tertiary">{{ formatDateTime(entry.createdAt) }}</span>
          </article>
          <p v-if="audit.length === 0 && !loading" class="p-6 text-center text-caption text-label-tertiary">
            还没有组织操作记录。
          </p>
        </section>
      </template>
    </div>

    <Dialog
      v-model:open="orgDialogOpen"
      layout="form"
      :title="orgDialogMode === 'create' ? '创建组织' : '编辑组织'"
      :description="orgDialogMode === 'create'
        ? '组织是成员和项目的归属边界。创建后你会成为组织所有者。'
        : '组织标识创建后不可修改。'"
      :icon="Building2"
    >
      <div class="flex flex-col gap-3">
        <DialogField label="组织标识" required hint="小写字母开头，可包含数字、下划线和连字符">
          <Input v-model="orgCode" :disabled="orgDialogMode === 'edit'" placeholder="hangzhou_rd" />
        </DialogField>
        <DialogField label="组织名称" required>
          <Input v-model="orgName" placeholder="例如：杭州研发中心" />
        </DialogField>
        <DialogField label="组织说明">
          <Textarea v-model="orgDescription" :rows="3" placeholder="这个组织主要负责什么" />
        </DialogField>
        <p v-if="orgError" class="text-caption text-danger">{{ orgError }}</p>
      </div>
      <template #footer>
        <Button @click="orgDialogOpen = false">取消</Button>
        <Button
          variant="primary"
          :disabled="orgSaving || !orgName.trim() || (orgDialogMode === 'create' && !orgCode.trim())"
          @click="saveOrganization"
        >
          {{ orgSaving ? '保存中…' : orgDialogMode === 'create' ? '创建组织' : '保存修改' }}
        </Button>
      </template>
    </Dialog>

    <Dialog
      v-model:open="inviteDialogOpen"
      layout="form"
      title="邀请成员"
      description="邀请链接 7 天有效。新邮箱接受后创建账号，已有账号则直接加入组织。"
      :icon="UserPlus"
    >
      <div class="flex flex-col gap-3">
        <DialogField label="邮箱" required>
          <Input v-model="inviteEmail" type="email" placeholder="name@example.com" />
        </DialogField>
        <DialogField label="组织角色" required>
          <Select v-model="inviteRole" :items="roleOptions" class="w-full" />
        </DialogField>
        <p class="text-caption text-label-tertiary">{{ roleDescriptions[inviteRole] }}</p>
        <div
          v-if="createdInvitation"
          class="rounded-panel border p-3"
          :class="
            createdInvitation.emailSent
              ? 'border-success/25 bg-success/5'
              : 'border-warning/25 bg-warning/5'
          "
        >
          <div
            class="flex items-center gap-2 text-caption font-emphasis"
            :class="createdInvitation.emailSent ? 'text-success' : 'text-warning'"
          >
            <Icon :as="Check" size="inline" />
            {{ createdInvitation.emailSent ? '邀请邮件已发送' : '邀请已创建，邮件发送失败' }}
          </div>
          <p class="mt-1 text-caption text-label-secondary">
            {{
              createdInvitation.emailSent
                ? `已发送至 ${createdInvitation.invitation.email}，也可以复制下面的链接：`
                : `请把下面链接发送给 ${createdInvitation.invitation.email}：`
            }}
          </p>
          <div class="mt-2 flex items-center gap-2">
            <code class="min-w-0 flex-1 select-all break-all rounded-control bg-content px-2 py-1.5 text-[10px] text-label-secondary">
              {{ inviteLink }}
            </code>
            <Button size="sm" @click="copyInviteLink">
              <Icon :as="Copy" size="inline" />复制
            </Button>
          </div>
        </div>
        <p v-if="inviteError" class="text-caption text-danger">{{ inviteError }}</p>
      </div>
      <template #footer>
        <Button @click="inviteDialogOpen = false">{{ createdInvitation ? '关闭' : '取消' }}</Button>
        <Button
          v-if="!createdInvitation"
          variant="primary"
          :disabled="inviteSaving || !inviteEmail.trim()"
          @click="sendInvitation"
        >
          {{ inviteSaving ? '创建中…' : '创建邀请' }}
        </Button>
      </template>
    </Dialog>

    <Dialog
      v-model:open="accountDialogOpen"
      layout="form"
      title="手动创建账号"
      description="账号初始密码统一为 12345678。成员首次登录后必须先修改密码。"
      :icon="KeyRound"
    >
      <div class="flex flex-col gap-3">
        <DialogField label="邮箱" required>
          <Input
            v-model="accountEmail"
            type="email"
            placeholder="name@example.com"
            :disabled="createdAccount !== null"
          />
        </DialogField>
        <DialogField label="显示名称" required>
          <Input
            v-model="accountDisplayName"
            placeholder="成员姓名"
            :disabled="createdAccount !== null"
          />
        </DialogField>
        <DialogField label="组织角色" required>
          <Select
            v-model="accountRole"
            :items="roleOptions"
            class="w-full"
            :disabled="createdAccount !== null"
          />
        </DialogField>
        <p class="text-caption text-label-tertiary">{{ roleDescriptions[accountRole] }}</p>

        <div
          v-if="createdAccount"
          class="rounded-panel border border-success/25 bg-success/5 p-3"
        >
          <div class="flex items-center gap-2 text-caption font-emphasis text-success">
            <Icon :as="Check" size="inline" />账号已创建
          </div>
          <p class="mt-1 text-caption text-label-secondary">
            登录名：{{ createdAccount.username }} · {{ createdAccount.email }}
          </p>
          <p class="mt-2 text-caption text-label-tertiary">
            初始密码统一为：
          </p>
          <div class="mt-2 flex items-center gap-2">
            <code class="min-w-0 flex-1 select-all break-all rounded-control bg-content px-2 py-1.5 font-mono text-caption text-label-secondary">
              {{ createdAccount.initialPassword }}
            </code>
            <Button size="sm" @click="copyInitialPassword">
              <Icon :as="Copy" size="inline" />复制
            </Button>
          </div>
          <p class="mt-2 text-[10px] text-warning">
            初始密码只保存哈希。成员首次登录时会被强制要求修改。
          </p>
        </div>
        <p v-if="accountError" class="text-caption text-danger">{{ accountError }}</p>
      </div>
      <template #footer>
        <Button @click="accountDialogOpen = false">{{ createdAccount ? '关闭' : '取消' }}</Button>
        <Button
          v-if="!createdAccount"
          variant="primary"
          :disabled="accountSaving || !accountEmail.trim() || !accountDisplayName.trim()"
          @click="createAccount"
        >
          {{ accountSaving ? '创建中…' : '创建账号' }}
        </Button>
      </template>
    </Dialog>

    <Dialog
      v-model:open="memberImportOpen"
      layout="form"
      title="批量导入成员与账号"
      description="按 Excel 模板导入。已有账号直接加入组织，新邮箱自动创建临时账号。"
      :icon="FileSpreadsheet"
    >
      <div class="flex flex-col gap-3">
        <div class="rounded-panel border border-accent/20 bg-accent/5 p-3">
          <p class="text-caption font-emphasis text-label">导入规则</p>
          <p class="mt-1 text-caption text-label-secondary">
            单次最多 50 行，文件不超过 2MB。可导入组织管理员、项目经理和普通成员；
            组织所有者不能通过 Excel 授予。
          </p>
          <div class="mt-2 flex flex-wrap items-center gap-2">
            <Button
              size="sm"
              :disabled="memberTemplateDownloading"
              @click="downloadMemberTemplate"
            >
              <Icon :as="Download" size="inline" />
              {{ memberTemplateDownloading ? '下载中…' : '下载 Excel 模板' }}
            </Button>
            <span class="text-[10px] text-label-tertiary">
              模板包含填写说明和角色下拉选项
            </span>
          </div>
        </div>

        <input
          ref="memberImportInput"
          type="file"
          class="hidden"
          accept=".xlsx,.xls,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-excel"
          @change="chooseMemberImportFile"
        >
        <div class="flex flex-wrap items-center gap-2">
          <Button
            size="sm"
            :disabled="memberImporting"
            @click="memberImportInput?.click()"
          >
            <Icon :as="Upload" size="inline" />选择 Excel
          </Button>
          <span class="min-w-0 flex-1 truncate text-caption text-label-secondary">
            {{ memberImportFile?.name ?? '尚未选择文件' }}
          </span>
        </div>

        <div
          v-if="memberImportResult"
          class="rounded-panel border border-success/25 bg-success/5 p-3"
        >
          <div class="flex items-center gap-2 text-caption font-emphasis text-success">
            <Icon :as="Check" size="inline" />导入处理完成
          </div>
          <div class="mt-2 grid grid-cols-2 gap-2 text-caption sm:grid-cols-5">
            <span>总计 <strong class="tnum">{{ memberImportResult.total }}</strong></span>
            <span>新建 <strong class="tnum">{{ memberImportResult.created }}</strong></span>
            <span>加入 <strong class="tnum">{{ memberImportResult.added }}</strong></span>
            <span>跳过 <strong class="tnum">{{ memberImportResult.skipped }}</strong></span>
            <span :class="memberImportResult.failed > 0 ? 'text-danger' : ''">
              失败 <strong class="tnum">{{ memberImportResult.failed }}</strong>
            </span>
          </div>
          <p class="mt-2 text-[10px] text-warning">
            结果 Excel 已自动下载。新建账号初始密码统一为 12345678；
            请通过安全渠道发送账号和初始密码，并提醒成员首次登录后立即修改。
          </p>
          <Button class="mt-2" size="sm" @click="downloadMemberImportResult">
            <Icon :as="Download" size="inline" />重新下载结果 Excel
          </Button>
        </div>

        <p v-if="memberImportError" class="text-caption text-danger">
          {{ memberImportError }}
        </p>
      </div>
      <template #footer>
        <Button @click="memberImportOpen = false">
          {{ memberImportResult ? '关闭' : '取消' }}
        </Button>
        <Button
          v-if="!memberImportResult"
          variant="primary"
          :disabled="memberImporting || !memberImportFile"
          @click="submitMemberImport"
        >
          {{ memberImporting ? '导入中…' : '开始导入' }}
        </Button>
      </template>
    </Dialog>

    <Dialog
      v-model:open="resetDialogOpen"
      layout="form"
      title="重置成员密码"
      description="密码会被重置为统一初始密码，成员下次操作时必须先修改密码。"
      :icon="KeyRound"
    >
      <div class="flex flex-col gap-3">
        <div class="rounded-panel border border-warning/25 bg-warning/5 p-3">
          <p class="text-caption font-emphasis text-label">
            确认重置 {{ resetTarget?.displayName }} 的密码？
          </p>
          <p class="mt-1 text-caption text-label-secondary">
            {{ resetTarget?.email || resetTarget?.username }}
          </p>
          <p class="mt-2 text-caption text-warning">
            重置后旧密码立即失效；对方当前已登录的会话也会被要求先改密。
          </p>
        </div>

        <div
          v-if="resetPassword"
          class="rounded-panel border border-success/25 bg-success/5 p-3"
        >
          <div class="flex items-center gap-2 text-caption font-emphasis text-success">
            <Icon :as="Check" size="inline" />密码已重置
          </div>
          <div class="mt-2 flex items-center gap-2">
            <code class="min-w-0 flex-1 select-all break-all rounded-control bg-content px-2 py-1.5 font-mono text-caption text-label-secondary">
              {{ resetPassword }}
            </code>
            <Button size="sm" @click="copyResetPassword">
              <Icon :as="Copy" size="inline" />复制
            </Button>
          </div>
          <p class="mt-2 text-[10px] text-warning">
            请通过安全渠道告知成员，并提醒首次登录后立即修改。
          </p>
        </div>

        <p v-if="resetError" class="text-caption text-danger">{{ resetError }}</p>
      </div>
      <template #footer>
        <Button @click="resetDialogOpen = false">
          {{ resetPassword ? '关闭' : '取消' }}
        </Button>
        <Button
          v-if="!resetPassword"
          variant="primary"
          :disabled="resetting"
          @click="confirmResetPassword"
        >
          {{ resetting ? '重置中…' : '确认重置为 12345678' }}
        </Button>
      </template>
    </Dialog>
  </div>
</template>
