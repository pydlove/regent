import type { DeploymentServer, Project } from '@/types/api'

export type DeploymentServerSelection = {
  projectId: number | null
  servers: DeploymentServer[]
}

/**
 * 解析新部署弹框打开时的项目与服务器范围。
 *
 * 新部署必须先有服务器才能保存。若顶栏选中的项目没有服务器，而服务器页里已有其他项目的
 * 资源，就使用最新服务器的归属项目；否则用户会停在一个无法选择服务器的空表单。
 * 选中项目本身有服务器时仍保持原项目，不跨项目选择服务器。完全没有服务器时才保留
 * 明确选中的项目，方便用户直接为该项目新建第一台服务器。
 */
export function resolveDeploymentServerSelection(
  requestedProjectId: number | null,
  projects: readonly Pick<Project, 'id'>[],
  servers: readonly DeploymentServer[],
): DeploymentServerSelection {
  const requestedProjectHasServer =
    requestedProjectId !== null &&
    servers.some((server) => server.projectId === requestedProjectId)
  const projectId = requestedProjectHasServer
    ? requestedProjectId
    : servers[0]?.projectId ?? requestedProjectId ?? projects[0]?.id ?? null
  return {
    projectId,
    servers:
      projectId === null
        ? []
        : servers.filter((server) => server.projectId === projectId),
  }
}
