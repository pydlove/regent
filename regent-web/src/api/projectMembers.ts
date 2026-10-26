import { apiResourceUrl, deleteRequest, get, postJson, putJson } from '@/api/http'
import type {
  ProjectMember,
  ProjectMemberCandidate,
  ProjectMemberRole,
} from '@/types/projectMember'

function resolveMember(member: ProjectMember): ProjectMember {
  const avatarUrl = member.avatarUrl ? apiResourceUrl(member.avatarUrl) : null
  return avatarUrl === member.avatarUrl ? member : { ...member, avatarUrl }
}

function resolveMembers(members: ProjectMember[]): ProjectMember[] {
  return members.map(resolveMember)
}

export const listProjectMembers = async (projectId: number) =>
  resolveMembers(await get<ProjectMember[]>(`/projects/${projectId}/members`))

export const listProjectMemberCandidates = (projectId: number) =>
  get<ProjectMemberCandidate[]>(`/projects/${projectId}/members/candidates`)

export const addProjectMember = async (
  projectId: number,
  email: string,
  role: ProjectMemberRole,
) => resolveMembers(await postJson<ProjectMember[]>(`/projects/${projectId}/members`, { email, role }))

export const removeProjectMember = (
  projectId: number,
  userId: number,
  role: ProjectMemberRole,
) => deleteRequest(`/projects/${projectId}/members/${userId}/${role}`)

export const updateProjectMemberRoles = async (
  projectId: number,
  userId: number,
  roles: ProjectMemberRole[],
) => resolveMembers(
  await putJson<ProjectMember[]>(
    `/projects/${projectId}/members/${userId}/roles`,
    { roles },
  ),
)
