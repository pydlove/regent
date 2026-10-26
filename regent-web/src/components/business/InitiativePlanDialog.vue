<script setup lang="ts">
import { CalendarRange, Clock3, UsersRound } from '@lucide/vue'
import { computed, reactive, ref, watch } from 'vue'

import { saveInitiativePlan } from '@/api/delivery'
import { ApiError } from '@/api/http'
import type { WorkflowTemplateStage } from '@/api/workflowTemplates'
import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import DialogField from '@/components/ui/dialog/DialogField.vue'
import Input from '@/components/ui/input/Input.vue'
import Textarea from '@/components/ui/input/Textarea.vue'
import Select from '@/components/ui/select/Select.vue'
import { useToast } from '@/composables/useToast'
import type { Initiative } from '@/types/delivery'
import type { ProjectMember, ProjectMemberRole } from '@/types/projectMember'
import {
  buildInitiativePlanInput,
  buildPlanStageSchedule,
  effortUnits,
  formatEffort,
  resolvePlanStageSeeds,
  type PlannedStageSeed,
} from '@/utils/initiativePlan'
import {
  projectMemberRoleLabels,
  workflowRoleOptions,
} from '@/utils/projectMemberRoles'

const props = withDefaults(defineProps<{
  initiative: Initiative
  stages?: WorkflowTemplateStage[]
  templateName?: string
  members?: ProjectMember[]
}>(), {
  stages: () => [],
  templateName: '标准瀑布式',
  members: () => [],
})

const emit = defineEmits<{
  changed: []
}>()

const open = defineModel<boolean>('open', { default: false })
const toast = useToast()
const saving = ref(false)
const error = ref<string | null>(null)
const usingFallbackStages = ref(false)
let stageSequence = 0

type PlanStageForm = PlannedStageSeed & {
  key: string
  durationDays: string
  headcount: string
}

const form = reactive({
  planStartDate: '',
  milestones: '',
  stages: [] as PlanStageForm[],
})

const roleOptions = workflowRoleOptions

const localDate = (value: Date) => {
  const offset = value.getTimezoneOffset() * 60_000
  return new Date(value.getTime() - offset).toISOString().slice(0, 10)
}
const today = () => localDate(new Date())

const scheduledStages = computed(() =>
  buildPlanStageSchedule(
    form.planStartDate,
    form.stages.map((stage) => ({
      ...stage,
      durationDays: Number(stage.durationDays) || 0,
      headcount: Number(stage.headcount) || 0,
    })),
  ),
)

const planEndDate = computed(() => scheduledStages.value.at(-1)?.endDate ?? '')
const totalWorkload = computed(() =>
  scheduledStages.value.reduce((total, stage) => total + stage.workloadDays, 0),
)
const totalEffort = computed(() => effortUnits(totalWorkload.value))
const roleWorkloads = computed(() => {
  const totals = new Map<ProjectMemberRole, number>()
  for (const stage of scheduledStages.value) {
    totals.set(stage.roleCode, (totals.get(stage.roleCode) ?? 0) + stage.workloadDays)
  }
  return [...totals.entries()]
    .map(([roleCode, workloadDays]) => ({ roleCode, workloadDays }))
    .sort((left, right) => right.workloadDays - left.workloadDays)
})

const canSave = computed(() =>
  form.planStartDate !== ''
  && form.stages.length > 0
  && form.stages.every((stage) => {
    const durationDays = Number(stage.durationDays)
    const headcount = Number(stage.headcount)
    return Number.isInteger(durationDays)
      && durationDays >= 1
      && durationDays <= 99
      && Number.isInteger(headcount)
      && headcount >= 1
      && headcount <= 99
      && roleOptions.some((role) => role.value === stage.roleCode)
  }),
)

function membersForRole(roleCode: ProjectMemberRole): string {
  return props.members
    .filter((member) => member.roles.includes(roleCode))
    .map((member) => member.displayName)
    .join('、')
}

