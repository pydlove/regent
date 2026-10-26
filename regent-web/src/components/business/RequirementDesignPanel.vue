<script setup lang="ts">
import {
  Check,
  Paperclip,
  Pencil,
  RotateCcw,
  Send,
  Trash2,
  Upload,
  UserRoundPlus,
} from '@lucide/vue'
import { computed, reactive, ref, watch } from 'vue'

import {
  actRequirementDelivery,
  createDesignModule,
  deleteDesignModule,
  reopenDesignModule,
  saveModuleDesign as saveModuleDesignRequest,
  submitDesignModule,
  updateDesignModule,
  uploadDesignAttachment,
} from '@/api/delivery'
import { ApiError } from '@/api/http'
import { apiResourceUrl } from '@/api/http'
import Badge from '@/components/ui/badge/Badge.vue'
import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import DialogField from '@/components/ui/dialog/DialogField.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import Input from '@/components/ui/input/Input.vue'
import Textarea from '@/components/ui/input/Textarea.vue'
import Select from '@/components/ui/select/Select.vue'
import { useToast } from '@/composables/useToast'
import type { DesignModule, RequirementDelivery } from '@/types/delivery'
import type { ProjectMember, ProjectMemberRole } from '@/types/projectMember'
import { formatBytes } from '@/utils/format'
import { projectMemberRoleLabels } from '@/utils/projectMemberRoles'

const props = defineProps<{
  delivery: RequirementDelivery
  members: ProjectMember[]
  currentUserId: number | null
  canTechLead: boolean
}>()

const emit = defineEmits<{
  changed: []
}>()

const toast = useToast()
const outlineSummary = ref('')
const outlineContent = ref('')
const outlineSaving = ref(false)
const outlineUploading = ref(false)
const outlineError = ref<string | null>(null)
const outlineFileInput = ref<HTMLInputElement | null>(null)

const moduleDialogOpen = ref(false)
const moduleAssignmentOpen = ref(false)
const moduleSaving = ref(false)
const moduleUploading = ref(false)
const moduleError = ref<string | null>(null)
const editingModule = ref<DesignModule | null>(null)
const moduleFileInput = ref<HTMLInputElement | null>(null)
const moduleForm = reactive({
  name: '',
  description: '',
  assigneeId: '',
  designSummary: '',
  designContent: '',
})

const outlineAttachments = computed(() =>
  props.delivery.designAttachments.filter((attachment) => attachment.moduleId === null),
)
const modules = computed(() => props.delivery.modules)
const allModulesSubmitted = computed(
  () => modules.value.length > 0
    && modules.value.every((module) => module.status === 'SUBMITTED'),
)
const developerOptions = computed(() => {
  const people = new Map<number, ProjectMember>()
  for (const member of props.members) {
    const canDevelop = member.roles.some(
      (role) => role === 'DEVELOPER' || role === 'TECH_LEAD',
    )
    if (canDevelop) people.set(member.userId, member)
  }
  return [...people.values()].map((member) => ({
    value: String(member.userId),
    label: `${member.displayName} · ${roleSummary(member.roles)}`,
  }))
})

watch(
  () => props.delivery.id,
  () => {
    outlineSummary.value = props.delivery.outlineDesignSummary ?? ''
    outlineContent.value = props.delivery.outlineDesignContent ?? ''
    outlineError.value = null
  },
  { immediate: true },
)

function roleSummary(roles: ProjectMemberRole[]): string {
  const labels = roles
    .filter((role) => role === 'DEVELOPER' || role === 'TECH_LEAD')
    .map((role) => projectMemberRoleLabels[role])
  return labels.join(' / ')
}

function memberName(userId: number): string {
  return props.members.find((member) => member.userId === userId)?.displayName
    ?? `用户 #${userId}`
}

function moduleAttachments(moduleId: number) {
  return props.delivery.designAttachments.filter(
    (attachment) => attachment.moduleId === moduleId,
  )
}

function canEditModule(module: DesignModule): boolean {
  return props.canTechLead || module.assigneeId === props.currentUserId
}

function moduleStatusMeta(module: DesignModule): {
  label: string
  tone: 'neutral' | 'success' | 'warning'
} {
  if (module.status === 'SUBMITTED') return { label: '已提交', tone: 'success' }
  if (module.status === 'REVISION_REQUIRED') return { label: '需修改', tone: 'warning' }
  return { label: '待设计', tone: 'neutral' }
}

