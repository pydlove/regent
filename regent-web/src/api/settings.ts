import { get, putJson } from '@/api/http'
import type { ExecutionSettings } from '@/types/api'

export const getExecutionSettings = () => get<ExecutionSettings>('/execution/settings')

export const updateExecutionSettings = (maxConcurrentTasks: number) =>
  putJson<ExecutionSettings>('/execution/settings', { maxConcurrentTasks })