function initializePlan() {
  error.value = null
  const plan = props.initiative.plan
  usingFallbackStages.value = props.stages.length === 0
  const seeds = resolvePlanStageSeeds(props.stages)
  const existingItems = plan?.items ?? []
  form.planStartDate = plan?.planStartDate ?? today()
  form.milestones = plan?.milestones ?? ''
  form.stages = seeds.map((seed, index) => {
    const namedItem = existingItems.find((item) => item.name.trim() === seed.name)
    const legacyItem = namedItem
      ?? (existingItems.length === seeds.length ? existingItems[index] : undefined)
    const roleCode = legacyItem
      && roleOptions.some((role) => role.value === legacyItem.roleCode)
      ? legacyItem.roleCode as ProjectMemberRole
      : seed.roleCode
    return {
      ...seed,
      key: `plan-stage-${++stageSequence}`,
      roleCode,
      durationDays: String(
        legacyItem?.workingDays ?? seed.defaultDurationDays,
      ),
      headcount: String(legacyItem?.headcount ?? 1),
    }
  })
}

watch(open, (value) => {
  if (value) initializePlan()
}, { immediate: true })

watch(() => props.stages, () => {
  if (open.value && usingFallbackStages.value && props.stages.length > 0) {
    initializePlan()
  }
})

function messageOf(value: unknown): string {
  return value instanceof ApiError
    ? value.message
    : value instanceof Error
      ? value.message
      : '版本计划保存失败'
}

