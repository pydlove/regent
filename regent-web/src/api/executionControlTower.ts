import { get } from '@/api/http'
import type { ExecutionControlTowerSnapshot } from '@/types/api'

export const getExecutionControlTower = (
  projectId: number | null = null,
  limit = 100,
) => get<ExecutionControlTowerSnapshot>('/execution-control-tower', { projectId, limit })
