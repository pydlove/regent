import type { ProjectMemberRole } from '@/types/projectMember'

export type OrganizationRole =
  | 'ORG_OWNER'
  | 'ORG_ADMIN'
  | 'PROJECT_MANAGER'
  | 'ORG_MEMBER'

export type Organization = {
  id: number
  code: string
  name: string
  description: string | null
  status: 'ACTIVE' | 'DISABLED'
  currentUserRoles: OrganizationRole[]
  memberCount: number
  projectCount: number
  createdAt: string
}

export type OrganizationMember = {
  userId: number
  username: string
  displayName: string
  email: string | null
  avatarUrl: string | null
  roles: OrganizationRole[]
  projects: Array<{
    projectId: number
    projectName: string
    roles: ProjectMemberRole[]
  }>
}

export type OrganizationInvitationStatus =
  | 'PENDING'
  | 'ACCEPTED'
  | 'REVOKED'
  | 'EXPIRED'

export type OrganizationInvitation = {
  id: number
  email: string
  role: OrganizationRole
  status: OrganizationInvitationStatus
  expiresAt: string
  acceptedAt: string | null
  createdAt: string
}

export type CreatedOrganizationInvitation = {
  invitation: OrganizationInvitation
  token: string
  invitationUrl: string
  emailSent: boolean
}

export type CreatedOrganizationAccount = {
  userId: number
  username: string
  displayName: string
  email: string
  role: OrganizationRole
  initialPassword: string
  mustChangePassword: boolean
}

export type ResetOrganizationMemberPassword = {
  initialPassword: string
  mustChangePassword: boolean
}

export type OrganizationInvitationPreview = {
  organizationName: string
  email: string
  role: OrganizationRole
  status: OrganizationInvitationStatus
  expiresAt: string
  accountExists: boolean
}

export type AcceptedOrganizationInvitation = {
  organizationId: number
  organizationName: string
  userId: number
  username: string
  displayName: string
}

export type OrganizationAudit = {
  id: number
  actorId: number
  action: string
  targetType: string
  targetId: number | null
  detail: string | null
  createdAt: string
}

export type OrganizationInput = {
  code: string
  name: string
  description: string
}
