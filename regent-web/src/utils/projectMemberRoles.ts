import type { ProjectMemberRole } from '@/types/projectMember'

export const projectMemberRoleLabels: Record<ProjectMemberRole, string> = {
  OWNER: '项目负责人',
  BUSINESS_OWNER: '业务负责人',
  PRODUCT: '产品负责人',
  TECH_LEAD: '技术负责人',
  ARCHITECT: '架构师',
  DEVELOPER: '开发',
  QA: '测试负责人',
  OPS: '运维/SRE',
  SECURITY: '安全/合规',
  RELEASE_MANAGER: '发布负责人',
  VIEWER: '观察者',
  AUDITOR: '审计员',
}

export const projectMemberRoleDescriptions: Record<ProjectMemberRole, string> = {
  OWNER: '团队管理、最终风险',
  BUSINESS_OWNER: '业务目标、验收与价值确认',
  PRODUCT: '需求与需求评审',
  TECH_LEAD: '详细设计与设计评审',
  ARCHITECT: '架构方案与技术风险',
  DEVELOPER: '研发实现',
  QA: '冒烟、自动化、转测与集成',
  OPS: '环境、部署与生产运维',
  SECURITY: '安全、合规与风险评审',
  RELEASE_MANAGER: '发版评审与生产发布',
  VIEWER: '只读查看',
  AUDITOR: '只读审计与证据检查',
}

export const projectMemberRoleOptions = (
  Object.keys(projectMemberRoleLabels) as ProjectMemberRole[]
)
  .filter((role) => role !== 'OWNER')
  .map((role) => ({ value: role, label: projectMemberRoleLabels[role] }))
