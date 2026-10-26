import {
  apiResourceUrl,
  deleteRequest,
  get,
  getFile,
  post,
  postFormFile,
  postJson,
  putJson,
  type DownloadedFile,
} from '@/api/http'
import type { Project } from '@/types/api'
import type {
  AcceptedOrganizationInvitation,
  CreatedOrganizationAccount,
  CreatedOrganizationInvitation,
  Organization,
  OrganizationAudit,
  OrganizationInput,
  OrganizationInvitation,
  OrganizationInvitationPreview,
  OrganizationMember,
  OrganizationRole,
  ResetOrganizationMemberPassword,
} from '@/types/organization'

export const listOrganizations = () => get<Organization[]>('/organizations')

export const createOrganization = (input: OrganizationInput) =>
  postJson<Organization>('/organizations', input)

export const updateOrganization = (
  id: number,
  input: Omit<OrganizationInput, 'code'>,
) => putJson<Organization>(`/organizations/${id}`, input)

function resolveMember(member: OrganizationMember): OrganizationMember {
  const avatarUrl = member.avatarUrl ? apiResourceUrl(member.avatarUrl) : null
  return avatarUrl === member.avatarUrl ? member : { ...member, avatarUrl }
}

function resolveMembers(members: OrganizationMember[]): OrganizationMember[] {
  return members.map(resolveMember)
}

export const listOrganizationMembers = async (id: number) =>
  resolveMembers(await get<OrganizationMember[]>(`/organizations/${id}/members`))

export const exportOrganizationMembers = (id: number) =>
  getFile(`/organizations/${id}/members/export`, `Regent-组织成员-${id}.xlsx`)

export const downloadOrganizationMemberTemplate = (id: number) =>
  getFile(
    `/organizations/${id}/members/import-template`,
    'Regent-组织成员导入模板.xlsx',
  )

export type OrganizationMemberImportResult = DownloadedFile & {
  total: number
  created: number
  added: number
  skipped: number
  failed: number
}

export async function importOrganizationMembers(
  id: number,
  file: File,
): Promise<OrganizationMemberImportResult> {
  const body = new FormData()
  body.append('file', file)
  const downloaded = await postFormFile(
    `/organizations/${id}/members/import`,
    body,
    'Regent-组织成员导入结果.xlsx',
  )
  const count = (name: string) => Number(downloaded.headers.get(name) ?? 0)
  return {
    ...downloaded,
    total: count('X-Regent-Import-Total'),
    created: count('X-Regent-Import-Created'),
    added: count('X-Regent-Import-Added'),
    skipped: count('X-Regent-Import-Skipped'),
    failed: count('X-Regent-Import-Failed'),
  }
}

export const addOrganizationMemberRole = async (
  id: number,
  userId: number,
  role: OrganizationRole,
) => resolveMembers(
  await postJson<OrganizationMember[]>(`/organizations/${id}/members/${userId}/roles`, { role }),
)

export const removeOrganizationMemberRole = (
  id: number,
  userId: number,
  role: OrganizationRole,
) => deleteRequest(`/organizations/${id}/members/${userId}/roles/${role}`)

export const removeOrganizationMember = (id: number, userId: number) =>
  deleteRequest(`/organizations/${id}/members/${userId}`)

export const resetOrganizationMemberPassword = (id: number, userId: number) =>
  post<ResetOrganizationMemberPassword>(
    `/organizations/${id}/members/${userId}/reset-password`,
  )

export const listOrganizationInvitations = (id: number) =>
  get<OrganizationInvitation[]>(`/organizations/${id}/invitations`)

export const inviteOrganizationMember = (
  id: number,
  email: string,
  role: OrganizationRole,
) => postJson<CreatedOrganizationInvitation>(`/organizations/${id}/invitations`, { email, role })

export const createOrganizationAccount = (
  id: number,
  email: string,
  displayName: string,
  role: OrganizationRole,
) => postJson<CreatedOrganizationAccount>(`/organizations/${id}/accounts`, {
  email,
  displayName,
  role,
})

export const revokeOrganizationInvitation = (id: number, invitationId: number) =>
  deleteRequest(`/organizations/${id}/invitations/${invitationId}`)

export const listOrganizationProjects = (id: number) =>
  get<Project[]>(`/organizations/${id}/projects`)

export const listOrganizationAudit = (id: number, limit = 50) =>
  get<OrganizationAudit[]>(`/organizations/${id}/audit`, { limit })

export const previewOrganizationInvitation = (token: string) =>
  get<OrganizationInvitationPreview>(`/invitations/${encodeURIComponent(token)}`)

export const acceptOrganizationInvitation = (
  token: string,
  input: { displayName?: string; password?: string } = {},
) =>
  postJson<AcceptedOrganizationInvitation>(
    `/invitations/${encodeURIComponent(token)}/accept`,
    input,
  )
