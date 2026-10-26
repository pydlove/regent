export type AuthUser = {
  id: number
  username: string
  displayName: string
  email: string | null
  avatarUrl: string | null
}

export type MobileAuthResponse = {
  user: AuthUser
  accessToken: string
  accessExpiresAt: string
  refreshToken: string
  refreshExpiresAt: string
}

export type MobileSession = MobileAuthResponse & {
  installationId: string
}

export type DeviceCodex = {
  available: boolean
  executablePath: string
  version: string
  message: string
  checkedAt: string
}

export type DevicePresence = {
  deviceId: string
  hostname: string
  platform: string
  executor: string
  agentInstanceId: string
  online: boolean
  codex: DeviceCodex
  clientRelease: string
  shellRelease: string
  lastSeenAt: string
  taskIds: number[]
}

export type Project = {
  id: number
  code: string
  name: string
  path: string
  defaultDecisionLevel: string
  enabled: boolean
}

export type TaskStatus =
  | 'PENDING'
  | 'QUEUED'
  | 'RUNNING'
  | 'BLOCKED'
  | 'DONE'
  | 'FAILED'
  | 'CANCELLED'

export type TaskStats = Record<TaskStatus, number>

export type TaskSummary = {
  id: number
  projectId: number
  projectName: string
  title: string
  status: TaskStatus
  level: string
  decisionId: number | null
  updatedAt: string
}

export type DecisionSummary = {
  id: number
  taskId: number
  taskTitle: string
  projectId: number
  projectName: string
  effectiveLevel: string
  status: string
  question: string
  recommendation: string
  raisedAt: string
}

export type PageResponse<T> = {
  items: T[]
  total: number
  page: number
  size: number
  totalPages: number
}

export type DashboardData = {
  device: DevicePresence | null
  projects: Project[]
  stats: TaskStats
  tasks: TaskSummary[]
  decisions: DecisionSummary[]
  loadedAt: string
}