function messageOf(error: unknown, fallback: string): string {
  return error instanceof ApiError
    ? error.message
    : error instanceof Error
      ? error.message
      : fallback
}

async function uploadOutline(file: File) {
  outlineUploading.value = true
  outlineError.value = null
  try {
    await uploadDesignAttachment(props.delivery.id, file)
    emit('changed')
    toast.success('概要设计附件已上传')
  } catch (error) {
    outlineError.value = messageOf(error, '概要设计附件上传失败')
  } finally {
    outlineUploading.value = false
  }
}

async function onOutlineFiles(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (file) await uploadOutline(file)
}

async function submitOutlineDesign() {
  if (outlineSaving.value) return
  outlineSaving.value = true
  outlineError.value = null
  try {
    await actRequirementDelivery(props.delivery.id, {
      action: 'SUBMIT_OUTLINE_DESIGN',
      designSummary: outlineSummary.value.trim(),
      designContent: outlineContent.value.trim(),
    })
    emit('changed')
    toast.success('概要设计已提交', '进入模块详细设计阶段')
  } catch (error) {
    outlineError.value = messageOf(error, '概要设计提交失败')
  } finally {
    outlineSaving.value = false
  }
}

function openCreateModule() {
  editingModule.value = null
  moduleForm.name = ''
  moduleForm.description = ''
  moduleForm.assigneeId = developerOptions.value[0]?.value ?? ''
  moduleError.value = null
  moduleAssignmentOpen.value = true
}

function openEditAssignment(module: DesignModule) {
  editingModule.value = module
  moduleForm.name = module.name
  moduleForm.description = module.description ?? ''
  moduleForm.assigneeId = String(module.assigneeId)
  moduleError.value = null
  moduleAssignmentOpen.value = true
}

async function saveModuleAssignment() {
  if (
    moduleSaving.value
    || !moduleForm.name.trim()
    || !moduleForm.assigneeId
  ) return
  moduleSaving.value = true
  moduleError.value = null
  try {
    const input = {
      name: moduleForm.name.trim(),
      description: moduleForm.description.trim() || null,
      assigneeId: Number(moduleForm.assigneeId),
    }
    if (editingModule.value) {
      await updateDesignModule(props.delivery.id, editingModule.value.id, input)
    } else {
      await createDesignModule(props.delivery.id, input)
    }
    moduleAssignmentOpen.value = false
    emit('changed')
    toast.success(editingModule.value ? '模块负责人已调整' : '模块已分配')
  } catch (error) {
    moduleError.value = messageOf(error, '模块派工失败')
  } finally {
    moduleSaving.value = false
  }
}

function openModuleDesign(module: DesignModule) {
  editingModule.value = module
  moduleForm.designSummary = module.designSummary ?? ''
  moduleForm.designContent = module.designContent ?? ''
  moduleError.value = null
  moduleDialogOpen.value = true
}

async function onModuleFiles(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  const module = editingModule.value
  if (!file || !module) return
  moduleUploading.value = true
  moduleError.value = null
  try {
    await uploadDesignAttachment(props.delivery.id, file, module.id)
    emit('changed')
    toast.success('模块设计附件已上传')
  } catch (error) {
    moduleError.value = messageOf(error, '模块设计附件上传失败')
  } finally {
    moduleUploading.value = false
  }
}

async function saveModuleDesign(submitAfterSave: boolean) {
  const module = editingModule.value
  if (!module || moduleSaving.value) return
  moduleSaving.value = true
  moduleError.value = null
  try {
    await saveModuleDesignRequest(props.delivery.id, module.id, {
      designSummary: moduleForm.designSummary.trim(),
      designContent: moduleForm.designContent.trim(),
    })
    if (submitAfterSave) {
      await submitDesignModule(props.delivery.id, module.id)
    }
    moduleDialogOpen.value = false
    emit('changed')
    toast.success(submitAfterSave ? '模块详细设计已提交' : '模块详细设计已保存')
  } catch (error) {
    moduleError.value = messageOf(error, '模块详细设计保存失败')
  } finally {
    moduleSaving.value = false
  }
}

async function reopenModule(module: DesignModule) {
  try {
    await reopenDesignModule(props.delivery.id, module.id)
    emit('changed')
    toast.success(`模块「${module.name}」已退回修改`)
  } catch (error) {
    toast.failure(messageOf(error, '退回模块失败'))
  }
}

