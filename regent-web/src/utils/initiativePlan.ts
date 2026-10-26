import type { WorkflowTemplateStage } from '@/api/workflowTemplates'
import type { InitiativePlanInput } from '@/types/delivery'
import type { ProjectMemberRole } from '@/types/projectMember'

export type PlannedStageSeed = {
  code: string
  name: string
  description: string
  roleCode: ProjectMemberRole
  defaultDurationDays: number
}

export type PlanStageInput = PlannedStageSeed & {
  durationDays: number
  headcount: number
}

export type PlanStageSchedule = PlanStageInput & {
  startDate: string
  endDate: string
  workloadDays: number
}

const stageRoles: Record<string, ProjectMemberRole> = {
  VERSION_INITIATION: 'PRODUCT',
  REQUIREMENT_REVIEW: 'PRODUCT',
  OUTLINE_DESIGN: 'TECH_LEAD',
  DESIGN: 'TECH_LEAD',
  DESIGN_REVIEW: 'TECH_LEAD',
  DEVELOPMENT: 'DEVELOPER',
  SMOKE: 'TESTER',
  INTEGRATION: 'TESTER',
  SPECIAL_TEST: 'TESTER',
  REGRESSION: 'TESTER',
  RELEASE_REVIEW: 'OWNER',
  CLOSE: 'OWNER',
}

const stageDurations: Record<string, number> = {
  VERSION_INITIATION: 1,
  REQUIREMENT_REVIEW: 2,
  OUTLINE_DESIGN: 3,
  DESIGN: 5,
  DESIGN_REVIEW: 2,
  DEVELOPMENT: 10,
  SMOKE: 1,
  INTEGRATION: 3,
  SPECIAL_TEST: 3,
  REGRESSION: 2,
  RELEASE_REVIEW: 1,
  CLOSE: 1,
}

export const STANDARD_PLAN_STAGE_SEEDS: PlannedStageSeed[] = [
  ['VERSION_INITIATION', '版本立项', '目标、范围和资源确认', 'PRODUCT', 1],
  ['REQUIREMENT_REVIEW', '需求评审', '评审需求范围和验收标准', 'PRODUCT', 2],
  ['OUTLINE_DESIGN', '概要设计', '技术方案和系统边界设计', 'TECH_LEAD', 3],
  ['DESIGN', '详细设计', '模块拆分和详细设计', 'TECH_LEAD', 5],
  ['DESIGN_REVIEW', '设计评审', '确认技术方案和风险', 'TECH_LEAD', 2],
  ['DEVELOPMENT', '研发', '编码、自测和代码评审', 'DEVELOPER', 10],
  ['SMOKE', '冒烟测试', '验证构建制品基本可用', 'TESTER', 1],
  ['INTEGRATION', '集成测试', '验证跨模块和接口协同', 'TESTER', 3],
  ['SPECIAL_TEST', '专项测试', '执行稳定性测试、性能测试、安全扫描等专项验证', 'TESTER', 3],
  ['REGRESSION', '版本回归', '执行版本级回归测试', 'TESTER', 2],
  ['RELEASE_REVIEW', '发版评审', '确认发布范围和回滚方案', 'OWNER', 1],
  ['CLOSE', '版本关闭', '确认复盘和遗留问题', 'OWNER', 1],
].map(([code, name, description, roleCode, defaultDurationDays]) => ({
  code: code as string,
  name: name as string,
  description: description as string,
  roleCode: roleCode as ProjectMemberRole,
  defaultDurationDays: defaultDurationDays as number,
}))

const projectMemberRoles = new Set<string>([
  'OWNER',
  'PRODUCT',
  'TECH_LEAD',
  'ARCHITECT',
  'DEVELOPER',
  'QA',
  'TESTER',
])

const nonEstimablePlanStages = new Set([
  'AUTOMATION',
  'TRANSFER',
  'PRODUCTION_RELEASE',
  'OBSERVE',
])

function normalizeDate(value: string): Date | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return null
  const [year, month, day] = value.split('-').map(Number)
  const date = new Date(Date.UTC(year, month - 1, day))
  return date.getUTCFullYear() === year
    && date.getUTCMonth() === month - 1
    && date.getUTCDate() === day
    ? date
    : null
}

function toDateString(date: Date): string {
  return date.toISOString().slice(0, 10)
}

