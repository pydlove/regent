export type ProjectMemberRole =
  | 'OWNER'
  | 'BUSINESS_OWNER'
  | 'PRODUCT'
  | 'TECH_LEAD'
  | 'ARCHITECT'
  | 'DEVELOPER'
  | 'QA'
  | 'TESTER'
  | 'OPS'
  | 'SECURITY'
  | 'RELEASE_MANAGER'
  | 'VIEWER'
  | 'AUDITOR'

export type ProjectMember = {
  userId: number
  username: string
  displayName: string
  email: string | null
  avatarUrl: string | null
  roles: ProjectMemberRole[]
}

export type ProjectMemberCandidate = {
  userId: number
  username: string
  displayName: string
  email: string | null
  avatarUrl: string | null
  organizationRoles: Array<
    'ORG_OWNER' | 'ORG_ADMIN' | 'PROJECT_MANAGER' | 'ORG_MEMBER'
  >
  projectRoles: ProjectMemberRole[]
}