async function removeModule(module: DesignModule) {
  if (!window.confirm(`删除模块「${module.name}」？`)) return
  try {
    await deleteDesignModule(props.delivery.id, module.id)
    emit('changed')
    toast.success(`模块「${module.name}」已删除`)
  } catch (error) {
    toast.failure(messageOf(error, '删除模块失败'))
  }
}

async function submitDesignReview() {
  if (!allModulesSubmitted.value) return
  try {
    await actRequirementDelivery(props.delivery.id, {
      action: 'SUBMIT_DESIGN',
      designSummary: '',
      designContent: '',
    })
    emit('changed')
    toast.success('详细设计已提交评审')
  } catch (error) {
    toast.failure(messageOf(error, '提交设计评审失败'))
  }
}
</script>

<template>
  <div class="space-y-4">
    <section class="rounded-panel border border-separator-soft bg-window p-3">
      <div class="flex items-start gap-3">
        <div class="min-w-0 flex-1">
          <div class="flex flex-wrap items-center gap-2">
            <p class="text-caption font-emphasis text-label">概要设计</p>
            <Badge :tone="delivery.outlineDesignSubmittedAt ? 'success' : 'neutral'">
              {{ delivery.outlineDesignSubmittedAt ? '已提交' : '待提交' }}
            </Badge>
          </div>
          <p class="mt-1 text-caption text-label-tertiary">
            技术负责人上传版本/需求概要设计文档，提交后进入模块详细设计。
          </p>
        </div>
        <input
          ref="outlineFileInput"
          type="file"
          class="hidden"
          accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.md,image/*"
          @change="onOutlineFiles"
        />
        <Button
          v-if="canTechLead && delivery.stage === 'OUTLINE_DESIGN'"
          size="sm"
          :disabled="outlineUploading"
          @click="outlineFileInput?.click()"
        >
          <Icon :as="Upload" size="inline" />
          {{ outlineUploading ? '上传中…' : '上传概要设计' }}
        </Button>
      </div>

      <div
        v-if="delivery.outlineDesignSummary || delivery.outlineDesignContent"
        class="mt-3 rounded-control bg-content p-2.5"
      >
        <p v-if="delivery.outlineDesignSummary" class="text-caption font-emphasis text-label">
          {{ delivery.outlineDesignSummary }}
        </p>
        <p
          v-if="delivery.outlineDesignContent"
          class="mt-1 whitespace-pre-wrap text-caption text-label-secondary"
        >
          {{ delivery.outlineDesignContent }}
        </p>
      </div>

      <ul v-if="outlineAttachments.length > 0" class="mt-2 space-y-1.5">
        <li
          v-for="attachment in outlineAttachments"
          :key="attachment.id"
          class="flex items-center gap-2 rounded-control border border-separator-soft bg-content px-2.5 py-2"
        >
          <Icon :as="Paperclip" size="inline" class="text-label-tertiary" />
          <a
            :href="apiResourceUrl(attachment.contentUrl)"
            target="_blank"
            rel="noreferrer"
            class="min-w-0 flex-1 truncate text-caption text-accent hover:underline"
          >
            {{ attachment.filename }}
          </a>
          <span class="text-[10px] text-label-tertiary">{{ formatBytes(attachment.sizeBytes) }}</span>
        </li>
      </ul>

      <template v-if="delivery.stage === 'OUTLINE_DESIGN' && canTechLead">
        <Input
          v-model="outlineSummary"
          class="mt-3"
          placeholder="概要设计摘要，例如：新增模块化设计与派工链路"
        />
        <Textarea
          v-model="outlineContent"
          class="mt-2"
          :rows="4"
          placeholder="概述架构、模块边界、接口和数据影响；也可以只上传文档"
        />
        <p v-if="outlineError" class="mt-2 text-caption text-danger">{{ outlineError }}</p>
        <Button
          class="mt-2"
          variant="primary"
          size="sm"
          :disabled="outlineSaving || outlineUploading"
          @click="submitOutlineDesign"
        >
          <Icon :as="Send" size="inline" />
          {{ outlineSaving ? '提交中…' : '提交概要设计' }}
        </Button>
      </template>
    </section>

    <section
      v-if="['DESIGN', 'DESIGN_REVIEW'].includes(delivery.stage)"
      class="rounded-panel border border-separator-soft bg-window p-3"
    >
      <div class="flex items-start gap-3">
        <div class="min-w-0 flex-1">
          <div class="flex flex-wrap items-center gap-2">
            <p class="text-caption font-emphasis text-label">模块详细设计</p>
            <Badge tone="neutral">{{ modules.length }} 个模块</Badge>
            <Badge v-if="allModulesSubmitted" tone="success">全部已提交</Badge>
          </div>
          <p class="mt-1 text-caption text-label-tertiary">
            技术负责人拆分模块并指定研发；每个研发只维护自己负责的模块。
          </p>
        </div>
        <Button
          v-if="delivery.stage === 'DESIGN' && canTechLead"
          variant="primary"
          size="sm"
          @click="openCreateModule"
        >
          <Icon :as="UserRoundPlus" size="inline" />
          分配模块
        </Button>
      </div>

      <div v-if="modules.length === 0" class="mt-3 rounded-control bg-content px-3 py-6 text-center">
        <p class="text-caption font-emphasis text-label">还没有分配模块</p>
        <p class="mt-1 text-caption text-label-tertiary">
          技术负责人先拆模块并选择研发负责人，再进入详细设计。
        </p>
      </div>

      <div v-else class="mt-3 space-y-2">
        <article
          v-for="module in modules"
          :key="module.id"
          class="rounded-control border border-separator-soft bg-content p-3"
        >
          <div class="flex items-start gap-2">
            <div class="min-w-0 flex-1">
              <div class="flex flex-wrap items-center gap-2">
                <p class="text-caption font-emphasis text-label">{{ module.name }}</p>
                <Badge :tone="moduleStatusMeta(module).tone">
                  {{ moduleStatusMeta(module).label }}
                </Badge>
                <span class="text-[10px] text-label-tertiary">
                  研发：{{ memberName(module.assigneeId) }}
                </span>
              </div>
              <p v-if="module.description" class="mt-1 text-caption text-label-secondary">
                {{ module.description }}
              </p>
            </div>
            <div v-if="delivery.stage === 'DESIGN'" class="flex shrink-0 flex-wrap justify-end gap-1.5">
              <Button
                v-if="canEditModule(module) && module.status !== 'SUBMITTED'"
                size="sm"
                @click="openModuleDesign(module)"
              >
                <Icon :as="Pencil" size="inline" />
                填写设计
              </Button>
              <Button
                v-if="canTechLead"
                size="sm"
                @click="openEditAssignment(module)"
              >
                调整派工
              </Button>
              <Button
                v-if="canTechLead && module.status === 'SUBMITTED'"
                size="sm"
                @click="reopenModule(module)"
              >
                <Icon :as="RotateCcw" size="inline" />
                退回
              </Button>
              <Button
                v-if="canTechLead"
                variant="ghost"
                size="sm"
                @click="removeModule(module)"
              >
                <Icon :as="Trash2" size="inline" />
              </Button>
            </div>
          </div>

          <div v-if="module.designSummary || module.designContent" class="mt-2 rounded-control bg-window p-2.5">
            <p v-if="module.designSummary" class="text-caption font-emphasis text-label">
              {{ module.designSummary }}
            </p>
            <p
              v-if="module.designContent"
              class="mt-1 whitespace-pre-wrap text-caption text-label-secondary"
            >
              {{ module.designContent }}
            </p>
          </div>
          <ul v-if="moduleAttachments(module.id).length > 0" class="mt-2 space-y-1.5">
            <li
              v-for="attachment in moduleAttachments(module.id)"
              :key="attachment.id"
              class="flex items-center gap-2 rounded-control border border-separator-soft bg-window px-2.5 py-2"
            >
              <Icon :as="Paperclip" size="inline" class="text-label-tertiary" />
              <a
                :href="apiResourceUrl(attachment.contentUrl)"
                target="_blank"
                rel="noreferrer"
                class="min-w-0 flex-1 truncate text-caption text-accent hover:underline"
              >
                {{ attachment.filename }}
              </a>
              <span class="text-[10px] text-label-tertiary">
                {{ formatBytes(attachment.sizeBytes) }}
              </span>
            </li>
          </ul>
        </article>
      </div>

      <div
        v-if="delivery.stage === 'DESIGN' && canTechLead"
        class="mt-3 flex items-center justify-end border-t border-separator-soft pt-3"
      >
        <Button
          variant="primary"
          size="sm"
          :disabled="!allModulesSubmitted"
          @click="submitDesignReview"
        >
          <Icon :as="Check" size="inline" />
          提交设计评审
        </Button>
      </div>
    </section>

    <Dialog
      v-model:open="moduleAssignmentOpen"
      layout="form"
      size="large"
      :title="editingModule ? '调整模块派工' : '分配设计模块'"
      description="模块负责人必须具备开发或技术负责人角色。"
      :close-on-outside-click="false"
    >
      <div class="flex flex-col gap-3">
        <DialogField label="模块名称" required>
          <Input v-model="moduleForm.name" :maxlength="160" placeholder="例如：鉴权与权限模块" />
        </DialogField>
        <DialogField label="模块说明">
          <Textarea
            v-model="moduleForm.description"
            :rows="3"
            placeholder="说明负责范围、接口边界和与其他模块的依赖"
          />
        </DialogField>
        <DialogField label="研发负责人" required>
          <Select
            v-model="moduleForm.assigneeId"
            :items="developerOptions"
            placeholder="选择研发负责人"
            class="w-full"
          />
        </DialogField>
        <p v-if="moduleError" class="text-caption text-danger">{{ moduleError }}</p>
      </div>
      <template #footer>
        <div class="flex justify-end gap-2">
          <Button size="sm" :disabled="moduleSaving" @click="moduleAssignmentOpen = false">
            取消
          </Button>
          <Button
            variant="primary"
            size="sm"
            :disabled="moduleSaving || !moduleForm.name.trim() || !moduleForm.assigneeId"
            @click="saveModuleAssignment"
          >
            {{ moduleSaving ? '保存中…' : editingModule ? '保存派工' : '创建模块' }}
          </Button>
        </div>
      </template>
    </Dialog>

    <Dialog
      v-model:open="moduleDialogOpen"
      layout="form"
      size="wide"
      :title="editingModule ? `填写「${editingModule.name}」详细设计` : '填写模块详细设计'"
      description="可以填写设计正文、上传文档，或两者都提供。提交后由技术负责人汇总评审。"
      :close-on-outside-click="false"
    >
      <div class="flex flex-col gap-3">
        <DialogField label="设计摘要">
          <Input
            v-model="moduleForm.designSummary"
            :maxlength="4000"
            placeholder="例如：新增权限校验与审计留痕"
          />
        </DialogField>
        <DialogField label="设计正文">
          <Textarea
            v-model="moduleForm.designContent"
            :rows="8"
            placeholder="说明接口、数据、类/服务改造、异常处理、测试点和回滚方案"
          />
        </DialogField>
        <DialogField label="设计附件" hint="支持 PDF、Office、文本和图片；提交前上传">
          <input
            ref="moduleFileInput"
            type="file"
            class="hidden"
            accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.md,image/*"
            @change="onModuleFiles"
          />
          <Button size="sm" :disabled="moduleUploading" @click="moduleFileInput?.click()">
            <Icon :as="Upload" size="inline" />
            {{ moduleUploading ? '上传中…' : '上传模块设计文档' }}
          </Button>
          <ul
            v-if="editingModule && moduleAttachments(editingModule.id).length > 0"
            class="mt-2 space-y-1.5"
          >
            <li
              v-for="attachment in moduleAttachments(editingModule.id)"
              :key="attachment.id"
              class="flex items-center gap-2 rounded-control border border-separator-soft bg-content px-2.5 py-2"
            >
              <Icon :as="Paperclip" size="inline" class="text-label-tertiary" />
              <a
                :href="apiResourceUrl(attachment.contentUrl)"
                target="_blank"
                rel="noreferrer"
                class="min-w-0 flex-1 truncate text-caption text-accent hover:underline"
              >
                {{ attachment.filename }}
              </a>
              <span class="text-[10px] text-label-tertiary">
                {{ formatBytes(attachment.sizeBytes) }}
              </span>
            </li>
          </ul>
        </DialogField>
        <p v-if="moduleError" class="text-caption text-danger">{{ moduleError }}</p>
      </div>
      <template #footer>
        <div class="flex justify-end gap-2">
          <Button size="sm" :disabled="moduleSaving" @click="moduleDialogOpen = false">
            取消
          </Button>
          <Button size="sm" :disabled="moduleSaving" @click="saveModuleDesign(false)">
            保存草稿
          </Button>
          <Button
            variant="primary"
            size="sm"
            :disabled="moduleSaving || moduleUploading"
            @click="saveModuleDesign(true)"
          >
            {{ moduleSaving ? '提交中…' : '保存并提交' }}
          </Button>
        </div>
      </template>
    </Dialog>
  </div>
</template>