function isWorkingDay(date: Date): boolean {
  const day = date.getUTCDay()
  return day !== 0 && day !== 6
}

function moveToWorkingDay(date: Date): Date {
  const next = new Date(date)
  while (!isWorkingDay(next)) next.setUTCDate(next.getUTCDate() + 1)
  return next
}

export function resolvePlanStageSeeds(stages: WorkflowTemplateStage[] = []): PlannedStageSeed[] {
  if (stages.length === 0) return STANDARD_PLAN_STAGE_SEEDS
  const seeds = [...stages]
    .filter((stage) => !nonEstimablePlanStages.has(stage.code))
    .sort((left, right) => left.order - right.order)
    .map((stage) => {
      const fallbackRole = stage.approverRole?.toUpperCase()
      const roleCode = stageRoles[stage.code]
        ?? (fallbackRole && projectMemberRoles.has(fallbackRole)
          ? fallbackRole as ProjectMemberRole
          : 'DEVELOPER')
      return {
        code: stage.code,
        name: stage.name,
        description: stage.description ?? '',
        roleCode,
        defaultDurationDays: stageDurations[stage.code] ?? 1,
      }
    })
  const integrationIndex = seeds.findIndex((stage) => stage.code === 'INTEGRATION')
  if (
    integrationIndex >= 0
    && !seeds.some((stage) => stage.code === 'SPECIAL_TEST')
  ) {
    seeds.splice(integrationIndex + 1, 0, {
      code: 'SPECIAL_TEST',
      name: '专项测试',
      description: '执行稳定性测试、性能测试、安全扫描等专项验证',
      roleCode: 'TESTER',
      defaultDurationDays: 3,
    })
  }
  return seeds
}

export function addWorkingDays(startDate: string, durationDays: number): string {
  const start = normalizeDate(startDate)
  if (!start) return ''
  let cursor = moveToWorkingDay(start)
  let remaining = Math.max(1, Math.floor(durationDays))
  while (remaining > 1) {
    cursor.setUTCDate(cursor.getUTCDate() + 1)
    if (isWorkingDay(cursor)) remaining--
  }
  return toDateString(cursor)
}

export function nextWorkingDay(dateValue: string): string {
  const date = normalizeDate(dateValue)
  if (!date) return ''
  date.setUTCDate(date.getUTCDate() + 1)
  return toDateString(moveToWorkingDay(date))
}

export function buildPlanStageSchedule(
  planStartDate: string,
  stages: PlanStageInput[],
): PlanStageSchedule[] {
  let cursor = planStartDate
  return stages.map((stage) => {
    const durationDays = Math.max(1, Math.floor(stage.durationDays || 1))
    const headcount = Math.max(1, Math.floor(stage.headcount || 1))
    const normalized = cursor ? normalizeDate(cursor) : null
    if (!normalized) {
      return {
        ...stage,
        startDate: '',
        endDate: '',
        workloadDays: durationDays * headcount,
      }
    }
    const startDate = moveToWorkingDay(normalized)
    const start = startDate ? toDateString(startDate) : ''
    const endDate = addWorkingDays(start, durationDays)
    cursor = endDate ? nextWorkingDay(endDate) : ''
    return {
      ...stage,
      startDate: start,
      endDate,
      workloadDays: durationDays * headcount,
    }
  })
}

export function effortUnits(personDays: number) {
  const days = Number.isFinite(personDays) ? Math.max(0, personDays) : 0
  return {
    personDays: days,
    personWeeks: days / 5,
    personMonths: days / 21.75,
  }
}

export function buildInitiativePlanInput(
  planStartDate: string,
  milestones: string,
  stages: PlanStageInput[],
): InitiativePlanInput {
  const schedule = buildPlanStageSchedule(planStartDate, stages)
  const planEndDate = schedule.at(-1)?.endDate
  if (!planEndDate) throw new Error('版本计划开始日期无效')
  return {
    planStartDate,
    planEndDate,
    milestones,
    items: schedule.map((stage) => ({
      name: stage.name,
      roleCode: stage.roleCode,
      startDate: stage.startDate,
      endDate: stage.endDate,
      headcount: Math.max(1, Math.floor(stage.headcount)),
    })),
  }
}

export function formatEffort(value: number): string {
  if (!Number.isFinite(value)) return '0'
  return Number.isInteger(value)
    ? String(value)
    : value.toFixed(1).replace(/\.0$/, '')
}