async function save() {
  if (!canSave.value || saving.value) return
  saving.value = true
  error.value = null
  try {
    await saveInitiativePlan(
      props.initiative.id,
      buildInitiativePlanInput(
        form.planStartDate,
        form.milestones.trim(),
        form.stages.map((stage) => ({
          ...stage,
          durationDays: Number(stage.durationDays),
          headcount: Number(stage.headcount),
        })),
      ),
    )
    open.value = false
    emit('changed')
    toast.success(
      '版本计划已保存',
      `${formatEffort(totalEffort.value.personDays)} 人天 · `
        + `${formatEffort(totalEffort.value.personWeeks)} 人周 · `
        + `${formatEffort(totalEffort.value.personMonths)} 人月`,
    )
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
    size="xwide"
    height="fixed"
    title="配置版本计划"
    :description="`${templateName}流程已固定。项目负责人只需配置每个阶段的工期、投入角色和人数，系统自动排期并换算工作量。`"
    :icon="CalendarRange"
    :close-on-outside-click="false"
  >
    <div class="flex flex-col gap-4">
      <div class="grid gap-3 md:grid-cols-[minmax(0,260px)_minmax(0,1fr)]">
        <DialogField label="版本计划开始" required>
          <Input v-model="form.planStartDate" type="date" />
        </DialogField>
        <div class="rounded-panel border border-separator-soft bg-window px-3 py-2.5">
          <div class="flex flex-wrap items-center gap-2">
            <span class="rounded-badge bg-accent/10 px-2 py-0.5 text-caption font-emphasis text-accent">
              {{ templateName }}
            </span>
            <span class="text-caption text-label-tertiary">
              {{ form.stages.length }} 个固定阶段
            </span>
          </div>
          <p class="mt-1.5 text-caption text-label-secondary">
            自动排期：{{ form.planStartDate || '未设置' }} 至 {{ planEndDate || '未设置' }}
          </p>
        </div>
      </div>

      <section class="overflow-hidden rounded-panel border border-separator-soft">
        <div class="flex flex-wrap items-center gap-2 border-b border-separator-soft bg-window px-3 py-2.5">
          <div class="min-w-0 flex-1">
            <p class="text-body font-emphasis text-label">固定阶段与投入</p>
            <p class="mt-0.5 text-caption text-label-tertiary">
              工期按工作日计算；自动化回归、转测、生产发布和发布观察属于门禁动作，不计入计划工期和工作量。
            </p>
          </div>
          <span class="text-caption text-label-tertiary">
            共 {{ form.stages.length }} 个阶段
          </span>
        </div>

        <div class="divide-y divide-separator-soft">
          <div
            v-for="(stage, index) in form.stages"
            :key="stage.key"
            class="px-3 py-3"
          >
            <div class="flex items-start gap-3">
              <span class="tnum grid size-7 shrink-0 place-items-center rounded-badge bg-accent/10 text-caption font-emphasis text-accent">
                {{ index + 1 }}
              </span>
              <div class="min-w-0 flex-1">
                <p class="text-caption font-emphasis text-label">{{ stage.name }}</p>
                <p class="mt-0.5 text-[10px] text-label-tertiary">
                  {{ stage.description || '固定研发流程阶段' }}
                </p>
              </div>
              <div class="hidden shrink-0 text-right lg:block">
                <p class="text-[10px] text-label-tertiary">该角色成员</p>
                <p class="mt-0.5 max-w-56 truncate text-caption text-label-secondary">
                  {{ membersForRole(stage.roleCode) || '尚未配置该角色成员' }}
                </p>
              </div>
            </div>

            <div class="mt-3 grid gap-2 sm:grid-cols-2 xl:grid-cols-[minmax(180px,1fr)_120px_100px_minmax(210px,1fr)]">
              <label class="block">
                <span class="mb-1 block text-[10px] text-label-tertiary">投入角色</span>
                <Select v-model="stage.roleCode" :items="roleOptions" class="w-full" />
              </label>
              <label class="block">
                <span class="mb-1 block text-[10px] text-label-tertiary">工期（工作日）</span>
                <Input
                  v-model="stage.durationDays"
                  type="number"
                  min="1"
                  max="99"
                  class="w-full"
                />
              </label>
              <label class="block">
                <span class="mb-1 block text-[10px] text-label-tertiary">投入人数</span>
                <Input
                  v-model="stage.headcount"
                  type="number"
                  min="1"
                  max="99"
                  class="w-full"
                />
              </label>
              <div class="rounded-control border border-separator-soft bg-window px-2.5 py-2">
                <p class="text-[10px] text-label-tertiary">自动排期与估算</p>
                <p class="mt-0.5 text-caption text-label">
                  {{ scheduledStages[index]?.startDate || '待设置' }}
                  至
                  {{ scheduledStages[index]?.endDate || '待设置' }}
                </p>
                <p class="mt-0.5 text-[10px] text-accent">
                  {{ scheduledStages[index]?.workloadDays ?? 0 }} 人天
                </p>
              </div>
            </div>
            <p class="mt-1 text-[10px] text-label-tertiary lg:hidden">
              该角色成员：{{ membersForRole(stage.roleCode) || '尚未配置' }}
            </p>
          </div>
        </div>
      </section>

      <div class="grid gap-2 sm:grid-cols-3">
        <div class="rounded-panel border border-separator-soft bg-window px-3 py-2.5">
          <p class="flex items-center gap-1.5 text-[10px] text-label-tertiary">
            <Clock3 :size="12" />总工作量
          </p>
          <p class="mt-1 text-title font-emphasis text-accent">
            {{ formatEffort(totalEffort.personDays) }} 人天
          </p>
        </div>
        <div class="rounded-panel border border-separator-soft bg-window px-3 py-2.5">
          <p class="text-[10px] text-label-tertiary">折合人周</p>
          <p class="mt-1 text-title font-emphasis text-label">
            {{ formatEffort(totalEffort.personWeeks) }} 人周
          </p>
        </div>
        <div class="rounded-panel border border-separator-soft bg-window px-3 py-2.5">
          <p class="text-[10px] text-label-tertiary">折合人月（21.75 工作日）</p>
          <p class="mt-1 text-title font-emphasis text-label">
            {{ formatEffort(totalEffort.personMonths) }} 人月
          </p>
        </div>
      </div>

      <div class="rounded-panel border border-separator-soft bg-window px-3 py-2.5">
        <p class="flex items-center gap-1.5 text-caption font-emphasis text-label">
          <UsersRound :size="14" />角色投入汇总
        </p>
        <div v-if="roleWorkloads.length > 0" class="mt-2 flex flex-wrap gap-1.5">
          <span
            v-for="role in roleWorkloads"
            :key="role.roleCode"
            class="rounded-badge bg-content px-2 py-1 text-[10px] text-label-secondary"
          >
            {{ projectMemberRoleLabels[role.roleCode] }}
            {{ role.workloadDays }} 人天
          </span>
        </div>
        <p v-else class="mt-1 text-caption text-label-tertiary">尚未生成角色投入汇总。</p>
      </div>

      <DialogField
        label="计划说明（可选）"
        hint="阶段起止日期会自动形成里程碑；这里只记录额外约束、依赖或风险"
      >
        <Textarea
          v-model="form.milestones"
          :rows="3"
          placeholder="例如：研发阶段需要与网关团队联调；发布窗口仅限周四晚间"
        />
      </DialogField>

      <p v-if="error" class="text-caption text-danger">{{ error }}</p>
    </div>

    <template #footer>
      <div class="flex justify-end gap-2">
        <Button size="sm" :disabled="saving" @click="open = false">取消</Button>
        <Button variant="primary" size="sm" :disabled="!canSave || saving" @click="save">
          {{ saving ? '保存中…' : '保存版本计划' }}
        </Button>
      </div>
    </template>
  </Dialog>
</template>
