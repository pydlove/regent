import type { DeploymentServer, Project } from '@/types/api'

export type DeploymentServerSelection = {
  projectId: number | null
  servers: DeploymentServer[]
}

/**
 * 解析新部署弹框打开时的项目与服务器范围。
 *
 * 顶栏已经选定项目时必须保持该项目，即使它还没有服务器。部署不能擅自跨项目，
 * 否则表单会显示另一个项目的名称和服务器，用户很容易把部署建到错误项目下。
 * 项目没有服务器时保留空服务器列表，由用户在当前弹框里直接新建服务器。
 * 只有顶栏处于「全部项目」时才自动挑一个项目作为默认值。
 */
export function resolveDeploymentServerSelection(
  requestedProjectId: number | null,
  projects: readonly Pick<Project, 'id'>[],
  servers: readonly DeploymentServer[],
): DeploymentServerSelection {
  const projectId =
    requestedProjectId
    ?? servers[0]?.projectIds[0]
    ?? projects[0]?.id
    ?? null
  return {
    projectId,
    servers:
      projectId === null
        ? []
        : servers.filter((server) => server.projectIds.includes(projectId)),
  }
}
