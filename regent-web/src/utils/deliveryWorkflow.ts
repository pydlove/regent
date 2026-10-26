import type {
  DeliveryStage,
  DeliveryStatus,
  InitiativeStatus,
  ReleaseStage,
  ReleaseStatus,
} from '@/types/delivery'
import type { ProjectMemberRole } from '@/types/projectMember'

export type WorkflowRole = ProjectMemberRole

export type WorkflowStep = {
  label: string
  role: WorkflowRole | null
  hint: string
}

export const deliveryWorkflowSteps = [
  '版本立项',
  '需求池',
  '需求评审',
  '概要设计',
  '详细设计',
  '设计评审',
  '研发',
  '冒烟测试',
  '自动化回归',
  '转测',
  '集成测试',
  '发版',
  '观察关闭',
] as const

export function deliveryNextStep(
  stage: DeliveryStage,
  status: DeliveryStatus,
): WorkflowStep | null {
  if (status === 'CANCELLED') {
    return { label: '交付已取消', role: null, hint: '该交付不再进入后续门禁。' }
  }
  switch (stage) {
    case 'REQUIREMENT_REVIEW':
      return {
        label: '需求评审',
        role: 'PRODUCT',
        hint: '确认需求边界、验收标准和影响范围后进入概要设计。',
      }
    case 'OUTLINE_DESIGN':
      return {
        label: '提交概要设计',
        role: 'TECH_LEAD',
        hint: '上传概要设计文档，确认模块边界、接口和技术风险。',
      }
    case 'DESIGN':
      return {
        label: '模块详细设计',
        role: 'TECH_LEAD',
        hint: '技术负责人分配模块，研发分别提交自己模块的详细设计。',
      }
    case 'DESIGN_REVIEW':
      return {
        label: '设计评审',
        role: 'TECH_LEAD',
        hint: '技术负责人确认方案可实施且风险可控后进入研发。',
      }
    case 'DEVELOPMENT':
      return {
        label: '确认研发完成',
        role: 'TECH_LEAD',
        hint: '实现、自测和代码审阅完成后进入冒烟测试。',
      }
    case 'SMOKE':
      return {
        label: '冒烟测试',
        role: 'QA',
        hint: '测试负责人确认核心链路可用后进入自动化回归。',
      }
    case 'AUTOMATION':
      return {
        label: '自动化回归',
        role: 'QA',
        hint: '测试负责人确认自动化结果和阻塞 Bug 后进入待转测。',
      }
    case 'READY_FOR_TRANSFER':
      return {
        label: '纳入版本发布',
        role: 'OWNER',
        hint: '项目负责人把需求纳入不可变制品的发布轮次。',
      }
    case 'IN_RELEASE':
      return {
        label: '随发布单推进',
        role: null,
        hint: '转测、集成和发版由发布单统一推进，需求交付保持只读。',
      }
    case 'RELEASED':
      return {
        label: '关闭需求交付',
        role: 'OWNER',
        hint: '生产观察无异常后关闭发布，需求交付同步关闭。',
      }
    case 'CLOSED':
      return null
  }
}

export function initiativeNextStep(status: InitiativeStatus): WorkflowStep | null {
  switch (status) {
    case 'DRAFT':
    case 'REJECTED':
      return {
        label: '提交立项审批',
        role: null,
        hint: '产品负责人补齐目标与材料，项目负责人配置版本计划后提交审批。',
      }
    case 'SUBMITTED':
      return {
        label: '立项审批',
        role: 'OWNER',
        hint: '项目负责人确认版本计划、资源投入和业务价值。',
      }
    case 'APPROVED':
      return {
        label: '进入需求池',
        role: 'PRODUCT',
        hint: '把需求录入需求池，再纳入当前产品版本并完成需求评审。',
      }
    case 'CANCELLED':
      return null
  }
}

export function releaseNextStep(stage: ReleaseStage, status: ReleaseStatus): WorkflowStep | null {
  if (status === 'CANCELLED' || status === 'SUPERSEDED') {
    return { label: '发布已结束', role: null, hint: '历史记录保留，不再推进门禁。' }
  }
  switch (stage) {
    case 'TRANSFER':
      return {
        label: '转测准入',
        role: 'QA',
        hint: '测试负责人确认制品、需求范围和遗留风险后进入集成测试。',
      }
    case 'INTEGRATION':
      return {
        label: '集成测试',
        role: 'QA',
        hint: '在集成环境验证同一制品的跨模块协同。',
      }
    case 'REGRESSION':
      return {
        label: '版本回归',
        role: 'QA',
        hint: '确认版本级回归通过且没有阻塞 Bug。',
      }
    case 'RELEASE_REVIEW':
      return {
        label: '发版评审',
        role: 'OWNER',
        hint: '项目负责人确认风险、窗口和回滚方案。',
      }
    case 'PRODUCTION':
      return {
        label: '确认生产部署',
        role: 'OWNER',
        hint: '确认生产部署和发布后冒烟完成。',
      }
    case 'OBSERVING':
      return {
        label: '关闭发布',
        role: 'OWNER',
        hint: '观察期无异常后关闭发布并结项。',
      }
    case 'CLOSED':
      return null
  }
}

export function canActWithRoles(
  requiredRole: WorkflowRole | null,
  roles: WorkflowRole[],
  memberCount: number,
): boolean {
  if (!requiredRole) return false
  if (roles.includes(requiredRole)) return true
  return roles.includes('OWNER') && memberCount === 1
}
