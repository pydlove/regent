import type { DeploymentServer, Project } from '@/types/api'

export type DeploymentServerSelection = {
  projectId: number | null
  servers: DeploymentServer[]
}

/**
 * 解析新部署弹框打开时的项目与服务器范围。
 *
 * 顶栏选择“全部项目”时没有明确项目，若直接取项目列表第一项，会遇到“服务器页有数据，
 * 但该项目的服务器为空”。此时优先使用当前账号最新服务器的归属项目；只有没有服务器时
 * 才回退到第一个项目。已经指定项目时必须保持原项目，不能跨项目选择服务器。
 */
export function resolveDeploymentServerSelection(
  requestedProjectId: number | null,
  projects: readonly Pick<Project, 'id'>[],
  servers: readonly DeploymentServer[],
): DeploymentServerSelection {
  const projectId = requestedProjectId ?? servers[0]?.projectId ?? projects[0]?.id ?? null
  return {
    projectId,
    servers:
      projectId === null
        ? []
        : servers.filter((server) => server.projectId === projectId),
  }
}
